package com.codeci.ide.ui.terminal

/**
 * Phase 103.1 — the terminal's selection MODEL.
 *
 * Pure Kotlin: no Android, no Compose, no canvas. That is the point. The
 * owner's report (*"partial text copy not working"*, 2026-10-14) was not a
 * drawing bug — the extractor was reachable and correct — but a GESTURE bug:
 * the long-press handler picked one word and opened a menu in the same
 * gesture, so there was never a state to extend. The state machine that was
 * missing lives here, where a host test can drive it instead of a thumb.
 *
 * Coordinate system (identical to [TerminalSnapshot.lineAt], and to the
 * view's `topRow`): **`y == 0` is the top row of the LIVE screen, negative
 * rows count back into scrollback.** It is deliberately NOT an absolute
 * buffer index: content slides one step deeper into history for every row of
 * new output, so a selection that must stay on its own text has to be
 * shifted — that is [SelectionModel.shift] and the reason
 * [TerminalBuffer.scrolledRows] exists.
 *
 * `x2` is **inclusive** (the last selected column), matching the grid's
 * half-open drawing code with the smallest possible surprise: `x2 + 1` is
 * the exclusive end, exactly as the renderer computes it.
 */
enum class SelectionHandle {
    /** The earlier edge of the selection (top-left). */
    START,

    /** The later edge of the selection (bottom-right). */
    END
}

/**
 * @param x1 first selected column of row [y1] (inclusive)
 * @param x2 last selected column of row [y2] (**inclusive**)
 */
data class TextSelection(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int
) {
    /** A one-cell range: a long-press on a blank cell, before any drag. */
    val isEmpty: Boolean get() = x1 == x2 && y1 == y2

    /** The same range with the earlier edge first. */
    fun normalized(): TextSelection =
        if (y1 < y2 || (y1 == y2 && x1 <= x2)) this else TextSelection(x2, y2, x1, y1)
}

/**
 * Result of a handle drag: the new range **and** which handle the finger is
 * now holding. When a drag crosses the other edge the range flips over, so
 * the finger keeps holding the edge it is dragging — without this the
 * handles would swap under the thumb and the drag would fight itself.
 */
data class SelectionDrag(
    val selection: TextSelection,
    val handle: SelectionHandle
)

object SelectionModel {

    /**
     * Word characters for the long-press "grab the word under the finger"
     * step. Paths and flags are one word (`/data/data/...`, `--force`), which
     * is what a terminal user actually long-presses on.
     */
    fun isWordChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '/'

    /**
     * Inclusive column bounds of the word containing [col].
     *
     * A non-word column (a space, or a blank cell past the text) selects just
     * that one column: an anchor the drag can grow from, never a crash and
     * never a whole-line surprise.
     */
    fun wordBounds(text: String, col: Int): Pair<Int, Int> {
        if (text.isEmpty()) return 0 to 0
        val c = col.coerceIn(0, text.lastIndex)
        if (!isWordChar(text[c])) return c to c
        var start = c
        var end = c
        while (start > 0 && isWordChar(text[start - 1])) start--
        while (end < text.lastIndex && isWordChar(text[end + 1])) end++
        return start to end
    }

    /**
     * The long-press state: the word under [col] on row [extY].
     *
     * Returns `null` when that row does not exist (a tap in the dead area
     * beyond the buffer), so the caller can leave the previous state alone
     * rather than install an empty selection.
     */
    fun beginWord(snapshot: TerminalSnapshot, col: Int, extY: Int): TextSelection? {
        val line = snapshot.lineAt(extY) ?: return null
        val (start, end) = wordBounds(line.text, col)
        val clampedEnd = end.coerceAtMost(snapshot.cols - 1)
        return TextSelection(start.coerceAtLeast(0), extY, clampedEnd, extY)
    }

    /** True when (y1,x1) is strictly before (y2,x2) in reading order. */
    private fun before(y1: Int, x1: Int, y2: Int, x2: Int): Boolean =
        y1 < y2 || (y1 == y2 && x1 < x2)

