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
        // Phase 72.1 — the floor is a strip plus an input line now, so it is
        // 220 dp where the space exists and 65 % where it does not.
        assertEquals(260f, PreviewToolsPolicy.panelHeight(900f, 400f), .01f)
        assertEquals(220f, PreviewToolsPolicy.panelHeight(1f, 400f), .01f)
        assertEquals(65f, PreviewToolsPolicy.panelHeight(240f, 100f), .01f)
        assertEquals(0f, PreviewToolsPolicy.panelHeight(240f, 0f), .01f)
        assertEquals(240f, PreviewToolsPolicy.panelHeight(Float.NaN, 1000f), .01f)
    }

    // ---- Phase 72.1: the strip, the table columns and the viewport ask ----

    @Test fun `the strip is the five tabs the shots show, in order`() {
        assertEquals(
            listOf(
                PreviewToolTab.CONSOLE,
                PreviewToolTab.ELEMENTS,
                PreviewToolTab.NETWORK,
                PreviewToolTab.RESOURCES,
                PreviewToolTab.SETTINGS,
            ),
            PreviewToolTab.entries.toList(),
        )
    }

    @Test fun `redaction is the one rule both lists share`() {
        assertEquals(
            "https://example.org:8443/a/b",
            PreviewToolsPolicy.redact("https://user:password@example.org:8443/a/b?q=1#f"),
        )
        assertNull(PreviewToolsPolicy.redact("file:///secret"))
        assertNull(PreviewToolsPolicy.redact("data:text/plain,secret"))
        assertNull(PreviewToolsPolicy.redact("not a url"))
    }

    @Test fun `page timings join the observer's rows by address`() {
        val requests = listOf(
            PreviewToolsPolicy.request("GET", "http://127.0.0.1:1/a.css?x=1", false)!!,
            PreviewToolsPolicy.request("GET", "http://127.0.0.1:1/b.png", false)!!,
        )
        val resources = listOf(
            PreviewToolsPolicy.resource("http://127.0.0.1:1/a.css?secret=1", "link", 2048, 12)!!,
            PreviewToolsPolicy.resource("https://cdn.example.org/x.js", "script", 10, 5)!!,
        )
        val merged = PreviewToolsPolicy.merge(requests, resources)
        assertEquals("GET", merged[0].method)
        assertEquals("link", merged[0].type)
        assertEquals(2048L, merged[0].size)
        assertEquals(12L, merged[0].time)
        // Nobody reported the image: its cells stay “—” instead of inventing numbers.
        assertNull(merged[1].type)
        assertNull(merged[1].size)
        // The page reported nothing: the observer's rows come back untouched,
        // never re-shaped (empty page list in, same list out).
        assertEquals(requests, PreviewToolsPolicy.merge(requests, emptyList()))
        // …and a page row whose address nobody requested stays out of the
        // table rather than inventing a Method for itself.
        assertEquals(2, merged.size)
    }

    @Test fun `resources parse back from the page's own answer`() {
        val row = listOf("https://example.org/a.js?token=1", "script", "4096", "18").joinToString("\u0001")
        val rows = PreviewToolsPolicy.parseResources(PreviewConsolePolicy.quote(row))
        assertEquals(1, rows.size)
        assertEquals("https://example.org/a.js", rows.single().address)
        assertEquals("script", rows.single().type)
        assertEquals(4096L, rows.single().size)
        assertEquals(18L, rows.single().time)
        assertTrue(PreviewToolsPolicy.parseResources(null).isEmpty())
        assertTrue(
            PreviewToolsPolicy.parseResources(
                PreviewConsolePolicy.quote("file:///x\u0001img\u00010\u00010")
            ).isEmpty()
        )
        // A row without its four fields is dropped, never half-read.
        assertTrue(PreviewToolsPolicy.parseResources(PreviewConsolePolicy.quote("https://a/b")).isEmpty())
    }

    @Test fun `a resource type never leaves an empty cell`() {
        assertEquals("img", PreviewToolsPolicy.resource("https://a/b", "img", 1, 1)!!.type)
        assertEquals("other", PreviewToolsPolicy.resource("https://a/b", "", 1, 1)!!.type)
        assertEquals(0L, PreviewToolsPolicy.resource("https://a/b", "img", -5, -5)!!.size)
    }

    @Test fun `the viewport ask only ever adds a missing default`() {
        val script = PreviewToolsPolicy.viewportScript()
        assertTrue(script.contains("meta[name=viewport]"))
        assertTrue(script.contains("document.head"))
        assertTrue(script.contains("width=device-width, initial-scale=1"))
        assertTrue(script.contains("h.appendChild(t)"))
        assertEquals(PreviewViewport.AUTHORED, PreviewToolsPolicy.viewportOutcome("\"authored\""))
        assertEquals(PreviewViewport.ADDED, PreviewToolsPolicy.viewportOutcome("\"added\""))
        assertEquals(PreviewViewport.UNKNOWN, PreviewToolsPolicy.viewportOutcome("\"none\""))
        assertEquals(PreviewViewport.UNKNOWN, PreviewToolsPolicy.viewportOutcome(null))
    }

    @Test fun `columns are honest about the numbers WebView never reports`() {
        assertEquals("—", PreviewToolsPolicy.statusLabel(null))
        assertEquals("200", PreviewToolsPolicy.statusLabel(200))
        assertEquals("—", PreviewToolsPolicy.typeLabel(null))
        assertEquals("img", PreviewToolsPolicy.typeLabel("img"))
        assertEquals("—", PreviewToolsPolicy.sizeLabel(null))
        assertEquals("—", PreviewToolsPolicy.sizeLabel(0))
        assertEquals("512 B", PreviewToolsPolicy.sizeLabel(512))
        assertEquals("1.5 kB", PreviewToolsPolicy.sizeLabel(1536))
        assertEquals("2.0 MB", PreviewToolsPolicy.sizeLabel(2L * 1024 * 1024))
        assertEquals("—", PreviewToolsPolicy.timeLabel(null))
        assertEquals("—", PreviewToolsPolicy.timeLabel(0))
        assertEquals("18 ms", PreviewToolsPolicy.timeLabel(18))
        assertEquals("1.2 s", PreviewToolsPolicy.timeLabel(1234))
    }
    @Test fun `viewport fit never upscales or overflows either bound`() {
        assertEquals(.25f, PreviewToolsPolicy.fitScale(320f, 400f, 1280f, 720f), .001f)
        assertEquals(.1f, PreviewToolsPolicy.fitScale(500f, 64f, 360f, 640f), .001f)
        assertEquals(1f, PreviewToolsPolicy.fitScale(2000f, 2000f, 360f, 640f), .001f)
    }
    @Test fun `the page box line names the page's box and the view's`() {
        // 2026-09-28, owner: *"browser have a good view and code is very smaller
        // view"*. The console says which box the page was laid out in and which
        // box the view was given, so the two can be told apart from one shot.
        assertEquals(PreviewPageBox(360, 430, 1.75), PreviewToolsPolicy.parsePageBox("\"360 430 1.75\""))
        assertEquals(
            "page box 360\u00d7430 CSS px \u00b7 view 360\u00d7430 dp \u00b7 dpr 1.75",
            PreviewToolsPolicy.pageBoxLabel(PreviewPageBox(360, 430, 1.75), 360, 430),
        )
        assertEquals(
            "page box 412\u00d7915 CSS px \u00b7 view 360\u00d7800 dp \u00b7 dpr 2",
            PreviewToolsPolicy.pageBoxLabel(PreviewPageBox(412, 915, 2.0), 360, 800),
        )
    }
    @Test fun `a page that never answered still reports the view it was given`() {
        assertNull(PreviewToolsPolicy.parsePageBox(null))
        assertNull(PreviewToolsPolicy.parsePageBox("\"null\""))
        assertNull(PreviewToolsPolicy.parsePageBox("\"\""))
        assertNull(PreviewToolsPolicy.parsePageBox("\"0 0 1\""))
        assertEquals(
            "page box unanswered \u00b7 view 360\u00d7600 dp",
            PreviewToolsPolicy.pageBoxLabel(null, 360, 600),
        )
    }
    @Test fun `zoom presets have a reset and viewport choices retain device mode`() {
        assertEquals(listOf(50, 75, 100, 125, 150, 200), PreviewToolsPolicy.zoomPresets)
        assertEquals(0, PreviewResolution.DEVICE.widthDp)
        assertEquals(1280, PreviewResolution.DESKTOP.widthDp)
    }
}
