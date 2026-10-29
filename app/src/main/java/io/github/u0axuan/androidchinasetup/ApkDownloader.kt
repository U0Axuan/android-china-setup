package io.github.u0axuan.androidchinasetup

import android.content.Context
import android.content.Intent
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
                if (name.endsWith(".apk") && !name.contains("debug") && !name.contains("source")
                    && !name.contains("x86") && !name.contains("armeabi-v7a")) {
                    candidates.add(asset)
                }
            }
            require(candidates.isNotEmpty()) { "无适合的 Android APK，请访问项目官网" }
            val preferred = candidates.filter {
                val name = it.optString("name").lowercase()
                name.contains("arm64") || name.contains("universal") || name.contains("all")
            }
            val chosen = if (preferred.size == 1) preferred.first()
                else if (candidates.size == 1) candidates.first()
                else error("存在多个 APK 构建版本，请前往项目发布页手动选择")
            val download = chosen.getString("browser_download_url")
            require(URL(download).host.equals("github.com", true)) { "发布资源来源不是 GitHub" }
            val digest = chosen.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
            return ReleaseAsset(download, chosen.getString("name"), digest)
        } finally { conn.disconnect() }
    }

    fun download(
        asset: ReleaseAsset,
        options: NetworkOptions,
        mirrorPrefix: String,
        progress: (Int) -> Unit,
        sourceStatus: (String) -> Unit
    ): File {
        val sources = DownloadSources.candidates(asset, mirrorPrefix)
        var lastError: Exception? = null
        for ((index, source) in sources.withIndex()) {
            sourceStatus("正在尝试：${source.label}（${index + 1}/${sources.size}）")
            try {
                return downloadSingle(asset, source.url, options, progress)
            } catch (e: Exception) {
                lastError = e
                sourceStatus("${source.label} 失败：${e.message}")
            }
        }
        throw lastError ?: IllegalStateException("没有可用的可信下载源")
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
