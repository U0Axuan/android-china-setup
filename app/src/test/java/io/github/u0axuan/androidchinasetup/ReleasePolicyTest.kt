package io.github.u0axuan.androidchinasetup

import org.junit.Assert.*
import org.junit.Test

class ReleasePolicyTest {
    @Test fun selectsSupportedArchitectureBeforeDownload() {
        val arm = "dsh-go-1.1-arm64.apk"
        val x64 = "tool-x86_64.apk"
        val variants = listOf(arm, x64)
        assertEquals(arm, ApkVariant.select(variants, listOf("arm64-v8a")))
        assertEquals(x64, ApkVariant.select(variants, listOf("x86_64")))
        assertThrows(IllegalStateException::class.java) { ApkVariant.select(listOf(arm), listOf("x86_64")) }
        assertThrows(IllegalStateException::class.java) { ApkVariant.select(variants, listOf("x86_64"), ".*arm64.*") }
        assertEquals("universal.apk", ApkVariant.select(listOf(arm, "universal.apk"), listOf("x86")))
        assertThrows(IllegalStateException::class.java) { ApkVariant.select(listOf("one.apk", "two.apk"), listOf("x86")) }
    }

    @Test fun retriesFailedRefreshAfterCooldown() {
        assertTrue(CatalogRepository.refreshDue(null, "today", 0, 2000))
        assertFalse(CatalogRepository.refreshDue(null, "today", 1000, 60999))
        assertTrue(CatalogRepository.refreshDue(null, "today", 1000, 61000))
        assertFalse(CatalogRepository.refreshDue("today", "today", 1000, 91000))
        assertTrue(CatalogRepository.refreshDue("yesterday", "today", 1000, 91000))
    }
}
