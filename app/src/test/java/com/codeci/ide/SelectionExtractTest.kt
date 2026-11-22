package com.codeci.ide

import com.codeci.ide.ui.terminal.SelectionModel
import com.codeci.ide.ui.terminal.SelectionPoint
import com.codeci.ide.ui.terminal.SelectionRange
import com.codeci.ide.ui.terminal.TerminalEmulator
import com.codeci.ide.ui.terminal.TerminalLine
import com.codeci.ide.ui.terminal.TerminalSnapshot
import com.codeci.ide.ui.terminal.TerminalSnapshotText
import com.codeci.ide.ui.terminal.asSelectionText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 103.1 — the copy contract.
 *
 * The owner's row was *"partial text copy not working"*, and the step he cares
 * about is the last one: what actually lands on the clipboard. Termux's
 * `getSelectedText(joinBackLines, joinFullLines)` rejoins soft-wrapped rows;
 * this is the same behaviour re-implemented (behaviour only — GPL-3.0 source
 * was read, never copied) and pinned against the real emulator.
 */
class SelectionExtractTest {

    private val wrappedUrl =
        "https://github.com/pabi277/CodeC/blob/main/docs/phases/01-terminal-userland"

    private fun line(text: String, softWrap: Boolean = false) = TerminalLine(text, emptyList(), null, softWrap)

    private fun sourceOf(vararg lines: TerminalLine): TerminalSnapshotText =
        TerminalSnapshotText { row -> lines.getOrNull(row) }

    // ---- the golden case -------------------------------------------------

    @Test
    fun `a wrapped URL copies as one unbroken string`() {
        // 40 columns: the URL is laid out over three rows of the real width.
        val emu = TerminalEmulator(cols = 40, rows = 8, scrollbackLimit = 100)
        emu.feed("Run: $wrappedUrl\r\n")
        val snap = emu.snapshot()
        val all = SelectionModel.selectAll(snap)
        val text = SelectionModel.extract(snap.asSelectionText(), all.range)
        assertTrue(
            "the wrapped URL must survive as one string, got:\n$text",
            text.contains(wrappedUrl)
        )
    }

    @Test
    fun `a soft-wrapped row joins the next with no separator at all`() {
        val src = sourceOf(line("https://exa", softWrap = true), line("mple.com/x"))
        val out = SelectionModel.extract(src, SelectionRange(SelectionPoint(0, 0), SelectionPoint(1, 9)))
        assertEquals("https://example.com/x", out)
    }

    @Test
    fun `a hard line keeps its newline`() {
        val src = sourceOf(line("first"), line("second"))
        val out = SelectionModel.extract(src, SelectionRange(SelectionPoint(0, 0), SelectionPoint(1, 5)))
        assertEquals("first\nsecond", out)
    }

    @Test
    fun `trailing blanks are trimmed on a hard row and kept on a wrapped one`() {
        val hard = sourceOf(line("abc      "))
        assertEquals("abc", SelectionModel.extract(hard, SelectionRange(SelectionPoint(0, 0), SelectionPoint(0, 8))))
        // A wrapped row's padding is part of the join: keep it byte-true.
        val soft = sourceOf(line("abc      ", softWrap = true), line("def"))
        assertEquals(
            "abc      def",
            SelectionModel.extract(soft, SelectionRange(SelectionPoint(0, 0), SelectionPoint(1, 2)))
        )
    }

    @Test
    fun `a partial selection starts and ends exactly on its cells`() {
        val src = sourceOf(line("0123456789"))
        val out = SelectionModel.extract(src, SelectionRange(SelectionPoint(0, 2), SelectionPoint(0, 4)))
        assertEquals("234", out)
    }

    @Test
    fun `an empty or off-buffer range yields nothing rather than throwing`() {
        val src = sourceOf(line("abc"))
        assertEquals("", SelectionModel.extract(src, SelectionRange(SelectionPoint(4, 0), SelectionPoint(6, 3))))
        val emu = TerminalEmulator()
        val empty = SelectionModel.extract(emu.snapshot().asSelectionText(), SelectionRange(SelectionPoint(99, 0), SelectionPoint(99, 9)))
        assertEquals("", empty)
    }

    @Test
    fun `the selection reads history rows too, not just the live screen`() {
        val src = TerminalSnapshotText { row ->
            when (row) {
                -2 -> line("old-one")
                -1 -> line("old-two")
                0 -> line("live")
                else -> null
            }
        }
        val out = SelectionModel.extract(src, SelectionRange(SelectionPoint(-2, 0), SelectionPoint(0, 3)))
        assertEquals("old-one\nold-two\nlive", out)
    }

    // ---- the law: no transcript fallback ---------------------------------

    @Test
    fun `an empty selection copies nothing — never the transcript`() {
        val emu = TerminalEmulator(cols = 20, rows = 4)
        emu.feed("some output\r\n")
        val snap = emu.snapshot()
        val caret = SelectionRange(SelectionPoint(3, 3), SelectionPoint(3, 3))
        val blank = SelectionModel.extract(snap.asSelectionText(), caret)
        assertTrue("a caret on a blank cell is blank: '$blank'", blank.isBlank())
        // The pre-103 bug in one line: the toolbar used to fall back to the
        // WHOLE transcript here. Nothing in the extraction path may.
        assertFalse(blank.contains("some output"))
    }

    @Test
    fun `a real partial selection never grows to include its neighbours`() {
        val src = sourceOf(line("keep me|drop me"))
        val out = SelectionModel.extract(src, SelectionRange(SelectionPoint(0, 0), SelectionPoint(0, 6)))
        assertEquals("keep me", out)
    }

    @Test
    fun `the emulator's soft-wrap flags reach the snapshot`() {
        val emu = TerminalEmulator(cols = 10, rows = 6)
        emu.feed("0123456789ABCDEF\r\n")
        val snap: TerminalSnapshot = emu.snapshot()
        val first = snap.lines[0]
        assertTrue("row 0 ended by auto-wrap", first.softWrapped)
        assertFalse("row 1 is the hard end of the line", snap.lines[1].softWrapped)
    }

    @Test
    fun `extractLine clamps a caret one past the right margin`() {
        val l = line("abc")
        assertEquals("abc", SelectionModel.extractLine(l, 0, 99))
        assertEquals("", SelectionModel.extractLine(l, 9, 20))
    }
}
