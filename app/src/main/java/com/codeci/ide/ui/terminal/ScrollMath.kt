package com.codeci.ide.ui.terminal

/**
 * Phase 19.2 device round 3 (owner: "not smooth scrolling"): drag scrolling
 * moved in WHOLE-ROW jumps (`(dy / cellH).toInt()`), which reads as notchy
 * next to pixel-smooth touch scrolling. This accumulator keeps the
 * sub-row remainder in pixels so the renderer can translate the grid by a
 * fraction of a row — finger-follower smoothing, Termux-style.
 *
 * `topRow` semantics: 0 = live screen, negative = scrolled into scrollback.
 * `deltaPx` is web-convention scroll (positive = view newer content).
 */
object ScrollMath {

    data class Result(
        val topRow: Int,
        /** Sub-row remainder in px, always within (-cellH, cellH). */
        val subRowPx: Float
    )

    /**
     * Advance the scroll position by [deltaPx].
     *
     * @param subRowPx the previous sub-row remainder (px)
     * @param cellH row height in px (>= 1)
     * @param topRow the previous row offset (<= 0)
     * @param minTop the oldest reachable row (-scrollbackCount)
     */
    fun step(
        subRowPx: Float,
        deltaPx: Float,
        cellH: Float,
        topRow: Int,
        minTop: Int
    ): Result {
        val h = cellH.coerceAtLeast(1f)
        var px = subRowPx + deltaPx
        var row = topRow
        // Absorb whole rows in one go (a fling can jump many rows).
        val whole = (px / h).toInt()
        px -= whole * h
        val target = row + whole
        row = target.coerceIn(minTop.coerceAtMost(0), 0)
        if (row != target) px = 0f // hit a rail — drop the overshoot
        return Result(row, px)
    }
}
