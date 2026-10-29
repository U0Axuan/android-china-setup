package io.github.u0axuan.androidchinasetup

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Latency and bounded 128 KiB transfer rate, not a whole-file download. */
data class ProbeResult(
    val id: String,
    val name: String,
    val latencyMs: Long?,
    val throughputKBps: Long?,
    val detail: String
)

/** A user-triggered, limited HTTPS range request to each eligible source. */
class SourceBenchmarker {
    fun test(
        asset: ReleaseAsset,
        network: NetworkOptions,
        sources: SourceSelection,
        onResult: (ProbeResult) -> Unit
    ) {
        val choices = DownloadSources.candidates(
            asset, SourceSelection(DownloadSources.AUTO, sources.customs))
        val pool = Executors.newFixedThreadPool(5)
        try {
            val jobs = choices.map { source ->
                source to pool.submit<ProbeResult> {
                    val proxy = if (network.proxyEnabled) {
                        require(network.proxyPort in 1..65535 && network.proxyHost.isNotBlank()) {
                            "代理地址无效"
                        }
                        Proxy(
                            if (network.proxyType == "SOCKS5") Proxy.Type.SOCKS else Proxy.Type.HTTP,
                            InetSocketAddress(network.proxyHost, network.proxyPort))
                    } else Proxy.NO_PROXY
                    val target = URL(source.url)
                    require(target.protocol == "https") { "只允许 HTTPS" }
                    val connection = target.openConnection(proxy) as HttpURLConnection
                    try {
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.requestMethod = "GET"
                        connection.instanceFollowRedirects = true
                        connection.setRequestProperty("Range", "bytes=0-131071")
                        connection.setRequestProperty("User-Agent", "Android-China-Setup-SpeedTest")
                        val started = android.os.SystemClock.elapsedRealtime()
                        val code = connection.responseCode
                        val latency = android.os.SystemClock.elapsedRealtime() - started
                        if (code !in 200..299 || connection.url.protocol != "https") {
                            ProbeResult(source.id, source.label, null, null, "HTTP " + code)
                        } else {
                            val type = connection.contentType.orEmpty().lowercase()
                            if (type.contains("text/html")) {
                                ProbeResult(source.id, source.label, null, null, "响应不是 APK")
                            } else {
                                val buffer = ByteArray(8192)
                                var count = 0
                                val transferStart = android.os.SystemClock.elapsedRealtime()
                                connection.inputStream.use { input ->
                                    while (count < 131072) {
                                        val n = input.read(buffer, 0, minOf(buffer.size, 131072-count))
                                        if (n < 0) break
                                        count += n
                                    }
                                }
                                val ms = (android.os.SystemClock.elapsedRealtime()-transferStart).coerceAtLeast(1)
                                val rate = if (count >= 4096) count * 1000L / ms / 1024 else null
                                ProbeResult(source.id, source.label, latency, rate, "HTTP " + code)
                            }
                        }
                    } catch (e: Exception) {
                        ProbeResult(source.id, source.label, null, null,
                            e.javaClass.simpleName)
                    } finally { connection.disconnect() }
                }
            }
            jobs.forEach { (source, future) ->
                val result = try {
                    future.get(9, TimeUnit.SECONDS)
                } catch (_: Exception) {
                    future.cancel(true)
                    ProbeResult(source.id, source.label, null, null, "连接超时")
                }
                onResult(result)
            }
        } finally { pool.shutdownNow() }
    }
}
