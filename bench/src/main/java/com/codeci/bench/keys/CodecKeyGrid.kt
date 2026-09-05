package com.codeci.bench.keys

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.editor.EditorKeySet

/**
 * Phase 28.1 — the spike key-grid model, PURE (Compose text data classes only,
 * no Android runtime) so the exact press → edit math is host-tested in CI.
 *
 * Scope law for the spike (spec §2.1): a 3-row letters grid plus DEL, space,
 * ⏎ and TAB — nothing else. No popups, no swipe layers, no shift state, no
 * symbol/digit caps: those are 28.2's layout engine and would only add noise
 * to the ONE thing 28.1 measures (IME-free commit latency + feel).
 *
 * Every non-DEL cap routes through the REAL app model (`EditorKeySet.apply`,
 * mirrored verbatim into this module) — the spike therefore exercises the
 * production edit semantics, not a re-implementation of them.
 */
data class GridKeycap(
    /** What the cap shows and what the tap echo logs (e.g. "q", "DEL"). */
    val label: String,
    /** The app-model key; null when [backspace] is the action (see below). */
    val key: EditorKey? = null,
    /**
     * DEL is the one spike cap with no `EditorKey` twin — the app model has no
     * backspace key because the system IME owns deletion today. A full
     * keyboard (28.2) will settle DEL's real model home; until then the spike
     * models it as this explicit flag so `commit()` never fakes an EditorKey.
     */
    val backspace: Boolean = false,
    /** Wide cap (space bar). */
    val wide: Boolean = false,
    /**
     * Cap holds with 26.1's shared timers (150 ms initial / 40 ms repeat).
     * Only DEL repeats in the spike — holding a letter is a typo, not a
     * feature, until 28.2 decides per-cap repeat policy.
     */
    val holdRepeat: Boolean = false
) {
    companion object {
        fun letter(c: Char) = GridKeycap(c.toString(), EditorKey.Insert(c.toString()))
        val TAB = GridKeycap("TAB", EditorKey.Tab)
        val ENTER = GridKeycap("⏎", EditorKey.Insert("\n"))
        val SPACE = GridKeycap("space", EditorKey.Insert(" "), wide = true)
        val DEL = GridKeycap("DEL", backspace = true, holdRepeat = true)
    }
}

object CodecKeyGrid {

    /** The spike commits against the same indent law as the app default. */
    const val TAB_SIZE = 4

    /** Labels reserved for the special row (everything else is a letter). */
    private val SPECIALS: Map<String, GridKeycap> = listOf(
        GridKeycap.TAB, GridKeycap.SPACE, GridKeycap.ENTER, GridKeycap.DEL
    ).associateBy { it.label }

    /** The three letter rows, QWERTY. 26 letters, no shift state (spike scope). */
    val letterRows: List<List<GridKeycap>> = listOf(
        "qwertyuiop", "asdfghjkl", "zxcvbnm"
    ).map { row -> row.map { GridKeycap.letter(it) } }

    /** The special row: TAB, DEL, ⏎, a wide space. */
    val specialRow: List<GridKeycap> =
        listOf(GridKeycap.TAB, GridKeycap.DEL, GridKeycap.ENTER, GridKeycap.SPACE)

    /** Grid as rendered: 3 letter rows + special row. */
    fun rows(): List<List<GridKeycap>> = letterRows + listOf(specialRow)

    /** Label lookup used by the scripted runner (tap label → cap). */
    fun find(label: String): GridKeycap? =
        SPECIALS[label] ?: label.singleOrNull()?.lowercase()?.let { c ->
            letterRows.asSequence().flatten().firstOrNull { it.label == c }
        }

    /** All caps in the grid, in visual order. */
    fun allCaps(): List<GridKeycap> = rows().flatten()

    /**
     * Apply one cap to the buffer at [value]. Letters/⏎/space/TAB go through
     * `EditorKeySet.apply` verbatim (TAB inserts [TAB_SIZE] spaces, an insert
     * replaces any selection and lands after itself). DEL is the spike-local
     * backspace: selection first, else the single character before the caret.
     */
    fun commit(cap: GridKeycap, value: TextFieldValue): TextFieldValue {
        if (cap.backspace) return backspace(value)
        val key = cap.key ?: return value
        return EditorKeySet.apply(key, value, TAB_SIZE)
    }

    /** Backspace math — mirrors what sora's own delete path does for the K2 core. */
    fun backspace(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val start = minOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
        val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
        val to = if (start != end) start else (start - 1).coerceAtLeast(0)
        return TextFieldValue(text.substring(0, to) + text.substring(end), TextRange(to))
    }

    /**
     * The document text produced by pressing [caps] in order into an empty
     * buffer — the pure oracle the device runs are audited against (and the
     * thing the host tests pin). Only meaningful for caret-typing scripts.
     */
    fun expectedText(caps: List<GridKeycap>): String {
        var value = TextFieldValue("")
        for (cap in caps) value = commit(cap, value)
        return value.text
    }
}
