package com.codeci.ide.ui.editor

/**
 * Phase 23.2 — the extra-keys strip for interactive runs. When the Output
 * Panel's inline input (Phase 23.1) is live, the keys row swaps its editor
 * keys for this run set: submit the line, interrupt the program, insert a
 * tab, and (future) REPL history navigation. Pure and data-driven so the
 * exact set is host-tested; the `↑`/`↓` history keys are present but no-op
 * stubs until REPL history exists (D2).
 */
enum class RunKey { SUBMIT, INTERRUPT, TAB, HISTORY_UP, HISTORY_DOWN }

/** One rendered run-key cap. */
data class RunKeyDef(
    val label: String,
    val action: RunKey,
    val wide: Boolean = false
)

object RunKeySet {

    /** The interactive-run key set: Enter, Ctrl+C, Tab, then history arrows. */
    val KEYS: List<RunKeyDef> = listOf(
        RunKeyDef("↵ Enter", RunKey.SUBMIT, wide = true),
        RunKeyDef("Ctrl+C", RunKey.INTERRUPT, wide = true),
        RunKeyDef("Tab", RunKey.TAB),
        RunKeyDef("↑", RunKey.HISTORY_UP),
        RunKeyDef("↓", RunKey.HISTORY_DOWN)
    )
}
