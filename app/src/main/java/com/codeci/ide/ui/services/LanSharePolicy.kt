package com.codeci.ide.ui.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 37.1 — the LAN-sharing switch, in exactly one place.
 *
 * Two surfaces show it (the Output Panel share row and the Web Preview address
 * bar) and both must agree, so neither owns it: the switch is a single
 * observable boolean, **off by default**, held for the life of the process.
 * Not persisted on purpose — "anyone on this Wi-Fi can open these files" is a
 * decision the owner's spec makes per run, not once forever.
 */
class LanSharePolicy {

    private val _enabled = MutableStateFlow(false)

    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun isEnabled(): Boolean = _enabled.value

    /** Turning LAN sharing on/off is also the signal to restart a live server. */
    fun set(enabled: Boolean): Boolean {
        if (_enabled.value == enabled) return false
        _enabled.value = enabled
        return true
    }

    fun toggle(): Boolean {
        set(!_enabled.value)
        return _enabled.value
    }

    companion object {
        /** The app-wide switch the editor, the preview screen and the host share. */
        val shared = LanSharePolicy()
    }
}
