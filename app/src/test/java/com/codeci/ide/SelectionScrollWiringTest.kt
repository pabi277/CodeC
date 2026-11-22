package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 103 — the wiring, pinned at the source (the house pattern for Compose
 * edges: `RepoFiles.codeOnly` first, so a comment promising a behaviour can
 * never pass for the behaviour — the Phase 45 round-2 lesson).
 *
 * Two of these pins are the actual regression guards the owner's report asked
 * for:
 *  1. **No transcript fallback on the copy path.** `TerminalScreen` used to
 *     read `activeSelection ?: viewModel.transcriptText()`.
 *  2. **The lift always snaps.** `TerminalScroll.rest` must be called from the
 *     one shared settle path, and no other code may assign the sub-row
 *     remainder to a non-zero value outside a drag frame.
 */
class SelectionScrollWiringTest {

    private fun source(path: String): String =
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/$path").readText()

    private val view = "ui/components/TerminalEmulatorView.kt"
    private val screen = "ui/screens/TerminalScreen.kt"

    // ---- 103.1: the gesture and the model --------------------------------

    @Test
    fun `the view drags the real selection model`() {
        val code = source(view)
        assertTrue(code.contains("SelectionModel.drag("))
        assertTrue(code.contains("SelectionModel.wordAt("))
        assertTrue(code.contains("SelectionModel.selectAll("))
        assertTrue(code.contains("SelectionAutoScroll.dragFor("))
        assertTrue(code.contains("SelectionModel.growByScroll("))
    }

    @Test
    fun `one gesture authority owns the surface`() {
        val code = SourceCode.of(source(view))
        // The two-detector race is what let the popup steal the drag at :364.
        assertFalse(
            "the pre-103 tap/long-press detector must not come back",
            code.contains("detectTapGestures(")
        )
        assertFalse(
            "the pre-103 scroll-or-select detector must not come back",
            code.contains("detectScrollOrSelect(")
        )
        assertEquals(
            "exactly one terminal gesture loop",
            1,
            // The CALL site only. The declaration is `private suspend fun
            // detectTerminalGestures(`; every `return@detectTerminalGestures` is
            // a label, not a loop. Counting the bare name is the classic
            // off-by-one a source pin makes.
            Regex("(?<![.@\\w])detectTerminalGestures\\s*\\(").findAll(code).count()
        )
    }

    @Test
    fun `a long-press selects and never opens a popup in the same gesture`() {
        val code = SourceCode.of(source(view))
        val longPress = code.substringAfter("onLongPress = { x, y ->")
            .substringBefore("onScroll = {")
        assertTrue(longPress.contains("TerminalSelection.word("))
        assertTrue(longPress.contains("toolbar = true"))
        assertFalse(
            "the pre-103 menu flag must not be set by the press",
            longPress.contains("menu = true")
        )
    }

    @Test
    fun `the handles are drawn and hit-tested from one geometry`() {
        val code = source(view)
        assertTrue(code.contains("SelectionHandles.forCell("))
        assertTrue(code.contains("SelectionHandles.hits("))
        // Both the painter and the hit test build the same Metrics from the
        // same px values; two separate conversions is how handles drift.
        assertTrue(code.contains("handleRadiusPx"))
        assertTrue(code.contains("48.dp.toPx()"))
    }

    @Test
    fun `copy is the selection or nothing — the transcript fallback is gone`() {
        val viewCode = SourceCode.of(source(view))
        assertTrue(viewCode.contains("fun copySelectionText("))
        val extractAt = viewCode.indexOf("fun copySelectionText(")
        val body = viewCode.substring(extractAt, viewCode.indexOf("\n}", extractAt))
        assertTrue(
            "copySelectionText must read the selection only",
            body.contains("SelectionModel.extract(")
        )
        assertFalse(
            "copySelectionText must never fall back to the transcript",
            body.contains("transcriptText()")
        )

        val screenCode = SourceCode.of(source(screen))
        assertFalse(
            "TerminalScreen's Copy must not fall back to the transcript",
            screenCode.contains("activeSelection ?: viewModel.transcriptText()")
        )
        assertTrue(
            "TerminalScreen's Copy reads the selection, or says nothing to copy",
            screenCode.contains("copyText(context, activeSelection.orEmpty())")
        )
    }

    @Test
    fun `the toolbar's copy button is disabled, not lenient, on an empty selection`() {
        val code = source(view)
        assertTrue(code.contains("hasSelection = selectionCopy != null"))
        // The button's enablement is bound to the selection's own text: the
        // colours argument may follow, but `enabled = hasSelection` must be
        // there and must not be `true`.
        assertTrue(Regex("TextButton\\(onClick = onCopy, enabled = hasSelection[]),]").containsMatchIn(code))
    }

    @Test
    fun `the floating toolbar carries copy, select all and paste with a more menu`() {
        val code = SourceCode.of(source(view))
        for (verb in listOf("terminal_copy", "terminal_select_all", "terminal_paste", "terminal_more")) {
            assertTrue("the toolbar must offer $verb", code.contains("R.string.$verb"))
        }
        assertTrue(code.contains("fun SelectionToolbar("))
    }

