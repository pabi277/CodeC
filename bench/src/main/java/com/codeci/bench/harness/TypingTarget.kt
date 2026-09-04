package com.codeci.bench.harness

import android.view.View

/**
 * Phase 25.1 — what a candidate editor exposes to the scenario runner.
 *
 * Each candidate implements this so the SAME scripted scenarios run against
 * all three cores. The keystroke path differs by input mode:
 *  - [com.codeci.bench.harness.InputMode.KEY_EVENTS]: the runner dispatches
 *    synthesized hardware-key events (KeyCharacterMap VIRTUAL_KEYBOARD) into
 *    the focused view — the production hardware-keyboard path.
 *  - [com.codeci.bench.harness.InputMode.DIRECT]: the runner calls
 *    [insertAtCaret], i.e. each candidate's own public content API — the
 *    documented fallback when a core does not consume dispatched keys.
 * The results sheet records which mode produced each row.
 */
interface TypingTarget {
    /** The candidate's root view; the runner dispatches touch/key events here. */
    val view: View

    /** Insert [text] at the current caret through the widget's own edit pipeline (DIRECT mode). */
    fun insertAtCaret(text: String)

    /** Current content length — used to verify typed characters actually landed. */
    fun length(): Int

    /** Best-effort scroll back to the top of the file between repetitions. */
    fun scrollToTop()

    /** Traversal probe for the caret-drag scenario: current first visible line (or -1 if unknown). */
    fun firstVisibleLine(): Int
}

enum class InputMode(val label: String) {
    /** Synthesized hardware-key events into the focused view. */
    KEY_EVENTS("keys"),

    /** Direct content-API inserts at the caret. */
    DIRECT("direct")
}
