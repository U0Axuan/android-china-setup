package io.github.u0axuan.androidchinasetup

import android.content.Context
import org.json.JSONArray
import java.net.URI

/**
 * Immutable catalog entries. Snapshot copy is curated and stored in JSON,
 * never fetched or parsed while LazyColumn is scrolling.
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
    const val MAX_CATALOG_BYTES = 256 * 1024
    private val repoPattern = Regex("""^[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}$""")
    private val highRiskNames = setOf("GhostLock", "DirtyFrag", "KSuRoot")

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
        val ordered = categoryOrder + byCategory.keys.filter { it !in categoryOrder }.sorted()
        return ordered.distinct().mapNotNull { key -> byCategory[key]?.let { key to it } }
    }

    fun read(context: Context): List<CatalogApp> =
        parse(context.assets.open("apps.json").bufferedReader().use { it.readText() })

    private fun validHttps(value: String): Boolean = try {
        val uri = URI(value)
        val host = uri.host.orEmpty()
        uri.scheme.equals("https", ignoreCase = true) &&
            host.isNotBlank() && !host.equals("localhost", true) &&
            uri.userInfo == null && uri.port == -1 &&
            host.contains(".") &&
            !host.matches(Regex("""^\d+(?:\.\d+){3}$"""))
    } catch (_: Exception) {
        false
    }

    /** Same rules for bundled JSON, remote JSON and the CLI validator. */
    fun parse(json: String): List<CatalogApp> {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_CATALOG_BYTES) {
            "目录超过 256 KiB"
        }
        val raw = JSONArray(json)
        require(raw.length() in 1..300) { "目录数量必须为 1 至 300" }
        val result = (0 until raw.length()).map { index ->
            val item = raw.getJSONObject(index)
            CatalogApp(
                name = item.getString("name"),
                category = item.getString("category"),
                summary = item.getString("summary"),
                readmeSnapshot = item.getString("readmeSnapshot"),
                homepage = item.getString("homepage"),
                readmeUrl = item.getString("readmeUrl"),
                repository = item.optString("repository"),
                notice = item.optString("notice"),
                risk = item.optString("risk"),
                downloadMode = item.optString("downloadMode", "auto"),
                preferredApkRegex = item.optString("preferredApkRegex")
            )
        }
        require(result.map { it.name }.distinct().size == result.size) { "应用名称重复" }
        require(result.all { app ->
            app.name.isNotBlank() && app.name.length <= 70 &&
                app.category.isNotBlank() && app.category.length <= 40 &&
                app.summary.isNotBlank() && app.summary.length <= 260 &&
                app.readmeSnapshot.isNotBlank() && app.readmeSnapshot.length <= 900 &&
                app.notice.length <= 200 &&
                validHttps(app.homepage) && validHttps(app.readmeUrl) &&
                (app.repository.isBlank() || repoPattern.matches(app.repository)) &&
                app.downloadMode in setOf("auto", "external") &&
                app.risk in setOf("", "caution", "high") &&
                app.preferredApkRegex.length <= 160 &&
                (app.repository.isNotBlank() || app.downloadMode == "external") &&
                (app.risk != "high" || app.downloadMode == "external") &&
                (app.name !in highRiskNames || app.risk == "high")
        }) { "清单字段或 URL 不符合安全约束" }
        return result
    }
}
