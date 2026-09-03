package com.codeci.ide.ui.services

import java.io.File

/**
 * Phase 21.2 — "is this language's toolchain installed?" as a pure,
 * host-testable file check against the CodeC userland prefix.
 *
 * Deliberately *not* a `pkg` query: RUN ▶ must not spawn a process just to
 * decide whether it can spawn a process. A partial install that still left the
 * binary behind counts as installed — `pkg repair` is the cure for that, not
 * this probe (see PART_21_2 §6 D1).
 */
object LanguageToolProbe {

    /**
     * True when `<prefix>/bin/<binary>` exists. `File.exists()` follows
     * symlinks, so a dangling link reads as missing — which is the honest
     * answer to "can I run this?".
     */
    fun isInstalled(prefix: File, binary: String): Boolean {
        if (binary.isBlank()) return false
        return File(File(prefix, "bin"), binary).exists()
    }
}

/**
 * Phase 21.2 — the state behind the editor's "Install <tool>?" bottom sheet.
 */
data class InstallPromptState(
    val packageName: String,
    val displayName: String,
    val sizeHint: String? = null,
    val pendingRunOnSuccess: Boolean = true,
)