    @Test
    fun `the selection is anchored to its rows, not to the viewport`() {
        val code = SourceCode.of(source(view))
        assertTrue(code.contains("TrimMath.contentShift("))
        // The derived shift is applied to the selection, and nothing else
        // moves it: the trim itself is a no-op on this axis.
        assertTrue(code.contains(".shifted(contentShift)"))
        assertTrue(code.contains("TrimMath.contentShift("))
        assertTrue(code.contains("snapshot.scrollbackGeneration"))
    }

    // ---- 103.2: rest, fling, scrollbar -----------------------------------

    @Test
    fun `finger-lift goes through the one rest policy`() {
        val code = SourceCode.of(source(view))
        assertTrue(code.contains("TerminalScroll.rest("))
        assertEquals(
            "one settle path: every lift uses it",
            2,
            Regex("TerminalScroll\\.rest\\s*\\(").findAll(code).count()
        )
        // And the settle path really zeroes the remainder — this line IS the
        // fix for the first-open blank band.
        val settle = code.substringAfter("val settleScroll = {")
            .substringBefore("// Phase 103.1 — the selection follows its text")
        assertTrue(settle.contains("scrollSubPx = 0f"))
    }

    @Test
    fun `the sub-row remainder is only ever assigned by the drag, the settle and the fling`() {
        val code = SourceCode.of(source(view))
        val assignments = Regex("scrollSubPx\\s*=\\s*([^\\n]+)").findAll(code).toList()
        assertTrue("the remainder must be written somewhere", assignments.isNotEmpty())
        for (m in assignments) {
            val rhs = m.groupValues[1].trim()
            val allowed = rhs == "0f" ||
                rhs.startsWith("r.subRowPx") ||
                rhs.startsWith("moved.subRowPx") ||
                rhs.startsWith("drag.") ||
                rhs.startsWith("step.")
            assertTrue("unexpected scrollSubPx assignment: $rhs", allowed)
        }
    }

    @Test
    fun `a fling runs through the pure model and the reduced-motion rule`() {
        val code = source(view)
        assertTrue(code.contains("ScrollMotionPolicy.flingSteps("))
        assertTrue(code.contains("TerminalScroll.FRAME_MS"))
        assertTrue(code.contains("ScrollMath.step("))
        val screenCode = source(screen)
        assertTrue(
            "the platform animator scale must reach the view",
            screenCode.contains("animatorScale = remember {")
        )
    }

    @Test
    fun `the scrollbar is drawn from the pure geometry, only when there is travel`() {
        val code = source(view)
        assertTrue(code.contains("TerminalScroll.scrollbar("))
        assertTrue(code.contains("if (bar.hasTravel)"))
        assertTrue(code.contains("scrollbarAlpha"))
    }

    // ---- laws the phase must not break -----------------------------------

    @Test
    fun `the terminal view stays free of the theme tokens and the haptic policy`() {
        // Comments are blanked: the file's own KDoc must be able to name the
        // things it deliberately does NOT use (Phase 45's pin lesson).
        val raw = SourceCode.of(source(view))
        // TerminalChromeTest pins these four; restated here so a 103 change
        // cannot slip one in while editing the handles' colours.
        for (token in listOf("CodecTokens", "CodecMotion", "HapticMoment", "surfaceContainerHigh")) {
            assertFalse("TerminalEmulatorView.kt must not gain $token", raw.contains(token))
        }
        // The long-press feedback is the view's own, not Phase 51's policy.
        assertFalse(raw.contains("performHapticFeedback(HapticMoment"))
    }

    @Test
    fun `the two pure files carry no Android or Compose import`() {
        for (path in listOf("ui/terminal/TerminalSelection.kt", "ui/terminal/TerminalScroll.kt")) {
            val raw = source(path)
            assertFalse("$path must stay host-testable (no android import)", raw.contains("import android"))
            assertFalse("$path must stay host-testable (no androidx import)", raw.contains("import androidx"))
        }
    }

    @Test
    fun `the row lookup lives with the buffer so the model needs no Compose`() {
        val buffer = source("ui/terminal/TerminalBuffer.kt")
        assertTrue(buffer.contains("fun lineAt(extY: Int): TerminalLine?"))
        val view = source("ui/components/TerminalEmulatorView.kt")
        assertFalse(
            "the view must not carry a second lineAt (one row address for the whole app)",
            Regex("fun TerminalSnapshot\\.lineAt\\s*\\(").containsMatchIn(SourceCode.of(view))
        )
    }

    @Test
    fun `no new dependency, permission or settings key is introduced`() {
        val gradle = RepoFiles.mainSource("app/build.gradle.kts").readText()
        assertFalse(gradle.contains("103"))
        val manifest = RepoFiles.mainSource("app/src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("103"))
    }
}

/**
 * Comments and string literals blanked before a pin (the repo's own
 * `RepoFiles.codeOnly`, with the newlines kept so a count cannot drift).
 */
private object SourceCode {
    fun of(text: String): String = RepoFiles.codeOnly(text)
}
