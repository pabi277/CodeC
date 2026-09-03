package com.codeci.ide.ui.services

/**
 * Phase 23.1 — the inline stdin line the user is typing for an interactive
 * run. Pure so the submit semantics (send the line, clear it, send nothing
 * for an empty line) are host-tested; [com.codeci.ide.ui.viewmodels.EditorViewModel]
 * mirrors it into `OutputRunState.inputBuffer` so the Output Panel observes a
 * single flow and the buffer survives recomposition/scroll.
 */
class InteractiveInputBuffer(initial: String = "") {

    private var value: String = initial

    /** The current, unsent input line. */
    fun current(): String = value

    /** Replace the input line (a keystroke or a run-key append). */
    fun onChange(text: String) {
        value = text
    }

    /**
     * Return the line to send to the program's stdin and clear the buffer.
     * Returns null for an empty line — pressing Enter with nothing typed
     * sends nothing (same guard the old input row used).
     */
    fun submit(): String? {
        val line = value
        value = ""
        return line.takeIf { it.isNotEmpty() }
    }
}
