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

    fun reset() {
        cp = ' '.code
        fg = XtermColors.COLOR_DEFAULT_FG
        bg = XtermColors.COLOR_DEFAULT_BG
        flags = 0
    }

    fun copyFrom(other: Cell) {
        cp = other.cp
        fg = other.fg
        bg = other.bg
        flags = other.flags
    }

    fun set(codePoint: Int, style: CellStyle) {
        cp = codePoint
        fg = style.fg
        bg = style.bg
        flags = style.flags
    }

    fun isBlank(): Boolean =
        cp == ' '.code &&
            flags == 0 &&
            fg == XtermColors.COLOR_DEFAULT_FG &&
            bg == XtermColors.COLOR_DEFAULT_BG
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
    val runs: List<StyleRun>
)

data class TerminalSnapshot(
    val cols: Int,
    val rows: Int,
    val lines: List<TerminalLine>,
    val scrollbackLines: List<TerminalLine> = emptyList(),
    val cursorX: Int,
    val cursorY: Int,
    val cursorVisible: Boolean,
    val title: String,
    val generation: Long
) {
    val scrollbackCount: Int get() = scrollbackLines.size

    /** Scrollback + live screen, trailing spaces stripped per line. */
    fun transcriptText(): String = buildString {
        fun appendLine(line: TerminalLine, last: Boolean) {
            append(line.text.trimEnd())
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

    val style: CellStyle = CellStyle()

    private var usingAlt: Boolean = false
    private var screen: Array<Array<Cell>> = freshScreen(this.rows, this.cols)
    private var altScreen: Array<Array<Cell>>? = null
    private val scrollback: ArrayDeque<Array<Cell>> = ArrayDeque()
    private var generation: Long = 0L

    val isAltScreen: Boolean get() = usingAlt
    val scrollbackSize: Int get() = scrollback.size

    fun cell(x: Int, y: Int): Cell = screen[y][x]

    fun currentRow(): Array<Cell> = screen[cursorY]

    private fun freshScreen(r: Int, c: Int): Array<Array<Cell>> =
        Array(r) { Array(c) { Cell() } }

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
        title = "Terminal"
        style.reset()
        usingAlt = false
        altScreen = null
        screen = freshScreen(rows, cols)
        scrollback.clear()
        generation++
    }

    fun resize(newCols: Int, newRows: Int) {
        val c = newCols.coerceAtLeast(1)
        val r = newRows.coerceAtLeast(1)
        if (c == cols && r == rows) return
        screen = resizeGrid(screen, c, r)
        altScreen = altScreen?.let { resizeGrid(it, c, r) }
        cols = c
        rows = r
        scrollTop = 0
        scrollBottom = rows - 1
        cursorX = cursorX.coerceIn(0, cols - 1)
        cursorY = cursorY.coerceIn(0, rows - 1)
        generation++
    }

    private fun resizeGrid(src: Array<Array<Cell>>, c: Int, r: Int): Array<Array<Cell>> {
        val dst = freshScreen(r, c)
        val copyRows = minOf(src.size, r)
        val copyCols = minOf(src[0].size, c)
        for (y in 0 until copyRows) {
            for (x in 0 until copyCols) {
                dst[y][x].copyFrom(src[y][x])
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
        if (wrapPending && autoWrap) {
            carriageReturn()
            lineFeed()
        }
        if (insertMode) {
            insertCells(1)
        }
        screen[cursorY][cursorX].set(codePoint, style)
        if (cursorX >= cols - 1) {
            wrapPending = autoWrap
        } else {
            cursorX++
        }
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
        val row = screen[cursorY]
        when (mode) {
            0 -> for (x in cursorX until cols) row[x].reset()
            1 -> for (x in 0..cursorX) row[x].reset()
            2 -> for (x in 0 until cols) row[x].reset()
        }
    }

    fun eraseChars(count: Int) {
        val n = count.coerceAtLeast(1)
        val row = screen[cursorY]
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
        val row = screen[cursorY]
        for (x in (cols - 1) downTo cursorX + n) {
            row[x].copyFrom(row[x - n])
        }
        for (x in cursorX until (cursorX + n).coerceAtMost(cols)) {
            row[x].reset()
        }
    }

    fun deleteCells(count: Int) {
        val n = count.coerceAtLeast(1)
        val row = screen[cursorY]
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

    private fun copyRow(from: Array<Cell>, to: Array<Cell>) {
        val n = minOf(from.size, to.size)
        for (i in 0 until n) to[i].copyFrom(from[i])
    }

    private fun cloneRow(row: Array<Cell>): Array<Cell> =
        Array(row.size) { i -> Cell().also { it.copyFrom(row[i]) } }

    private fun clearRow(y: Int) {
        for (cell in screen[y]) cell.reset()
    }

    private fun pushScrollback(row: Array<Cell>) {
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
        for (row in scrollback) history.add(rowToLine(row))
        val lines = ArrayList<TerminalLine>(rows)
        for (y in 0 until rows) {
            lines.add(rowToLine(screen[y]))
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
            generation = generation
        )
    }

    fun visibleText(): String = buildString {
        for (y in 0 until rows) {
            if (y > 0) append('\n')
            val row = screen[y]
            val sb = StringBuilder(cols)
            for (x in 0 until cols) {
                val cp = row[x].cp
                if (cp <= 0) sb.append(' ') else sb.appendCodePoint(cp)
            }
            append(sb.toString().trimEnd())
        }
    }.trimEnd()

    fun scrollbackText(): String = buildString {
        for (row in scrollback) {
            val sb = StringBuilder(row.size)
            for (cell in row) {
                val cp = cell.cp
                if (cp <= 0) sb.append(' ') else sb.appendCodePoint(cp)
            }
            append(sb.toString().trimEnd())
            append('\n')
        }
    }

    companion object {
        fun rowToLine(row: Array<Cell>): TerminalLine {
            val text = StringBuilder(row.size)
            val runs = ArrayList<StyleRun>()
            var runStart = 0
            var runFg = row[0].fg
            var runBg = row[0].bg
            var runFlags = row[0].flags
            for (x in row.indices) {
                val cell = row[x]
                val cp = if (cell.cp <= 0) ' '.code else cell.cp
                text.appendCodePoint(cp)
                if (cell.fg != runFg || cell.bg != runBg || cell.flags != runFlags) {
                    runs.add(StyleRun(runStart, x, runFg, runBg, runFlags))
                    runStart = x
                    runFg = cell.fg
                    runBg = cell.bg
                    runFlags = cell.flags
                }
            }
            runs.add(StyleRun(runStart, row.size, runFg, runBg, runFlags))
            return TerminalLine(text.toString(), runs)
        }
    }
}
