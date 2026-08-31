package com.codeci.ide.ui.terminal

/**
 * Phase 19.5 — minimal standard-alphabet base64 decoder (RFC 4648), written
 * from the spec so the pure-Kotlin emulator layer (OSC 52 clipboard) works
 * in host unit tests without `android.util.Base64`.
 *
 * Tolerates embedded whitespace/line breaks and `=` padding; returns null
 * for any invalid character (fail closed rather than corrupting a clip).
 * Output is decoded as UTF-8 (the OSC 52 payload convention).
 */
object Base64Codec {

    fun decode(text: String): String? {
        val bytes = ByteArray(text.length / 4 * 3 + 3)
        var len = 0
        var acc = 0
        var bits = 0
        for (ch in text) {
            when {
                ch == '=' -> break
                ch == '\n' || ch == '\r' || ch == ' ' || ch == '\t' -> continue
                else -> {
                    val v = valueOf(ch) ?: return null
                    acc = (acc shl 6) or v
                    bits += 6
                    if (bits >= 8) {
                        bits -= 8
                        bytes[len++] = ((acc shr bits) and 0xFF).toByte()
                    }
                }
            }
        }
        return String(bytes, 0, len, Charsets.UTF_8)
    }

    private fun valueOf(ch: Char): Int? = when (ch) {
        in 'A'..'Z' -> ch - 'A'
        in 'a'..'z' -> ch - 'a' + 26
        in '0'..'9' -> ch - '0' + 52
        '+' -> 62
        '/' -> 63
        else -> null
    }
}
