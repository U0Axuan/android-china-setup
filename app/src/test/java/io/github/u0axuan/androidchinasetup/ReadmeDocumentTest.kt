package io.github.u0axuan.androidchinasetup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadmeDocumentTest {
    @Test fun htmlAndBadgesAreNotShownAsRawMarkup() {
        val source = """
            <div align="center">
              <img src="https://img.shields.io/badge/test-blue" alt="Build">
              <h1>Sample App</h1>
              <p>A readable <b>description</b>.</p>
            </div>

            ## Features
            - NFC automation
            - Local-first

            [![Status](https://img.shields.io/badge/status-blue)](https://example.com)
        """.trimIndent()
        val parts = ReadmeDocument.parse(source)
        val text = parts.joinToString("\n") { it.text }
        assertTrue(text.contains("Sample App"))
        assertTrue(text.contains("Features"))
        assertTrue(text.contains("NFC automation"))
        assertFalse(text.contains("<img"))
        assertFalse(text.contains("<div"))
        assertFalse(text.contains("img.shields.io"))
        assertFalse(text.contains("badge/status"))
    }

    @Test fun codeAndListsRetainTheirStructure() {
        val source = "# Project\n\n1. First\n2. Second\n\n~~~bash\necho hi\n~~~\n"
        val parts = ReadmeDocument.parse(source)
        assertEquals(ReadmePart.Type.HEADING, parts.first().type)
        assertTrue(parts.any { it.type == ReadmePart.Type.BULLET && it.text == "1. First" })
        assertTrue(parts.any { it.type == ReadmePart.Type.CODE && it.text == "echo hi" })
    }

    @Test fun dangerousHtmlIsNeverRendered() {
        val parts = ReadmeDocument.parse("<script>alert('x')</script>\n\nHello")
        assertFalse(parts.joinToString("") { it.text }.contains("alert("))
        assertTrue(parts.any { it.text == "Hello" })
    }

    @Test fun htmlTablesAndDetailsAreReadableWithoutTags() {
        val text = """
            <details><summary>Install instructions</summary>
              <p>Use the original release.</p>
              <table><tr><th>Platform</th><th>Package</th></tr>
              <tr><td>Android</td><td>APK</td></tr></table>
              <pre>adb install app.apk</pre>
            </details>
        """.trimIndent()
        val parts = ReadmeDocument.parse(text)
        val readable = parts.joinToString("\n") { it.text }
        assertTrue(readable.contains("Install instructions"))
        assertTrue(readable.contains("Android  |  APK"))
        assertTrue(parts.any { it.type == ReadmePart.Type.CODE && it.text.contains("adb install") })
        assertFalse(readable.contains("<details"))
        assertFalse(readable.contains("<table"))
    }

    @Test fun onlySafeRasterIconPathsAreAccepted() {
        assertTrue(Catalog.validLogoPath("app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp"))
        assertTrue(Catalog.validLogoPath(""))
        assertFalse(Catalog.validLogoPath("screenshot/logo.png"))
        assertFalse(Catalog.validLogoPath("docs/images/app-icon-512.png"))
        assertFalse(Catalog.validLogoPath("assets/graphics/icon_small.png"))
        assertFalse(Catalog.validLogoPath("../bad.png"))
        assertFalse(Catalog.validLogoPath("https://img.shields.io/badge.svg"))
        assertFalse(Catalog.validLogoPath("docs/images/brand.svg"))
        assertFalse(Catalog.validLogoPath("docs/../screenshots/test.png"))
    }
}
