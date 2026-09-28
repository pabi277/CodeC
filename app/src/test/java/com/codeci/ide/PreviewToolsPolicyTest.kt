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
    @Test fun `the page box line names the page's box, its meta, the view and the scale`() {
        // 2026-09-28, owner: *"browser have a good view and code is very smaller
        // view"*. Two reports in a row could not be told apart from a screenshot,
        // so the console now prints every number that separates them: the page's
        // own box, the viewport it declared, the view's box and the scale the
        // WebView actually applied (which should equal dpr — one CSS pixel per dp).
        // Round 4 added a fourth field before the meta: what `100vh` measured.
        val box = PreviewToolsPolicy.parsePageBox("\"360|619|3|619|width=device-width, initial-scale=1.0\"")
        assertEquals(PreviewPageBox(360, 619, 3.0, "width=device-width, initial-scale=1.0", 619), box)
        assertEquals(
            "page box 360\u00d7619 CSS px \u00b7 100vh 619 px \u00b7 meta width=device-width, initial-scale=1.0 \u00b7 " +
                "view 360\u00d7430 dp \u00b7 scale 3 \u00b7 dpr 3",
            PreviewToolsPolicy.pageBoxLabel(box, 360, 430, 3.0),
        )
        // A page that declares no viewport is the case `fitToPhone` answers.
        val bare = PreviewToolsPolicy.parsePageBox("\"980|1200|2|1200|\"")
        assertEquals(PreviewPageBox(980, 1200, 2.0, null, 1200), bare)
        assertEquals(
            "page box 980\u00d71200 CSS px \u00b7 100vh 1200 px \u00b7 meta none \u00b7 view 360\u00d7800 dp \u00b7 " +
                "scale 2 \u00b7 dpr 2",
            PreviewToolsPolicy.pageBoxLabel(bare, 360, 800, 2.0),
        )
        // 514 CSS px shown in a 360 dp box at scale 1: the "very smaller view" shape.
        // A box built without the probe's answer says so rather than inventing one.
        assertEquals(
            "page box 514\u00d7500 CSS px \u00b7 100vh unmeasured \u00b7 meta none \u00b7 view 360\u00d7430 dp \u00b7 " +
                "scale 1 \u00b7 dpr 3",
            PreviewToolsPolicy.pageBoxLabel(PreviewPageBox(514, 500, 3.0), 360, 430, 1.0),
        )
        // A meta is capped, and a separator smuggled into it is neutralised: the
        // declaration is prose, so everything after the fourth field is kept.
        val long = PreviewToolsPolicy.parsePageBox("\"360|600|1|600|width=device-width,| user-scalable=no\"")
        assertEquals("width=device-width, user-scalable=no", long?.meta)
        val capped = PreviewToolsPolicy.parsePageBox("\"360|600|1|600|" + "x".repeat(200) + "\"")
        assertEquals(PreviewToolsPolicy.META_LIMIT, capped?.meta?.length)
        // The probe's own failure answer is -1 and parses to "unmeasured"; a
        // three-field answer is the old wire format and no longer parses.
        assertNull(PreviewToolsPolicy.parsePageBox("\"360|600|1|-1|\"")?.vhHeight)
        assertNull(PreviewToolsPolicy.parsePageBox("\"360|600|1\""))
    }
    @Test fun `a 100vh shorter than half the page is the collapsed layout viewport`() {
        // Round 4, 2026-09-28: the owner's modal rendered as a 42 px strip — its
        // `max-height: 88vh` box collapsed to padding + border — under a page-box
        // line that read `411×655 CSS px · view 411×656 dp · scale 2.63 · dpr 2.63`.
        // `innerHeight` is the visual viewport and cannot see this; only the
        // page's own `100vh` can. 0 is the quirk's exact signature and must be
        // kept as a number, never dropped as "unanswered".
        val collapsed = PreviewToolsPolicy.parsePageBox("\"411|655|2.63|0|width=device-width, initial-scale=1.0\"")
        assertEquals(0, collapsed?.vhHeight)
        assertTrue(PreviewToolsPolicy.layoutHeightCollapsed(collapsed))
        assertEquals(
            "page box 411\u00d7655 CSS px \u00b7 100vh 0 px \u00b7 meta width=device-width, initial-scale=1.0 \u00b7 " +
                "view 411\u00d7656 dp \u00b7 scale 2.63 \u00b7 dpr 2.63",
            PreviewToolsPolicy.pageBoxLabel(collapsed, 411, 656, 2.63),
        )
        assertEquals(
            "100vh is 0 px in a 655 px tall page: the layout viewport has collapsed, so vh units " +
                "and height:100% resolve to 0 — the view's height must not be wrap-content",
            PreviewToolsPolicy.collapsedLabel(collapsed!!),
        )
        // The healthy line — the one the fix should produce on the same phone.
        assertFalse(PreviewToolsPolicy.layoutHeightCollapsed(PreviewPageBox(411, 655, 2.63, null, 655)))
        // A pinch-zoom shrinks innerHeight, never vh: taller is never collapsed.
        assertFalse(PreviewToolsPolicy.layoutHeightCollapsed(PreviewPageBox(200, 320, 2.63, null, 655)))
        // Half is the line: 327 of 655 is collapsed, 328 is not.
        assertTrue(PreviewToolsPolicy.layoutHeightCollapsed(PreviewPageBox(411, 655, 2.63, null, 327)))
        assertFalse(PreviewToolsPolicy.layoutHeightCollapsed(PreviewPageBox(411, 655, 2.63, null, 328)))
        // Nothing to judge with: no probe answer, no page.
        assertFalse(PreviewToolsPolicy.layoutHeightCollapsed(PreviewPageBox(411, 655, 2.63, null, null)))
        assertFalse(PreviewToolsPolicy.layoutHeightCollapsed(null))
    }
    @Test fun `the mismatch rule fires on the owner's one bad load and nothing else`() {
        // 2026-09-28: the owner's console, four loads of the same page in a
        // 411×656 dp box — three laid out 411 CSS px wide, one 457. Only the
        // 457 one is a page laid out for a box it does not have.
        val deviceWidth = "width=device-width, initial-scale=1.0, viewport-fit=cover"
        val right = PreviewPageBox(411, 655, 2.63, deviceWidth)
        val wrong = PreviewPageBox(457, 728, 2.63, deviceWidth)
        assertFalse(PreviewToolsPolicy.boxMismatch(right, 411, 100))
        assertTrue(PreviewToolsPolicy.boxMismatch(wrong, 411, 100))
        assertEquals(
            "page laid out 457 CSS px wide in a 411 dp view — loading it again so it lays out for this box",
            PreviewToolsPolicy.mismatchLabel(457, 411),
        )
        // The tolerance is a rounding guard, not a licence.
        assertFalse(PreviewToolsPolicy.boxMismatch(PreviewPageBox(414, 655, 2.63, deviceWidth), 411, 100))
        assertTrue(PreviewToolsPolicy.boxMismatch(PreviewPageBox(420, 655, 2.63, deviceWidth), 411, 100))
    }
    @Test fun `the mismatch rule respects zoom and other people's widths`() {
        // At 150 % the page is *supposed* to lay out at two thirds of the view.
        val zoomed = PreviewPageBox(274, 437, 2.63, "width=device-width, initial-scale=1.0")
        assertFalse(PreviewToolsPolicy.boxMismatch(zoomed, 411, 150))
        // A page that declares its own width is not ours to correct.
        assertFalse(PreviewToolsPolicy.boxMismatch(PreviewPageBox(1024, 768, 2.63, "width=1024"), 411, 100))
        // A page that declared nothing is exactly the one `fitToPhone` answers.
        assertTrue(PreviewToolsPolicy.boxMismatch(PreviewPageBox(980, 1200, 2.63, null), 411, 100))
        // Nothing to judge with.
        assertFalse(PreviewToolsPolicy.boxMismatch(null, 411, 100))
        assertFalse(PreviewToolsPolicy.boxMismatch(PreviewPageBox(411, 655, 2.63, null), 0, 100))
    }

    @Test fun `a page that never answered still reports the view it was given`() {
        assertNull(PreviewToolsPolicy.parsePageBox(null))
        assertNull(PreviewToolsPolicy.parsePageBox("\"null\""))
        assertNull(PreviewToolsPolicy.parsePageBox("\"\""))
        assertNull(PreviewToolsPolicy.parsePageBox("\"0|0|1\""))
        assertEquals(
            "page box unanswered \u00b7 view 360\u00d7600 dp",
            PreviewToolsPolicy.pageBoxLabel(null, 360, 600),
        )
        assertEquals(
            "page box unanswered \u00b7 view 360\u00d7600 dp \u00b7 scale 1.5",
            PreviewToolsPolicy.pageBoxLabel(null, 360, 600, 1.5),
        )
    }
    @Test fun `zoom presets have a reset and viewport choices retain device mode`() {
        assertEquals(listOf(50, 75, 100, 125, 150, 200), PreviewToolsPolicy.zoomPresets)
        assertEquals(0, PreviewResolution.DEVICE.widthDp)
        assertEquals(1280, PreviewResolution.DESKTOP.widthDp)
    }
}
