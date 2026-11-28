package io.github.u0axuan.androidchinasetup

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadmeLanguagesTest {
    @Test fun localSendRootLinkAndMaterialFilesRelativeLink() {
        val repo = "localsend/localsend"
        val body = "[中文](/support/readme/README_ZH.md) [本文中文版](README_zh-CN.md)"
        assertEquals(listOf("support/readme/README_ZH.md", "README_zh-CN.md"),
            ReadmeLanguages.linkedPaths(repo, "main", "README.md", body))
    }

    @Test fun dshGoKeepsChineseOriginalAndFindsEnglish() {
        val body = """<p><b>简体中文</b> · <a href="README.en.md">English</a></p>"""
        val choices = ReadmeLanguages.linkedPaths("ccch1mneyyy/dsh-Go", "main", "README.md", body)
        assertEquals(listOf("README.en.md"), choices)
        assertEquals("zh-Hans", ReadmeLanguages.originalTag(body))
        assertEquals("README.md", ReadmeLanguages.preferred(choices, "README.md", "zh-Hans"))
    }

    @Test fun prioritizesChineseAndIgnoresExternalLinks() {
        val paths = listOf("README.en.md", "docs/zh-TW/README.md", "README_CN.md")
        assertEquals(paths.last(), ReadmeLanguages.preferred(paths, "README.md"))
        assertEquals(paths[1], ReadmeLanguages.preferred(paths.take(2), "README.md"))
        assertEquals("README.md", ReadmeLanguages.preferred(paths.take(1), "README.md"))
        assertEquals(emptyList<String>(), ReadmeLanguages.linkedPaths("owner/app", "main", "README.md",
            "[中文](https://outside.invalid/README.zh.md) [跳转](../../other/README.zh.md)"))
    }
}
