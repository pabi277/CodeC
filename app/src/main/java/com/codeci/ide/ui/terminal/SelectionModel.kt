package com.codeci.ide.ui.terminal

import kotlin.math.floor

/**
 * Phase 103.1 — terminal text selection as a pure, Android-free model.
 *
 * A selection is stored in **buffer-absolute** rows: row 0 is the oldest
 * retained history line, the live screen follows it (see
 * [TerminalSnapshot.absRowOf]). Output that scrolls the screen therefore
 * never moves a selection off its text — the bug the owner hit when a
 * selection made mid-`cat` drifted with the output.
 *
 * Behaviour spec (clean-room, read 2026-10-14 in termux-app
 * `TextSelectionCursorController` / `TerminalView.getSelectedText`):
 * two draggable handles, the word under a long-press as the first selection,
 * and extraction that re-joins soft-wrapped rows.
 */

/** A cell in buffer-absolute coordinates. Ordered reading-wise: row, then column. */
data class SelCell(val row: Long, val col: Int) : Comparable<SelCell> {
    override fun compareTo(other: SelCell): Int =
        if (row != other.row) row.compareTo(other.row) else col.compareTo(other.col)
}

/** Which end of the selection a finger is holding. */
enum class SelHandle { START, END }

/**
 * An inclusive selection. Invariant: [start] <= [end]. [active] is the handle
 * under the finger during a drag (null when the selection was just made).
 */
data class TermSelection(
    val start: SelCell,
    val end: SelCell,
    val active: SelHandle? = null
) {
    init {
        require(start <= end) { "selection start must not follow its end" }
    }
}

object SelectionModel {

    /** Edge band (in rows) where a dragged handle starts to auto-scroll. */
    const val EDGE_BAND_ROWS = 2f

    /** Fastest auto-scroll, in rows per frame, at the very edge. */
    const val EDGE_MAX_ROWS_PER_FRAME = 0.6f

    /**
     * The first selection made by a long-press: the word under the finger.
     * Returns null on the alternate screen — full-screen apps own the grid,
     * so a selection there would copy the wrong text (Termux refuses too).
     */
    fun begin(row: Long, wordStart: Int, wordEnd: Int, altScreen: Boolean): TermSelection? {
        if (altScreen) return null
        val lo = minOf(wordStart, wordEnd)
        val hi = maxOf(wordStart, wordEnd)
        return TermSelection(SelCell(row, lo), SelCell(row, hi), active = null)
    }

    /** Select the whole retained buffer (history + live screen). */
    fun selectAll(snap: TerminalSnapshot): TermSelection {
        val first = snap.historyBase
        val last = snap.absRowOf(snap.rows - 1)
        return TermSelection(
            SelCell(first, 0),
            SelCell(last, (snap.cols - 1).coerceAtLeast(0)),
            active = null
        )
    }

    /**
     * Move the handle that is under the finger ([grabbed]) to [to]. The other
     * end stays put. If the finger crosses the other end the two swap roles
     * and [TermSelection.active] names the end the finger now holds, so the
     * caller keeps dragging the right one.
     */
    fun drag(sel: TermSelection, grabbed: SelHandle, to: SelCell): TermSelection {
        val other = if (grabbed == SelHandle.START) sel.end else sel.start
        val lo = minOf(to, other)
        val hi = maxOf(to, other)
        val active = when {
            to < other -> SelHandle.START
            to > other -> SelHandle.END
            else -> grabbed
        }
        return TermSelection(lo, hi, active)
    }

    /**
     * Keep a selection valid when the retained buffer changes under it (history
     * cleared, width changed). Rows are clamped into [oldest]..[newest]; a
     * selection that lies entirely outside them is dropped (null).
     */
    fun clamp(sel: TermSelection, oldest: Long, newest: Long, cols: Int): TermSelection? {
        if (sel.end.row < oldest || sel.start.row > newest) return null
        val last = (cols - 1).coerceAtLeast(0)
        val start = if (sel.start.row < oldest) SelCell(oldest, 0) else sel.start.let {
            SelCell(it.row, it.col.coerceIn(0, last))
        }
        val end = if (sel.end.row > newest) SelCell(newest, last) else sel.end.let {
            SelCell(it.row, it.col.coerceIn(0, last))
        }
        if (start > end) return null
        return TermSelection(start, end, sel.active)
    }

    /**
     * Inclusive column span this selection covers on buffer row [row], or null
     * when the row is outside the selection. Interior rows are full width.
     */
    fun spanOnRow(sel: TermSelection, row: Long, cols: Int): IntRange? {
        if (row < sel.start.row || row > sel.end.row) return null
        val last = (cols - 1).coerceAtLeast(0)
        val from = if (row == sel.start.row) sel.start.col else 0
        val to = if (row == sel.end.row) sel.end.col else last
        if (from > to) return null
        return from..to
    }

