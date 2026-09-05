package com.codeci.ide.ui.editor.sora

import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion

/**
 * Phase 27.2/27.3 — sora's native completion panel, gated to *browse mode*.
 *
 * The stock component auto-pops its floating window near the caret on every
 * inserted character; on a phone that floats mouse-era UI over the code mid-
 * keystroke (the Phase 27 complaint: "it suggests and can't do anything").
 * The phone-first surfaces are the ghost (27.1) and the suggestion strip
 * (27.2); this component keeps sora's panel as the explicit "⌄ more" browse
 * surface (S5) and NOTHING else:
 *
 * - [requireCompletion] is gated: sora's own auto triggers (content/caret
 *   events) fall through as no-ops unless a browse session is active.
 * - [browseNow] starts a session (the strip's "⌄ more" cap); while browsing,
 *   super's normal update-per-keystroke behavior applies, including its
 *   hardware Tab/Enter/arrows handling (matrix's PANEL column).
 * - Every hide path ([dismiss]) ends the session — tap-away (sora's own
 *   ClickEvent subscription), empty results, scroll-fling — so a dismissed
 *   panel never resurrects on the next keystroke.
 * - [onBrowseVisibility] mirrors real attach/detach to Compose state so
 *   [com.codeci.ide.ui.editor.CompletionPolicy.surfaceFor] never claims
 *   PANEL while nothing is on screen.
 */
class CodeCCompletionComponent(editor: CodeEditor) : EditorAutoCompletion(editor) {

    /** Master Settings switch: when false the component is also setEnabled(false). */
    var masterEnabled: Boolean = true

    private var browse = false

    /** Compose-side mirror of real panel visibility. Called on the UI thread. */
    var onBrowseVisibility: ((Boolean) -> Unit)? = null

    override fun requireCompletion() {
        // The 27.2 law: typing and caret moves NEVER pop the floating panel.
        if (!masterEnabled || !browse) return
        super.requireCompletion()
    }

    /** "⌄ more" entry point. result unknown synchronously (sora defers 70 ms). */
    fun browseNow() {
        if (!masterEnabled) return
        browse = true
        requireCompletion()
    }

    override fun hide() {
        val wasBrowsing = browse
        browse = false
        super.hide()
        if (wasBrowsing) onBrowseVisibility?.invoke(false)
    }

    override fun show() {
        // EditorAutoCompletion.show() posts the real window attach; report
        // visibility only if the panel actually appeared (empty item sets
        // hide instead and land in the dismiss path below).
        super.show()
        if (browse) {
            getEditor().postDelayed({
                if (isShowing) onBrowseVisibility?.invoke(true)
            }, 90)
        }
    }
}
