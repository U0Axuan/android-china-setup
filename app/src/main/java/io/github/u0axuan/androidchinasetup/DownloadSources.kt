package io.github.u0axuan.androidchinasetup

import java.net.URL

/**
 * Download policy:
 * - Official GitHub Releases URL is always available.
 * - A user-configured HTTPS mirror is considered only when GitHub provides a
 *   verifiable SHA-256 digest for the exact release asset.
 * - The mirror URL consists of a prefix followed by the official release URL.
 *   No mirror is bundled by default or silently trusted.
 */
data class SourceChoice(val label: String, val url: String)

object DownloadSources {
    fun candidates(asset: ReleaseAsset, mirrorPrefix: String): List<SourceChoice> {
        val official = SourceChoice("GitHub 官方发布源", asset.url)
        if (mirrorPrefix.isBlank() || asset.sha256 == null) return listOf(official)
        val prefix = mirrorPrefix.trim().trimEnd('/')
        val parsed = runCatching { URL(prefix) }.getOrNull() ?: return listOf(official)
        if (parsed.protocol != "https" || parsed.host.isBlank() ||
            parsed.userInfo != null || parsed.query != null || parsed.ref != null) return listOf(official)
        // Avoid embedding arbitrary values as separate proxy query parameters.
        val mirror = SourceChoice("自定义 HTTPS 镜像", prefix + "/" + asset.url)
        return listOf(official, mirror)
    }

    fun explain(asset: ReleaseAsset, mirrorPrefix: String): String =
        if (mirrorPrefix.isNotBlank() && asset.sha256 == null)
            "该发布包未提供可信 SHA-256，按安全策略禁用镜像，仅使用官方地址"
        else if (mirrorPrefix.isBlank()) "使用官方 GitHub 下载源"
        else "官方源优先；失败后尝试镜像，并校验 SHA-256"
}
