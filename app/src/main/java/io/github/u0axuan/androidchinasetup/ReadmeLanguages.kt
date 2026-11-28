package io.github.u0axuan.androidchinasetup

import java.util.Locale

internal object ReadmeLanguages {
    // Language tokens in filenames or a parent folder, e.g. docs/zh-CN/README.md.
    fun tag(path: String): String? {
        val file = path.substringAfterLast('/').lowercase(Locale.ROOT)
        if (!file.startsWith("readme") || !file.endsWith(".md")) return null
        val suffix = file.removeSuffix(".md").removePrefix("readme").trim('-', '_', '.', '(', ')', ' ')
        val token = suffix.ifBlank { path.substringBeforeLast('/', "").substringAfterLast('/') }
            .replace('_', '-').lowercase(Locale.ROOT)
        return when (token) {
            "zh", "cn", "chinese", "zh-cn", "zh-hans", "zh-chs", "简体中文", "中文" -> "zh-Hans"
            "tw", "zh-tw", "zh-hk", "zh-hant", "zh-cht", "繁體中文" -> "zh-Hant"
            "english" -> "en"
            else -> token.takeIf { it.substringBefore('-') in Locale.getISOLanguages() &&
                it.matches(Regex("[a-z]{2}(-[a-z]{2,4})?")) }
        }
    }

    fun linkedPaths(repo: String, ref: String, original: String, body: String): List<String> {
        val base = java.net.URI("https", "github.com", "/$repo/blob/$ref/$original", null)
        val prefix = "/$repo/blob/$ref/"
        val markdown = Regex("""\]\(\s*<?([^\s)>]+)""").findAll(body).map { it.groupValues[1] }
        val html = org.jsoup.Jsoup.parseBodyFragment(body).select("a[href]").map { it.attr("href") }
        return (markdown + html.asSequence()).mapNotNull { target ->
            val url = runCatching { (if (target.startsWith("/") && !target.startsWith("//") && !target.startsWith(prefix))
                base.resolve(prefix + target.drop(1)) else base.resolve(target)).normalize() }.getOrNull() ?: return@mapNotNull null
            val full = url.path ?: return@mapNotNull null
            val path = full.removePrefix(prefix)
            path.takeIf { url.scheme == "https" && url.host == "github.com" && full.startsWith(prefix) &&
                path.split('/').none { it == ".." || it == "." } && tag(path) != null }
        }.distinct().toList()
    }

    fun originalTag(body: String): String? {
        val header = body.take(1800)
        val active = org.jsoup.Jsoup.parseBodyFragment(header).select("b, strong").eachText()
        return when {
            "简体中文" in active -> "zh-Hans"
            "繁體中文" in active -> "zh-Hant"
            Regex("""(?i)\[English(?: \(Default\))?]\(\.?/?README\.md\)""").containsMatchIn(header) -> "en"
            else -> null
        }
    }

    fun preferred(paths: List<String>, original: String, originalTag: String? = null): String =
        if (originalTag == "zh-Hans") original else
        paths.firstOrNull { tag(it) == "zh-Hans" } ?: paths.firstOrNull { tag(it) == "zh-Hant" } ?: original

    fun label(path: String, explicitTag: String? = null): String = when (val tag = explicitTag ?: tag(path)) {
        "zh-Hans" -> "简体中文"
        "zh-Hant" -> "繁體中文"
        null -> "项目默认"
        else -> Locale.forLanguageTag(tag).getDisplayName(Locale.SIMPLIFIED_CHINESE)
    }
}
