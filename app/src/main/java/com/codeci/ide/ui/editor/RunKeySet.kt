package com.codeci.ide.ui.editor

/**
 * Phase 23.2 — the extra-keys strip for interactive runs. When the Output
 * Panel's inline input (Phase 23.1) is live, the keys row swaps its editor
 * keys for this run set: submit the line, interrupt the program, insert a
 * tab, and (future) REPL history navigation. Pure and data-driven so the
 * exact set is host-tested; the `↑`/`↓` history keys are present but no-op
 * stubs until REPL history exists (D2).
 *
 * Phase 26.1 — run keys gain popup + swipe layers (own popups) so the
 * interactive strip has the same density as the editor strip: long-press on
 * arrows = HOME/END, Tab popup = Ctrl+C etc. The spec's exit 5 requires
 * run keys to have popups distinct from the editor set.
 */
enum class RunKey {
    SUBMIT, INTERRUPT, TAB, HISTORY_UP, HISTORY_DOWN,
    // Phase 26.1 popup targets (mapped in EditorScreen.handleRunKey)
    HOME, END, PAGE_UP, PAGE_DOWN
}

/** One rendered run-key cap — Phase 26.1 adds popup + swipe. */
data class RunKeyDef(
    val label: String,
    val action: RunKey,
    val wide: Boolean = false,
    val popup: RunKey? = null,
    val popupLabel: String? = null,
    val swipeUp: RunKey? = null,
    val swipeDown: RunKey? = null
)

object RunKeySet {

    /** The interactive-run key set: Enter, Ctrl+C, Tab, then history arrows. */
    val KEYS: List<RunKeyDef> = listOf(
        RunKeyDef("↵ Enter", RunKey.SUBMIT, wide = true, popup = RunKey.PAGE_DOWN, popupLabel = "⇟"),
        RunKeyDef("Ctrl+C", RunKey.INTERRUPT, wide = true, popup = RunKey.PAGE_UP, popupLabel = "⇞"),
        RunKeyDef("Tab", RunKey.TAB, popup = RunKey.INTERRUPT, popupLabel = "Ctrl+C"),
        RunKeyDef("↑", RunKey.HISTORY_UP, popup = RunKey.HOME, popupLabel = "Home"),
        RunKeyDef("↓", RunKey.HISTORY_DOWN, popup = RunKey.END, popupLabel = "End")
    )
}
