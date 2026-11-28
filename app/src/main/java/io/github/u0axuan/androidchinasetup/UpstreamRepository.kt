package io.github.u0axuan.androidchinasetup

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Image identities are editorially verified. README images are never used as app icons. */
data class UpstreamInfo(val stars: Int? = null, val logo: String = "",
    val readme: String = "", val logoPath: String = "")

class UpstreamRepository(private val context: Context) {
    // v2 intentionally invalidates the old cache which contained guessed badges/screenshots.
    private val prefs = context.getSharedPreferences("upstream-metadata-v2", Context.MODE_PRIVATE)
    private val lock = Any()
    private fun key(repo: String) = repo.replace("/", "__")

    fun cached(repo: String, logoPath: String = ""): UpstreamInfo {
        if (repo.isBlank()) return UpstreamInfo()
        val json = synchronized(lock) { prefs.getString(key(repo), null) } ?: return UpstreamInfo()
        return runCatching {
            val obj = JSONObject(json)
            UpstreamInfo(
                stars = obj.optInt("stars").takeIf { it >= 0 },
                logo = obj.optString("logo").takeIf { logoPath.isNotBlank() &&
                    obj.optString("iconPath") == logoPath }.orEmpty(),
                readme = obj.optString("readme"),
                logoPath = obj.optString("iconPath")
            )
        }.getOrDefault(UpstreamInfo())
    }

    fun isStale(repo: String): Boolean {
        if (repo.isBlank()) return false
        val last = synchronized(lock) { prefs.getLong(key(repo) + "-time", 0L) }
        val elapsed = System.currentTimeMillis() - last
        return elapsed < 0 || elapsed >= 24L * 60 * 60 * 1000
    }

    private fun getJson(address: String): JSONObject {
        require(URL(address).protocol == "https" && URL(address).host == "api.github.com")
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6000
            connection.readTimeout = 7000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Android-China-Setup/0.7")
            require(connection.responseCode == 200) { "GitHub HTTP " + connection.responseCode }
            require(connection.contentLengthLong < 0 || connection.contentLengthLong <= 512000)
            val body = connection.inputStream.bufferedReader().use { it.readText().take(512001) }
            require(body.length <= 512000)
            return JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun verifyCuratedIcon(repo: String, branch: String, path: String): String {
        if (!Catalog.validLogoPath(path) || path.isBlank()) return ""
        val encodedRef = java.net.URLEncoder.encode(branch, "UTF-8")
        val entry = runCatching {
            getJson("https://api.github.com/repos/$repo/contents/$path?ref=$encodedRef")
        }.getOrNull() ?: return ""
        if (entry.optString("type") != "file") return ""
        val size = entry.optLong("size", -1)
        if (size !in 100..350000) return ""
        val raw = entry.optString("download_url")
        val expectedPrefix = "https://raw.githubusercontent.com/$repo/"
        val image = runCatching { URL(raw) }.getOrNull() ?: return ""
        if (image.protocol != "https" || image.host != "raw.githubusercontent.com" ||
            !raw.startsWith(expectedPrefix) || !image.path.endsWith("/$path")) return ""
        return raw
    }

    /** Called on a worker, not from a Compose composition or scrolling callback. */
    fun update(repo: String, logoPath: String = ""): UpstreamInfo {
        if (!Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$""").matches(repo)) return UpstreamInfo()
        val old = cached(repo, logoPath)
        val meta = runCatching { getJson("https://api.github.com/repos/$repo") }.getOrNull()
            ?: return old
        val branch = meta.optString("default_branch", "main").takeIf {
            it.matches(Regex("""[A-Za-z0-9_./-]+""")) && !it.contains("..")
        } ?: "main"

        val logo = if (logoPath.isBlank()) "" else old.logo.ifBlank {
            verifyCuratedIcon(repo, branch, logoPath)
        }
        val info = UpstreamInfo(meta.optInt("stargazers_count").takeIf { it >= 0 }, logo, old.readme, logoPath)
        synchronized(lock) {
            prefs.edit().putString(key(repo), JSONObject()
                .put("stars", info.stars ?: -1)
                .put("logo", info.logo)
                .put("iconPath", logoPath)
                .put("readme", info.readme).toString())
                .putLong(key(repo) + "-time", System.currentTimeMillis()).apply()
        }
        return info
    }
}
