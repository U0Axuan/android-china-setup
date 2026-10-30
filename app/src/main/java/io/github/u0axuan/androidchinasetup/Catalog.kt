package io.github.u0axuan.androidchinasetup

import android.content.Context
import org.json.JSONArray

/**
 * Offline, source-linked catalog. No README network reads happen while scrolling.
 * Snapshot text is a short editorial summary of the upstream README, not a live copy.
 */
data class CatalogApp(
    val name: String,
    val category: String,
    val summary: String,
    val readmeSnapshot: String,
    val homepage: String,
    val readmeUrl: String,
    val repository: String = "",
    val notice: String = "",
    val risk: String = "",
    val downloadMode: String = "auto",
    val preferredApkRegex: String = ""
) {
    val canDownload: Boolean get() =
        repository.isNotBlank() && downloadMode == "auto" && risk != "high"
    val externalOnly: Boolean get() = !canDownload
}

object Catalog {
    val categoryOrder = listOf(
        "日常与效率",
        "游戏与影音",
        "网络与连接",
        "应用与文件",
        "系统与调试",
        "高级与 Root"
    )

    fun grouped(apps: List<CatalogApp>): List<Pair<String, List<CatalogApp>>> {
        val byCategory = apps.groupBy { it.category }
        val all = categoryOrder + byCategory.keys.filter { it !in categoryOrder }.sorted()
        return all.distinct().mapNotNull { key ->
            byCategory[key]?.let { key to it }
        }
    }

    fun read(context: Context): List<CatalogApp> {
        val raw = context.assets.open("apps.json").bufferedReader().use { it.readText() }
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            CatalogApp(
                name = item.getString("name"),
                category = item.getString("category"),
                summary = item.optString("summary"),
                readmeSnapshot = item.optString("readmeSnapshot"),
                homepage = item.getString("homepage"),
                readmeUrl = item.optString("readmeUrl", item.getString("homepage")),
                repository = item.optString("repository"),
                notice = item.optString("notice"),
                risk = item.optString("risk"),
                downloadMode = item.optString("downloadMode", "auto"),
                preferredApkRegex = item.optString("preferredApkRegex")
            )
        }.also { apps ->
            require(apps.map { it.name }.distinct().size == apps.size) {
                "重复的应用名称"
            }
            require(apps.all { it.homepage.startsWith("https://") &&
                 it.readmeUrl.startsWith("https://") &&
                 it.summary.isNotBlank() && it.readmeSnapshot.isNotBlank() }) {
                "应用目录存在缺失或非 HTTPS 地址"
            }
        }
    }
}
