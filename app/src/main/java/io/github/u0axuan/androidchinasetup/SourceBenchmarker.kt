package io.github.u0axuan.androidchinasetup

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ProbeResult(val id: String, val name: String, val latencyMs: Long?, val detail: String)

/**
 * Real HTTP connection-latency probes, not a bandwidth benchmark. Do not
 * download complete APKs just to benchmark download sources.
 */
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
                    val start = android.os.SystemClock.elapsedRealtime()
                    val proxy = if (network.proxyEnabled) {
                        require(network.proxyPort in 1..65535 && network.proxyHost.isNotBlank()) {
                            "代理地址无效"
                        }
                        Proxy(
                            if (network.proxyType == "SOCKS5") Proxy.Type.SOCKS else Proxy.Type.HTTP,
                            InetSocketAddress(network.proxyHost, network.proxyPort))
                    } else Proxy.NO_PROXY
                    val url = URL(source.url)
                    require(url.protocol == "https") { "仅支持 HTTPS" }
                    val connection = url.openConnection(proxy) as HttpURLConnection
                    try {
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.requestMethod = "HEAD"
                        connection.instanceFollowRedirects = true
                        connection.setRequestProperty("User-Agent", "Android-China-Setup-Benchmark")
                        val code = connection.responseCode
                        val elapsed = android.os.SystemClock.elapsedRealtime() - start
                        if (code in 200..299 && connection.url.protocol == "https")
                            ProbeResult(source.id, source.label, elapsed, "HTTP " + code)
                        else ProbeResult(source.id, source.label, null, "HTTP " + code)
                    } catch (e: Exception) {
                        ProbeResult(source.id, source.label, null, e.javaClass.simpleName)
                    } finally {
                        connection.disconnect()
                    }
                }
            }
            jobs.forEach { (source, future) ->
                val result = try {
                    future.get(8, TimeUnit.SECONDS)
                } catch (_: Exception) {
                    future.cancel(true)
                    ProbeResult(source.id, source.label, null, "连接超时")
                }
                onResult(result)
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
