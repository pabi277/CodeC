package com.codeci.ide.ui.editor

/**
 * Phase 27.3 — ONE file that owns the accept/dismiss law so the three
 * completion surfaces (ghost, strip, panel) can never disagree. Every cell
 * of the matrix below is a host test.
 *
 * The invariants (the phase's guarding design law):
 *  1. Enter is sacred: on a soft keyboard it is NEVER consumed for completion
 *     (only the sora panel's OWN hardware-key handler may accept a navigated
 *     item in browse mode — the panel is then visibly a browsing surface).
 *  2. TAB is dual-mood but never ambiguous: the cap reads "TAB ▸" when tap
 *     accepts and plain "TAB" when it indents; long-press is ALWAYS raw
 *     indent. The look tells the truth — [tabCapLabel] is the single source.
 *  3. Dismissal is per-identifier (see StripContext.dismissedAnchor) and is
 *     re-armed when the caret crosses an identifier boundary.
 *  4. The master Settings switch removes ALL completion chrome (nothing
 *     residual, nothing computed) — see the VM pipeline's everythingOff path.
 */

/** Settings snapshot for the completion pipeline (DataStore-backed; §1.3). */
data class CompletionSettings(
    val master: Boolean = true,
    val ghost: Boolean = true,
    val strip: Boolean = true,
    val panel: Boolean = true,
    val debounceMs: Long = 120L
) {
    /** Master off ⇒ feature gone ENTIRELY (invariant 4). */
    val everythingOff: Boolean get() = !master
    val anyOn: Boolean get() = master && (ghost || strip || panel)
}

/** The mutually-exclusive surface currently showing (the matrix's columns). */
enum class CompletionSurface {
    /** No completion chrome. */
    NONE,

    /** Ghost visible, strip still in key mode (zero or one candidate). */
    GHOST_ONLY,

    /** Ghost + suggestion chips (multi-candidate). */
    STRIP,

    /** The sora floating panel is open as explicit "⌄ more" browse mode. */
    PANEL
}

/** Inputs the matrix arbitrates (strip caps AND hardware keyboards). */
enum class CompletionInput {
    ENTER_SOFT, TAB_TAP, TAB_LONG, ARROW_RIGHT, ARROW_LEFT, ARROW_UP, ARROW_DOWN,
    ESCAPE, UNMATCHED_CHAR
}

enum class CompletionAction {
    NEWLINE, ACCEPT_FULL, ACCEPT_WORD, INDENT, MOVE_CARET,
    REJECT_GHOST, DISMISS_STRIP, CLOSE_PANEL, INSERT_CHAR, BROWSE_PANEL, NOTHING
}

object CompletionPolicy {

    /**
     * Resolve the visible surface from raw state bits. Panel trumps strip,
     * strip trumps ghost-only; a single candidate is served by the ghost
     * alone (S1: multi-candidate is when the strip helps).
     */
    fun surfaceFor(
        ghostVisible: Boolean,
        candidateCount: Int,
        stripEnabled: Boolean,
        panelBrowsing: Boolean,
        hasSelection: Boolean
    ): CompletionSurface = when {
        panelBrowsing -> CompletionSurface.PANEL
        hasSelection -> CompletionSurface.NONE
        stripEnabled && candidateCount >= 2 -> CompletionSurface.STRIP
        ghostVisible -> CompletionSurface.GHOST_ONLY
        else -> CompletionSurface.NONE
    }

    /** The matrix. Every return is a pinned test case in CompletionPolicyTest. */
    fun decide(surface: CompletionSurface, input: CompletionInput): CompletionAction = when (input) {
        // Invariant 1 — Enter is sacred on soft keyboards: always a newline.
        CompletionInput.ENTER_SOFT -> CompletionAction.NEWLINE
        CompletionInput.TAB_TAP -> when (surface) {
            CompletionSurface.NONE -> CompletionAction.INDENT
            CompletionSurface.GHOST_ONLY, CompletionSurface.STRIP -> CompletionAction.ACCEPT_FULL
            CompletionSurface.PANEL -> CompletionAction.NOTHING // sora panel owns Tab while browsing
        }
        CompletionInput.TAB_LONG -> CompletionAction.INDENT // accessibility escape hatch, every surface
        CompletionInput.ARROW_RIGHT -> when (surface) {
            CompletionSurface.GHOST_ONLY, CompletionSurface.STRIP -> CompletionAction.ACCEPT_WORD
            else -> CompletionAction.MOVE_CARET
        }
        CompletionInput.ARROW_LEFT -> CompletionAction.MOVE_CARET
        CompletionInput.ARROW_UP, CompletionInput.ARROW_DOWN -> when (surface) {
            CompletionSurface.PANEL -> CompletionAction.BROWSE_PANEL // panel navigates items
            else -> CompletionAction.MOVE_CARET // the strip is never arrow-targeted
        }
        CompletionInput.ESCAPE -> when (surface) {
            CompletionSurface.GHOST_ONLY -> CompletionAction.REJECT_GHOST
            CompletionSurface.STRIP -> CompletionAction.DISMISS_STRIP
            CompletionSurface.PANEL -> CompletionAction.CLOSE_PANEL
            CompletionSurface.NONE -> CompletionAction.NOTHING
        }
        CompletionInput.UNMATCHED_CHAR -> when (surface) {
            // The char is never swallowed; any open chrome clears (S3).
            else -> CompletionAction.INSERT_CHAR
        }
    }

    /** Invariant 2 — the TAP label of the TAB cap; long-press is always indent. */
    fun tabCapLabel(surface: CompletionSurface): String = when (surface) {
        CompletionSurface.GHOST_ONLY, CompletionSurface.STRIP -> "TAB ▸"
        else -> "TAB"
    }

    /** Truth-telling label for the → cap in accept-word mode. */
    fun rightCapLabel(surface: CompletionSurface): String = when (surface) {
        CompletionSurface.GHOST_ONLY, CompletionSurface.STRIP -> "→▸"
        else -> "→"
    }
}
