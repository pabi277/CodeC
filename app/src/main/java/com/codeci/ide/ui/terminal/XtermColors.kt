package com.codeci.ide.ui.terminal

/**
 * xterm-256color palette + SGR color encoding.
 *
 * Encoding of [CellStyle] color ints:
 *  - [COLOR_DEFAULT_FG] / [COLOR_DEFAULT_BG] — theme defaults
 *  - 0..255 — indexed xterm color
 *  - bit 24 set ([COLOR_RGB_FLAG]) — 24-bit RGB in the low 24 bits
 */
object XtermColors {
    const val COLOR_DEFAULT_FG = -1
    const val COLOR_DEFAULT_BG = -2
    const val COLOR_RGB_FLAG = 0x01000000

    fun rgb(r: Int, g: Int, b: Int): Int =
        COLOR_RGB_FLAG or
            ((r.coerceIn(0, 255) and 0xFF) shl 16) or
            ((g.coerceIn(0, 255) and 0xFF) shl 8) or
            (b.coerceIn(0, 255) and 0xFF)

    fun isRgb(color: Int): Boolean = color and COLOR_RGB_FLAG != 0

    fun isDefault(color: Int): Boolean =
        color == COLOR_DEFAULT_FG || color == COLOR_DEFAULT_BG

    fun red(color: Int): Int = (color shr 16) and 0xFF
    fun green(color: Int): Int = (color shr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF

    /**
     * Resolves an encoded color to packed 0x00RRGGBB. [bold] promotes the
     * standard 0–7 palette to the bright 8–15 variants (xterm convention).
     */
    fun toRgb(color: Int, defaultRgb: Int, bold: Boolean = false): Int {
        if (isDefault(color)) return defaultRgb
        if (isRgb(color)) return color and 0x00FFFFFF
        val index = if (bold && color in 0..7) color + 8 else color.coerceIn(0, 255)
        return PALETTE[index]
    }

    /** Standard xterm 256-color palette as 0x00RRGGBB. */
    val PALETTE: IntArray = IntArray(256).also { table ->
        val ansi = intArrayOf(
            0x000000, 0xCD0000, 0x00CD00, 0xCDCD00,
            0x0000EE, 0xCD00CD, 0x00CDCD, 0xE5E5E5,
            0x7F7F7F, 0xFF0000, 0x00FF00, 0xFFFF00,
            0x5C5CFF, 0xFF00FF, 0x00FFFF, 0xFFFFFF
        )
        for (i in 0..15) table[i] = ansi[i]
        val cube = intArrayOf(0x00, 0x5F, 0x87, 0xAF, 0xD7, 0xFF)
        var idx = 16
        for (r in 0..5) {
            for (g in 0..5) {
                for (b in 0..5) {
                    table[idx++] = (cube[r] shl 16) or (cube[g] shl 8) or cube[b]
                }
            }
        }
        for (i in 0..23) {
            val gray = 8 + i * 10
            table[232 + i] = (gray shl 16) or (gray shl 8) or gray
        }
    }
}

object CellFlags {
    const val BOLD = 1
    const val FAINT = 2
    const val ITALIC = 4
    const val UNDERLINE = 8
    const val BLINK = 16
    const val INVERSE = 32
    const val INVISIBLE = 64
    const val STRIKE = 128
}
