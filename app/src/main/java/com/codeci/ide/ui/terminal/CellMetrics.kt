package com.codeci.ide.ui.terminal

import kotlin.math.abs
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
        cellHeightPx(fontSpacingPx, 1f)

    /**
     * Cell height with a terminal [lineFactor] (< 1 tightens the row pitch).
     *
     * Phase 19.2 device round 2: JetBrains Mono ships an editor-roomy 1.32 em
     * line (ascent 1020 + descent 300 per 1000 em, verified by parsing the
     * TTF hhea table) — drawn as-is the terminal gets ~33 rows where Termux
     * fits 39 ("rows too airy", owner). Terminals classically run ~1.17-1.20
     * em; [TERMINAL_LINE_FACTOR] brings JBM to ~1.19 em ≈ ratio 2.0 × the
     * 0.6 em advance — Termux-class density while keeping ascender/descender
     * ink clearance (JBM's actual descender ink is well inside 0.3 em).
     */
    fun cellHeightPx(fontSpacingPx: Float, lineFactor: Float): Int =
        ceil(fontSpacingPx.coerceAtLeast(1f) * lineFactor.coerceIn(0.5f, 2f))
            .toInt().coerceAtLeast(1)

    /** Row pitch tightening for the bundled JetBrains Mono (see above). */
    const val TERMINAL_LINE_FACTOR = 0.9f

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

    // ------------------------------------------------------------------
    // Phase 19.2 device-round fix (2026-08-31): fit the FONT SIZE to the
    // grid, not just the cell. Owner report: "letters have a noticeable
    // gap between them". Cause: [cellWidthPx] CEILS the advance, so every
    // cell was up to a full pixel wider than the glyph advance — uniform
    // extra tracking (~4-5% at phone sizes). Fix: nudge the text size a
    // hair so the advance IS a whole pixel; the integer cell then equals
    // the font's own advance — still drift-free (every origin stays an
    // integer multiple) but with no added spacing.
    // ------------------------------------------------------------------

    /** Snap tolerance: |advance − cell| at or below this is invisible. */
    private const val FIT_EPS_PX = 0.05f

    /** Never bend the user's requested size by more than this fraction. */
    private const val FIT_MAX_DRIFT = 0.08f

    /** Newton refinement steps; a linear monospace font converges in one. */
    private const val FIT_MAX_STEPS = 4

    /**
     * A text size fitted to the pixel grid.
     *
     * @property textSizePx the (possibly nudged) size — set it as
     *   `Paint.textSize`.
     * @property cellWidthPx whole-pixel cell width; when [snapped] this
     *   EQUALS the font's advance at [textSizePx] (within `FIT_EPS_PX`).
     * @property snapped false = fitting was refused (degenerate metrics or
     *   the nudge would exceed `FIT_MAX_DRIFT`); the cell is then the plain
     *   ceil fallback and the requested size is kept untouched.
     */
    data class FontFit(
        val textSizePx: Float,
        val cellWidthPx: Int,
        val snapped: Boolean
    )

    /**
     * Nudge the requested text size so the measured monospace advance lands
     * on a whole pixel, and return that pixel as the cell width.
     *
     * Example (the owner's device): advance 22.05 px → old cell = ceil = 23
     * → +0.95 px tracking per letter. Here: size ×22/22.05 → advance 22.00
     * → cell 22 → gap 0.00 px, size changed by 0.2%.
     *
     * Rounding DOWN is safe even though the cell may be a hair narrower than
     * the advance: glyphs are drawn at `col * cellWidth`, never sequentially,
     * so error cannot accumulate and is capped at `FIT_EPS_PX` per glyph.
     *
     * @param requestedSizePx the size in px the user asked for (setting or
     *   live pinch).
     * @param measure monospace advance in px at a given text size. The view
     *   passes `Paint.measureText("MMMMMMMMMM") / 10f`; host tests inject a
     *   synthetic font model.
     */
    fun fitSizeToGrid(
        requestedSizePx: Float,
        measure: (textSizePx: Float) -> Float
    ): FontFit {
        val size = requestedSizePx.coerceAtLeast(1f)
        val a0 = measure(size)
        fun fallback() = FontFit(size, cellWidthPx(a0), snapped = false)
        if (!a0.isFinite() || a0 < 1f) return fallback()

        val target = (a0 + 0.5f).toInt().coerceAtLeast(1)
        if (abs(target - a0) <= FIT_EPS_PX) return FontFit(size, target, snapped = true)
        if (abs(target / a0 - 1f) > FIT_MAX_DRIFT) return fallback()

        var fitted = size * target / a0
        var step = 0
        while (step < FIT_MAX_STEPS) {
            if (!fitted.isFinite()) return fallback()
            if (fitted > size * (1f + FIT_MAX_DRIFT) || fitted < size * (1f - FIT_MAX_DRIFT)) {
                return fallback()
            }
            val a = measure(fitted)
            if (!a.isFinite() || a <= 0f) return fallback()
            if (abs(a - target) <= FIT_EPS_PX) return FontFit(fitted, target, snapped = true)
            fitted *= target / a
            step++
        }
        return fallback()
    }
}
