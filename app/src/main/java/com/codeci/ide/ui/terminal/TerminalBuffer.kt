package com.codeci.ide.ui.terminal

/**
 * A VT-style character cell grid with scrollback and an optional alternate
 * screen. Mutated by [AnsiParser] / [TerminalEmulator]; [snapshot] produces
 * an immutable view for Compose.
 */
class Cell {
    var cp: Int = ' '.code
    var fg: Int = XtermColors.COLOR_DEFAULT_FG
    var bg: Int = XtermColors.COLOR_DEFAULT_BG
    var flags: Int = 0

    /** Zero-width marks combined onto this base cell (Phase 19.4); null = none. */
    var comb: IntArray? = null

    fun reset() {
        cp = ' '.code
        fg = XtermColors.COLOR_DEFAULT_FG
        bg = XtermColors.COLOR_DEFAULT_BG
        flags = 0
        comb = null
    }

    fun copyFrom(other: Cell) {
        cp = other.cp
        fg = other.fg
        bg = other.bg
        flags = other.flags
        comb = other.comb?.copyOf()
    }

    fun set(codePoint: Int, style: CellStyle) {
        cp = codePoint
        fg = style.fg
        bg = style.bg
        flags = style.flags
        comb = null
    }

    /** Attaches a zero-width mark, capped against pathological input. */
    fun appendCombining(codePoint: Int) {
        val current = comb ?: run {
            comb = intArrayOf(codePoint)
            return
        }
        if (current.size < MAX_COMBINING) {
            comb = current + codePoint
        }
    }

    fun isBlank(): Boolean =
        cp == ' '.code &&
            flags == 0 &&
            comb == null &&
            fg == XtermColors.COLOR_DEFAULT_FG &&
            bg == XtermColors.COLOR_DEFAULT_BG

    companion object {
        const val MAX_COMBINING = 8
    }
}

data class CellStyle(
    var fg: Int = XtermColors.COLOR_DEFAULT_FG,
    var bg: Int = XtermColors.COLOR_DEFAULT_BG,
    var flags: Int = 0
) {
    fun reset() {
        fg = XtermColors.COLOR_DEFAULT_FG
        bg = XtermColors.COLOR_DEFAULT_BG
        flags = 0
    }

    fun copy(): CellStyle = CellStyle(fg, bg, flags)

    fun has(flag: Int): Boolean = flags and flag != 0

    fun setFlag(flag: Int, on: Boolean) {
        flags = if (on) flags or flag else flags and flag.inv()
    }
}

data class StyleRun(
    val start: Int,
    val end: Int,
    val fg: Int,
    val bg: Int,
    val flags: Int
)

data class TerminalLine(
    val text: String,
    val runs: List<StyleRun>,
    /**
     * Phase 19.4: column → rendered cluster string (base + combining
     * marks), present only when the line has zero-width combining marks.
     * [text] keeps exactly one character per column so column ↔ index
     * mapping stays 1:1; the renderer draws the cluster instead of the bare
     * base character when present.
     */
    val clusters: Map<Int, String>? = null
) {
    /** True when [col] is the (invisible) right half of a wide glyph. */
    fun columnIsContinuation(col: Int): Boolean {
        if (col !in text.indices) return false
        for (run in runs) {
            if (col >= run.start && col < run.end) {
                return run.flags and CellFlags.WIDE_CONT != 0
            }
        }
        return false
    }

    /**
     * Human text of this line for copy/share: joins wide pairs (skips the
     * placeholder continuation columns) and expands combining clusters.
     */
    fun readableText(): String {
        if (runs.none { it.flags and CellFlags.WIDE_CONT != 0 } && clusters == null) {
            return text.trimEnd()
        }
        val sb = StringBuilder(text.length)
        for (col in text.indices) {
            if (columnIsContinuation(col)) continue
            val cluster = clusters?.get(col)
            if (cluster != null) sb.append(cluster) else sb.append(text[col])
        }
        return sb.toString().trimEnd()
    }
}

