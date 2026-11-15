package io.github.u0axuan.androidchinasetup

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

/** Untrusted upstream metadata is only used as display text/images, never to download APKs. */
data class UpstreamInfo(val stars: Int? = null, val logo: String = "", val readme: String = "")

class UpstreamRepository(private val context: Context) {
    private val lock = Any()
    private val prefs = context.getSharedPreferences("upstream-metadata-v1", Context.MODE_PRIVATE)

    private fun key(repo: String) = repo.replace("/", "__")
    fun cached(repo: String): UpstreamInfo {
        if (repo.isBlank()) return UpstreamInfo()
        val raw = synchronized(lock) { prefs.getString(key(repo), null) } ?: return UpstreamInfo()
        return runCatching {
            val v = JSONObject(raw)
            UpstreamInfo(
                v.optInt("stars").takeIf { it >= 0 },
                v.optString("logo"),
                v.optString("readme")
            )
        }.getOrDefault(UpstreamInfo())
    }

    fun isStale(repo: String): Boolean {
        if (repo.isBlank()) return false
        val last = synchronized(lock) { prefs.getLong(key(repo) + "-time", 0L) }
        val delta = System.currentTimeMillis() - last
        return delta < 0L || delta >= 24L * 60 * 60 * 1000
    }

    private fun getJson(address: String): JSONObject {
        val url = URL(address)
        require(url.protocol == "https" && url.host == "api.github.com")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6000
            connection.readTimeout = 7000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Android-China-Setup/0.7")
            require(connection.responseCode == 200) { "GitHub HTTP " + connection.responseCode }
            val text = connection.inputStream.bufferedReader().use { it.readText().take(240000) }
            return JSONObject(text)
        } finally { connection.disconnect() }
    }

    private fun originalLogo(readme: String, repo: String, branch: String): String {
        val html = Regex("""(?i)<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["']""").find(readme)?.groupValues?.get(1)
        val markdown = Regex("""!\[[^]]*]\(([^)\s]+)""").find(readme)?.groupValues?.get(1)
        val image = (html ?: markdown ?: "").trim().removePrefix("./")
        if (image.isBlank() || image.startsWith("data:")) return ""
        val candidate = when {
            image.startsWith("https://") -> image
            image.startsWith("/") || image.contains("..") || image.startsWith("#") -> return ""
            else -> "https://raw.githubusercontent.com/$repo/$branch/$image"
        }
        val url = runCatching { URL(candidate) }.getOrNull() ?: return ""
        return if (url.protocol == "https" && url.host in setOf(
                "raw.githubusercontent.com", "github.com", "avatars.githubusercontent.com",
                "user-images.githubusercontent.com")) candidate else ""
    }

    /** Network calls are deliberately synchronous; invoke from a worker thread only. */
    fun update(repo: String, includeReadme: Boolean = false): UpstreamInfo {
        if (!Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$""").matches(repo)) return cached(repo)
        val old = cached(repo)
        val metadata = runCatching { getJson("https://api.github.com/repos/$repo") }.getOrNull()
            ?: return old
        val branch = metadata.optString("default_branch", "main").takeIf {
            it.matches(Regex("""[A-Za-z0-9_.\-/]+""")) && !it.contains("..")
        } ?: "main"
        var readme = old.readme
        var logo = old.logo
        if (includeReadme || logo.isEmpty()) {
            val readmeData = runCatching { getJson("https://api.github.com/repos/$repo/readme") }.getOrNull()
            val markdown = runCatching {
                val encoded = readmeData?.optString("content").orEmpty()
                if (encoded.isBlank()) "" else String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                    .take(48000)
            }.getOrDefault("")
            if (markdown.isNotBlank()) {
                if (includeReadme) readme = markdown
                val detected = originalLogo(markdown, repo, branch)
                if (detected.isNotBlank()) logo = detected
            }
        }
        val value = UpstreamInfo(metadata.optInt("stargazers_count").takeIf { it >= 0 }, logo, readme)
        synchronized(lock) {
            prefs.edit().putString(key(repo), JSONObject()
                .put("stars", value.stars ?: -1)
                .put("logo", value.logo)
                .put("readme", value.readme).toString())
                .putLong(key(repo) + "-time", System.currentTimeMillis()).apply()
        }
        return value
    }
}
