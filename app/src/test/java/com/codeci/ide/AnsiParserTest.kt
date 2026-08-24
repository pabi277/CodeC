package com.codeci.ide

import com.codeci.ide.ui.terminal.CellFlags
import com.codeci.ide.ui.terminal.TerminalEmulator
import com.codeci.ide.ui.terminal.XtermColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiParserTest {

    private fun emu(cols: Int = 40, rows: Int = 10): TerminalEmulator =
        TerminalEmulator(cols, rows)

    @Test
    fun `plain text lands on the screen`() {
        val emu = emu()
        emu.feed("hello")
        assertEquals("hello", emu.visibleText())
        assertEquals(5, emu.buffer.cursorX)
        assertEquals(0, emu.buffer.cursorY)
    }

    @Test
    fun `newline and carriage return move the cursor`() {
        val emu = emu()
        emu.feed("ab\r\ncd")
        assertEquals("ab\ncd", emu.visibleText())
        assertEquals(2, emu.buffer.cursorX)
        assertEquals(1, emu.buffer.cursorY)
    }

    @Test
    fun `cup positions the cursor one-based`() {
        val emu = emu()
        emu.feed("\u001b[3;5H*")
        assertEquals(5, emu.buffer.cursorX)
        assertEquals(2, emu.buffer.cursorY)
        assertEquals('*'.code, emu.buffer.cell(4, 2).cp)
    }

    @Test
    fun `cursor movement CUU CUD CUF CUB`() {
        val emu = emu()
        emu.feed("\u001b[5;5H")
        emu.feed("\u001b[2A\u001b[2C\u001b[1B\u001b[3D")
        assertEquals(3, emu.buffer.cursorY)
        assertEquals(3, emu.buffer.cursorX)
    }

    @Test
    fun `sgr indexed and bright colors`() {
        val emu = emu()
        emu.feed("\u001b[31;1mX")
        val cell = emu.buffer.cell(0, 0)
        assertEquals(1, cell.fg)
        assertTrue(cell.flags and CellFlags.BOLD != 0)
        emu.feed("\u001b[0m\u001b[94mY")
        assertEquals(12, emu.buffer.cell(1, 0).fg)
    }

    @Test
    fun `sgr 256-color and rgb`() {
        val emu = emu()
        emu.feed("\u001b[38;5;196mA")
        assertEquals(196, emu.buffer.cell(0, 0).fg)
        emu.feed("\u001b[48;2;10;20;30mB")
        val bg = emu.buffer.cell(1, 0).bg
        assertTrue(XtermColors.isRgb(bg))
        assertEquals(10, XtermColors.red(bg))
        assertEquals(20, XtermColors.green(bg))
        assertEquals(30, XtermColors.blue(bg))
    }

    @Test
    fun `erase in line and display`() {
        val emu = emu()
        emu.feed("ABCD\u001b[1;3H\u001b[K")
        assertEquals("AB", emu.visibleText())
        emu.feed("\u001b[2J")
        assertEquals("", emu.visibleText())
    }

    @Test
    fun `auto wrap at the last column`() {
        val emu = emu(cols = 4, rows = 3)
        emu.feed("abcde")
        assertEquals("abcd\ne", emu.visibleText())
        assertEquals(1, emu.buffer.cursorX)
        assertEquals(1, emu.buffer.cursorY)
    }

    @Test
    fun `scrollback captures lines that leave the top`() {
        val emu = emu(cols = 8, rows = 3)
        emu.feed("one\r\ntwo\r\nthree\r\nfour")
        assertTrue(emu.buffer.scrollbackSize >= 1)
        val history = emu.buffer.scrollbackText()
        assertTrue(history.contains("one"))
        assertTrue(emu.visibleText().contains("four"))
    }

    @Test
    fun `alternate screen and bracketed paste modes`() {
        val emu = emu()
        emu.feed("keep")
        emu.feed("\u001b[?1049h")
        assertTrue(emu.buffer.isAltScreen)
        emu.feed("alt")
        assertEquals("alt", emu.visibleText())
        emu.feed("\u001b[?1049l")
        assertFalse(emu.buffer.isAltScreen)
        assertTrue(emu.visibleText().contains("keep"))

        emu.feed("\u001b[?2004h")
        assertTrue(emu.bracketedPaste)
        assertEquals("\u001b[200~hi\u001b[201~", emu.wrapPaste("hi"))
        emu.feed("\u001b[?2004l")
        assertEquals("hi", emu.wrapPaste("hi"))
    }

    @Test
    fun `osc title and reset`() {
        val emu = emu()
        emu.feed("\u001b]0;CodeC\u0007")
        assertEquals("CodeC", emu.title)
        emu.feed("hello\u001bc")
        assertEquals("", emu.visibleText())
        assertEquals("Terminal", emu.title)
    }

    @Test
    fun `utf8 across feed chunks`() {
        val emu = emu()
        val bytes = "é".toByteArray(Charsets.UTF_8)
        assertTrue(bytes.size >= 2)
        emu.feed(bytes, 0, 1)
        emu.feed(bytes, 1, bytes.size - 1)
        assertEquals("é", emu.visibleText())
    }

    @Test
    fun `dsr cursor report`() {
        var reply: String? = null
        val emu = emu()
        emu.responder = { reply = it }
        emu.feed("\u001b[5;7H\u001b[6n")
        assertEquals("\u001b[5;7R", reply)
    }

    @Test
    fun `insert and delete characters`() {
        val emu = emu()
        emu.feed("ABCD\u001b[1;2H\u001b[2P")
        assertEquals("AD", emu.visibleText())
        emu.feed("\u001b[1;2H\u001b[2@XX")
        assertEquals("AXXD", emu.visibleText())
    }

    @Test
    fun `unknown csi is ignored without corrupting text`() {
        val emu = emu()
        emu.feed("ok\u001b[999;1;2~more")
        assertEquals("okmore", emu.visibleText())
    }

    @Test
    fun `transcript includes scrollback plus the live screen`() {
        val emu = emu(cols = 8, rows = 3)
        emu.feed("one\r\ntwo\r\nthree\r\nfour")
        val text = emu.transcriptText()
        assertTrue(text.contains("one"))
        assertTrue(text.contains("four"))
    }

    @Test
    fun `osc 1337 CodeCRequestStorage triggers storage callback`() {
        var requested = false
        val emu = emu()
        emu.onStoragePermissionRequested = { requested = true }
        emu.feed("\u001b]1337;CodeCRequestStorage\u0007")
        assertTrue(requested)
    }

    @Test
    fun `application cursor keys flip the sequence`() {
        val emu = emu()
        assertEquals("\u001b[A", emu.cursorKey('A'))
        emu.feed("\u001b[?1h")
        assertEquals("\u001bOA", emu.cursorKey('A'))
    }
}
