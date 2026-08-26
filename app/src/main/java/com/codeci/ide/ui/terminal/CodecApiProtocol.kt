package com.codeci.ide.ui.terminal

import java.io.File

/**
 * CodeC terminal bridge protocol — Phase 4.7 foundation.
 *
 * Terminal programs request Android capabilities by printing an in-band
 * OSC 1337 sequence of the form:
 *
 *   ESC ] 1337 ; CodeCApi:<op>:<requestFile>:<responseFile> BEL
 *
 * The app ([CodecApiBridge]) validates that both paths are direct children
 * of `$PREFIX/tmp/codec-api`, performs the capability, and atomically
 * writes the outcome into `<responseFile>` (so the CLI can poll for the
 * file and never relies on terminal echo). Content never travels inside
 * the escape sequence itself — only paths to app-private files — which
 * keeps payloads small and avoids binary/base64 encoding issues.
 *
 * The `CodeCApi:` namespace is separate from the legacy
 * `CodeCRequestStorage` control (Phase 4.1) and is *additive*: old APKs
 * simply ignore unknown OSC values, and this APK still honors the legacy
 * control.
 *
 * Later capabilities (4.8+) add a new [Op] (or a new namespace) plus the
 * corresponding CLI script; the plumbing in [TerminalEmulator] /
 * [TerminalSession] / [CodecApiBridge] is reused unchanged.
 */
object CodecApiProtocol {
    const val OSC_CODE = "1337"
    const val NAMESPACE = "CodeCApi"
    const val API_DIR_NAME = "codec-api"

    /** Upper bound for `clipboard set` content (keeps the app's memory use bounded). */
    const val MAX_SET_BYTES = 4 * 1024 * 1024

    const val ERR_PREFIX = "ERR:"

    enum class Op(val wire: String) {
        CLIPBOARD_GET("clipboard.get"),
        CLIPBOARD_SET("clipboard.set"),
        CLIPBOARD_CLEAR("clipboard.clear"),
        CLIPBOARD_STATUS("clipboard.status");

        companion object {
            fun fromWire(value: String): Op? = entries.firstOrNull { it.wire == value }
        }
    }

    data class Request(
        val op: Op,
        /** Path of the payload file (`clipboard set` content). Null when not needed. */
        val requestFile: String?,
        val responseFile: String
    )

    /**
     * Parses the value part of an OSC 1337 sequence (`CodeCApi:...`).
     * Returns null for anything that is not a valid CodeCApi request so the
     * caller can ignore it (unknown/foreign sequences must not be fatal).
     */
    fun parse(payload: String): Request? {
        if (!payload.startsWith("$NAMESPACE:")) return null
        val parts = payload.substring(NAMESPACE.length + 1).split(':')
        if (parts.size != 3) return null
        val op = Op.fromWire(parts[0]) ?: return null
        val requestFile = parts[1].takeIf { it.isNotEmpty() }
        val responseFile = parts[2]
        if (responseFile.isEmpty()) return null
        return Request(op, requestFile, responseFile)
    }

    /** Inverse of [parse] (used by the CLI scripts and by host tests). */
    fun build(op: Op, requestFile: String?, responseFile: String): String =
        "$NAMESPACE:${op.wire}:${requestFile ?: ""}:$responseFile"

    /**
     * True only when [path] resolves (symlinks included) to a *direct child*
     * of [apiDir]. This is the security boundary of the bridge: a payload
     * from the terminal can name only files inside `$PREFIX/tmp/codec-api`.
     */
    fun isConfinedDirectChild(path: String, apiDir: File): Boolean {
        val root = runCatching { apiDir.canonicalFile }.getOrNull() ?: return false
        val canonical = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        return canonical.parentFile == root
    }
}