data class TerminalSnapshot(
    val cols: Int,
    val rows: Int,
    val lines: List<TerminalLine>,
    val scrollbackLines: List<TerminalLine> = emptyList(),
    val cursorX: Int,
    val cursorY: Int,
    val cursorVisible: Boolean,
    val title: String,
    val generation: Long,
    /** Phase 19.5: active mouse-reporting modes ([MouseModes] bits). */
    val mouseMode: Int = 0
) {
    val scrollbackCount: Int get() = scrollbackLines.size

    /** Scrollback + live screen, readable (wide pairs joined, clusters kept). */
    fun transcriptText(): String = buildString {
        fun appendLine(line: TerminalLine, last: Boolean) {
            append(line.readableText())
            if (!last) append('\n')
        }
        val history = scrollbackLines
        val live = lines
        history.forEachIndexed { i, line ->
            appendLine(line, last = live.isEmpty() && i == history.lastIndex)
        }
        live.forEachIndexed { i, line ->
            appendLine(line, last = i == live.lastIndex)
        }
    }.trimEnd()
}

/**
 * One stored visual row: its cells plus whether it ended by **soft
 * auto-wrap** (Phase 19.1). A row with `wrapped = true` is a fragment of a
 * longer logical line that continues on the next row; `resize()` re-joins
 * and re-splits those logical lines at the new width so zooming reflows
 * history exactly like mature terminals do.
 */
class Row(val cells: Array<Cell>, var wrapped: Boolean = false) {
    constructor(cols: Int) : this(Array(cols) { Cell() })
}

