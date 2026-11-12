package com.codeci.ide.ui.terminal

/**
 * Phase 104.2 — pure pixel-extent math for the PTY `winsize` pixel fields.
 *
 * `struct winsize` holds the pixel width and height as unsigned 16-bit values. A
 * grid of [cells] columns (or rows) of [cellPx] pixels each spans
 * `cells * cellPx` pixels; the result is clamped to the 16-bit field range, and
 * a non-positive input yields 0 ("unknown"), which is what the kernel expects
 * when the size is not measured.
 */
object PtyWinSize {
    const val MAX_FIELD = 65_535

    fun pixelExtent(cells: Int, cellPx: Int): Int {
        if (cells <= 0 || cellPx <= 0) return 0
        val total = cells.toLong() * cellPx.toLong()
        return if (total > MAX_FIELD) MAX_FIELD else total.toInt()
    }
}
