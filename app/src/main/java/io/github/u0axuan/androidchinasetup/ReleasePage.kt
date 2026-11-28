package io.github.u0axuan.androidchinasetup

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.net.URI

internal object ReleasePage {
    fun assets(page: Document, repo: String, tag: String): JSONArray {
        val prefix = "/$repo/releases/download/$tag/"
        val result = JSONArray()
        for (row in page.select("li")) {
            val link = row.select("a[href]").firstOrNull { it.attr("href").startsWith(prefix) } ?: continue
            val path = link.attr("href")
            val name = runCatching { URI(path).path.substringAfterLast('/') }.getOrNull() ?: continue
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val digest = Regex("""sha256:[0-9a-fA-F]{64}""").find(row.text())?.value ?: continue
            result.put(JSONObject().put("name", name).put("digest", digest.lowercase())
                .put("browser_download_url", "https://github.com$path"))
        }
        require(result.length() > 0) { "官方发布页未提供可校验的 APK，请稍后重试" }
        return result
    }
}
