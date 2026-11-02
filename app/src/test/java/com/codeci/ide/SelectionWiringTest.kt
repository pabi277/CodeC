package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 103.1 — the pins a unit test cannot reach.
 *
 * `SelectionModelTest` proves the arithmetic; this file proves the app is
 * actually wired to it. Every assertion reads the REAL sources (the Phase 38
 * `RepoFiles` pattern), because each one of these is a thing a later phase
 * could quietly undo: the menu re-opening inside the long-press, the
 * transcript fallback creeping back into Copy, a handle drag that also
 * scrolls the buffer, or a row computed without the sub-row offset.
 *
 * Owner's rows, verbatim (2026-10-14):
 * *"partial text copy not working"*
 * *"select some text, copy"* — and the selection style he chose:
 * *"Termux-style — long-press → drag handles → floating Copy/Select-all
 * toolbar"*.
 */
class SelectionWiringTest {

    private val view = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/components/TerminalEmulatorView.kt"
    ).readText()

    private val screen = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/TerminalScreen.kt"
    ).readText()

    private val buffer = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/terminal/TerminalBuffer.kt"
    ).readText()

    private fun code(source: String): String = RepoFiles.codeOnly(source)

    /** The [length] characters of [source] starting at [marker]. */
    private fun window(source: String, marker: String, length: Int): String {
        val start = source.indexOf(marker)
        if (start < 0) return ""
        return source.substring(start, (start + length).coerceAtMost(source.length))
    }

    @Test
    fun `the long press starts a selection and does NOT open the menu over it`() {
        // The original bug in one line: `menu = true` inside the long-press
        // handler. The popup stole the gesture, so there was never a range
        // left for the finger to extend — hence "partial text copy not
        // working".
        val block = window(code(view), "onLongPress = {", 2200)
        assertTrue("the long-press must start a range", block.contains("beginSelectionAt("))
        assertFalse(
            "opening the menu inside the long-press is the bug — it ends the gesture",
            block.contains("menu = true")
        )
        assertTrue(
            "the range is built by the model, not by the view",
            code(view).contains("SelectionModel.beginWord")
        )
        assertTrue("the same gesture must arm drag-extension", code(view).contains("selecting = true"))
        assertTrue("the handles must appear", code(view).contains("showHandles = true"))
    }

    @Test
    fun `a double tap grabs the word too`() {
        // The owner tried it before he tried the long-press: *"No copy with
        // long press or double tap"*. On a phone a double-tap IS the
        // word-grabbing gesture, so it must not be left unbound.
        val block = window(code(view), "onDoubleTap = {", 150)
        assertTrue(block.contains("beginSelectionAt("))
        assertFalse("a double-tap is not a long-press: no tick", block.contains("tick = true"))
    }

    @Test
    fun `the extend branch never swallows an event it cannot use`() {
        // The 2026-10-14 device failure, in one condition. The extend branch
        // consumes the event; if it is taken while there is nothing to
        // extend, the terminal stops scrolling AND the tap detector next
        // door — which needs an unconsumed down — stops firing. Long-press,
        // double-tap, scroll: all three dead at once, which is exactly what
        // the owner saw.
        assertTrue(
            "extending must require an actual range",
            code(view).contains("extending = { selecting && selection != null }")
        )
    }

    @Test
    fun `the per-gesture flags are cleared when the detector is torn down`() {
        // The detector is keyed on the cell metrics, and `rows` changes when
        // the soft keyboard opens — that CANCELS the coroutine, so the
        // end-of-gesture cleanup (`onGestureEnd`) never runs. Without this
        // reset, one long-press could leave `selecting` switched on for good.
        assertTrue(code(view).contains("DisposableEffect(cellW, cellH, cols, rows)"))
        val block = window(code(view), "DisposableEffect(cellW, cellH, cols, rows)", 400)
        assertTrue("the teardown must clear the extend flag", block.contains("selecting = false"))
        assertTrue("and the held handle", block.contains("draggingHandle = null"))
    }

    @Test
    fun `one detector owns every gesture on the glass`() {
        // Two detectors over the canvas (the original build put the handle
        // drag on a full-screen overlay above it) is two places a gesture
        // can be swallowed, and this surface cannot afford one.
        assertTrue(code(view).contains("private suspend fun PointerInputScope.detectTerminalGestures("))
        assertFalse("the full-screen handle overlay is gone", code(view).contains("detectHandleDrag"))
        assertFalse(
            "and so is the second full-screen pointerInput it needed",
            code(view).contains("hitTest = { pos ->")
        )
    }

    @Test
    fun `the long press ticks through the app's ONE haptics adapter`() {
        // Phase 51.4 (pinned by `HapticWiringTest`) makes
        // TerminalEmulatorView.kt a HAPTICS-FREE zone, so the view only
        // ANNOUNCES that a selection began; the screen — which owns the
        // terminal's chrome — performs the tick through the adapter. That
        // keeps the platform call in the two files the house allows and keeps
        // the owner's Settings switch in force. DRAG_STARTED is the house
        // moment for "a long press took".
        assertTrue("the view must announce the moment", code(view).contains("onSelectionStarted()"))
        assertFalse(
            "the emulator view must not name a moment (Phase 51.4)",
            code(view).contains("HapticMoment")
        )
        assertFalse(
            "and must never touch the platform haptics itself",
            code(view).contains("performHapticFeedback")
        )
        assertTrue(
            "the screen performs it, through the adapter",
            code(screen).contains("haptics.perform(HapticMoment.DRAG_STARTED)")
        )
        assertTrue(
            "the screen borrows the house handle, not a new one",
            code(screen).contains("rememberCodecHaptics()")
        )
    }

    @Test
    fun `the emulator view stays off the app token system`() {
        // Phase 51.3 pin (`TerminalChromeTest`): Phase 36 owns the emulator's
        // rendering, so this file draws from the terminal theme only.
        assertFalse(code(view).contains("CodecTokens"))
    }

    @Test
    fun `a handle drag owns the gesture — the buffer must not scroll under it`() {
        // The single detector hit-tests the handles on the DOWN and consumes
        // from there, so neither the scroll branch nor the tap detector can
        // act on the same pointer.
        assertTrue(code(view).contains("val held = hitHandle(down.position)"))
        assertTrue(code(view).contains("down.consume()"))
        assertTrue(code(view).contains("onHandleEnd()"))
    }

    @Test
    fun `a handle is a 48 dp touch target, not a dot you have to hit`() {
        assertTrue(code(view).contains("HandleTouchSize = 48.dp"))
        assertTrue(code(view).contains(".size(HandleTouchSize)"))
    }

    @Test
    fun `the row under the finger is measured with the sub-row scroll offset`() {
        // Content is drawn translated by -scrollSubPx, so a row computed from
        // the raw y is up to a row off whenever the view rests between rows
        // (the Phase 103.2 rest-position bug shows up here too).
        assertTrue(
            code(view).contains("((pos.y + scrollSubPx) / cellH)") ||
                code(view).contains("((y + scrollSubPx) / cellH)")
        )
    }

    @Test
    fun `an open selection follows its own text while output streams`() {
        assertTrue("the view must shift the range", code(view).contains("SelectionModel.shift("))
        assertTrue("and the buffer must count the rows it pushes", code(buffer).contains("scrolledRows++"))
        assertTrue(
            "the count is published on the snapshot, or the view cannot see it",
            code(buffer).contains("scrolledRows = scrolledRows")
        )
    }

    @Test
    fun `the soft-wrap flag reaches the immutable line, or the copy rule cannot work`() {
        assertTrue(code(buffer).contains("val wrapped: Boolean = false"))
        assertTrue(code(buffer).contains("TerminalLine(text.toString(), runs, clusters, row.wrapped)"))
    }

    @Test
    fun `copy writes the selection or is disabled — the transcript fallback is gone`() {
        // TerminalScreen's toolbar button used to do
        // `activeSelection ?: viewModel.transcriptText()`: whenever the
        // gesture layer left the selection empty — which it always did — the
        // button silently copied the whole session. That fallback is deleted,
        // not kept as a net.
        assertFalse(
            "the toolbar copy must not fall back to the transcript",
            code(screen).contains("transcriptText")
        )
        assertTrue(
            "with no selection the button is disabled instead",
            code(screen).contains("enabled = activeSelection != null")
        )
    }

    @Test
    fun `the menu's copy item cannot fall back to the transcript either`() {
        assertFalse(
            code(view).contains("selectedText ?: snapshot.transcriptText()")
        )
        assertTrue(
            "an empty selection disables the item",
            code(view).contains("enabled = selectedText.isNotEmpty()")
        )
    }

    @Test
    fun `the copy rule lives in the model, with the view only forwarding`() {
        assertTrue(
            "the public shim delegates instead of keeping a second rule",
            code(view).contains("SelectionModel.extract(this, TextSelection(")
        )
        assertFalse(
            "the view must not build grid ranges of its own any more",
            code(view).contains("selection = GridSelection")
        )
    }

    @Test
    fun `the floating toolbar is the owner's chosen selection style`() {
        assertTrue(code(view).contains("private fun SelectionToolbar("))
        assertTrue(code(view).contains("ToolbarAction(stringResource(R.string.terminal_copy)"))
        assertTrue(code(view).contains("ToolbarAction(stringResource(R.string.terminal_paste)"))
        // Labels that are plain string literals are pinned against the RAW
        // source: `codeOnly` blanks string literals on purpose (the Phase 45
        // lesson — a pin that greps a word inside a comment proves nothing).
        assertTrue(
            "Select all must be one tap away, as the owner chose",
            view.contains("ToolbarAction(\"Select all\"")
        )
    }

    @Test
    fun `dragging into the edge band keeps scrolling, so a selection can pass one screen`() {
        assertTrue(code(view).contains("EDGE_SCROLL_PX_PER_FRAME"))
        assertTrue(code(view).contains("EDGE_SCROLL_BAND_ROWS"))
    }
}
