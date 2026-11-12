package com.codeci.ide.ui.terminal

/**
 * Phase 104.3 — alternate-screen scrolling (pure).
 *
 * In the alternate screen the terminal has no scrollback, so a wheel or a fling
 * becomes cursor keys when DEC mode 1007 (alternate scroll) is set and no mouse
 * tracking mode is active. `less`, `man` and similar pagers then scroll with the
 * finger, as in Termux.
 */
object AltScroll {

    /** Whole rows to send ([rows] > 0 = down, < 0 = up) and the sub-row [remainderPx] to carry. */
    data class Steps(val rows: Int, val remainderPx: Float)

    /**
     * True when a swipe or a fling must become cursor keys: alternate screen,
     * mode 1007 set, and no mouse tracking (tracking takes the wheel itself).
     */
    fun active(altScreen: Boolean, mouseMode: Int): Boolean =
        altScreen &&
            (mouseMode and MouseModes.ALT_SCROLL) != 0 &&
            (mouseMode and MouseModes.CAPTURE_MASK) == 0

    /**
     * Converts a pixel [deltaPx] into whole rows, keeping the sub-row remainder
     * so slow drags still scroll. Truncates toward zero, so up and down are
     * symmetric. A non-positive [cellH] yields no rows.
     */
    fun steps(deltaPx: Float, remainderPx: Float, cellH: Float): Steps {
        if (cellH <= 0f) return Steps(0, 0f)
        val total = remainderPx + deltaPx
        val rows = (total / cellH).toInt()
        return Steps(rows, total - rows * cellH)
    }
}