    /**
     * Move one [handle] of [selection] to cell ([col], [extY]).
     *
     * The opposite edge stays where it is; when the dragged edge passes it
     * the range flips and the returned handle flips with it, so a drag into
     * "negative" territory keeps working (the reverse-drag case).
     */
    fun drag(
        selection: TextSelection,
        handle: SelectionHandle,
        col: Int,
        extY: Int,
        cols: Int,
        minRow: Int,
        maxRow: Int
    ): SelectionDrag {
        val c = col.coerceIn(0, (cols - 1).coerceAtLeast(0))
        val y = extY.coerceIn(minRow, maxRow)
        return when (handle) {
            SelectionHandle.END -> {
                if (before(selection.y1, selection.x1, y, c) || (y == selection.y1 && c >= selection.x1)) {
                    SelectionDrag(TextSelection(selection.x1, selection.y1, c, y), SelectionHandle.END)
                } else {
                    SelectionDrag(TextSelection(c, y, selection.x1, selection.y1), SelectionHandle.START)
                }
            }
            SelectionHandle.START -> {
                if (before(y, c, selection.y2, selection.x2) || (y == selection.y2 && c <= selection.x2)) {
                    SelectionDrag(TextSelection(c, y, selection.x2, selection.y2), SelectionHandle.START)
                } else {
                    SelectionDrag(TextSelection(selection.x2, selection.y2, c, y), SelectionHandle.END)
                }
            }
        }
    }

    /**
     * Keep a selection on its own text while [rows] new rows of output push
     * the older content deeper into history.
     *
     * Returns `null` when the whole range has scrolled out of the buffer —
     * the honest answer (the text is gone) instead of a highlight parked on
     * whatever now occupies those coordinates.
     */
    fun shift(selection: TextSelection, rows: Int, minRow: Int): TextSelection? {
        if (rows == 0) return selection
        val rawStart = selection.y1 - rows
        val rawEnd = selection.y2 - rows
        if (rawEnd < minRow) return null
        val start = rawStart.coerceAtLeast(minRow)
        // A clipped top edge also loses its column: the row is a fragment.
        val startX = if (start == rawStart) selection.x1 else 0
        return TextSelection(startX, start, selection.x2, rawEnd)
    }

    /** Force a range inside the buffer (used after a resize or a clear). */
    fun clamp(
        selection: TextSelection,
        cols: Int,
        minRow: Int,
        maxRow: Int
    ): TextSelection {
        val lastCol = (cols - 1).coerceAtLeast(0)
        return TextSelection(
            x1 = selection.x1.coerceIn(0, lastCol),
            y1 = selection.y1.coerceIn(minRow, maxRow),
            x2 = selection.x2.coerceIn(0, lastCol),
            y2 = selection.y2.coerceIn(minRow, maxRow)
        ).normalized()
    }

    /**
     * The copied text.
     *
     * Two rules, both about a WRAPPED line — a long command or URL that the
     * terminal broke across rows because it did not fit:
     *
     * 1. A row flagged `wrapped` continues onto the next row, so the
     *    fragments are rejoined with **nothing** between them. This is the
     *    owner's case: a 200-character URL copied as two lines with a `\n`
     *    wedged in the middle is not pasteable and not clickable.
     * 2. Trailing blanks are trimmed at the end of a LOGICAL line, never
     *    inside one — a wrapped row's trailing space may be real text.
     *
     * Hard lines keep their `\n`, so a multi-line selection still copies as
     * multiple lines.
     */
    fun extract(snapshot: TerminalSnapshot, selection: TextSelection): String {
        val n = selection.normalized()
        val out = StringBuilder()
        val logical = StringBuilder()
        for (y in n.y1..n.y2) {
            val line = snapshot.lineAt(y) ?: continue
            val start = if (y == n.y1) n.x1.coerceAtLeast(0) else 0
            val end = if (y == n.y2) (n.x2 + 1).coerceAtMost(line.text.length) else line.text.length
            if (end > start) logical.append(line.sliceText(start, end))
            // A wrapped row continues the logical line; anything else ends it.
            val continues = line.wrapped && y < n.y2
            if (!continues) {
                if (out.isNotEmpty()) out.append('\n')
                out.append(logical.toString().trimEnd())
                logical.setLength(0)
            }
        }
        if (logical.isNotEmpty()) {
            if (out.isNotEmpty()) out.append('\n')
            out.append(logical.toString().trimEnd())
        }
        return out.toString()
    }

    /**
     * Human text of columns [start] (inclusive) .. [end] (exclusive):
     * wide-glyph continuation columns are dropped and combining clusters are
     * expanded, so what lands on the clipboard is what the eye saw.
     */
    private fun TerminalLine.sliceText(start: Int, end: Int): String {
        val last = end.coerceAtMost(text.length)
        val sb = StringBuilder((last - start).coerceAtLeast(0))
        for (col in start until last) {
            if (columnIsContinuation(col)) continue
            val cluster = clusters?.get(col)
            if (cluster != null) sb.append(cluster) else sb.append(text[col])
        }
        return sb.toString()
    }
}