    /**
     * Copy text for [sel]. Soft-wrapped physical rows (a [TerminalLine.wrapped]
     * row continues on the next row of the same logical line) are joined with
     * NO separator; hard line ends keep `\n`. Trailing spaces are trimmed per
     * hard line only — a wrapped row's spaces are real content in the middle of
     * a line. Wide-glyph continuation columns are skipped and combining
     * clusters are expanded (see [TerminalLine.columnIsContinuation]).
     */
    fun extract(sel: TermSelection, snap: TerminalSnapshot): String = buildString {
        for (row in sel.start.row..sel.end.row) {
            val line = snap.absoluteLine(row)
            val span = spanOnRow(sel, row, snap.cols)
            if (line != null && span != null) {
                val sb = StringBuilder(span.last - span.first + 1)
                val text = line.text
                for (col in span) {
                    if (col >= text.length) break
                    if (line.columnIsContinuation(col)) continue
                    val cluster = line.clusters?.get(col)
                    if (cluster != null) sb.append(cluster) else sb.append(text[col])
                }
                append(if (line.wrapped) sb.toString() else sb.toString().trimEnd())
            }
            if (!(line?.wrapped ?: false) && row != sel.end.row) append('\n')
        }
    }.trimEnd('\n')

    /**
     * Map a touch point to the buffer cell under it. [x]/[y] are view pixels;
     * the grid is translated up by [scrollSubPx] (see the renderer), so content
     * y is `y + scrollSubPx`. Clamped to the visible grid.
     */
    fun cellAt(
        x: Float,
        y: Float,
        cellW: Int,
        cellH: Int,
        scrollSubPx: Float,
        cols: Int,
        rows: Int,
        topRow: Int,
        historyBase: Long,
        scrollbackCount: Int
    ): SelCell {
        val col = floor(x / cellW.coerceAtLeast(1)).toInt().coerceIn(0, (cols - 1).coerceAtLeast(0))
        val screenRow = floor((y + scrollSubPx) / cellH.coerceAtLeast(1)).toInt()
            .coerceIn(0, (rows - 1).coerceAtLeast(0))
        return SelCell(historyBase + scrollbackCount + topRow + screenRow, col)
    }

    /**
     * Screen row (0 = top visible) of buffer [row] for the current scroll
     * position. Negative or ≥ rows means off-screen.
     */
    fun screenRowOf(row: Long, historyBase: Long, scrollbackCount: Int, topRow: Int): Int =
        (row - historyBase - scrollbackCount - topRow).toInt()

    /**
     * Auto-scroll for a dragged handle held at view-y [y] (px) in a view
     * [viewHeightPx] tall. Returns the web-convention scroll step (positive =
     * view newer) for this frame: negative near the top (reveal older rows),
     * positive near the bottom, zero in the middle. Speed rises linearly to
     * [EDGE_MAX_ROWS_PER_FRAME] rows at the edge.
     */
    fun edgeScrollPx(y: Float, viewHeightPx: Float, cellH: Int): Float {
        val band = EDGE_BAND_ROWS * cellH.coerceAtLeast(1)
        val maxStep = EDGE_MAX_ROWS_PER_FRAME * cellH.coerceAtLeast(1)
        if (viewHeightPx <= 2 * band) return 0f
        return when {
            y < band -> -maxStep * ((band - y) / band).coerceIn(0f, 1f)
            y > viewHeightPx - band -> maxStep * ((y - (viewHeightPx - band)) / band).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    /**
     * Where a handle's touch anchor sits, in view px, for buffer cell [cell].
     * START anchors just inside its cell's left edge; END just inside its right
     * edge, so [cellAt] of the anchor returns the cell the handle marks and a
     * grab never jumps the selection by a cell.
     */
    fun handleAnchorX(handle: SelHandle, cell: SelCell, cellW: Int): Float =
        when (handle) {
            SelHandle.START -> cell.col * cellW + 1f
            SelHandle.END -> (cell.col + 1) * cellW - 1f
        }
}

/** The line for buffer-absolute [row], or null when no such row is retained. */
fun TerminalSnapshot.absoluteLine(row: Long): TerminalLine? {
    val listIndex = row - historyBase
    if (listIndex < 0L) return null
    val idx = listIndex
    val history = scrollbackCount.toLong()
    return if (idx < history) {
        scrollbackLines[idx.toInt()]
    } else {
        val live = (idx - history).toInt()
        if (live in lines.indices) lines[live] else null
    }
}
