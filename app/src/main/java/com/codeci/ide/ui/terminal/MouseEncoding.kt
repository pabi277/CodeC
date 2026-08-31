package com.codeci.ide.ui.terminal

/**
 * Phase 19.5 — xterm mouse-reporting modes and encoders.
 *
 * Written from the public xterm control-sequence specification
 * (https://invisible-island.net/xterm/ctlseqs/ctlseqs.html, "Mouse
 * Tracking"): DECSET `?9` (X10), `?1000` (normal), `?1002` (button-event),
 * `?1003` (any-event), `?1006` (SGR extension), `?1007` (alternate scroll).
 *
 * Encodings produced:
 *  * SGR (1006): `CSI < Cb ; Cx ; Cy M` for press/motion and final `m` for
 *    release. Cb = button (0/1/2) | 4(shift) | 8(meta) | 16(ctrl) | 32(motion),
 *    wheel = 64/65; Cx/Cy are 1-based columns/rows.
 *  * Legacy X10: `CSI M` + chars 32+Cb, 32+col, 32+row (null when the
 *    1-based coordinate cannot fit in a byte, > 223).
 */
object MouseModes {
    const val X10 = 1
    const val NORMAL = 2
    const val BUTTON = 4
    const val ANY = 8
    const val SGR_EXT = 16
    const val ALT_SCROLL = 32

    /** Any tracking mode that makes the terminal deliver mouse events. */
    const val CAPTURE_MASK = X10 or NORMAL or BUTTON or ANY
}

object MouseEncoding {

    /** Wheel directions for [wheel]. */
    const val WHEEL_UP = 0
    const val WHEEL_DOWN = 1

    fun press(button: Int, col: Int, row: Int, sgr: Boolean, shift: Boolean = false, alt: Boolean = false, ctrl: Boolean = false): String? {
        val cb = base(button, shift, alt, ctrl)
        return if (sgr) {
            sgr(cb, col, row, press = true)
        } else {
            legacy(cb, col, row)
        }
    }

    fun release(button: Int, col: Int, row: Int, sgr: Boolean, shift: Boolean = false, alt: Boolean = false, ctrl: Boolean = false): String? {
        if (!sgr) {
            // Legacy X10/normal tracking reports release as button 3.
            return legacy(base(3, shift, alt, ctrl), col, row)
        }
        return sgr(base(button, shift, alt, ctrl), col, row, press = false)
    }

    /** Motion with a button held (button-event/any-event modes). */
    fun motion(button: Int, col: Int, row: Int, sgr: Boolean): String? {
        val cb = base(button, false, false, false) or 32
        return if (sgr) sgr(cb, col, row, press = true) else legacy(cb, col, row)
    }

    /** Free motion with no button held (any-event mode). */
    fun freeMotion(col: Int, row: Int, sgr: Boolean): String? {
        val cb = 3 or 32
        return if (sgr) sgr(cb, col, row, press = true) else legacy(cb, col, row)
    }

    /** Wheel events are press-only (button 64 = up, 65 = down). */
    fun wheel(direction: Int, col: Int, row: Int, sgr: Boolean): String? {
        val cb = if (direction == WHEEL_UP) 64 else 65
        return if (sgr) sgr(cb, col, row, press = true) else legacy(cb, col, row)
    }

    private fun base(button: Int, shift: Boolean, alt: Boolean, ctrl: Boolean): Int =
        button.coerceIn(0, 3) or
            (if (shift) 4 else 0) or
            (if (alt) 8 else 0) or
            (if (ctrl) 16 else 0)

    private fun sgr(cb: Int, col: Int, row: Int, press: Boolean): String {
        val final = if (press) 'M' else 'm'
        return "\u001b[<$cb;${col.coerceAtLeast(1)};${row.coerceAtLeast(1)}$final"
    }

    private fun legacy(cb: Int, col: Int, row: Int): String? {
        val c = col.coerceAtLeast(1)
        val r = row.coerceAtLeast(1)
        if (c > 223 || r > 223) return null
        return buildString {
            append("\u001b[M")
            append((32 + cb).toChar())
            append((32 + c).toChar())
            append((32 + r).toChar())
        }
    }
}
