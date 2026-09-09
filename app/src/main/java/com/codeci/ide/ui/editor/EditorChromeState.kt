package com.codeci.ide.ui.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 32.1 — the one fact the app scaffold needs from the editor to hide
 * the 5-tab bar: is CodeC Keys currently on screen? The editor is the only
 * surface that knows its keyboard state (and only the editor's keyboard
 * should ever hide the bar off-IME); a process-wide singleton keeps the
 * signal alive across the NavHost's destination swaps without threading a
 * callback through every route.
 */
object EditorChromeState {

    private val _keysVisible = MutableStateFlow(false)

    /** True while CodeC Keys is the editor's on-screen keyboard. */
    val keysVisible: StateFlow<Boolean> = _keysVisible.asStateFlow()

    fun setKeysVisible(visible: Boolean) {
        _keysVisible.value = visible
    }
}
