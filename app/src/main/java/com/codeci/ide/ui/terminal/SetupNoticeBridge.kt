package com.codeci.ide.ui.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 44.2 — the boot repair → UI bridge (the same shape as
 * `CrashHandOffBridge` / `IncomingImportBridge`, which is how an out-of-NavHost
 * surface hands `MainApp` something to show).
 *
 * `SetupRecovery.recover` runs on a daemon thread in `MainActivity.onCreate`,
 * before any Compose exists. When it had to put a previous userland back (a
 * kill between the two renames of `swapPrefix`) the user is told once, in the
 * setup bar, and the note is dismissible — it is a repair report, not a nag.
 */
object SetupNoticeBridge {

    private val _message = MutableStateFlow<String?>(null)

    /** The setup bar collects this; null = nothing to say. */
    val message: StateFlow<String?> = _message

    fun post(message: String?) {
        if (!message.isNullOrBlank()) _message.value = message
    }

    fun clear() {
        _message.value = null
    }
}
