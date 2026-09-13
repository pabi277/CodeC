package com.codeci.ide.ui.editor

/**
 * Phase 48 — nothing hides behind the keyboard (PART_48_1).
 *
 * Owner row: *"If the code is very big it's last line go under the keyboard,
 * when i use a suggestion it go down and hide behind the keyboard."*
 *
 * The mechanism (evidence, read 2026-09-12): the editor's column is already
 * `imePadding()`'d, so when the IME (or CodeC Keys, or the output panel, or
 * the status bar, or the keys row) changes, the sora box is laid out SMALLER
 * — but nothing ever asks sora to bring the caret back into the new
 * viewport. Sora auto-scrolls on SELECTION change and on its own edits
 * (`ScrollEvent.CAUSE_MAKE_POSITION_VISIBLE`); a viewport resize is neither.
 *
 * The policy decides WHEN a re-scroll is owed; the one Android edge
 * (`SoraEditorHost`, the app's single `ensurePositionVisible` call site,
 * pinned by `CaretCallSiteTest`) performs it after the new layout settles.
 *
 * Why the policy is about HEIGHT and not about the five chrome booleans: the
 * booleans are the CAUSE, the height is the EFFECT, and only the effect can
 * hide the caret. Testing the height means a future chrome row (a find bar,
 * a diagnostic strip, anything) is covered without touching this file — the
 * same reason `NavBarPolicy` takes `imeVisible`/`keysVisible` rather than
 * reading them itself.
 */
data class EditorViewport(
    /** The sora box's laid-out height in px (the editor Box, not the column). */
    val heightPx: Int,
    val imeVisible: Boolean,
    val codecKeysVisible: Boolean,
    val stripVisible: Boolean,
    val outputExpanded: Boolean,
    val statusVisible: Boolean,
    val fontSizeSp: Float
) {

    /**
     * The chrome facts the policy cannot see from height alone are kept so
     * the struct is honest and the snapshot test can pin what
     * `EditorScreen` can really produce. One pair is mutually exclusive BY
     * CONSTRUCTION — EditorScreen switches sora's soft input off while CodeC
     * Keys is up (Phase 28.2's IME lever) — so [normalised] collapses it and
     * the struct cannot represent the same on-screen state twice.
     */
    fun normalised(): EditorViewport =
        if (codecKeysVisible) copy(imeVisible = false) else this
}

object CaretVisibilityPolicy {

    /**
     * One-line-of-air rule: the caret must not sit on the last visible row.
     * Sora's `ensurePositionVisible` (line, column) guarantees visibility
     * without a margin argument; this constant records the intent and the
     * honest debt (if sora ever grows a margin parameter, this is its value).
     * The very-short-viewport case — editor + IME + CodeC Keys + expanded
     * output on a 5" screen — degrades the right way for free: if the
     * viewport cannot show the caret plus one line, the caret alone wins,
     * because visibility is what sora's call guarantees (PART_50's device
     * matrix measures it; nothing is guessed here).
     */
    const val KEEP_LINES_BELOW = 1

    /**
     * IME / keys animations report many intermediate sizes; the GROWING
     * direction (chrome yielding the box back) coalesces into one call after
     * this window. A SHRINKING box is urgent — the keyboard is rising over
     * the caret — and is never debounced ([debounceMs] returns 0 there).
     */
    const val RESCROLL_DEBOUNCE_MS = 90L

    /**
     * True when the editor box's height (or its font size) changed since the
     * last observation — the caret may now sit outside the viewport and a
     * re-scroll is owed.
     *
     * Deliberately narrow:
     * - [previous] == null is the FIRST observation (first composition /
     *   first layout): sora placed the caret itself on open, and a forced
     *   scroll on open is the Phase 35.4 quiet-on-open state — never owed.
     * - A chrome boolean flipping while the height stays constant is noise
     *   (a row swapped for a row); the booleans never trigger on their own,
     *   because a rescroll that is not owed is a scroll fight with the
     *   user's hand (README risk 1: scrolled away to read, the IME toggles,
     *   the view must NOT yank back).
     * - Font-size change at constant height fires, because reflow moves
     *   every line under a constant box.
     */
    fun owesRescroll(previous: EditorViewport?, current: EditorViewport): Boolean {
        if (previous == null) return false
        return previous.heightPx != current.heightPx ||
            previous.fontSizeSp != current.fontSizeSp
    }

    /**
     * How long the edge should wait before performing an owed re-scroll.
     * A shrinking box is urgent (0 ms — the caret is about to be covered;
     * the edge still posts AFTER the layout pass, which is the part that
     * makes the call land on the new metrics). A growing box (keyboard
     * closing, panel collapsing) waits one coalescing window so the
     * animation's intermediate frames collapse into a single call — the
     * flutter-quill settle-then-reposition lesson for the identical bug.
     * Total for every input: `previous == null` never owes, and the value
     * for that case keeps the edge's schedule flow simple.
     */
    fun debounceMs(previous: EditorViewport?, current: EditorViewport): Long =
        if (previous != null && current.heightPx < previous.heightPx) 0L
        else RESCROLL_DEBOUNCE_MS
}
