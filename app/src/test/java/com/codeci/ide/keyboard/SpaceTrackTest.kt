package com.codeci.ide.keyboard

import com.codeci.ide.ui.keyboard.SpaceTrack
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 28.2 round 2 — the space-bar trackpad math, pure and pinned: the
 * Samsung-style caret laws (clamp inside the line, travel lines keeping the
 * column, absolute origin quantization without drift).
 */
class SpaceTrackTest {

    private val twoLines = "abc\ndef"

    @Test
    fun columnsClampInsideTheCurrentLine() {
        // caret at index 5 (line 2, col 1): dragging right runs out at 'f'.
        assertEquals(6, SpaceTrack.caretAfterDrag(twoLines, 5, 10, 0))
        // …and left runs out at 'd'.
        assertEquals(4, SpaceTrack.caretAfterDrag(twoLines, 5, -10, 0))
    }

    @Test
    fun linesKeepTheColumnAndClampAtTheDocument() {
        assertEquals(1, SpaceTrack.caretAfterDrag(twoLines, 5, 0, -1)) // same column up
        assertEquals(5, SpaceTrack.caretAfterDrag(twoLines, 1, 0, 1))  // same column down
        assertEquals(0, SpaceTrack.caretAfterDrag(twoLines, 1, 0, -9)) // top
        assertEquals(twoLines.length, SpaceTrack.caretAfterDrag(twoLines, 1, 0, 9)) // bottom
    }

    @Test
    fun raggedLinesClampShorterLinesOnly() {
        val text = "0123456789\nab\ncdefghijkl"
        // down onto the 2-char line: column clamps to its end…
        val onShort = SpaceTrack.caretAfterDrag(text, 1, 0, 1)
        assertEquals(12, onShort)
        // …but the column SURVIVES the travel: down again lands at col 1 of
        // the long line (the desired-column law, not a sticky clamp).
        assertEquals(15, SpaceTrack.caretAfterDrag(text, onShort, 0, 1))
    }

    @Test
    fun quantizationIsAbsoluteNoDrift() {
        // origin-relative quantize: repeated sampling at the same px yields
        // the same units — the finger can never outrun the buffer.
        val per = 12f
        assertEquals(0, SpaceTrack.quantize(6.0f, per))
        assertEquals(1, SpaceTrack.quantize(6.1f, per))
        assertEquals(2, SpaceTrack.quantize(18.0f, per))
        assertEquals(-1, SpaceTrack.quantize(-6.1f, per))
        assertEquals(0, SpaceTrack.quantize(5f, 0f)) // degenerate unit = nothing
    }

    @Test
    fun emptyDocumentStaysZero() {
        assertEquals(0, SpaceTrack.caretAfterDrag("", 0, 3, -3))
    }

    @Test
    fun triggerIsArmedBeforeThePopupThreshold() {
        // The trackpad must beat the 26.1 popup hold or a deliberate press
        // shows a popup bubble instead of sliding.
        assertEquals(true, SpaceTrack.TRIGGER_MS < 300L)
    }
}
