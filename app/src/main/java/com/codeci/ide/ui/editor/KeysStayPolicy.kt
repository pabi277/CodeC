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

    /**
     * Phase 70.1 (2026-09-28) — the run-keys row's own visibility.
     *
     * The owner's answer Q3, verbatim: *“A — The strip says ‘Waiting for input —
     * tap to answer’, and the ↵ key opens/focuses the field”*, plus his request
     * to bring the Phase 23.2 run keys back as the touch path. [isVisible]
     * above deliberately hands the IME to a waiting program, which also hid the
     * run-keys row with it — so the very keys that submit a line, interrupt a
     * program and insert a tab were unreachable exactly when they were needed.
     * This is that row's own law: an interactive run shows it, and only an
     * explicit collapse or the master switch can take it away.
     */
    fun isRunStripVisible(
        keepOpen: Boolean,
        waitingForInput: Boolean,
        explicitlyCollapsed: Boolean
    ): Boolean =
        keepOpen && waitingForInput && !explicitlyCollapsed
}
