package io.github.u0axuan.androidchinasetup

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleasePageTest {
    @Test fun requiresDigestInTheSameOfficialReleaseRow() {
        val digest = "sha256:" + "a".repeat(64)
        val html = """<ul>
            <li><a href="/o/r/releases/download/v1/app.apk">app.apk</a><code>$digest</code></li>
            <li><a href="/o/r/releases/download/v1/unsigned.apk">unsigned.apk</a></li>
            <li><a href="/other/r/releases/download/v1/mixed.apk">mixed.apk</a>$digest</li>
            </ul>"""
        val assets = ReleasePage.assets(Jsoup.parse(html), "o/r", "v1")
        assertEquals(1, assets.length())
        assertEquals(digest, assets.getJSONObject(0).getString("digest"))
        assertEquals("app.apk", assets.getJSONObject(0).getString("name"))
    }
}
