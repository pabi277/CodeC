package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 103.1 + 103.2 — source pins. The gesture layer is Compose and cannot run
 * on the host, so these pin the wiring that the device rows depend on: the view
 * builds its selection from the pure model, copy has no transcript fallback, and
 * the release path discards the scroll remainder before it flings.
 */
class TerminalGestureWiringTest {

    private val view = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/components/TerminalEmulatorView.kt"
    ).readText()

    private val screen = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/TerminalScreen.kt"
    ).readText()

    private val buffer = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/terminal/TerminalBuffer.kt"
    ).readText()

    @Test
    fun `long-press selects through the pure model and opens no word popup`() {
        assertTrue(view.contains("SelectionModel.begin("))
        assertTrue(view.contains("SelectionModel.spanOnRow("))
        assertTrue(view.contains("SelectionModel.extract("))
        // The old single-word popup opened its menu from the long-press handler.
        assertFalse(view.contains("menuOffset"))
        assertFalse(view.contains("selecting = true"))
    }

    @Test
    fun `the handles and the floating toolbar are drawn by the view`() {
        assertTrue(view.contains("private fun SelectionKnob("))
        assertTrue(view.contains("private fun TerminalSelectionAction("))
        assertTrue(view.contains("SelectionModel.selectAll(snapshot)"))
        assertTrue(view.contains("SelectionModel.edgeScrollPx("))
    }

    @Test
    fun `copy writes the selection or nothing, never the transcript`() {
        assertTrue(view.contains("selectedText?.let(onCopyText)"))
        assertFalse(
            "the whole-transcript fallback must not come back into the toolbar Copy",
            screen.contains("activeSelection ?: viewModel.transcriptText()"),
        )
        assertTrue(screen.contains("enabled = activeSelection != null"))
    }

    @Test
    fun `the selection is held in buffer-absolute rows`() {
        assertTrue(view.contains("snap.absRowOf(topRow + row)"))
        assertTrue(view.contains("absRow = snapshot.absRowOf(extY)"))
        assertTrue(buffer.contains("historyBase = historyDropped"))
    }

    @Test
    fun `a width change drops the selection because the history was re-laid`() {
        assertTrue(view.contains("LaunchedEffect(cols)"))
    }

    @Test
    fun `the release path discards the remainder before it flings`() {
        val release = view.substring(view.indexOf("onRelease = {"))
        val rest = release.indexOf("ScrollMath.rest(topRow, scrollSubPx, -snap.scrollbackCount)")
        val fling = release.indexOf("ScrollMath.planFling(")
        assertTrue("release must call the rest policy", rest >= 0)
        assertTrue("release must plan the fling", fling >= 0)
        assertTrue("rest must come before the fling", rest < fling)
    }

    @Test
    fun `the old un-reset accumulation cannot return`() {
        // Every path that ends a gesture goes through rest(); the remainder is never
        // left to accumulate across lifts.
        assertTrue(view.contains("ScrollMath.rest(topRow, scrollSubPx, -latestSnapshot.scrollbackCount)"))
        assertTrue(view.contains("ScrollMath.thumb("))
    }

    @Test
    fun `the fling runs on the frame clock with no new ticker`() {
        assertTrue(view.contains("withFrameNanos { it }"))
        assertFalse(
            "a fling must not start its own timer loop",
            view.contains("Timer(") || view.contains("ticker"),
        )
    }

    @Test
    fun `the terminal line carries the soft-wrap flag`() {
        assertTrue(buffer.contains("val wrapped: Boolean = false"))
        assertTrue(buffer.contains("rowToLine(screen[y].cells, screen[y].wrapped)"))
    }
}
