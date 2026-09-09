package com.codeci.ide.ui.editor

/**
 * Phase 35.1 — the visibility law for the editor's auxiliary keyboard.
 *
 * The editor screen is the focused editing session even while the output
 * panel is selected. Only an explicit collapse or an interactive stdin run
 * may take the keys away; a transient IME transition must not do so.
 */
object KeysStayPolicy {

    fun isVisible(
        keepOpen: Boolean,
        editorFocused: Boolean,
        waitingForInput: Boolean,
        explicitlyCollapsed: Boolean
    ): Boolean =
        keepOpen && editorFocused && !waitingForInput && !explicitlyCollapsed
}
