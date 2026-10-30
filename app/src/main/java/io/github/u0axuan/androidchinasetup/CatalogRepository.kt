package io.github.u0axuan.androidchinasetup

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

data class CatalogRefreshResult(val apps: List<CatalogApp>, val message: String)

/**
 * Live catalog independent from APK releases.
 * HTTPS GitHub JSON -> strict validation -> atomic cache; bundled offline fallback.
 * Never fetch or parse README on the main/scroll thread.
 */
object CatalogRepository {
    private const val RAW_URL =
        "https://raw.githubusercontent.com/U0Axuan/android-china-setup/refs/heads/main/catalog/apps.json"
    private const val API_URL =
        "https://api.github.com/repos/U0Axuan/android-china-setup/contents/catalog/apps.json?ref=main"
    private const val PREFS = "catalog-update"
    private const val LAST_ATTEMPT = "last_attempt"
    private const val INTERVAL_MS = 12L * 60 * 60 * 1000
    private const val MAX_HTTP_BYTES = 384 * 1024
    private const val CACHE_NAME = "catalog-cache.json"

    fun shouldAutoRefresh(context: Context): Boolean {
        val previous = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(LAST_ATTEMPT, 0L)
        return System.currentTimeMillis() - previous > INTERVAL_MS
    }

    /** Read last validated data, or the APK-bundled catalog when offline. */
    fun load(context: Context, bundled: List<CatalogApp>): List<CatalogApp> {
        return runCatching {
            val bytes = AtomicFile(File(context.filesDir, CACHE_NAME))
                .openRead().use { it.readBytes() }
            require(bytes.size <= Catalog.MAX_CATALOG_BYTES)
            Catalog.parse(bytes.toString(Charsets.UTF_8))
        }.getOrDefault(bundled)
    }

    fun refresh(context: Context, options: NetworkOptions): CatalogRefreshResult {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(LAST_ATTEMPT, System.currentTimeMillis()).apply()
        val failures = mutableListOf<String>()
        for (endpoint in listOf(RAW_URL, API_URL)) {
            try {
                val json = fetchJson(endpoint, options)
                val apps = Catalog.parse(json)
                val cache = AtomicFile(File(context.filesDir, CACHE_NAME))
                val stream = cache.startWrite()
                try {
                    stream.write(json.toByteArray(Charsets.UTF_8))
                    cache.finishWrite(stream)
                } catch (e: Exception) {
                    cache.failWrite(stream)
                    throw e
                }
                return CatalogRefreshResult(apps, "已同步 ${apps.size} 个项目 · 无需升级 APK")
            } catch (e: Exception) {
                failures += e.javaClass.simpleName
            }
        }
        throw IllegalStateException("无法连接在线目录（${failures.joinToString("/")}），仍可使用离线或已缓存目录")
    }

    private fun networkProxy(options: NetworkOptions): Proxy {
        if (!options.proxyEnabled) return Proxy.NO_PROXY
        require(options.proxyHost.isNotBlank() && options.proxyPort in 1..65535) {
            "代理地址无效"
        }
        return Proxy(
            if (options.proxyType == "SOCKS5") Proxy.Type.SOCKS else Proxy.Type.HTTP,
            InetSocketAddress(options.proxyHost, options.proxyPort)
        )
    }

    private fun fetchJson(endpoint: String, options: NetworkOptions): String {
        val connection = URL(endpoint).openConnection(networkProxy(options)) as HttpURLConnection
        try {
            connection.connectTimeout = 6500
            connection.readTimeout = 8500
            connection.setRequestProperty("User-Agent", "Android-China-Setup-Catalog")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "HTTP ${connection.responseCode}"
            }
            require(connection.url.protocol == "https") { "目录必须通过 HTTPS 下载" }
            val data = readBounded(connection, MAX_HTTP_BYTES)
            return if (endpoint == API_URL) {
                val json = JSONObject(data.toString(Charsets.UTF_8))
                require(json.getString("encoding") == "base64")
                val decoded = Base64.decode(json.getString("content"), Base64.DEFAULT)
                require(decoded.size <= Catalog.MAX_CATALOG_BYTES)
                decoded.toString(Charsets.UTF_8)
            } else {
                require(data.size <= Catalog.MAX_CATALOG_BYTES)
                data.toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(connection: HttpURLConnection, maxBytes: Int): ByteArray {
        require(connection.contentLengthLong <= maxBytes ||
            connection.contentLengthLong < 0) { "在线目录超出容量限制" }
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                require(out.size() + count <= maxBytes) { "在线目录超出容量限制" }
                out.write(buffer, 0, count)
            }
        }
        return out.toByteArray()
    }
}
