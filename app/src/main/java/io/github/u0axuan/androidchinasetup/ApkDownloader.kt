package io.github.u0axuan.androidchinasetup

import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.security.MessageDigest

data class NetworkOptions(
    val proxyEnabled: Boolean = false,
    val proxyType: String = "HTTP",
    val proxyHost: String = "",
    val proxyPort: Int = 0
)

data class ReleaseAsset(val url: String, val name: String, val sha256: String?)

/**
 * Only GitHub-hosted releases are automatically resolved.
 * No untrusted arbitrary download mirror is silently selected.
 */
class ApkDownloader(private val context: Context) {
    private fun connection(url: String, options: NetworkOptions): HttpURLConnection {
        require(URL(url).protocol == "https") { "仅允许 HTTPS 下载" }
        val proxy = if (options.proxyEnabled) {
            require(options.proxyHost.isNotBlank() && options.proxyPort in 1..65535) { "请填写有效的代理地址和端口" }
            val kind = if (options.proxyType == "SOCKS5") Proxy.Type.SOCKS else Proxy.Type.HTTP
            Proxy(kind, InetSocketAddress(options.proxyHost, options.proxyPort))
        } else Proxy.NO_PROXY
        return (URL(url).openConnection(proxy) as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 25000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Android-China-Setup/0.2")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
    }

    fun findLatestApk(repository: String, options: NetworkOptions): ReleaseAsset {
        require(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(repository)) { "项目地址不合法" }
        val api = "https://api.github.com/repos/" + repository + "/releases/latest"
        val conn = connection(api, options)
        try {
            require(conn.responseCode == 200) { "无法获取项目发布信息：HTTP " + conn.responseCode }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            val candidates = mutableListOf<JSONObject>()
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name").lowercase()
                if (name.endsWith(".apk") && !name.contains("debug") &&
                    !name.contains("source")) {
                    candidates.add(asset)
                }
            }
            require(candidates.isNotEmpty()) { "无 APK 发布文件，请访问项目官网" }
            // Prefer normal upstream signatures over distinct F-Droid distribution
            // variants where both kinds exist. Never mix signatures on updates.
            val ordinary = candidates.filter {
                !it.optString("name").contains("fdroid", ignoreCase = true) &&
                !it.optString("name").contains("google-play", ignoreCase = true)
            }
            val pool = ordinary.ifEmpty { candidates }
            fun abiMatch(name: String, abi: String): Boolean = when (abi) {
                "arm64-v8a" -> name.contains("arm64") || name.contains("aarch64")
                "armeabi-v7a" -> name.contains("armeabi-v7a") || name.contains("arm32v7")
                "x86_64" -> name.contains("x86_64") || name.contains("x64")
                "x86" -> Regex("(^|[-_.])x86([-_.]|$)").containsMatchIn(name)
                else -> false
            }
            val perDevice = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
                pool.filter { abiMatch(it.optString("name").lowercase(), abi) }
                    .singleOrNull()
            }
            val universal = pool.filter {
                val name = it.optString("name").lowercase()
                name.contains("universal") || name.contains("noarch") ||
                    name.contains("all-arch") || name == "app-release.apk"
            }.singleOrNull()
            val chosen = perDevice ?: universal ?: pool.singleOrNull()
                ?: error("存在多个版本且无法确定匹配架构，请访问项目发布页手动选择")
            val download = chosen.getString("browser_download_url")
            require(URL(download).host.equals("github.com", true)) { "发布资源来源不是 GitHub" }
            val digest = chosen.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
            return ReleaseAsset(download, chosen.getString("name"), digest)
        } finally { conn.disconnect() }
    }

    /**
     * Explicit selection is tried first; AUTO probes all configured public
     * sources concurrently and ranks reachable ones by response latency.
     * Checksums come from the official GitHub Release metadata, never mirrors.
     */
    fun download(
        asset: ReleaseAsset,
        options: NetworkOptions,
        selection: SourceSelection,
        progress: (Int) -> Unit,
        sourceStatus: (String) -> Unit
    ): File {
        val candidates = DownloadSources.candidates(asset, selection)
        val sources = if (selection.selectedId == DownloadSources.AUTO &&
            candidates.size > 1) {
            sourceStatus("正在测速 ${candidates.size} 个下载源…")
            orderByConnectivity(candidates, options)
        } else candidates
        var lastError: Exception? = null
        for ((index, source) in sources.withIndex()) {
            sourceStatus("正在尝试：${source.label}（${index + 1}/${sources.size}）")
            try {
                return downloadSingle(asset, source.url, options, progress)
            } catch (e: Exception) {
                lastError = e
                sourceStatus("${source.label} 下载失败：${e.message}")
            }
        }
        throw lastError ?: IllegalStateException("没有可用的下载源")
    }

    private fun orderByConnectivity(
        candidates: List<SourceChoice>,
        options: NetworkOptions
    ): List<SourceChoice> {
        val service = java.util.concurrent.Executors.newFixedThreadPool(
            candidates.size.coerceAtMost(5))
        return try {
            val jobs = candidates.map { source ->
                source to service.submit<Long> {
                    val start = android.os.SystemClock.elapsedRealtime()
                    val conn = connection(source.url, options).apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                        requestMethod = "HEAD"
                    }
                    try {
                        val code = conn.responseCode
                        if (code !in 200..299) Long.MAX_VALUE
                        else android.os.SystemClock.elapsedRealtime() - start
                    } catch (_: Exception) {
                        Long.MAX_VALUE
                    } finally { conn.disconnect() }
                }
            }
            // All probes run at once, so total timeout stays bounded.
            val scored = jobs.map { (source, future) ->
                val ms = try {
                    future.get(7, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: Exception) {
                    future.cancel(true)
                    Long.MAX_VALUE
                }
                source to ms
            }
            // Fastest valid connection first. Failed mirrors remain fallback;
            // official GitHub is always available as the final alternative.
            scored.sortedWith(compareBy<Pair<SourceChoice, Long>> {
                if (it.second == Long.MAX_VALUE) 1 else 0
            }.thenBy { it.second }
                .thenBy { if (it.first.id == DownloadSources.OFFICIAL) 1 else 0 })
                .map { it.first }
        } finally {
            service.shutdownNow()
        }
    }

    private fun downloadSingle(asset: ReleaseAsset, sourceUrl: String,
        options: NetworkOptions, progress: (Int) -> Unit): File {
        val folder = File(context.cacheDir, "apk_downloads").apply { mkdirs() }
        val target = File(folder, "pending.apk")
        val connection = connection(sourceUrl, options)
        try {
            target.delete()
            require(connection.responseCode == 200) { "下载失败：HTTP " + connection.responseCode }
            val total = connection.contentLengthLong
            require(total <= 250L * 1024 * 1024 || total == -1L) { "文件过大" }
            val md = MessageDigest.getInstance("SHA-256")
            var count = 0L
            connection.inputStream.use { stream ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        count += n
                        require(count <= 250L * 1024 * 1024) { "超出文件大小限制" }
                        output.write(buffer, 0, n)
                        md.update(buffer, 0, n)
                        if (total > 0) progress((count * 100 / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            require(count > 0) { "下载文件为空" }
            require(total == -1L || count == total) { "下载内容不完整" }
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            asset.sha256?.let { require(actual.equals(it, true)) { "SHA-256 校验失败" } }
            // Android Package Installer validates package structure and signing.
            require(context.packageManager.getPackageArchiveInfo(target.absolutePath, 0) != null) {
                "下载内容不是有效的 APK"
            }
            progress(100)
            return target
        } catch (t: Throwable) {
            target.delete()
            throw t
        } finally { connection.disconnect() }
    }

    fun requestInstall(file: File) {
        val uri = FileProvider.getUriForFile(context,
            context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openInstallSettings() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
