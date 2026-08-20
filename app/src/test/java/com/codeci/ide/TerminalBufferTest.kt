package com.codeci.ide

import com.codeci.ide.ui.terminal.TerminalBuffer
import com.codeci.ide.ui.terminal.XtermColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalBufferTest {

    @Test
    fun `snapshot builds style runs`() {
        val buf = TerminalBuffer(cols = 8, rows = 2)
        buf.style.fg = 2
        buf.print('A'.code)
        buf.print('B'.code)
        buf.style.fg = 3
        buf.print('C'.code)
        val snap = buf.snapshot()
        assertEquals(2, snap.rows)
        assertEquals(8, snap.cols)
        val runs = snap.lines[0].runs
        assertTrue(runs.size >= 2)
        assertEquals(2, runs[0].fg)
        assertEquals(0, runs[0].start)
        assertEquals(2, runs[0].end)
        assertEquals(3, runs[1].fg)
    }

    @Test
    fun `resize keeps existing glyphs`() {
        val buf = TerminalBuffer(cols = 4, rows = 2)
        buf.print('Z'.code)
        buf.resize(6, 3)
        assertEquals(6, buf.cols)
        assertEquals(3, buf.rows)
        assertEquals('Z'.code, buf.cell(0, 0).cp)
    }

    @Test
    fun `scroll region and reverse index`() {
        val buf = TerminalBuffer(cols = 6, rows = 4)
        buf.setScrollRegion(2, 4)
        assertEquals(1, buf.scrollTop)
        assertEquals(3, buf.scrollBottom)
        // Origin mode is off, so DECSTBM homes to (0, 0), not the region top.
        assertEquals(0, buf.cursorY)
        buf.print('1'.code)
        buf.lineFeed()
        buf.carriageReturn()
        buf.print('2'.code)
        buf.reverseIndex()
        assertEquals(1, buf.cursorY)
    }

    @Test
    fun `default colors encode as sentinels`() {
        val buf = TerminalBuffer(cols = 2, rows = 1)
        buf.print('x'.code)
        assertEquals(XtermColors.COLOR_DEFAULT_FG, buf.cell(0, 0).fg)
        assertEquals(XtermColors.COLOR_DEFAULT_BG, buf.cell(0, 0).bg)
    }
}