class TerminalBuffer(
    cols: Int = 80,
    rows: Int = 24,
    val scrollbackLimit: Int = 2000
) {
    var cols: Int = cols.coerceAtLeast(1)
        private set
    var rows: Int = rows.coerceAtLeast(1)
        private set

    var cursorX: Int = 0
    var cursorY: Int = 0
    var savedX: Int = 0
    var savedY: Int = 0
    var savedStyle: CellStyle = CellStyle()

    var scrollTop: Int = 0
    var scrollBottom: Int = this.rows - 1

    var wrapPending: Boolean = false
    var autoWrap: Boolean = true
    var insertMode: Boolean = false
    var originMode: Boolean = false
    var cursorVisible: Boolean = true
    var reverseVideo: Boolean = false
    var applicationCursorKeys: Boolean = false
    var bracketedPaste: Boolean = false
    var title: String = "Terminal"

    /** Phase 19.5: active xterm mouse-reporting modes ([MouseModes] bits). */
    var mouseMode: Int = 0

    val style: CellStyle = CellStyle()

    private var usingAlt: Boolean = false
    private var screen: Array<Row> = freshScreen(this.rows, this.cols)
    private var altScreen: Array<Row>? = null
    private val scrollback: ArrayDeque<Row> = ArrayDeque()
    private var generation: Long = 0L

    val isAltScreen: Boolean get() = usingAlt
    val scrollbackSize: Int get() = scrollback.size

    fun cell(x: Int, y: Int): Cell = screen[y].cells[x]

    private fun freshScreen(r: Int, c: Int): Array<Row> =
        Array(r) { Row(c) }

    fun reset() {
        cursorX = 0
        cursorY = 0
        savedX = 0
        savedY = 0
        savedStyle.reset()
        scrollTop = 0
        scrollBottom = rows - 1
        wrapPending = false
        autoWrap = true
        insertMode = false
        originMode = false
        cursorVisible = true
        reverseVideo = false
        applicationCursorKeys = false
        bracketedPaste = false
        mouseMode = 0
        title = "Terminal"
        style.reset()
        usingAlt = false
        altScreen = null
        screen = freshScreen(rows, cols)
        scrollback.clear()
        generation++
    }

    /**
     * Phase 19.1 — resize with **reflow**.
     *
     * * Alt screen: rectangular copy only. Full-screen apps repaint on
     *   SIGWINCH; reflowing their screen fights them.
     * * Rows-only change (same width): cheap path — grow by pulling rows
     *   back out of scrollback, shrink by pushing top screen rows into
     *   scrollback. No re-wrapping needed.
     * * Width change (primary screen): the scrollback + screen rows are
     *   re-joined into logical lines (following [Row.wrapped]) and re-split
     *   at the new width by [Reflow], then repartitioned back into
     *   scrollback + screen. The cursor is mapped through the reflow so it
     *   lands on the same character.
     */
    fun resize(newCols: Int, newRows: Int) {
        val c = newCols.coerceAtLeast(1)
        val r = newRows.coerceAtLeast(1)
        if (c == cols && r == rows) return
        if (usingAlt) {
            screen = resizeGrid(screen, c, r)
            altScreen = altScreen?.let { resizeGrid(it, c, r) }
            cols = c
            rows = r
            scrollTop = 0
            scrollBottom = rows - 1
            cursorX = cursorX.coerceIn(0, cols - 1)
            cursorY = cursorY.coerceIn(0, rows - 1)
            generation++
            return
        }
        if (c == cols) {
            resizeRowsOnly(r)
            return
        }
        // Full reflow of scrollback + primary screen at the new width.
        val all = ArrayList<Row>(scrollback.size + rows)
        scrollback.forEach { all.add(it) }
        for (y in 0 until rows) all.add(screen[y])
        val cursorRowInAll = scrollback.size + cursorY
        val result = Reflow.reflow(all, c, cursorRowInAll, cursorX)
        val emitted = result.rows
        // Repartition: the bottom r rows become the screen, everything above
        // goes to scrollback (oldest rows beyond the budget are dropped).
        val firstScreen = (emitted.size - r).coerceAtLeast(0)
        scrollback.clear()
        val scrollEnd = firstScreen.coerceAtMost(scrollbackLimit)
        for (i in (firstScreen - scrollEnd) until scrollEnd) {
            scrollback.addLast(emitted[i])
        }
        screen = Array(r) { y ->
            val idx = firstScreen + y
            if (idx < emitted.size) emitted[idx] else Row(c)
        }
        cols = c
        rows = r
        scrollTop = 0
        scrollBottom = rows - 1
        cursorY = (result.cursorRow - scrollback.size).coerceIn(0, rows - 1)
        cursorX = result.cursorCol.coerceIn(0, cols - 1)
        wrapPending = false
        generation++
    }

    private fun resizeRowsOnly(r: Int) {
        val current = ArrayList<Row>(rows)
        for (y in 0 until rows) current.add(screen[y])
        if (r > rows) {
            // Grow: restore rows from scrollback (most recent first).
            val take = minOf(r - rows, scrollback.size)
            repeat(take) { current.add(0, scrollback.removeLast()) }
        } else if (r < rows) {
            // Shrink: overflow top screen rows into scrollback.
            val push = rows - r
            for (i in 0 until push) {
                scrollback.addLast(current[i])
                while (scrollback.size > scrollbackLimit) scrollback.removeFirst()
            }
            repeat(push) { current.removeAt(0) }
        }
        screen = Array(r) { y ->
            if (y < current.size) ensureWidth(current[y], cols) else Row(cols)
        }
        rows = r
        scrollTop = 0
        scrollBottom = rows - 1
        cursorY = cursorY.coerceIn(0, rows - 1)
        cursorX = cursorX.coerceIn(0, cols - 1)
        generation++
    }

    private fun ensureWidth(row: Row, c: Int): Row {
        if (row.cells.size == c) return row
        val grown = Row(c)
        val n = minOf(row.cells.size, c)
        for (x in 0 until n) grown.cells[x].copyFrom(row.cells[x])
        grown.wrapped = row.wrapped
        return grown
    }

    private fun resizeGrid(src: Array<Row>, c: Int, r: Int): Array<Row> {
        val dst = freshScreen(r, c)
        val copyRows = minOf(src.size, r)
        val copyCols = minOf(src[0].cells.size, c)
        for (y in 0 until copyRows) {
            dst[y].wrapped = src[y].wrapped
            for (x in 0 until copyCols) {
                dst[y].cells[x].copyFrom(src[y].cells[x])
            }
        }
        return dst
    }

    fun saveCursor() {
        savedX = cursorX
        savedY = cursorY
        savedStyle = style.copy()
    }

    fun restoreCursor() {
        cursorX = savedX.coerceIn(0, cols - 1)
        cursorY = savedY.coerceIn(0, rows - 1)
        style.fg = savedStyle.fg
        style.bg = savedStyle.bg
        style.flags = savedStyle.flags
        wrapPending = false
    }

    fun enterAltScreen() {
        if (usingAlt) return
        altScreen = freshScreen(rows, cols)
        val tmp = screen
        screen = altScreen!!
        altScreen = tmp
        usingAlt = true
        cursorX = 0
        cursorY = 0
        wrapPending = false
    }

    fun leaveAltScreen() {
        if (!usingAlt) return
        val tmp = screen
        screen = altScreen ?: freshScreen(rows, cols)
        altScreen = tmp
        usingAlt = false
        wrapPending = false
    }

    fun originTop(): Int = if (originMode) scrollTop else 0
    fun originBottom(): Int = if (originMode) scrollBottom else rows - 1

    fun clampCursor() {
        cursorX = cursorX.coerceIn(0, cols - 1)
        cursorY = cursorY.coerceIn(originTop(), originBottom())
    }

    fun carriageReturn() {
        cursorX = 0
        wrapPending = false
    }

    fun lineFeed() {
        wrapPending = false
        if (cursorY == scrollBottom) {
            scrollUp(1)
        } else if (cursorY < rows - 1) {
            cursorY++
        }
    }

    fun reverseIndex() {
        wrapPending = false
        if (cursorY == scrollTop) {
            scrollDown(1)
        } else if (cursorY > 0) {
            cursorY--
        }
    }

    fun tab() {
        wrapPending = false
        val next = ((cursorX / 8) + 1) * 8
        cursorX = next.coerceAtMost(cols - 1)
    }

    fun backspace() {
        wrapPending = false
        if (cursorX > 0) cursorX--
    }

    fun print(codePoint: Int) {
        // Phase 19.4: classify by terminal column width.
        val w = CharWidth.width(codePoint)
        if (w == 0) {
            // Combining mark (Mn/Me/Mc/Cf): joins the previous base cell —
            // one visual cluster per cell instead of smeared separate cells.
            appendCombining(codePoint)
            return
        }
        if (wrapPending && autoWrap) {
            // Mark BEFORE lineFeed(): if it scrolls, the row's clone must
            // carry the soft-wrap flag into history (Phase 19.1).
            screen[cursorY].wrapped = true
            carriageReturn()
            lineFeed()
        }
        if (w == 2 && cursorX >= cols - 1) {
            // A wide glyph cannot straddle the right margin.
            if (autoWrap) {
                screen[cursorY].wrapped = true
                carriageReturn()
                lineFeed()
            } else {
                placeGlyph(codePoint, wide = false)
                return
            }
        }
        placeGlyph(codePoint, wide = w == 2)
    }

    private fun placeGlyph(codePoint: Int, wide: Boolean) {
        if (insertMode) {
            insertCells(if (wide) 2 else 1)
        }
        val cell = screen[cursorY].cells[cursorX]
        cell.set(codePoint, style)
        if (wide) {
            cell.flags = cell.flags or CellFlags.WIDE_LEAD
            if (cursorX + 1 < cols) {
                val cont = screen[cursorY].cells[cursorX + 1]
                cont.reset()
                cont.flags = CellFlags.WIDE_CONT
            }
            if (cursorX >= cols - 2) {
                cursorX = cols - 1
                wrapPending = autoWrap
            } else {
                cursorX += 2
            }
        } else {
            if (cursorX >= cols - 1) {
                wrapPending = autoWrap
            } else {
                cursorX++
            }
        }
    }

    /**
     * Attaches a zero-width mark to the cell the cursor logically follows.
     * While [wrapPending] is set the cursor rests ON the last printed cell;
     * otherwise the printed cell is one to the left. A continuation cell
     * steps back onto its lead.
     */
    private fun appendCombining(codePoint: Int) {
        var x = if (wrapPending) cursorX else cursorX - 1
        val cells = screen[cursorY].cells
        if (x < 0) return
        if (cells[x].flags and CellFlags.WIDE_CONT != 0) x--
        if (x < 0) return
        cells[x].appendCombining(codePoint)
    }

    fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseInLine(0)
                for (y in (cursorY + 1) until rows) clearRow(y)
            }
            1 -> {
                eraseInLine(1)
                for (y in 0 until cursorY) clearRow(y)
            }
            2, 3 -> {
                for (y in 0 until rows) clearRow(y)
                if (mode == 3) scrollback.clear()
            }
        }
        wrapPending = false
    }

    fun eraseInLine(mode: Int) {
        val row = screen[cursorY].cells
        when (mode) {
            0 -> for (x in cursorX until cols) row[x].reset()
            1 -> for (x in 0..cursorX) row[x].reset()
            2 -> {
                for (x in 0 until cols) row[x].reset()
                screen[cursorY].wrapped = false
            }
        }
    }

    fun eraseChars(count: Int) {
        val n = count.coerceAtLeast(1)
        val row = screen[cursorY].cells
        for (i in 0 until n) {
            val x = cursorX + i
            if (x >= cols) break
            row[x].reset()
        }
    }

    fun insertLines(count: Int) {
        val n = count.coerceAtLeast(1)
        if (cursorY < scrollTop || cursorY > scrollBottom) return
        shiftRowsDown(cursorY, scrollBottom, n)
        wrapPending = false
    }

    fun deleteLines(count: Int) {
        val n = count.coerceAtLeast(1)
        if (cursorY < scrollTop || cursorY > scrollBottom) return
        shiftRowsUp(cursorY, scrollBottom, n, toScrollback = false)
        wrapPending = false
    }

    fun insertCells(count: Int) {
        val n = count.coerceAtLeast(1)
        val row = screen[cursorY].cells
        for (x in (cols - 1) downTo cursorX + n) {
            row[x].copyFrom(row[x - n])
        }
        for (x in cursorX until (cursorX + n).coerceAtMost(cols)) {
            row[x].reset()
        }
    }

    fun deleteCells(count: Int) {
        val n = count.coerceAtLeast(1)
        val row = screen[cursorY].cells
        var dst = cursorX
        var src = cursorX + n
        while (src < cols) {
            row[dst].copyFrom(row[src])
            dst++
            src++
        }
        while (dst < cols) {
            row[dst].reset()
            dst++
        }
    }

    fun scrollUp(count: Int) {
        shiftRowsUp(scrollTop, scrollBottom, count.coerceAtLeast(1), toScrollback = !usingAlt)
    }

    fun scrollDown(count: Int) {
        shiftRowsDown(scrollTop, scrollBottom, count.coerceAtLeast(1))
    }

    private fun shiftRowsUp(top: Int, bottom: Int, count: Int, toScrollback: Boolean) {
        val n = count.coerceAtMost(bottom - top + 1)
        if (n <= 0) return
        if (toScrollback) {
            repeat(n) {
                // Clone: the shift loop below overwrites the live rows, and
                // history must keep the content (and wrapped flag) as pushed.
                pushScrollback(cloneRow(screen[top + it]))
            }
        }
        var dst = top
        var src = top + n
        while (src <= bottom) {
            copyRow(screen[src], screen[dst])
            dst++
            src++
        }
        while (dst <= bottom) {
            clearRow(dst)
            dst++
        }
    }

    private fun shiftRowsDown(top: Int, bottom: Int, count: Int) {
        val n = count.coerceAtMost(bottom - top + 1)
        if (n <= 0) return
        var dst = bottom
        var src = bottom - n
        while (src >= top) {
            copyRow(screen[src], screen[dst])
            dst--
            src--
        }
        for (y in top until top + n) clearRow(y)
    }

    private fun copyRow(from: Row, to: Row) {
        val n = minOf(from.cells.size, to.cells.size)
        for (i in 0 until n) to.cells[i].copyFrom(from.cells[i])
        to.wrapped = from.wrapped
    }

    private fun cloneRow(row: Row): Row =
        Row(
            Array(row.cells.size) { i -> Cell().also { it.copyFrom(row.cells[i]) } },
            row.wrapped
        )

    private fun clearRow(y: Int) {
        for (cell in screen[y].cells) cell.reset()
        screen[y].wrapped = false
    }

    private fun pushScrollback(row: Row) {
        scrollback.addLast(row)
        while (scrollback.size > scrollbackLimit) scrollback.removeFirst()
    }

    fun setScrollRegion(top: Int, bottom: Int) {
        val t = (top - 1).coerceIn(0, rows - 1)
        val b = (bottom - 1).coerceIn(0, rows - 1)
        if (t >= b) return
        scrollTop = t
        scrollBottom = b
        cursorX = 0
        cursorY = originTop()
        wrapPending = false
    }

    fun snapshot(): TerminalSnapshot {
        generation++
        val history = ArrayList<TerminalLine>(scrollback.size)
        for (row in scrollback) history.add(rowToLine(row.cells))
        val lines = ArrayList<TerminalLine>(rows)
        for (y in 0 until rows) {
            lines.add(rowToLine(screen[y].cells))
        }
        return TerminalSnapshot(
            cols = cols,
            rows = rows,
            lines = lines,
            scrollbackLines = history,
            cursorX = cursorX.coerceIn(0, cols - 1),
            cursorY = cursorY.coerceIn(0, rows - 1),
            cursorVisible = cursorVisible,
            title = title,
            generation = generation,
            mouseMode = mouseMode
        )
    }

    fun visibleText(): String = buildString {
        for (y in 0 until rows) {
            if (y > 0) append('\n')
            val row = screen[y].cells
            val sb = StringBuilder(cols)
            for (x in 0 until cols) {
                val cp = row[x].cp
                if (cp <= 0) {
                    sb.append(' ')
                } else {
                    sb.appendCodePoint(cp)
                    row[x].comb?.forEach { m -> sb.appendCodePoint(m) }
                }
            }
            append(sb.toString().trimEnd())
        }
    }.trimEnd()

    fun scrollbackText(): String = buildString {
        for (row in scrollback) {
            val cells = row.cells
            val sb = StringBuilder(cells.size)
            for (cell in cells) {
                val cp = cell.cp
                if (cp <= 0) {
                    sb.append(' ')
                } else {
                    sb.appendCodePoint(cp)
                    cell.comb?.forEach { m -> sb.appendCodePoint(m) }
                }
            }
            append(sb.toString().trimEnd())
            append('\n')
        }
    }

    companion object {
        fun rowToLine(row: Array<Cell>): TerminalLine {
            val text = StringBuilder(row.size)
            val runs = ArrayList<StyleRun>()
            var clusters: HashMap<Int, String>? = null
            var runStart = 0
            var runFg = row[0].fg
            var runBg = row[0].bg
            var runFlags = row[0].flags
            for (x in row.indices) {
                val cell = row[x]
                val cp = if (cell.cp <= 0) ' '.code else cell.cp
                val marks = cell.comb
                if (cp > 0xFFFF) {
                    // Astral code point (emoji): a surrogate pair would break
                    // the one-character-per-column invariant of [text], so
                    // park the real glyph in the cluster map and keep a blank
                    // placeholder in the grid text.
                    val map = clusters ?: HashMap<Int, String>().also { clusters = it }
                    val cluster = StringBuilder(2)
                    cluster.appendCodePoint(cp)
                    marks?.forEach { m -> cluster.appendCodePoint(m) }
                    map[x] = cluster.toString()
                    text.append(' ')
                } else {
                    text.appendCodePoint(cp)
                    if (marks != null) {
                        val cluster = StringBuilder(1 + marks.size)
                        cluster.appendCodePoint(cp)
                        for (m in marks) cluster.appendCodePoint(m)
                        val map = clusters ?: HashMap<Int, String>().also { clusters = it }
                        map[x] = cluster.toString()
                    }
                }
                if (cell.fg != runFg || cell.bg != runBg || cell.flags != runFlags) {
                    runs.add(StyleRun(runStart, x, runFg, runBg, runFlags))
                    runStart = x
                    runFg = cell.fg
                    runBg = cell.bg
                    runFlags = cell.flags
                }
            }
            runs.add(StyleRun(runStart, row.size, runFg, runBg, runFlags))
            return TerminalLine(text.toString(), runs, clusters)
        }
    }
}
