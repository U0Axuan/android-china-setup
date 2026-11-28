package io.github.u0axuan.androidchinasetup

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class ReadmeIndex(val original: String, val paths: List<String>, val ref: String, val originalTag: String?)

internal class ReadmeReader(context: Context, private val repo: String) {
    private val prefs = context.getSharedPreferences("readme-languages-v1", Context.MODE_PRIVATE)
    private val key = "$repo/"
    private fun request(url: String, limit: Int): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 7000
            conn.setRequestProperty("User-Agent", "Android-China-Setup")
            require(conn.responseCode == 200)
            return conn.inputStream.bufferedReader().use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(4096)
                while (out.length < limit) {
                    val n = reader.read(buffer, 0, minOf(buffer.size, limit - out.length))
                    if (n < 0) break
                    out.append(buffer, 0, n)
                }
                out.toString()
            }
        } finally { conn.disconnect() }
    }

    fun choice(): String? = prefs.getString(key + ":choice", null)
    fun select(path: String) = prefs.edit().putString(key + ":choice", path).apply()

    fun cachedIndex(): ReadmeIndex? = runCatching {
        val data = JSONObject(prefs.getString(key + ":index", null) ?: return null)
        val paths = data.getJSONArray("paths")
        ReadmeIndex(data.getString("original"), List(paths.length()) { paths.getString(it) }, data.getString("ref"), data.optString("originalTag").ifBlank { null })
    }.getOrNull()

    fun discover(): ReadmeIndex {
        val cached = cachedIndex()
        if (cached != null && System.currentTimeMillis() - prefs.getLong(key + ":time", 0) in 0L..86_400_000L) return cached
        require(repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))
        val base = "https://api.github.com/repos/$repo"
        val entry = JSONObject(request("$base/readme", 1_000_000))
        val original = entry.getString("path")
        val raw = entry.getString("download_url")
        val prefix = "https://raw.githubusercontent.com/$repo/"
        require(raw.startsWith(prefix, ignoreCase = true))
        val location = Uri.decode(raw.substring(prefix.length))
        require(location.endsWith("/$original"))
        val ref = location.removeSuffix("/$original")
        require(ref.isNotBlank() && !ref.contains(".."))
        val body = String(Base64.decode(entry.getString("content"), Base64.DEFAULT), Charsets.UTF_8).take(48000)
        if (body.isNotBlank()) save(original, body)
        val paths = linkedSetOf(original)
        paths.addAll(ReadmeLanguages.linkedPaths(repo, ref, original, body))
        val directory = Uri.encode(original.substringBeforeLast('/', ""), "/")
        val listing = runCatching {
            JSONArray(request("$base/contents/$directory?ref=${Uri.encode(ref)}", 1_000_000))
        }.getOrNull()
        for (i in 0 until (listing?.length() ?: 0)) {
            val item = listing!!.getJSONObject(i)
            val path = item.optString("path")
            if (item.optString("type") == "file" && ReadmeLanguages.tag(path) != null) paths.add(path)
        }
        val index = ReadmeIndex(original, paths.toList(), ref, ReadmeLanguages.originalTag(body))
        prefs.edit().putString(key + ":index", JSONObject().put("original", original)
            .put("originalTag", index.originalTag).put("ref", ref).put("paths", JSONArray(index.paths)).toString())
            .putLong(key + ":time", if (listing == null) 0L else System.currentTimeMillis()).apply()
        return index
    }

    fun cached(path: String) = prefs.getString(key + path, "").orEmpty()
    private fun save(path: String, body: String) {
        prefs.edit().putString(key + path, body).putLong(key + path + ":time", System.currentTimeMillis()).apply()
    }
    fun read(index: ReadmeIndex, path: String): String {
        require(path in index.paths)
        val cached = cached(path)
        if (cached.isNotBlank() && System.currentTimeMillis() - prefs.getLong(key + path + ":time", 0) in 0L..86_400_000L) return cached
        return request("https://raw.githubusercontent.com/$repo/${Uri.encode(index.ref, "/")}/${Uri.encode(path, "/")}", 48000)
            .also { require(it.isNotBlank()); save(path, it) }
    }
}
