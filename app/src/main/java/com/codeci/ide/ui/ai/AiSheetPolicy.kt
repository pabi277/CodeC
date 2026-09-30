package com.codeci.ide.ui.ai

import com.codeci.ide.ui.editor.OutputPanelHeight

/** Where the chat sheet stands. HIDDEN = only the bubble shows. */
enum class AiSheetState { HIDDEN, HALF, FULL }

enum class AiSheetEvent {
    /** The bubble was tapped (or "Open AI chat" pressed). */
    TAP_BUBBLE,

    /** ⤢ — more room. */
    EXPAND,

    /** The drag-down / step-down gesture: FULL → HALF, HALF → HIDDEN. */
    COLLAPSE,

    /** ▾ — put it away: straight to the bubble from anywhere. */
    MINIMIZE,

    /** System Back: one step down, FULL → HALF → HIDDEN. */
    BACK,

    /** A drag on the handle ended; the resulting height fraction is passed alongside. */
    DRAG_END
}

/**
 * Owner Q3 (*"When Build i will test whatever looking good i will select"*):
 * what opening the chat does when the Output panel is open. BOTH are built;
 * the owner keeps one on the device and the other is deleted before merge.
 */
enum class AiOutputConflict {
    /** A — chat takes the bottom slot (HALF); the Output panel returns on minimize. */
    REPLACE_OUTPUT,

    /** B — chat opens FULL; the Output panel stays untouched underneath. */
    OPEN_FULL
}

/**
 * Phase 77.2 — the chat sheet's state machine and height, as pure code
 * (host-tested by `AiSheetPolicyTest`). The sheet is a **surface**, not a
 * route: nothing here touches navigation, and minimizing never clears the
 * exchange (D6 — only New question / project switch / process death do).
 */
object AiSheetPolicy {

    /** A drag ending above this share of the room asks for FULL. */
    const val FULL_AT = 0.75f

    /** …and below this share asks to be put away. Between the two: HALF. */
    const val HIDE_BELOW = 0.20f

    /**
     * While a finger drags HALF upward the drawn height stops here (the
     * release decides FULL): the code box beneath is never squeezed to nothing
     * mid-drag.
     */
    const val LIVE_DRAG_MAX = 0.80f

    /** What the device round starts on for [AiOutputConflict]. */
    val DEFAULT_CONFLICT = AiOutputConflict.REPLACE_OUTPUT

    /**
     * The transition table.
     * ```text
     * TAP_BUBBLE  HIDDEN -> HALF              (otherwise unchanged)
     * EXPAND      HALF   -> FULL
     * COLLAPSE    FULL   -> HALF, HALF -> HIDDEN
     * MINIMIZE    any    -> HIDDEN
     * BACK        FULL   -> HALF, HALF -> HIDDEN, HIDDEN stays (Back is not ours)
     * DRAG_END    fraction > FULL_AT -> FULL, < HIDE_BELOW -> HIDDEN, else HALF
     *             (a null / non-finite fraction changes nothing; never from HIDDEN)
     * ```
     */
    fun next(state: AiSheetState, event: AiSheetEvent, dragFraction: Float? = null): AiSheetState = when (event) {
        AiSheetEvent.TAP_BUBBLE -> if (state == AiSheetState.HIDDEN) AiSheetState.HALF else state
        AiSheetEvent.EXPAND -> if (state == AiSheetState.HALF) AiSheetState.FULL else state
        AiSheetEvent.COLLAPSE, AiSheetEvent.BACK -> when (state) {
            AiSheetState.FULL -> AiSheetState.HALF
            AiSheetState.HALF -> AiSheetState.HIDDEN
            AiSheetState.HIDDEN -> AiSheetState.HIDDEN
        }
        AiSheetEvent.MINIMIZE -> AiSheetState.HIDDEN
        AiSheetEvent.DRAG_END -> when {
            state == AiSheetState.HIDDEN || dragFraction == null || !dragFraction.isFinite() -> state
            dragFraction > FULL_AT -> AiSheetState.FULL
            dragFraction < HIDE_BELOW -> AiSheetState.HIDDEN
            else -> AiSheetState.HALF
        }
    }

    /**
     * Opening while the Output panel is [outputOpen]. With it closed the
     * answer is always HALF, in either variant.
     * Returns the sheet state to open in, and whether the Output panel must
     * make room (be hidden while the sheet is up; it returns on minimize).
     */
    fun openWithOutput(conflict: AiOutputConflict, outputOpen: Boolean): Pair<AiSheetState, Boolean> = when {
        !outputOpen -> AiSheetState.HALF to false
        conflict == AiOutputConflict.REPLACE_OUTPUT -> AiSheetState.HALF to true
        else -> AiSheetState.FULL to false
    }

    /**
     * The Output panel is taken off screen while the sheet is open (either
     * variant: under B it sits "underneath" the FULL sheet, under A the sheet
     * has its slot). Its own expanded flag is never changed, so it comes back
     * exactly as it was.
     */
    fun outputVisible(outputExpanded: Boolean, sheet: AiSheetState): Boolean =
        outputExpanded && sheet == AiSheetState.HIDDEN

    /**
     * HALF's height: the SAME rule as the Output panel (40 % by default, 55 %
     * cap, 38 % with the keyboard up, the 160 floor) — not a second model.
     */
    fun halfHeight(available: Float, imeVisible: Boolean): Float =
        OutputPanelHeight.resolve(OutputPanelHeight.defaultFor(available), available, imeVisible)

    /**
     * The sheet's height after a handle drag, as a share of the room, for
     * [next]'s DRAG_END. [dragDy] is positive downward.
     */
    fun heightFractionAfterDrag(currentHeight: Float, dragDy: Float, available: Float): Float {
        if (!currentHeight.isFinite() || !dragDy.isFinite() || !available.isFinite() || available <= 0f) return Float.NaN
        return ((currentHeight - dragDy) / available).coerceIn(0f, 1f)
    }
}
