package com.codeci.ide.ui.terminal

/**
 * Incremental UTF-8 + VT/xterm escape parser.
 *
 * Ground state prints Unicode; ESC / CSI / OSC are dispatched to [Host].
 * Unknown sequences are ignored (the ECMA-48 "ignore" rule) so a future
 * program that emits extra private modes does not corrupt the screen.
 */
class AnsiParser(private val host: Host) {

    interface Host {
        fun print(codePoint: Int)
        fun executeC0(byte: Int)
        fun csi(prefix: Char, params: IntArray, count: Int, intermediates: String, finalByte: Char)
        fun osc(payload: String)
        fun esc(intermediates: String, finalByte: Char)
    }

    private enum class State {
        GROUND, ESCAPE, CSI, OSC, DCS, IGNORE_ST
    }

    private var state = State.GROUND
    private val params = IntArray(MAX_PARAMS)
    private var paramCount = 0
    private var currentParam = -1
    private var prefix = '\u0000'
    private val intermediates = StringBuilder()
    private val osc = StringBuilder()
    private var utf8Need = 0
    private var utf8Acc = 0

    fun reset() {
        state = State.GROUND
        resetCsi()
        osc.setLength(0)
        utf8Need = 0
        utf8Acc = 0
    }

    fun feed(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        feed(bytes, 0, bytes.size)
    }

    fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        val end = (offset + length).coerceAtMost(bytes.size)
        var i = offset.coerceAtLeast(0)
        while (i < end) {
            val b = bytes[i].toInt() and 0xFF
            i++
            when (state) {
                State.GROUND -> consumeGround(b)
                State.ESCAPE -> consumeEscape(b)
                State.CSI -> consumeCsi(b)
                State.OSC -> consumeOsc(b)
                State.DCS, State.IGNORE_ST -> consumeUntilSt(b)
            }
        }
    }

    private fun consumeGround(b: Int) {
        if (utf8Need > 0) {
            if (b and 0xC0 != 0x80) {
                utf8Need = 0
                host.print(0xFFFD)
                consumeGround(b)
                return
            }
            utf8Acc = (utf8Acc shl 6) or (b and 0x3F)
            utf8Need--
            if (utf8Need == 0) host.print(utf8Acc)
            return
        }
        when {
            b == 0x1B -> {
                state = State.ESCAPE
                intermediates.setLength(0)
            }
            b == 0x9B -> enterCsi()
            b == 0x9D -> enterOsc()
            b < 0x20 -> host.executeC0(b)
            b < 0x80 -> host.print(b)
            b and 0xE0 == 0xC0 -> startUtf8(b and 0x1F, 1)
            b and 0xF0 == 0xE0 -> startUtf8(b and 0x0F, 2)
            b and 0xF8 == 0xF0 -> startUtf8(b and 0x07, 3)
            else -> host.print(0xFFFD)
        }
    }

    private fun startUtf8(acc: Int, need: Int) {
        utf8Acc = acc
        utf8Need = need
    }

    private fun consumeEscape(b: Int) {
        when (b) {
            0x1B -> {
                intermediates.setLength(0)
                return
            }
            '['.code -> enterCsi()
            ']'.code -> enterOsc()
            'P'.code, 'X'.code, '^'.code, '_'.code -> {
                state = State.IGNORE_ST
            }
            in 0x20..0x2F -> intermediates.append(b.toChar())
            in 0x30..0x7E -> {
                host.esc(intermediates.toString(), b.toChar())
                state = State.GROUND
            }
            in 0x00..0x1F -> {
                if (b != 0x1B) host.executeC0(b)
            }
            else -> state = State.GROUND
        }
    }

    private fun enterCsi() {
        state = State.CSI
        resetCsi()
    }

    private fun enterOsc() {
        state = State.OSC
        osc.setLength(0)
    }

    private fun resetCsi() {
        paramCount = 0
        currentParam = -1
        prefix = '\u0000'
        intermediates.setLength(0)
        for (i in params.indices) params[i] = 0
    }

    private fun consumeCsi(b: Int) {
        when (b) {
            0x1B -> {
                state = State.ESCAPE
                intermediates.setLength(0)
            }
            in 0x00..0x1F -> host.executeC0(b)
            '?'.code, '>'.code, '='.code -> {
                if (prefix == '\u0000' && paramCount == 0 && currentParam < 0 && intermediates.isEmpty()) {
                    prefix = b.toChar()
                }
            }
            in '0'.code..'9'.code -> {
                val digit = b - '0'.code
                currentParam = if (currentParam < 0) digit else (currentParam * 10 + digit).coerceAtMost(9999)
            }
            ';'.code, ':'.code -> {
                pushParam()
            }
            in 0x20..0x2F -> intermediates.append(b.toChar())
            in 0x40..0x7E -> {
                pushParam()
                val count = if (paramCount == 0) 0 else paramCount
                host.csi(prefix, params, count, intermediates.toString(), b.toChar())
                state = State.GROUND
            }
            else -> state = State.GROUND
        }
    }

    private fun pushParam() {
        if (paramCount < MAX_PARAMS) {
            params[paramCount] = if (currentParam < 0) 0 else currentParam
            paramCount++
        }
        currentParam = -1
    }

    private fun consumeOsc(b: Int) {
        when (b) {
            0x07 -> finishOsc()
            0x1B -> {
                // ST is ESC \; we peek via a one-byte "almost ST" by
                // switching to IGNORE and finishing on '\\'. Simpler: treat
                // BEL as the common terminator and ESC \ below.
                state = State.IGNORE_ST
                // If the next byte is '\', consumeUntilSt will finish OSC.
                pendingOsc = true
            }
            0x9C -> finishOsc()
            in 0x00..0x1F -> {
                /* drop other C0 inside OSC */
            }
            else -> {
                if (osc.length < MAX_OSC) osc.append(b.toChar())
            }
        }
    }

    private var pendingOsc: Boolean = false

    private fun consumeUntilSt(b: Int) {
        when (b) {
            0x07, 0x9C -> {
                if (pendingOsc) finishOsc() else state = State.GROUND
                pendingOsc = false
            }
            '\\'.code -> {
                if (pendingOsc) finishOsc() else state = State.GROUND
                pendingOsc = false
            }
            0x1B -> {
                /* stay, wait for '\' */
            }
            else -> {
                if (pendingOsc && osc.length < MAX_OSC && b >= 0x20) {
                    osc.append(b.toChar())
                }
            }
        }
    }

    private fun finishOsc() {
        host.osc(osc.toString())
        osc.setLength(0)
        pendingOsc = false
        state = State.GROUND
    }

    companion object {
        const val MAX_PARAMS = 16
        const val MAX_OSC = 1024

        fun param(params: IntArray, count: Int, index: Int, default: Int = 0): Int {
            if (index >= count) return default
            val value = params[index]
            return if (value == 0 && default != 0) default else value
        }
    }
}
