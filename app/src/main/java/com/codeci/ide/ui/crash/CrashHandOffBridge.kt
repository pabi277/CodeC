package com.codeci.ide.ui.crash

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 42.3 — the crash overlay → feedback screen hand-off bridge (the
 * same shape as `IncomingImportBridge` for "Open with CodeC", which is the
 * established way an out-of-NavHost surface asks `MainApp` to navigate).
 *
 * The overlay is mounted OUTSIDE `MainApp` on purpose (it must appear
 * before anything else), so it cannot hold the NavController: its
 * [Send a report] button records a request here and `MainApp` routes it
 * into `Screen.Feedback?crash=1`, which opens 41.2's feedback screen with
 * both attachments pre-ticked — replacing the old "COPY ALL and paste it
 * into the chat" instruction with a tap, exactly at the moment a user is
 * motivated to report.
 */
object CrashHandOffBridge {

    private val _reportCrashRequested = MutableStateFlow(false)

    /** MainApp collects this; true once → navigate to feedback, then clear. */
    val reportCrashRequested: StateFlow<Boolean> = _reportCrashRequested

    fun requestCrashReport() {
        _reportCrashRequested.value = true
    }

    fun clear() {
        _reportCrashRequested.value = false
    }
}
