package com.codeci.ide.ui.editor

import com.codeci.ide.ui.utils.LanguageType

/**
 * Phase 23.2 — which keys the editor's strip should show. The editor's
 * language keys and the interactive-run keys are different shapes (buffer
 * edits vs. VM actions), so the context decides which set — and the screen
 * renders one strip from the sealed result below.
 */
sealed class KeysContext {
    /** The editor is the active surface: show its per-language key set. */
    data class Editor(val language: LanguageType?) : KeysContext()

    /** An interactive run is waiting for input: show the run key set. */
    object InteractiveRun : KeysContext()

    /** No keys shown (the strip is hidden by the existing visibility toggle). */
    object Idle : KeysContext()
}

/** The resolved key data for a [KeysContext] — one kind, never a mix. */
sealed class KeysForContext {
    object None : KeysForContext()
    data class EditorKeys(val defs: List<EditorKeyDef>) : KeysForContext()
    data class RunKeys(val defs: List<RunKeyDef>) : KeysForContext()
}

/**
 * Phase 23.2 — map a [KeysContext] to the concrete key set. Pure so the
 * three-way decision (editor / interactive run / none) is host-tested; the
 * screen renders exactly what this returns.
 */
fun keysForContext(context: KeysContext, customSnippets: String? = null, storedJson: String? = null): KeysForContext =
    when (context) {
        is KeysContext.Editor ->
            KeysForContext.EditorKeys(EditorKeySet.keysFor(context.language, customSnippets, storedJson))
        KeysContext.InteractiveRun -> KeysForContext.RunKeys(RunKeySet.KEYS)
        KeysContext.Idle -> KeysForContext.None
    }
