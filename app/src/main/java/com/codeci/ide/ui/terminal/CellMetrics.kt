package com.codeci.ide.ui.terminal

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Phase 19.2 — integer-cell monospace grid metrics.
 *
 * The renderer previously derived `cellW` straight from a float
 * `Paint.measureText("X")` and positioned glyphs at `(start + i) * cellW`.
 * With a fractional cell width (e.g. 9.37 px) the per-column product
 * accumulates rounding error across a row, Android snaps each glyph to the
 * pixel grid independently, and dense columns visually collide — the
 * "letters overlapping" report. Background rects, selection and the cursor
 * could also drift out of alignment with the glyphs by fractional seams.
 *
 * The fix (the mechanism every crisp terminal uses): snap the cell to a
 * whole number of pixels and derive *every* position from it, so
 * `colX = col * cellWpx` is exact and can never drift.
 */
object CellMetrics {

    /**
     * Grid cell width in whole pixels. Ceil (never floor) so the cell is at
     * least as wide as the font's monospace advance — a fractionally smaller
     * cell would clip/overlap glyphs; a fractionally larger one adds at most
     * a sub-pixel of letter spacing.
     *
     * @param glyphAdvancePx the measured advance of the monospace glyph
     *   (averaging `measureText("MMMMMMMMMM") / 10f` reduces single-measure
     *   rounding).
     */
    fun cellWidthPx(glyphAdvancePx: Float): Int =
        ceil(glyphAdvancePx.coerceAtLeast(1f)).toInt().coerceAtLeast(1)

    /**
     * Grid cell (row) height in whole pixels. Ceil so descenders never bleed
     * into the row below.
     */
    fun cellHeightPx(fontSpacingPx: Float): Int =
        ceil(fontSpacingPx.coerceAtLeast(1f)).toInt().coerceAtLeast(1)

    /** Columns that fit [widthPx]; at least one. */
    fun columnsForWidth(widthPx: Float, cellWidthPx: Int): Int {
        if (cellWidthPx <= 0) return 1
        return floor(widthPx / cellWidthPx).toInt().coerceAtLeast(1)
    }

    /** Rows that fit [heightPx]; at least one. */
    fun rowsForHeight(heightPx: Float, cellHeightPx: Int): Int {
        if (cellHeightPx <= 0) return 1
        return floor(heightPx / cellHeightPx).toInt().coerceAtLeast(1)
    }

    /** Exact pixel origin of a column — integer multiple, no drift. */
    fun columnX(col: Int, cellWidthPx: Int): Int = col * cellWidthPx

    /** Exact pixel origin of a row — integer multiple, no drift. */
    fun rowY(row: Int, cellHeightPx: Int): Int = row * cellHeightPx
}
