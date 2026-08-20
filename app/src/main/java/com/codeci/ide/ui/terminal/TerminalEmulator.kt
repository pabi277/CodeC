package com.codeci.ide.ui.terminal

/**
 * xterm-256color subset: colors, cursor, scrollback, alt screen, SGR,
 * DEC private modes (incl. bracketed paste) and a handful of edit ops.
 *
 * Pure Kotlin so the whole emulator is unit-testable without a device.
 */
class TerminalEmulator(
    cols: Int = 80,
    rows: Int = 24,
    scrollbackLimit: Int = 2000,
    var responder: ((String) -> Unit)? = null
) : AnsiParser.Host {

    val buffer = TerminalBuffer(cols, rows, scrollbackLimit)
    private val parser = AnsiParser(this)
    private var lastPrinted: Int = ' '.code

    val cols: Int get() = buffer.cols
    val rows: Int get() = buffer.rows
    val bracketedPaste: Boolean get() = buffer.bracketedPaste
    val applicationCursorKeys: Boolean get() = buffer.applicationCursorKeys
    val title: String get() = buffer.title

    fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        parser.feed(bytes, offset, length)
    }

    fun feed(text: String) {
        parser.feed(text)
    }

    fun resize(cols: Int, rows: Int) {
        buffer.resize(cols, rows)
    }

    fun reset() {
        parser.reset()
        buffer.reset()
        lastPrinted = ' '.code
    }

    fun snapshot(): TerminalSnapshot = buffer.snapshot()

    fun visibleText(): String = buffer.visibleText()

    fun transcriptText(): String {
        val history = buffer.scrollbackText().trimEnd()
        val live = buffer.visibleText()
        return when {
            history.isEmpty() -> live
            live.isEmpty() -> history
            else -> "$history\n$live"
        }
    }

    fun wrapPaste(text: String): String =
        if (buffer.bracketedPaste) "\u001b[200~$text\u001b[201~" else text

    fun cursorKey(direction: Char): String =
        if (buffer.applicationCursorKeys) "\u001bO$direction" else "\u001b[$direction"

    override fun print(codePoint: Int) {
        lastPrinted = codePoint
        buffer.print(codePoint)
    }

    override fun executeC0(byte: Int) {
        when (byte) {
            0x07 -> { /* BEL — ignored at Phase 1 */ }
            0x08 -> buffer.backspace()
            0x09 -> buffer.tab()
            0x0A, 0x0B, 0x0C -> buffer.lineFeed()
            0x0D -> buffer.carriageReturn()
            0x0E, 0x0F -> { /* SO / SI charset — ignore */ }
        }
    }

    override fun esc(intermediates: String, finalByte: Char) {
        if (intermediates.isNotEmpty()) return
        when (finalByte) {
            '7' -> buffer.saveCursor()
            '8' -> buffer.restoreCursor()
            'c' -> reset()
            'D' -> buffer.lineFeed()
            'E' -> {
                buffer.carriageReturn()
                buffer.lineFeed()
            }
            'M' -> buffer.reverseIndex()
        }
    }

    override fun osc(payload: String) {
        val semi = payload.indexOf(';')
        val code = if (semi < 0) payload else payload.substring(0, semi)
        val value = if (semi < 0) "" else payload.substring(semi + 1)
        when (code) {
            "0", "2" -> buffer.title = value
        }
    }

    override fun csi(prefix: Char, params: IntArray, count: Int, intermediates: String, finalByte: Char) {
        if (intermediates.isNotEmpty()) return
        if (prefix == '?' || prefix == '>' || prefix == '=') {
            handlePrivate(prefix, params, count, finalByte)
            return
        }
        when (finalByte) {
            'A' -> moveCursor(0, -AnsiParser.param(params, count, 0, 1))
            'B' -> moveCursor(0, AnsiParser.param(params, count, 0, 1))
            'C' -> moveCursor(AnsiParser.param(params, count, 0, 1), 0)
            'D' -> moveCursor(-AnsiParser.param(params, count, 0, 1), 0)
            'E' -> {
                buffer.carriageReturn()
                moveCursor(0, AnsiParser.param(params, count, 0, 1))
            }
            'F' -> {
                buffer.carriageReturn()
                moveCursor(0, -AnsiParser.param(params, count, 0, 1))
            }
            'G', '`' -> {
                val col = AnsiParser.param(params, count, 0, 1)
                buffer.cursorX = (col - 1).coerceIn(0, buffer.cols - 1)
                buffer.wrapPending = false
            }
            'H', 'f' -> {
                val row = AnsiParser.param(params, count, 0, 1)
                val col = AnsiParser.param(params, count, 1, 1)
                buffer.cursorY = (buffer.originTop() + row - 1)
                    .coerceIn(buffer.originTop(), buffer.originBottom())
                buffer.cursorX = (col - 1).coerceIn(0, buffer.cols - 1)
                buffer.wrapPending = false
            }
            'J' -> buffer.eraseInDisplay(AnsiParser.param(params, count, 0, 0))
            'K' -> buffer.eraseInLine(AnsiParser.param(params, count, 0, 0))
            'L' -> buffer.insertLines(AnsiParser.param(params, count, 0, 1))
            'M' -> buffer.deleteLines(AnsiParser.param(params, count, 0, 1))
            'P' -> buffer.deleteCells(AnsiParser.param(params, count, 0, 1))
            '@' -> buffer.insertCells(AnsiParser.param(params, count, 0, 1))
            'S' -> buffer.scrollUp(AnsiParser.param(params, count, 0, 1))
            'T' -> buffer.scrollDown(AnsiParser.param(params, count, 0, 1))
            'X' -> buffer.eraseChars(AnsiParser.param(params, count, 0, 1))
            'd' -> {
                val row = AnsiParser.param(params, count, 0, 1)
                buffer.cursorY = (buffer.originTop() + row - 1)
                    .coerceIn(buffer.originTop(), buffer.originBottom())
                buffer.wrapPending = false
            }
            'e' -> moveCursor(0, AnsiParser.param(params, count, 0, 1))
            'a' -> moveCursor(AnsiParser.param(params, count, 0, 1), 0)
            'm' -> applySgr(params, count)
            'n' -> handleDsr(AnsiParser.param(params, count, 0, 0))
            'r' -> {
                val top = AnsiParser.param(params, count, 0, 1)
                val bottom = AnsiParser.param(params, count, 1, buffer.rows)
                buffer.setScrollRegion(top, bottom)
            }
            's' -> buffer.saveCursor()
            'u' -> buffer.restoreCursor()
            'b' -> {
                val n = AnsiParser.param(params, count, 0, 1).coerceAtLeast(1)
                repeat(n) { buffer.print(lastPrinted) }
            }
            'h' -> applyMode(params, count, enable = true)
            'l' -> applyMode(params, count, enable = false)
        }
    }

    private fun moveCursor(dx: Int, dy: Int) {
        buffer.cursorX = (buffer.cursorX + dx).coerceIn(0, buffer.cols - 1)
        buffer.cursorY = (buffer.cursorY + dy).coerceIn(buffer.originTop(), buffer.originBottom())
        buffer.wrapPending = false
    }

    private fun handleDsr(mode: Int) {
        when (mode) {
            5 -> responder?.invoke("\u001b[0n")
            6 -> {
                val row = buffer.cursorY - buffer.originTop() + 1
                val col = buffer.cursorX + 1
                responder?.invoke("\u001b[$row;${col}R")
            }
        }
    }

    private fun applyMode(params: IntArray, count: Int, enable: Boolean) {
        val n = if (count == 0) 1 else count
        for (i in 0 until n) {
            when (AnsiParser.param(params, count, i, 0)) {
                4 -> buffer.insertMode = enable
                20 -> { /* LNM — ignore */ }
            }
        }
    }

    private fun handlePrivate(prefix: Char, params: IntArray, count: Int, finalByte: Char) {
        if (prefix != '?') return
        if (finalByte != 'h' && finalByte != 'l') return
        val enable = finalByte == 'h'
        val n = if (count == 0) 1 else count
        for (i in 0 until n) {
            when (AnsiParser.param(params, count, i, 0)) {
                1 -> buffer.applicationCursorKeys = enable
                7 -> buffer.autoWrap = enable
                12 -> { /* cursor blink — cosmetic */ }
                25 -> buffer.cursorVisible = enable
                47, 1047 -> if (enable) buffer.enterAltScreen() else buffer.leaveAltScreen()
                1048 -> if (enable) buffer.saveCursor() else buffer.restoreCursor()
                1049 -> {
                    if (enable) {
                        buffer.saveCursor()
                        buffer.enterAltScreen()
                        buffer.eraseInDisplay(2)
                    } else {
                        buffer.leaveAltScreen()
                        buffer.restoreCursor()
                    }
                }
                2004 -> buffer.bracketedPaste = enable
            }
        }
    }

    private fun applySgr(params: IntArray, count: Int) {
        if (count == 0) {
            buffer.style.reset()
            return
        }
        var i = 0
        while (i < count) {
            when (val p = params[i]) {
                0 -> buffer.style.reset()
                1 -> buffer.style.setFlag(CellFlags.BOLD, true)
                2 -> buffer.style.setFlag(CellFlags.FAINT, true)
                3 -> buffer.style.setFlag(CellFlags.ITALIC, true)
                4 -> buffer.style.setFlag(CellFlags.UNDERLINE, true)
                5, 6 -> buffer.style.setFlag(CellFlags.BLINK, true)
                7 -> buffer.style.setFlag(CellFlags.INVERSE, true)
                8 -> buffer.style.setFlag(CellFlags.INVISIBLE, true)
                9 -> buffer.style.setFlag(CellFlags.STRIKE, true)
                21, 22 -> {
                    buffer.style.setFlag(CellFlags.BOLD, false)
                    buffer.style.setFlag(CellFlags.FAINT, false)
                }
                23 -> buffer.style.setFlag(CellFlags.ITALIC, false)
                24 -> buffer.style.setFlag(CellFlags.UNDERLINE, false)
                25 -> buffer.style.setFlag(CellFlags.BLINK, false)
                27 -> buffer.style.setFlag(CellFlags.INVERSE, false)
                28 -> buffer.style.setFlag(CellFlags.INVISIBLE, false)
                29 -> buffer.style.setFlag(CellFlags.STRIKE, false)
                in 30..37 -> buffer.style.fg = p - 30
                39 -> buffer.style.fg = XtermColors.COLOR_DEFAULT_FG
                in 40..47 -> buffer.style.bg = p - 40
                49 -> buffer.style.bg = XtermColors.COLOR_DEFAULT_BG
                in 90..97 -> buffer.style.fg = p - 90 + 8
                in 100..107 -> buffer.style.bg = p - 100 + 8
                38, 48 -> {
                    val isFg = p == 38
                    val consumed = applyExtendedColor(params, count, i + 1, isFg)
                    i += consumed
                }
            }
            i++
        }
    }

    /**
     * Consumes the bytes after 38/48. Returns how many extra params were used
     * (not counting the 38/48 itself).
     */
    private fun applyExtendedColor(params: IntArray, count: Int, start: Int, isFg: Boolean): Int {
        if (start >= count) return 0
        return when (params[start]) {
            5 -> {
                if (start + 1 >= count) return 1
                val indexed = params[start + 1].coerceIn(0, 255)
                if (isFg) buffer.style.fg = indexed else buffer.style.bg = indexed
                2
            }
            2 -> {
                if (start + 3 >= count) return (count - start).coerceAtLeast(1)
                val color = XtermColors.rgb(params[start + 1], params[start + 2], params[start + 3])
                if (isFg) buffer.style.fg = color else buffer.style.bg = color
                4
            }
            else -> 1
        }
    }
}
