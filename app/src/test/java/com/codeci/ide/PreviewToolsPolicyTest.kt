package com.codeci.ide

import com.codeci.ide.ui.services.*
import org.junit.Assert.*
import org.junit.Test

class PreviewToolsPolicyTest {
    @Test fun `all console levels including debug map without parsing message text`() {
        assertEquals(PreviewLevel.LOG, PreviewLevel.from("debug"))
        assertEquals(PreviewLevel.INFO, PreviewLevel.from("TIP"))
        assertEquals(PreviewLevel.WARN, PreviewLevel.from("warning"))
        assertEquals(PreviewLevel.ERROR, PreviewLevel.from("ERROR"))
        assertEquals(PreviewLevel.LOG, PreviewLevel.from("future-level"))
        val entry = PreviewToolsPolicy.console("log", "error: still a log", 7)
        assertEquals(PreviewLevel.LOG, entry.level)
    }
    @Test fun `filtering never destroys entries and all off is allowed`() {
        val entries = PreviewLevel.entries.map { PreviewConsoleEntry(it, "message", 0) }
        assertEquals(entries, PreviewToolsPolicy.visible(entries, PreviewLevel.entries.toSet()))
        assertEquals(listOf(entries.last()), PreviewToolsPolicy.visible(entries, setOf(PreviewLevel.ERROR)))
        assertTrue(PreviewToolsPolicy.visible(entries, emptySet()).isEmpty())
        assertEquals(4, entries.size)
    }
    @Test fun `bounded history retains newest entries`() {
        var entries = emptyList<Int>()
        repeat(250) { entries = PreviewToolsPolicy.append(entries, it) }
        assertEquals(200, entries.size)
        assertEquals(50, entries.first())
        assertEquals(249, entries.last())
    }
    @Test fun `console payloads and line numbers are bounded`() {
        val entry = PreviewToolsPolicy.console("log", "a".repeat(9000), -1)
        assertEquals(4096, entry.message.length)
        assertEquals(0, entry.line)
    }
    @Test fun `network strips secrets but preserves request kind and path`() {
        val entry = PreviewToolsPolicy.request("POST", "https://user:password@example.org:8443/api/data?q=secret#token", false)!!
        assertEquals("https://example.org:8443/api/data", entry.address)
        assertEquals("POST", entry.method)
        assertFalse(entry.mainFrame)
    }
    @Test fun `network rejects malformed local and payload urls`() {
        for (url in listOf("data:text/plain,secret", "blob:https://example.org/1", "file:///secret", "not a url", "https:///no-host")) {
            assertNull(url, PreviewToolsPolicy.request("GET", url, true))
        }
        assertNotNull(PreviewToolsPolicy.request("GET", "http://127.0.0.1:8080/main.html", true))
    }
    @Test fun `network preserves encoded paths without double encoding`() {
        assertEquals("https://example.org/a%20b%2Fc", PreviewToolsPolicy.request("GET", "https://example.org/a%20b%2Fc?key=x", true)!!.address)
    }
    @Test fun `panel clamps to the actual available height leaving page space`() {
        assertEquals(260f, PreviewToolsPolicy.panelHeight(900f, 400f), .01f)
        assertEquals(160f, PreviewToolsPolicy.panelHeight(1f, 400f), .01f)
        assertEquals(65f, PreviewToolsPolicy.panelHeight(240f, 100f), .01f)
        assertEquals(0f, PreviewToolsPolicy.panelHeight(240f, 0f), .01f)
        assertEquals(240f, PreviewToolsPolicy.panelHeight(Float.NaN, 1000f), .01f)
    }
    @Test fun `viewport fit never upscales or overflows either bound`() {
        assertEquals(.25f, PreviewToolsPolicy.fitScale(320f, 400f, 1280f, 720f), .001f)
        assertEquals(.1f, PreviewToolsPolicy.fitScale(500f, 64f, 360f, 640f), .001f)
        assertEquals(1f, PreviewToolsPolicy.fitScale(2000f, 2000f, 360f, 640f), .001f)
    }
    @Test fun `zoom presets have a reset and viewport choices retain device mode`() {
        assertEquals(listOf(50, 75, 100, 125, 150, 200), PreviewToolsPolicy.zoomPresets)
        assertEquals(0, PreviewResolution.DEVICE.widthDp)
        assertEquals(1280, PreviewResolution.DESKTOP.widthDp)
    }
}
