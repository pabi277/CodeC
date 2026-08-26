package com.codeci.ide.ui.terminal

import java.io.File

/**
 * CodeC terminal bridge protocol — Phase 4.7 foundation, extended by 4.8.
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
 * Capabilities are grouped by wire name (`clipboard.*`, `notify.*`, …).
 * Adding a capability in 4.9+ = one new [Op] + one CLI script; the
 * plumbing in [TerminalEmulator] / [TerminalSession] / [CodecApiBridge]
 * is reused unchanged.
 */
object CodecApiProtocol {
    const val OSC_CODE = "1337"
    const val NAMESPACE = "CodeCApi"
    const val API_DIR_NAME = "codec-api"

    /** Upper bound for `clipboard set` content (keeps the app's memory use bounded). */
    const val MAX_SET_BYTES = 4 * 1024 * 1024

    /** Upper bound for a `notify.send` payload (title + body). */
    const val MAX_NOTIFY_BYTES = 8 * 1024

    /** Upper bound for a `toast.show` message. */
    const val MAX_TOAST_BYTES = 4 * 1024

    /** Upper bound for a `share.text` payload. */
    const val MAX_SHARE_BYTES = 256 * 1024

    /** Upper bound for a `url.open` payload. */
    const val MAX_URL_BYTES = 8 * 1024

    /** `vibrate` duration bounds (milliseconds) and default when unspecified. */
    const val MAX_VIBRATE_MS = 10_000L
    const val DEFAULT_VIBRATE_MS = 500L

    const val ERR_PREFIX = "ERR:"

    /**
     * Response marker the app writes while a runtime permission is pending;
     * the CLI prints a hint and keeps polling until the app replaces the
     * file with the real outcome (or fails after a bounded wait).
     */
    const val NEED_PERMISSION_PREFIX = "NEED_PERMISSION:"

    enum class Op(val wire: String) {
        CLIPBOARD_GET("clipboard.get"),
        CLIPBOARD_SET("clipboard.set"),
        CLIPBOARD_CLEAR("clipboard.clear"),
        CLIPBOARD_STATUS("clipboard.status"),
        NOTIFY_SEND("notify.send"),
        NOTIFY_CLEAR("notify.clear"),
        NOTIFY_STATUS("notify.status"),
        TOAST_SHOW("toast.show"),
        SHARE_TEXT("share.text"),
        OPEN_URL("url.open"),
        VIBRATE("vibrate");

        val isNotifyOperation: Boolean
            get() = this == NOTIFY_SEND || this == NOTIFY_CLEAR || this == NOTIFY_STATUS

        val isTermuxApiOperation: Boolean
            get() = this == TOAST_SHOW || this == SHARE_TEXT || this == OPEN_URL || this == VIBRATE

        companion object {
            fun fromWire(value: String): Op? = entries.firstOrNull { it.wire == value }
        }
    }

    data class Request(
        val op: Op,
        /** Path of the payload file (e.g. `clipboard set`/`notify send` content). Null when not needed. */
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

    /** Body of the `NEED_PERMISSION` response sent to the CLI. */
    fun permissionNotice(permission: String): String = "$NEED_PERMISSION_PREFIX$permission"

    /**
     * Android's `/data/user/0/` is the user-emulation alias of the canonical
     * `/data/data/` spelling that dpkg and every published `.deb` record.
     * They address the same inodes, but `File.canonicalFile()` (realpath)
     * cannot always collapse one into the other: on some devices they are
     * distinct bind mounts rather than a symlink — the same reason the
     * Phase 4.5/4.6 review found `readlink -f` unreliable for canonicalization.
     * Maps to the dpkg-recorded spelling so path comparisons are invariant to
     * which alias a caller used.
     */
    fun canonicalUserPrefix(path: String): String =
        if (path.startsWith("/data/user/0/")) "/data/data/" + path.removePrefix("/data/user/0/")
        else path

    /**
     * True only when [path] resolves (symlinks included) to a *direct child*
     * of [apiDir]. This is the security boundary of the bridge: a payload
     * from the terminal can name only files inside `$PREFIX/tmp/codec-api`.
     *
     * KI-2 makes the shell export the canonical `/data/data/…` `$PREFIX`, so
     * the CLI emits `/data/data/…` request/response paths while the app still
     * computes `apiDir` from the raw `Context.getFilesDir()` spelling. Both
     * spellings are normalized ([canonicalUserPrefix]) *before* resolving so
     * the confinement check does not reject a valid request on bind-mounted
     * devices where `canonicalFile` cannot collapse the alias.
     */
    fun isConfinedDirectChild(path: String, apiDir: File): Boolean {
        val root = runCatching { File(canonicalUserPrefix(apiDir.path)).canonicalFile }.getOrNull()
            ?: return false
        val canonical = runCatching { File(canonicalUserPrefix(path)).canonicalFile }.getOrNull()
            ?: return false
        return canonical.parentFile == root
    }
}
