package com.codeci.ide

import com.codeci.ide.ui.terminal.CellFlags
import com.codeci.ide.ui.terminal.TerminalBuffer
import com.codeci.ide.ui.terminal.TerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 19.4 — double-width glyphs occupy two cells (lead + continuation),
 * combining marks join the preceding cell as one cluster, and the UTF-8
 * parser path exercises both through real emulator feeds.
 */
class TerminalUnicodeTest {

    @Test
    fun `double-width glyph occupies two cells and advances two columns`() {
        val buf = TerminalBuffer(cols = 8, rows = 2)
        buf.print(0x6F22)   // 漢

        assertEquals(0x6F22, buf.cell(0, 0).cp)
        assertTrue(buf.cell(0, 0).flags and CellFlags.WIDE_LEAD != 0)
        assertTrue(buf.cell(1, 0).flags and CellFlags.WIDE_CONT != 0)
        assertEquals(2, buf.cursorX)
    }

    @Test
    fun `combining mark does not advance and clusters onto the base cell`() {
        val buf = TerminalBuffer(cols = 8, rows = 2)
        buf.print('a'.code)
        buf.print(0x0301)   // combining acute

        assertEquals(1, buf.cursorX)
        val snap = buf.snapshot()
        assertEquals("a", snap.lines[0].text.trimEnd())
        assertEquals("a\u0301", snap.lines[0].clusters?.get(0))
    }

    @Test
    fun `combining mark after a wide glyph attaches to its lead cell`() {
        val buf = TerminalBuffer(cols = 8, rows = 2)
        buf.print(0x6F22)
        buf.print(0x0301)

        assertEquals(2, buf.cursorX)   // still just after the pair
        val snap = buf.snapshot()
        assertEquals("\u6F22\u0301", snap.lines[0].clusters?.get(0))
    }

    @Test
    fun `utf8 feed places bengali cluster in one cell`() {
        val emu = TerminalEmulator(cols = 8, rows = 2)
        emu.feed("কি")     // U+0995 letter + U+09BF vowel sign (Mc → 0 width)

        assertEquals(1, emu.buffer.cursorX)
        val snap = emu.buffer.snapshot()
        assertEquals("কি", snap.lines[0].clusters?.get(0))
    }

    @Test
    fun `utf8 feed lays out two CJK ideographs over four columns`() {
        val emu = TerminalEmulator(cols = 8, rows = 2)
        emu.feed("日本")

        assertEquals(4, emu.buffer.cursorX)
        // Grid text keeps one char per column (continuations are blanks)…
        assertEquals("日 本", emu.visibleText().trimEnd())
        // …while transcript text joins the pairs for copy/share.
        assertEquals("日本", emu.buffer.snapshot().transcriptText())
    }

    @Test
    fun `emoji occupies two columns and keeps text column-aligned`() {
        val emu = TerminalEmulator(cols = 8, rows = 2)
        emu.feed("A\uD83D\uDE00B")   // A, 😀 (astral), B

        assertEquals(4, emu.buffer.cursorX)
        val snap = emu.buffer.snapshot()
        // One char per column in the grid text (astral glyph is parked in
        // the cluster map), so column ↔ index math stays exact.
        assertEquals("A  B", snap.lines[0].text.trimEnd())
        assertEquals("\uD83D\uDE00", snap.lines[0].clusters?.get(1))
        assertEquals("A\uD83D\uDE00B", snap.transcriptText())
    }
}
