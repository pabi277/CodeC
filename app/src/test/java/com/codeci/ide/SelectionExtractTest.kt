package com.codeci.ide

import com.codeci.ide.ui.components.selectedText
import com.codeci.ide.ui.terminal.SelCell
import com.codeci.ide.ui.terminal.SelectionModel
import com.codeci.ide.ui.terminal.TermSelection
import com.codeci.ide.ui.terminal.TerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 103.1 — copy re-joins soft-wrapped rows, keeps hard line ends, and
 * trims trailing spaces per HARD line only.
 */
class SelectionExtractTest {

    private fun wholeBuffer(snap: com.codeci.ide.ui.terminal.TerminalSnapshot): TermSelection =
        SelectionModel.selectAll(snap)

    @Test
    fun `a long URL that wrapped copies back as one unbroken string`() {
        // 5 columns: the URL is split across five physical rows, four of them soft-wrapped.
        val emu = TerminalEmulator(cols = 5, rows = 6, scrollbackLimit = 50)
        emu.feed("https://example.com/a")
        val snap = emu.snapshot()
        assertEquals(
            "https://example.com/a",
            SelectionModel.extract(wholeBuffer(snap), snap)
        )
    }

    @Test
    fun `a partial copy of a wrapped URL yields exactly the dragged span`() {
        val emu = TerminalEmulator(cols = 5, rows = 6, scrollbackLimit = 50)
        emu.feed("https://example.com/a")
        val snap = emu.snapshot()
        // Rows: "https" "://ex" "ample" ".com/" "a" (the first four are soft-wrapped).
        // Drag from row 2 col 1 ('m') to row 3 col 1 ('c'): "mple" then ".c", no newline.
        val sel = TermSelection(SelCell(snap.absRowOf(2), 1), SelCell(snap.absRowOf(3), 1))
        assertEquals("mple.c", SelectionModel.extract(sel, snap))
    }

    @Test
    fun `hard line ends keep a newline and trailing spaces trim per line`() {
        val emu = TerminalEmulator(cols = 10, rows = 4, scrollbackLimit = 50)
        emu.feed("ab   \r\ncd   ")
        val snap = emu.snapshot()
        assertEquals("ab\ncd", SelectionModel.extract(wholeBuffer(snap), snap))
    }

    @Test
    fun `a wrapped row keeps its own trailing spaces because they are mid-line`() {
        val emu = TerminalEmulator(cols = 5, rows = 4, scrollbackLimit = 50)
        // "ab   " exactly fills the 5 columns, so the next character wraps.
        emu.feed("ab   cd")
        val snap = emu.snapshot()
        assertEquals("ab   cd", SelectionModel.extract(wholeBuffer(snap), snap))
    }

    @Test
    fun `a selection that ends mid-wrapped-line adds no newline`() {
        val emu = TerminalEmulator(cols = 5, rows = 4, scrollbackLimit = 50)
        emu.feed("abcdefgh\r\nxyz")
        val snap = emu.snapshot()
        // Row 0 is "abcde" (wrapped), row 1 "fgh" (hard). Select row 0 only.
        val sel = TermSelection(SelCell(snap.absRowOf(0), 0), SelCell(snap.absRowOf(0), 4))
        assertEquals("abcde", SelectionModel.extract(sel, snap))
    }

    @Test
    fun `joining a wrapped row with the next hard row adds exactly one newline`() {
        val emu = TerminalEmulator(cols = 5, rows = 4, scrollbackLimit = 50)
        emu.feed("abcdefgh\r\nxyz")
        val snap = emu.snapshot()
        val sel = TermSelection(SelCell(snap.absRowOf(0), 0), SelCell(snap.absRowOf(2), 2))
        assertEquals("abcdefgh\nxyz", SelectionModel.extract(sel, snap))
    }

    @Test
    fun `wide glyphs are copied once each`() {
        val emu = TerminalEmulator(cols = 10, rows = 3, scrollbackLimit = 50)
        emu.feed("漢字")
        val snap = emu.snapshot()
        assertEquals("漢字", SelectionModel.extract(wholeBuffer(snap), snap))
    }

    @Test
    fun `the legacy extended-Y selectedText agrees with the one extractor`() {
        val emu = TerminalEmulator(cols = 40, rows = 10, scrollbackLimit = 50)
        emu.feed("Hello World!\r\nSecond Line.")
        val snap = emu.snapshot()
        assertEquals(
            "World",
            snap.selectedText(com.codeci.ide.ui.components.GridSelection(6, 0, 10, 0))
        )
        assertEquals(
            "Hello World!\nSecond Line.",
            snap.selectedText(com.codeci.ide.ui.components.GridSelection(0, 0, 11, 1))
        )
    }

    @Test
    fun `an empty buffer copies nothing`() {
        val snap = TerminalEmulator(cols = 10, rows = 3, scrollbackLimit = 50).snapshot()
        assertTrue(SelectionModel.extract(wholeBuffer(snap), snap).isEmpty())
    }
}
