package com.codeci.ide

import com.codeci.ide.ui.terminal.CellMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.round

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

    // ------------------------------------------------------------------
    // Phase 19.2 device-round fix — fitSizeToGrid. Owner report
    // 2026-08-31: "letters have a noticeable gap between them". ceil()
    // added up to 1px of tracking per letter; the fit must make the
    // integer cell EQUAL the font's advance.
    // ------------------------------------------------------------------

    private val linearMono: (Float) -> Float = { 0.6f * it } // advance = 0.6em

    @Test
    fun `fit snaps the size so the cell equals the advance (no letter gaps)`() {
        // The owner's case: advance 22.05px. Old: ceil → 23 → +0.95px/letter.
        val fit = CellMetrics.fitSizeToGrid(36.75f, linearMono)
        assertTrue(fit.snapped)
        assertEquals(22, fit.cellWidthPx)
        // THE fix: gap between cell and advance is sub-1/20px, not sub-1px.
        assertTrue(abs(linearMono(fit.textSizePx) - fit.cellWidthPx) <= 0.05f)
        // The size bend is imperceptible (<2%).
        assertTrue(abs(fit.textSizePx - 36.75f) / 36.75f <= 0.08f)
    }

    @Test
    fun `already-integer advance keeps the requested size`() {
        val fit = CellMetrics.fitSizeToGrid(30f) { 0.5f * it } // 15.0px exactly
        assertTrue(fit.snapped)
        assertEquals(30f, fit.textSizePx, 1e-6f)
        assertEquals(15, fit.cellWidthPx)
    }

    @Test
    fun `fit rounds down safely - no drift, no overlap`() {
        // advance 9.36px at size 15.6 → target 9 (round down is fine:
        // per-glyph error is capped at FIT_EPS, it never accumulates).
        val fit = CellMetrics.fitSizeToGrid(15.6f) { 0.6f * it }
        assertTrue(fit.snapped)
        assertEquals(9, fit.cellWidthPx)
        assertTrue(abs(0.6f * fit.textSizePx - 9f) <= 0.05f)
    }

    @Test
    fun `excessive drift is refused - keep the size, fall back to ceil`() {
        // advance ≈4.5px: every whole-pixel target needs a >8% size bend →
        // refuse the snap, keep the user's size, use the old ceil cell.
        val fit = CellMetrics.fitSizeToGrid(10f) { 0.45f * it }
        assertFalse(fit.snapped)
        assertEquals(10f, fit.textSizePx, 1e-6f)
        assertEquals(5, fit.cellWidthPx) // ceil(4.5), the old behavior
    }

    @Test
    fun `fit invariants hold across a size sweep with quantized advances`() {
        // Half-pixel-quantized advance models hinted fonts; whatever the fit
        // does, the guarantees must hold.
        val measure: (Float) -> Float = { size -> round(0.62f * size * 2f) / 2f }
        var size = 8f
        while (size <= 40f) {
            val fit = CellMetrics.fitSizeToGrid(size, measure)
            assertTrue(fit.cellWidthPx >= 1)
            assertTrue(fit.textSizePx >= 1f)
            if (fit.snapped) {
                // Cell equals the advance and the bend stays within bounds.
                assertTrue(abs(measure(fit.textSizePx) - fit.cellWidthPx) <= 0.051f)
                assertTrue(abs(fit.textSizePx - size) / size <= 0.081f)
            } else {
                // Fallback = untouched size + old ceil cell.
                assertEquals(size, fit.textSizePx, 1e-6f)
                assertEquals(CellMetrics.cellWidthPx(measure(size)), fit.cellWidthPx)
            }
            size += 0.7f
        }
    }

    @Test
    fun `degenerate measurements fall back safely`() {
        CellMetrics.fitSizeToGrid(15f) { 0f }.let {
            assertFalse(it.snapped)
            assertEquals(15f, it.textSizePx, 1e-6f)
            assertEquals(1, it.cellWidthPx)
        }
        CellMetrics.fitSizeToGrid(15f) { Float.NaN }.let {
            assertFalse(it.snapped)
            assertEquals(1, it.cellWidthPx)
        }
    }
}
