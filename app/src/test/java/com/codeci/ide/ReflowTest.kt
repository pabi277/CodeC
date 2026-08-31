package com.codeci.ide

import com.codeci.ide.ui.terminal.CellFlags
import com.codeci.ide.ui.terminal.TerminalBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 19.1 — resize must REFLOW: soft-wrapped rows rejoin into logical
 * lines and re-split at the new width, the cursor maps through, scrollback
 * participates (and honors its limit), rows-only changes shift rows between
 * screen and scrollback, and the alt screen keeps its rectangular copy.
 */
class ReflowTest {

    private fun line(buf: TerminalBuffer, text: String) {
        for (ch in text) buf.print(ch.code)
        buf.carriageReturn()
        buf.lineFeed()
    }

    @Test
    fun `zoom out rejoins soft-wrapped rows to full width`() {
        val buf = TerminalBuffer(cols = 4, rows = 2, scrollbackLimit = 10)
        "abcdefgh".forEach { buf.print(it.code) }   // row0 wraps into row1

        buf.resize(8, 2)

        assertEquals(8, buf.cols)
        assertEquals('e'.code, buf.cell(4, 0).cp)
        assertEquals("abcdefgh", buf.visibleText())
    }

    @Test
    fun `zoom in re-wraps a long line without losing content`() {
        val buf = TerminalBuffer(cols = 8, rows = 2)
        "abcdefgh".forEach { buf.print(it.code) }

        buf.resize(4, 2)

        assertEquals("abcd\nefgh", buf.visibleText())
    }

    @Test
    fun `hard newlines are not rejoined`() {
        val buf = TerminalBuffer(cols = 4, rows = 2)
        line(buf, "ab")
        line(buf, "cd")

        buf.resize(8, 2)

        assertEquals("ab\ncd", buf.visibleText())
    }

    @Test
    fun `cursor position maps through a widen`() {
        val buf = TerminalBuffer(cols = 4, rows = 2)
        "abc".forEach { buf.print(it.code) }

        buf.resize(8, 2)

        assertEquals(0, buf.cursorY)
        assertEquals(3, buf.cursorX)
    }

    @Test
    fun `cursor position maps through a narrow`() {
        val buf = TerminalBuffer(cols = 8, rows = 2)
        "abcdefg".forEach { buf.print(it.code) }

        buf.resize(4, 4)

        // Logical column 7 lands on row 1, column 3 of the new 4-col grid.
        assertEquals(1, buf.cursorY)
        assertEquals(3, buf.cursorX)
        assertEquals("abcd\nefg", buf.visibleText())
    }

    @Test
    fun `scrollback reflows and honors its limit`() {
        val buf = TerminalBuffer(cols = 4, rows = 2, scrollbackLimit = 3)
        line(buf, "aa11")
        line(buf, "bb22")
        line(buf, "cc33")
        line(buf, "dd44")
        line(buf, "ee55")
        line(buf, "ff66")

        buf.resize(8, 2)

        // 6 logical lines -> 1 screen row? No: 6 lines, 2 screen rows, at
        // most 3 kept in scrollback -> the oldest line is dropped.
        assertEquals(3, buf.scrollbackSize)
        val snap = buf.snapshot()
        val transcript = snap.transcriptText()
        assertTrue(transcript.contains("ee55"))
        assertTrue(transcript.contains("ff66"))
        assertTrue(!transcript.contains("aa11"))
    }

    @Test
    fun `rows-only grow restores lines from scrollback`() {
        val buf = TerminalBuffer(cols = 4, rows = 2, scrollbackLimit = 10)
        line(buf, "aa")
        line(buf, "bb")
        line(buf, "cc")

        buf.resize(4, 4)

        assertEquals(0, buf.scrollbackSize)
        assertEquals("aa\nbb\ncc", buf.visibleText())
    }

    @Test
    fun `rows-only shrink overflows into scrollback`() {
        val buf = TerminalBuffer(cols = 4, rows = 4, scrollbackLimit = 10)
        line(buf, "aa")
        line(buf, "bb")
        line(buf, "cc")
        line(buf, "dd")

        buf.resize(4, 2)

        assertEquals(2, buf.scrollbackSize)
        assertEquals("cc\ndd", buf.visibleText())
        assertEquals("aa\nbb", buf.scrollbackText().trimEnd())
    }

    @Test
    fun `alt screen keeps a rectangular copy`() {
        val buf = TerminalBuffer(cols = 4, rows = 2)
        buf.enterAltScreen()
        "abcd".forEach { buf.print(it.code) }

        buf.resize(2, 2)

        assertEquals(2, buf.cols)
        assertEquals('a'.code, buf.cell(0, 0).cp)
        assertEquals('b'.code, buf.cell(1, 0).cp)
        // Not reflowed: the row was truncated, not wrapped onto row 1.
        assertEquals(' '.code, buf.cell(0, 1).cp)
    }

    @Test
    fun `wrapped row scrolled into history still rejoins`() {
        val buf = TerminalBuffer(cols = 4, rows = 2, scrollbackLimit = 10)
        "abcdefgh".forEach { buf.print(it.code) }   // row0(wrapped) + row1

        buf.resize(4, 1)                            // rows-only: row0 -> scrollback
        assertEquals(1, buf.rows)
        assertEquals("efgh", buf.visibleText())

        buf.resize(8, 1)                            // width change reflows history too
        assertEquals("abcdefgh", buf.visibleText())
        assertEquals(0, buf.scrollbackSize)
    }

    @Test
    fun `double-width glyph is never split at the new boundary`() {
        val buf = TerminalBuffer(cols = 3, rows = 2)
        buf.print('x'.code)
        buf.print(0x6F22)                            // 漢 = lead + continuation

        buf.resize(2, 2)

        assertEquals('x'.code, buf.cell(0, 0).cp)
        assertEquals(' '.code, buf.cell(1, 0).cp)   // padded blank at row end
        assertEquals(0x6F22, buf.cell(0, 1).cp)     // the wide glyph starts row 2
        assertTrue(buf.cell(0, 1).flags and CellFlags.WIDE_LEAD != 0)
    }

    @Test
    fun `wide pair at the right margin wraps whole`() {
        val buf = TerminalBuffer(cols = 4, rows = 2)
        "abc".forEach { buf.print(it.code) }
        buf.print(0x6F22)                            // would straddle col 3

        assertEquals(0x6F22, buf.cell(0, 1).cp)     // starts the next row instead
        assertEquals(2, buf.cursorX)                // lead + continuation
    }
}
