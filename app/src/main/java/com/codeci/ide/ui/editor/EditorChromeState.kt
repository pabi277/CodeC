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
 *
 * Phase 45.2 (device round) added the two facts the guided tour needs for the
 * same reason: the editor owns its dialogs and its drawer, and a coach mark is
 * drawn in the ACTIVITY window — so a box cut while a dialog is open lands
 * *underneath* what the user is looking at (an `AlertDialog` is its own window),
 * and a box cut while the drawer is open can land on a control the drawer is
 * covering. Both are reported here instead of being guessed from anchors.
 */
object EditorChromeState {

    private val _keysVisible = MutableStateFlow(false)

    /** True while CodeC Keys is the editor's on-screen keyboard. */
    val keysVisible: StateFlow<Boolean> = _keysVisible.asStateFlow()

    fun setKeysVisible(visible: Boolean) {
        _keysVisible.value = visible
    }

    private val _dialogOpen = MutableStateFlow(false)

    /** True while any editor dialog, sheet or popup menu is open. */
    val dialogOpen: StateFlow<Boolean> = _dialogOpen.asStateFlow()

    fun setDialogOpen(open: Boolean) {
        _dialogOpen.value = open
    }

    private val _drawerOpen = MutableStateFlow(false)

    /** True while the editor's ☰ drawer is open (the tour's beats 2-3 live in it). */
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    fun setDrawerOpen(open: Boolean) {
        _drawerOpen.value = open
    }

    private val _installRunning = MutableStateFlow(false)

    /**
     * Phase 45 round 4 — true while an install the user asked for is streaming
     * into the editor's Output Panel (`OutputRunState.installing && busy`). The
     * editor is the only surface that knows, and the app scaffold needs it: the
     * owner's *"When the userland is installing and unpacking the user can not
     * access any other option and it will show a sweet massage of why"* means
     * the OTHER TABS pause too, and they live in `MainActivity`, not here.
     *
     * `false` on dispose, like every fact in this object: a stale "installing"
     * would leave the whole app locked behind an install that finished.
     */
    val installRunning: StateFlow<Boolean> = _installRunning.asStateFlow()

    fun setInstallRunning(running: Boolean) {
        _installRunning.value = running
    }
}
