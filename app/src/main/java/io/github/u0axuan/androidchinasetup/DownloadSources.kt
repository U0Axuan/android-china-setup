package io.github.u0axuan.androidchinasetup

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/** Public HTTPS GitHub Release URL-prefix mirrors. None is a GitHub service. */
data class MirrorSource(val id: String, val name: String, val prefix: String)

data class SourceChoice(val label: String, val url: String, val id: String)

data class SourceSelection(
    val selectedId: String = "auto",
    val customs: List<MirrorSource> = emptyList()
)

object DownloadSources {
    const val AUTO = "auto"
    const val OFFICIAL = "official"
    const val CATALOG_PAGE = "https://git.hiwiki.org/zh-CN/mirrors"

    // Community services may change or disappear; test availability at runtime.
    // This is a curated subset of commonly documented URL-prefix proxies,
    // NOT a claim that the website above exposes this exact source list.
    val presets: List<MirrorSource> = listOf(
        MirrorSource("ghfast", "GHFast", "https://ghfast.top"),
        MirrorSource("gh-proxy", "GH-Proxy", "https://gh-proxy.com"),
        MirrorSource("llkk", "LLKK", "https://gh.llkk.cc"),
        MirrorSource("ghproxy-net", "GHProxy.net", "https://ghproxy.net")
    )

    fun validPrefix(raw: String): String? {
        val value = raw.trim().trimEnd('/')
        if (value.isEmpty() || value.any { it.isWhitespace() }) return null
        val url = runCatching { URL(value) }.getOrNull() ?: return null
        if (url.protocol != "https" || url.host.isBlank() || url.userInfo != null ||
            url.query != null || url.ref != null || url.port != -1) return null
        // No IP literals or localhost; mirror endpoints must have a DNS name.
        if (!url.host.contains('.') || url.host.equals("localhost", true)) return null
        if (Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$").matches(url.host)) return null
        return value
    }

    fun uniqueCustomSources(sources: List<MirrorSource>): List<MirrorSource> =
        sources.filter { it.id.startsWith("custom-") && validPrefix(it.prefix) != null }
            .distinctBy { validPrefix(it.prefix) }
            .take(12)

    fun fromJson(raw: String): List<MirrorSource> = runCatching {
        val array = JSONArray(raw)
        val result = mutableListOf<MirrorSource>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result += MirrorSource(obj.getString("id"),
                obj.optString("name"), obj.getString("prefix"))
        }
        uniqueCustomSources(result)
    }.getOrDefault(emptyList())

    fun toJson(sources: List<MirrorSource>): String =
        JSONArray().apply {
            uniqueCustomSources(sources).forEach { source ->
                put(JSONObject().put("id", source.id).put("name", source.name)
                    .put("prefix", source.prefix))
            }
        }.toString()

    fun validatedDigest(asset: ReleaseAsset): Boolean =
        asset.sha256?.matches(Regex("[a-fA-F0-9]{64}")) == true

    fun candidates(asset: ReleaseAsset, selection: SourceSelection): List<SourceChoice> {
        val official = SourceChoice("GitHub 官方", asset.url, OFFICIAL)
        if (selection.selectedId == OFFICIAL || !validatedDigest(asset))
            return listOf(official)

        val all = presets + uniqueCustomSources(selection.customs)
        val mirrors = all.mapNotNull { source ->
            val prefix = validPrefix(source.prefix) ?: return@mapNotNull null
            SourceChoice(source.name, prefix + "/" + asset.url, source.id)
        }
        return when (selection.selectedId) {
            AUTO -> mirrors + official
            else -> listOfNotNull(mirrors.find { it.id == selection.selectedId }) + official
        }.distinctBy { it.url }
    }

    fun explain(asset: ReleaseAsset, selection: SourceSelection): String =
        if (!validatedDigest(asset))
            "该发布资源没有可校验的 SHA-256：为防止镜像篡改，仅使用 GitHub 官方源"
        else when (selection.selectedId) {
            OFFICIAL -> "仅使用 GitHub 官方源"
            AUTO -> "自动测速选择可用源；下载后核验官方 SHA-256"
            else -> "优先使用所选镜像；失败后回退官方源，并验证 SHA-256"
        }
}
