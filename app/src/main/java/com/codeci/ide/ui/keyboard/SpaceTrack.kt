package com.codeci.ide.ui.keyboard

import kotlin.math.roundToInt

/**
 * Phase 28.2 round 2 — the space-bar trackpad (owner ask: "make the space
 * like Samsung keyboard to move the cursor by space button"). Press-hold
 * SPACE, slide, release: the caret follows the finger like a cursor
 * trackpad, and NO space character is typed once a drag began.
 *
 * The renderer quantizes the absolute drag into column/line counts at fixed
 * dp thresholds (never finger-outruns-buffer: quantization is computed from
 * the gesture origin, not incrementally), then this pure object resolves
 * (columns, lines) into a caret offset in the text:
 *
 *  - columns move within the CURRENT line and clamp at its ends;
 *  - lines travel keeping the desired column, clamping at the document ends;
 *  - selection collapse (anchor = start) is the caller's law, mirrored here
 *    by taking the given caret as-is.
 *
 * Host-tested so the caret math is CI-pinned; the only Android part is the
 * pointer plumbing that feeds it.
 */
object SpaceTrack {

    /** 1 caret column per 12 dp of horizontal drag (≈ half a keycap). */
    const val DP_PER_COLUMN = 12f

    /** 1 visual line per 28 dp of vertical drag. */
    const val DP_PER_LINE = 28f

    /** Hold threshold BEFORE the trackpad arms — a hair shorter than the
     * 26.1 popup hold (300 ms) so a deliberate press-and-hold always makes
     * the trackpad, while any quick tap still inserts a space. */
    const val TRIGGER_MS = 260L

    /** The drag origin → integer grid: total px delta per unit → units. */
    fun quantize(deltaPx: Float, pxPerUnit: Float): Int =
        if (pxPerUnit <= 0f) 0 else (deltaPx / pxPerUnit).roundToInt()

    /**
     * Caret after [columns] horizontal and [lines] vertical trackpad units
     * from [caret] in [text]. Columns first (within the starting line), then
     * lines (each travel keeps the column, clamped line-to-line). Always
     * within `0..text.length`.
     */
    fun caretAfterDrag(text: String, caret: Int, columns: Int, lines: Int): Int {
        if (text.isEmpty()) return 0
        var pos = caret.coerceIn(0, text.length)
        if (columns != 0) {
            val start = startOfLine(text, pos)
            val end = endOfLine(text, pos)
            pos = (pos + columns).coerceIn(start, end)
        }
        if (lines != 0) {
            repeat(lines.coerceAtLeast(-lines)) {
                pos = if (lines < 0) up(text, pos) else down(text, pos)
            }
        }
        return pos
    }

    private fun startOfLine(text: String, pos: Int): Int =
        if (pos == 0) 0 else text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)) + 1

    /** End of the line containing [pos], NOT including its '\n'. */
    private fun endOfLine(text: String, pos: Int): Int =
        text.indexOf('\n', pos.coerceIn(0, text.length)).let { if (it < 0) text.length else it }

    private fun up(text: String, pos: Int): Int {
        val lineStart = startOfLine(text, pos)
        if (lineStart == 0) return 0
        val column = pos - lineStart
        val prevStart = text.lastIndexOf('\n', lineStart - 2) + 1
        val prevEnd = text.indexOf('\n', prevStart).let { if (it < 0) lineStart - 1 else it }
        return (prevStart + column).coerceAtMost(prevEnd)
    }

    private fun down(text: String, pos: Int): Int {
        val lineStart = startOfLine(text, pos)
        val column = pos - lineStart
        val lineEnd = endOfLine(text, pos)
        if (lineEnd >= text.length) return text.length
        val nextStart = lineEnd + 1
        val nextEnd = text.indexOf('\n', nextStart).let { if (it < 0) text.length else it }
        return (nextStart + column).coerceAtMost(nextEnd)
    }
}
