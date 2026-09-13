package com.codeci.ide.ui.editor

/**
 * Phase 48 device round (2026-09-13, owner: *"when i use app dedicate keyboard
 * and typing it's blinking the full code"*) — the plan that turns a small
 * VM-driven buffer change into ONE incremental sora edit instead of a
 * wholesale `setText`.
 *
 * Why this exists (evidence, read against the pinned sora 0.24.6 tag): the
 * replay path used `CodeEditor.setText` for every programmatic edit — every
 * CodeC Keys keystroke, keys-row tap, snippet and ghost accept. `setText`
 * (CodeEditor.java:3951 → the `setText(text, true, null)` overload) builds a
 * brand-new Content, resets the analyze manager (a FULL re-tokenize — the
 * syntax colors flash), rebuilds the layout asynchronously (LineBreakLayout's
 * TaskMonitor re-measures every row — big files draw empty rows until it
 * lands), resets the render context, restarts the input method and
 * invalidates every render node. On a large file that is the whole code
 * blinking once per keystroke. Normal typing never does any of this: sora's
 * own input path edits the Content in place (`Content.insert/delete/replace`)
 * and every listener — the layout, the analyzer, our bridge — processes a
 * DELTA (`ACTION_INSERT` / `ACTION_DELETE`), which LineBreakLayout applies
 * incrementally.
 *
 * So the host now asks this pure planner for a delta between the text sora
 * holds and the text the VM holds. A plan within the budget is applied with
 * ONE `Content.replace(start, end, replacement)` — sora's own replace routes
 * through `deleteInternal` + `insertInternal`, the exact primitives typing
 * uses. No plan (first replay, a formatter rewrite, replace-all, an empty
 * editor being filled) falls back to the atomic `setText`, whose
 * wholesale-replacement crash story (2026-09-06, this file) is untouched.
 */
data class IncrementalEditPlan(
    /** Start index in the OLD text (inclusive). */
    val start: Int,
    /** End index in the OLD text (exclusive) — equal to [start] for a pure insert. */
    val end: Int,
    /** The text that replaces `oldText[start until end]` — empty for a pure delete. */
    val replacement: String
)

object IncrementalEdit {

    /**
     * The largest change still worth applying as one Content delta. A
     * keystroke is 1-2 chars, an auto-indent newline ~5, a snippet ~200; a
     * formatter rewrite or a replace-all touches thousands and is a single
     * visible event anyway, where the atomic path's full rebuild costs
     * nothing extra and carries its own (well-tested) semantics. 2048 keeps
     * every per-keystroke-shaped edit incremental and everything else honest.
     */
    const val MAX_AFFECTED_CHARS = 2048

    /**
     * The minimal delta turning [oldText] into [newText], or `null` when the
     * change is too big (or trivial) for the incremental path and the caller
     * must use the atomic fallback.
     *
     * The classic common-prefix/common-suffix walk: the prefix and suffix are
     * maximal and never overlap (the suffix walk is bounded by
     * `min(old, new) - prefix`), so `start <= end` always holds and the
     * plan is exactly `oldText[start until end] -> replacement`. A pure
     * insert has `start == end`; a pure delete has an empty replacement.
     */
    fun between(oldText: String, newText: String): IncrementalEditPlan? {
        if (oldText == newText) return null
        val minLen = minOf(oldText.length, newText.length)
        var prefix = 0
        while (prefix < minLen && oldText[prefix] == newText[prefix]) prefix++
        val maxSuffix = minOf(oldText.length - prefix, newText.length - prefix)
        var suffix = 0
        while (suffix < maxSuffix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) suffix++
        val start = prefix
        val end = oldText.length - suffix
        val replacement = newText.substring(prefix, newText.length - suffix)
        if ((end - start) + replacement.length > MAX_AFFECTED_CHARS) return null
        return IncrementalEditPlan(start, end, replacement)
    }
}
