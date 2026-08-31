package com.codeci.ide

import com.codeci.ide.ui.editor.LineEndings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 16 — the LF ⇄ CRLF conversion applied on save (pure, host-tested):
 * detection is majority-rule, the buffer always normalizes to LF, and saving
 * a CRLF-detected tab re-expands every newline exactly once.
 */
class LineEndingsTest {

    @Test
    fun `detect picks CRLF only when it dominates`() {
        assertEquals(LineEndings.CRLF, LineEndings.detect("a\r\nb\r\n"))
        assertEquals(LineEndings.CRLF, LineEndings.detect("a\nb\r\n")) // tie → CRLF wins
        assertEquals(LineEndings.LF, LineEndings.detect("a\nb\nc\r\n")) // 1 CRLF vs 2 LF
        assertEquals(LineEndings.LF, LineEndings.detect("a\nb\n"))
        assertEquals(LineEndings.LF, LineEndings.detect(""))
        assertEquals(LineEndings.LF, LineEndings.detect("single line, no newline"))
    }

    @Test
    fun `normalizeToLf flattens CRLF and lone CR`() {
        assertEquals("a\nb\nc", LineEndings.normalizeToLf("a\r\nb\rc"))
        assertEquals("already", LineEndings.normalizeToLf("already")) // identity fast path
    }

    @Test
    fun `open then save round-trips a CRLF file byte-identically`() {
        val raw = "int x;\r\nint y;\r\n"
        val ending = LineEndings.detect(raw)
        val buffer = LineEndings.normalizeToLf(raw)
        assertEquals("int x;\nint y;\n", buffer)
        assertEquals(ending, LineEndings.CRLF)
        assertEquals(raw, LineEndings.toNative(buffer, ending))
    }

    @Test
    fun `LF files save untouched`() {
        val raw = "int x;\nint y;\n"
        val buffer = LineEndings.normalizeToLf(raw)
        assertEquals(raw, LineEndings.toNative(buffer, LineEndings.detect(raw)))
    }

    @Test
    fun `toggle flips both ways and reads unknown as LF`() {
        assertEquals(LineEndings.CRLF, LineEndings.toggle(LineEndings.LF))
        assertEquals(LineEndings.LF, LineEndings.toggle(LineEndings.CRLF))
        assertEquals(LineEndings.CRLF, LineEndings.toggle("bogus"))
    }
}
