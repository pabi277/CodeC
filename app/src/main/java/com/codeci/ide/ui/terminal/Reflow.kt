package com.codeci.ide.ui.terminal

/**
 * Phase 19.1 — the resize **reflow** engine.
 *
 * Re-implementation of the mechanism every mature terminal uses (designed
 * from the xterm/VT100 auto-wrap model and ECMA-48; no third-party code):
 *
 *  1. The caller feeds the stored rows (scrollback first, then screen) in
 *     order. Rows flagged [Row.wrapped] ended by *soft auto-wrap*, so each
 *     run of `wrapped` rows plus the row after it is one **logical line**.
 *     Trailing blank cells are trimmed from each logical line so a
 *     full-width fragment does not materialise a phantom empty row.
 *  2. Each logical line is re-split at [newCols]; every fragment except the
 *     last is flagged `wrapped` (it continues on the next visual row).
 *     A double-width glyph (Phase 19.4) is never split across fragments —
 *     if its continuation would cross the boundary the lead moves down to
 *     the next fragment and a blank pads the end of this one.
 *  3. The cursor is mapped along: its row is located inside a logical line,
 *     its column becomes an offset within that logical line, and after the
 *     re-split the offset resolves back to (fragment row, column).
 *
 * Pure Kotlin over immutable snapshots of rows — directly unit-testable.
 */
object Reflow {

    data class Result(
        /** Reflowed visual rows, oldest first. Always non-empty. */
        val rows: List<Row>,
        /** Row index within [rows] the cursor restored to. */
        val cursorRow: Int,
        /** Column within that row the cursor restored to (clamped). */
        val cursorCol: Int
    )

    fun reflow(rows: List<Row>, newCols: Int, cursorRow: Int = -1, cursorCol: Int = 0): Result {
        val c = newCols.coerceAtLeast(1)
        val emitted = ArrayList<Row>(rows.size + 16)
        var newCursorRow = -1
        var newCursorCol = 0

        var i = 0
        while (i < rows.size) {
            // A logical line spans rows i..lineEnd: every row except the last
            // has wrapped=true (it continued by soft auto-wrap).
            var lineEnd = i
            while (lineEnd < rows.size - 1 && rows[lineEnd].wrapped) lineEnd++
            val line = ArrayList<Cell>(c * (lineEnd - i + 1))
            for (r in i..lineEnd) rows[r].cells.forEach { cell -> line.add(cell) }

            // Cursor: is it on this logical line (screen cursor row)?
            val cursorIsHere = cursorRow in i..lineEnd
            var logicalCursor = -1
            if (cursorIsHere) {
                logicalCursor = cursorCol
                for (r in i until cursorRow) logicalCursor += rows[r].cells.size
            }

            trimTrailingBlanks(line)

            if (line.isEmpty()) {
                emitted.add(Row(c))
                if (cursorIsHere) {
                    newCursorRow = emitted.size - 1
                    newCursorCol = 0
                }
                i = lineEnd + 1
                continue
            }

            // Re-split into fragments of width c.
            var offset = 0
            while (offset < line.size) {
                var take = minOf(c, line.size - offset)
                val wrappedFragment = offset + take < line.size
                if (wrappedFragment && take > 1 &&
                    line[offset + take - 1].flags and CellFlags.WIDE_LEAD != 0
                ) {
                    // Don't split a wide glyph: leave a blank at the row end
                    // and start the next row with the glyph.
                    take--
                }
                val row = Row(c)
                for (x in 0 until take) row.cells[x].copyFrom(line[offset + x])
                row.wrapped = wrappedFragment
                emitted.add(row)
                if (cursorIsHere && logicalCursor in offset..(offset + take) && newCursorRow == -1) {
                    newCursorRow = emitted.size - 1
                    newCursorCol = logicalCursor - offset
                }
                offset += take
            }
            i = lineEnd + 1
        }

        if (emitted.isEmpty()) {
            emitted.add(Row(c))
        }
        if (newCursorRow == -1) {
            newCursorRow = emitted.size - 1
            newCursorCol = 0
        }
        return Result(emitted, newCursorRow, newCursorCol.coerceIn(0, c - 1))
    }

    /** Drops trailing *default* blank cells (keeps styled blanks intact). */
    private fun trimTrailingBlanks(line: MutableList<Cell>) {
        var end = line.size
        while (end > 0 && line[end - 1].isBlank()) end--
        if (end < line.size) {
            line.subList(end, line.size).clear()
        }
    }
}
