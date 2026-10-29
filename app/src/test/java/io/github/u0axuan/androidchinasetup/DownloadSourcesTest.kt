package io.github.u0axuan.androidchinasetup

import org.junit.Assert.*
import org.junit.Test

class DownloadSourcesTest {
    private val official = "https://github.com/example/project/releases/download/v1/app.apk"
    private val digest = "a".repeat(64)

    @Test fun androidMirrorListIncludesKnownPresets() {
        assertEquals(4, DownloadSources.presets.size)
        assertTrue(DownloadSources.presets.all { DownloadSources.validPrefix(it.prefix) != null })
    }

    @Test fun rejectsUntrustedOrInvalidCustomMirrorUrls() {
        listOf("http://ghfast.top", "https://localhost", "https://127.0.0.1",
            "https://user:password@ghfast.top", "https://ghfast.top?a=1",
            "javascript:evil", "ftp://example.org").forEach { url ->
            assertNull(url, DownloadSources.validPrefix(url))
        }
        assertEquals("https://mirror.example.com", DownloadSources.validPrefix(
            "https://mirror.example.com/"))
    }

    @Test fun missingChecksumDisablesEveryMirror() {
        val asset = ReleaseAsset(official, "app.apk", null)
        val candidates = DownloadSources.candidates(asset, SourceSelection("ghfast"))
        assertEquals(listOf(DownloadSources.OFFICIAL), candidates.map { it.id })
    }

    @Test fun chosenMirrorIsFirstAndOfficialAlwaysFallback() {
        val asset = ReleaseAsset(official, "app.apk", digest)
        val candidates = DownloadSources.candidates(asset, SourceSelection("ghfast"))
        assertEquals(listOf("ghfast", DownloadSources.OFFICIAL), candidates.map { it.id })
        assertEquals("https://ghfast.top/" + official, candidates[0].url)
    }

    @Test fun automaticSelectionCoversAllPresetMirrors() {
        val asset = ReleaseAsset(official, "app.apk", digest)
        val candidates = DownloadSources.candidates(asset, SourceSelection())
        assertEquals(5, candidates.size)
        assertEquals(DownloadSources.OFFICIAL, candidates.last().id)
    }

    @Test fun customMirrorCanBeSelectedButNotWithoutChecksum() {
        val custom = MirrorSource("custom-123", "User Mirror", "https://mirror.example.com")
        val asset = ReleaseAsset(official, "app.apk", digest)
        val candidates = DownloadSources.candidates(asset, SourceSelection(custom.id, listOf(custom)))
        assertEquals("custom-123", candidates.first().id)
        assertEquals("https://mirror.example.com/" + official, candidates.first().url)
        assertEquals(DownloadSources.OFFICIAL, candidates.last().id)
    }

    @Test fun invalidDigestIsNotTreatedAsTrustedChecksum() {
        val candidates = DownloadSources.candidates(ReleaseAsset(official, "app.apk", "unknown"),
            SourceSelection("ghfast"))
        assertEquals(1, candidates.size)
        assertEquals(DownloadSources.OFFICIAL, candidates[0].id)
    }
}
