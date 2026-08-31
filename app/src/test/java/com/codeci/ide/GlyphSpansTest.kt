package com.codeci.ide

import com.codeci.ide.ui.terminal.GlyphSpans
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 19.2 device round 3 — run-batched glyph drawing. spanLength decides
 * which columns can be drawn in ONE drawText (identical positions because
 * the snapped font advance == cellW). Wrong spans = misaligned text, so the
 * rules are locked here.
 */
class GlyphSpansTest {

    @Test
    fun `plain ascii run is one span`() {
        assertEquals(9, GlyphSpans.spanLength("ls -la /usr", null, 0, 11))
        assertEquals(4, GlyphSpans.spanLength("abcd", null, 0, 4))
    }

    @Test
    fun `span starts mid-line and stops at end`() {
        assertEquals(3, GlyphSpans.spanLength("xxabc", null, 2, 5))
        assertEquals(0, GlyphSpans.spanLength("abc", null, 3, 3))
    }

    @Test
    fun `non-ascii breaks the span`() {
        // Bengali base + vowel sign, emoji — none batchable.
        assertEquals(0, GlyphSpans.spanLength("ক", null, 0, 1))
        assertEquals(2, GlyphSpans.spanLength("ab\u0981cd", null, 0, 5))
        assertEquals(0, GlyphSpans.spanLength("\uD83D\uDE00", null, 0, 2))
    }

    @Test
    fun `cluster column breaks the span`() {
        val clusters = mapOf(1 to "ক\u09BF")
        assertEquals(1, GlyphSpans.spanLength("ab", clusters, 0, 2))
        assertEquals(0, GlyphSpans.spanLength("ab", clusters, 1, 2))
    }

    @Test
    fun `control and del characters are not batchable`() {
        assertEquals(0, GlyphSpans.spanLength("\u001b[0m", null, 0, 4))
        assertEquals(3, GlyphSpans.spanLength("abc\u007f", null, 0, 4))
    }

    @Test
    fun `spaces are plain - spans bridge blanks`() {
        assertEquals(5, GlyphSpans.spanLength("a b c", null, 0, 5))
        assertEquals(true, GlyphSpans.isPlain(' '))
        assertEquals(false, GlyphSpans.isPlain('\n'))
    }
}
