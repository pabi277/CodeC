package com.codeci.ide.ui.editor.lsp

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Phase 31.5/31.6 — LSP stdio framing (Content-Length + UTF-8 body).
 *
 * The spec counts **bytes**, not characters. Mixing a `BufferedReader`
 * (chars) with a byte `Content-Length` hangs the client the first time
 * a completion item carries a non-ASCII signature. Host-testable, no
 * Android, no Process.
 */
object LspWire {

    fun frame(json: String): ByteArray {
        val body = json.toByteArray(StandardCharsets.UTF_8)
        val header = ("Content-Length: " + body.size + "\r\n\r\n")
            .toByteArray(StandardCharsets.UTF_8)
        return header + body
    }

    /**
     * Blocking read of one framed message. Returns null on EOF or a
     * malformed header. The caller is responsible for bounding the wait
     * (a dedicated reader thread + a timed poll on the queue).
     */
    fun readMessage(input: InputStream): LspResponse? {
        val headers = readHeaders(input) ?: return null
        val length = headers["content-length"]?.toIntOrNull() ?: return null
        if (length <= 0 || length > MAX_BODY_BYTES) return null
        val body = readExact(input, length) ?: return null
        return LspResponseParser.parse(String(body, StandardCharsets.UTF_8))
    }

    private fun readHeaders(input: InputStream): Map<String, String>? {
        val buf = ByteArrayOutputStream(128)
        var prev = -1
        while (true) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            if (prev == '\r'.code && b == '\n'.code && buf.size() >= 4) {
                val bytes = buf.toByteArray()
                val n = bytes.size
                if (bytes[n - 4] == '\r'.code.toByte() &&
                    bytes[n - 3] == '\n'.code.toByte() &&
                    bytes[n - 2] == '\r'.code.toByte() &&
                    bytes[n - 1] == '\n'.code.toByte()
                ) break
            }
            prev = b
            if (buf.size() > MAX_HEADER_BYTES) return null
        }
        val text = String(buf.toByteArray(), StandardCharsets.US_ASCII)
        val map = HashMap<String, String>()
        for (line in text.split("\r\n")) {
            if (line.isEmpty()) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            map[line.substring(0, colon).trim().lowercase()] =
                line.substring(colon + 1).trim()
        }
        return map
    }

    private fun readExact(input: InputStream, n: Int): ByteArray? {
        val body = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(body, off, n - off)
            if (r <= 0) return null
            off += r
        }
        return body
    }

    private const val MAX_HEADER_BYTES = 16_384
    private const val MAX_BODY_BYTES = 8_000_000
}

/**
 * `file://` URI for an on-device path. LSP servers refuse a relative
 * `file://main.c`; they want an absolute URI. Spaces and non-ASCII are
 * percent-encoded; `/` is kept so Android app-private paths round-trip.
 */
object LspUris {
    fun fileUri(path: String): String {
        if (path.isBlank()) return "file:///untitled"
        val abs = if (path.startsWith("/")) path else "/$path"
        val encoded = buildString(abs.length + 8) {
            for (ch in abs) {
                when {
                    ch == '/' -> append('/')
                    ch.code in 0x21..0x7e && ch != '%' && ch != '#' && ch != '?' && ch != ' ' ->
                        append(ch)
                    else -> {
                        append('%')
                        val hex = ch.code.toString(16).uppercase()
                        if (hex.length == 1) append('0')
                        append(hex)
                    }
                }
            }
        }
        return "file://$encoded"
    }
}
