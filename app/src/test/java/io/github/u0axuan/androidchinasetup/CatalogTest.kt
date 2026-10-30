package io.github.u0axuan.androidchinasetup

import org.junit.Assert.*
import org.junit.Test

class CatalogTest {
    private fun app(name: String, category: String, risk: String = "", repo: String = "owner/repo",
                    mode: String = "auto"): CatalogApp = CatalogApp(
        name=name, category=category, summary="一句话简介", readmeSnapshot="README 摘要",
        homepage="https://github.com/owner/repo",
        readmeUrl="https://github.com/owner/repo#readme",
        repository=repo, risk=risk, downloadMode=mode
    )

    @Test fun keepsDeclaredCategoryOrder() {
        val apps = listOf(
            app("DroidDeck","游戏与影音"),
            app("KSuRoot","高级与 Root",risk="high"),
            app("Via","日常与效率"),
            app("osu!","游戏与影音")
        )
        val groups = Catalog.grouped(apps)
        assertEquals(listOf("日常与效率","游戏与影音","高级与 Root"),
            groups.map { it.first })
        assertEquals(listOf("DroidDeck","osu!"),groups[1].second.map { it.name })
    }

    @Test fun highRiskAndExternalProjectsHaveNoDirectInstallerAction() {
        assertFalse(app("DirtyFrag","高级与 Root",risk="high").canDownload)
        assertFalse(app("KSuRoot","高级与 Root",risk="high").canDownload)
        assertFalse(app("Via","日常与效率",mode="external").canDownload)
        assertTrue(app("osu!","游戏与影音").canDownload)
    }

    @Test fun unnamedUnknownCategoryIsStillAccessible() {
        val grouped = Catalog.grouped(listOf(app("X","其他")))
        assertEquals("其他",grouped.single().first)
    }
}
