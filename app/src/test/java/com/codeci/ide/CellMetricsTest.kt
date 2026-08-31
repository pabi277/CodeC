package com.codeci.ide

import com.codeci.ide.ui.terminal.CellMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 19.2 — the grid metrics must be whole pixels (ceil, never below 1)
 * and every glyph origin must be an exact integer multiple of the cell, so
 * fractional accumulation can never make glyphs collide.
 */
class CellMetricsTest {

    @Test
    fun `cell width rounds up to a whole pixel and never shrinks the glyph`() {
        assertEquals(10, CellMetrics.cellWidthPx(9.37f))
        assertEquals(9, CellMetrics.cellWidthPx(9.0f))
        assertEquals(1, CellMetrics.cellWidthPx(0.2f))
        assertTrue(CellMetrics.cellWidthPx(-5f) >= 1)
    }

    @Test
    fun `cell height rounds up so descenders never bleed into the next row`() {
        assertEquals(18, CellMetrics.cellHeightPx(17.2f))
        assertEquals(17, CellMetrics.cellHeightPx(17.0f))
        assertEquals(1, CellMetrics.cellHeightPx(0.01f))
    }

    @Test
    fun `column and row counts floor the available space and are at least one`() {
        assertEquals(120, CellMetrics.columnsForWidth(1080f, 9))
        assertEquals(119, CellMetrics.columnsForWidth(1079.9f, 9))
        assertEquals(1, CellMetrics.columnsForWidth(3f, 9))
        assertEquals(47, CellMetrics.rowsForHeight(850f, 18))
        assertEquals(1, CellMetrics.rowsForHeight(17f, 18))
    }

    @Test
    fun `glyph origins are exact integer multiples across a wide row`() {
        var col = 0
        while (col <= 200) {
            assertEquals(col * 9, CellMetrics.columnX(col, 9))
            assertEquals(col * 18, CellMetrics.rowY(col, 18))
            col++
        }
    }
}
