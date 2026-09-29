package com.codeci.ide

import com.codeci.ide.ui.components.GridSelection
import com.codeci.ide.ui.components.selectedText
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
    fun `snapshot includes scrollback and transcript concatenates history`() {
        val buf = TerminalBuffer(cols = 8, rows = 2)
        buf.print('A'.code)
        buf.lineFeed()
        buf.carriageReturn()
        buf.print('B'.code)
        buf.lineFeed()
        buf.carriageReturn()
        buf.print('C'.code)
        val snap = buf.snapshot()
        assertTrue(snap.scrollbackCount >= 1)
        val text = snap.transcriptText()
        assertTrue(text.contains("A"))
        assertTrue(text.contains("C"))
    }

    @Test
    fun `selectedText copies a rectangle of the transcript`() {
        val buf = TerminalBuffer(cols = 8, rows = 2)
        buf.print('A'.code)
        buf.print('B'.code)
        buf.print('C'.code)
        val snap = buf.snapshot()
        val text = snap.selectedText(
            com.codeci.ide.ui.components.GridSelection(0, 0, 1, 0)
        )
        assertEquals("AB", text)
    }

    @Test
    fun `default colors encode as sentinels`() {
        val buf = TerminalBuffer(cols = 2, rows = 1)
        buf.print('x'.code)
        assertEquals(XtermColors.COLOR_DEFAULT_FG, buf.cell(0, 0).fg)
        assertEquals(XtermColors.COLOR_DEFAULT_BG, buf.cell(0, 0).bg)
    }

    // ---- Phase 71.1 — the soft keyboard must not move the cursor off its content ----

    private fun promptScreen(rows: Int = 12): TerminalBuffer {
        val buf = TerminalBuffer(cols = 20, rows = rows)
        fun line(t: String) { t.forEach { buf.print(it.code) }; buf.carriageReturn(); buf.lineFeed() }
        line("welcome to codec")
        line("tools ready")
        "$ ".forEach { buf.print(it.code) }   // prompt on row 2 of 12
        return buf
    }

    @Test
    fun `keyboard open then close leaves the screen and the cursor exactly as they were`() {
        val buf = promptScreen()
        val before = buf.visibleText()
        assertEquals(2, buf.cursorY)

        buf.resize(20, 6)   // keyboard opens
        buf.resize(20, 12)  // keyboard closes

        assertEquals(before, buf.visibleText())
        assertEquals(2, buf.cursorY)   // the bug left it on row 6: four phantom lines
        assertEquals(0, buf.scrollbackSize)
    }

    @Test
    fun `opening the keyboard keeps the prompt on screen instead of pushing it into history`() {
        val buf = promptScreen()

        buf.resize(20, 6)

        assertEquals(0, buf.scrollbackSize)          // nothing was owed to history
        assertEquals(2, buf.cursorY)
        assertEquals('$'.code, buf.cell(0, 2).cp)    // the prompt is still where it was
        assertTrue(buf.visibleText().startsWith("welcome to codec\ntools ready\n$"))
    }

    @Test
    fun `typing with the keyboard open lands on the prompt line, not above it`() {
        val buf = promptScreen()
        buf.resize(20, 6)
        "ls".forEach { buf.print(it.code) }

        assertEquals('l'.code, buf.cell(2, 2).cp)
        assertEquals('s'.code, buf.cell(3, 2).cp)
    }

    @Test
    fun `the keyboard animation's intermediate sizes are lossless`() {
        val buf = promptScreen()
        val before = buf.visibleText()

        listOf(10, 8, 6, 4, 6, 8, 10, 12).forEach { buf.resize(20, it) }

        assertEquals(before, buf.visibleText())
        assertEquals(2, buf.cursorY)
        assertEquals(0, buf.scrollbackSize)
    }

    @Test
    fun `a screen that is full still round-trips through a keyboard toggle`() {
        val buf = TerminalBuffer(cols = 20, rows = 8, scrollbackLimit = 50)
        for (n in 1..20) {
            "line $n".forEach { buf.print(it.code) }
            buf.carriageReturn(); buf.lineFeed()
        }
        "$ ".forEach { buf.print(it.code) }
        val before = buf.visibleText()
        val cursor = buf.cursorY

        buf.resize(20, 4)
        assertEquals('$'.code, buf.cell(0, buf.cursorY).cp)   // cursor still on the prompt
        buf.resize(20, 8)

        assertEquals(before, buf.visibleText())
        assertEquals(cursor, buf.cursorY)
    }

    @Test
    fun `rows that hold text below the cursor are never dropped`() {
        val buf = TerminalBuffer(cols = 8, rows = 6)
        fun row(y: Int, t: String) { t.forEachIndexed { x, c -> buf.cell(x, y).cp = c.code } }
        row(0, "aa"); row(1, "bb"); row(4, "below")
        buf.cursorY = 1; buf.cursorX = 2

        buf.resize(8, 4)

        assertTrue(buf.visibleText().contains("below"))
    }
}
