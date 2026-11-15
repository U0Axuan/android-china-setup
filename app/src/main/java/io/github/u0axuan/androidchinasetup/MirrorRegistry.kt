package io.github.u0axuan.androidchinasetup

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** The registry lives in this project's GitHub repo; not a third-party scraping site. */
object MirrorRegistry {
    private const val URL_RAW = "https://raw.githubusercontent.com/U0Axuan/android-china-setup/main/catalog/mirrors.json"
    private const val CACHE = "mirror-registry"
    private const val DAY = "last-attempt-day"

    fun load(context: Context) {
        val json = context.getSharedPreferences(CACHE, Context.MODE_PRIVATE).getString("registry", null)
        json?.let { parse(it) }
    }

    fun refreshDaily(context: Context) {
        load(context)
        val preferences = context.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (preferences.getString(DAY, "") == today) return
        preferences.edit().putString(DAY, today).apply()
        val conn = URL(URL_RAW).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6500
            conn.readTimeout = 6500
            require(conn.responseCode == 200)
            require(conn.url.protocol == "https")
            require((conn.contentLengthLong in -1L..32768L))
            val json = conn.inputStream.bufferedReader().use { it.readText().take(32769) }
            require(json.length <= 32768)
            if (parse(json)) preferences.edit().putString("registry", json).apply()
        } finally { conn.disconnect() }
    }

    private fun parse(json: String): Boolean = runCatching {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion") == 1)
        val sources = root.getJSONArray("sources")
        require(sources.length() in 1..20)
        val parsed = (0 until sources.length()).map { i ->
            val item = sources.getJSONObject(i)
            MirrorSource(item.getString("id"), item.getString("name"), item.getString("prefix"))
        }
        require(parsed.all { DownloadSources.validPrefix(it.prefix) != null })
        DownloadSources.setCuratedPresets(parsed)
        true
    }.getOrDefault(false)
}
