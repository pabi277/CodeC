package com.codeci.ide.ui.editor

import com.codeci.ide.ui.theme.CodecTokens

/**
 * Phase 51.2 — the editor chrome gets declared slots.
 *
 * The evidence (2026-09-21, `main` @ `0f1b650`): the editor column is assembled
 * ad hoc. `EditorScreen.kt` calls `EditorTabBar` (~:1259), the RUN action
 * (~:1612), `SoraEditorHost` (~:1765), `EditorStatusBar` (~:1894), the output
 * panel (~:1955), the suggestion strip (~:2386) and the keys row (~:2402) with
 * nothing anywhere declaring what the chrome *is*. Phase 50.1 gave every gap a
 * token; nothing said which gaps belong to which band, so the next edit is again
 * a hand-picked number — and every hand-picked number in the chrome is a change
 * to the code view's height, which is the one measurement Phase 48's
 * `CaretVisibilityPolicy` keys on.
 *
 * This object is that declaration: the slots in the order `EditorScreen.kt`
 * declares them, the token gap that opens each one, and a marker comment each
 * slot carries (`// Phase 51.2 slot: <name>`) so `EditorChromeSlotTest` can pin
 * the order from the real source.
 *
 * Phase 57.1 — the declaration follows the shots: the top row is the file's
 * mark + name + 🔍 + green ▶, the tab row is a row of its own below it, and the
 * ⋮'s list lives in the tab row's trailing cell. So the run action is declared
 * before the tab row, and the code view is one row shorter while tabs are open.
 *
 * **51.2 declared; 57.1 moved a piece — and the declaration is how that stayed
 * visible.** 51.2 added no chrome height and moved no composable: the slots and
 * the comment markers pinned where each band began, and the gaps were the token
 * steps 50.1 had already applied. The tab row's move out of the app bar (57.1)
 * is exactly the edit the declaration was built to catch: it changes the code
 * view's height by one row when tabs exist, and it re-orders the markers — both
 * recorded above rather than discovered on a device. Phase 48's caret policy
 * takes the *measured* box (`CaretVisibilityPolicy` reads `heightPx`), so a new
 * row is a re-measure, never a stale constant.
 */
enum class EditorChromeSlot(val marker: String) {
    /**
     * Phase 57.1 order note: the tab row moved OUT of the app bar (the shots
     * lay the tabs out as a row of their own, under the file's name), so the
     * RUN action — still in the bar's `actions` — is now declared first.
     */
    RUN_ACTION("run_action"),
    TAB_BAR("tab_bar"),
    FIND_BAR("find_bar"),
    CODE_VIEW("code_view"),
    STATUS_BAR("status_bar"),
    OUTPUT_PANEL("output_panel"),
    SUGGESTION_STRIP("suggestion_strip"),
    KEYS_ROW("keys_row"),
}

object EditorChrome {

    /** The comment every slot carries in `EditorScreen.kt`. */
    const val MARKER_PREFIX = "Phase 51.2 slot:"

    /** The slots, in the order the screen declares them (top of file first). */
    val order: List<EditorChromeSlot> = listOf(
        EditorChromeSlot.RUN_ACTION,
        EditorChromeSlot.TAB_BAR,
        EditorChromeSlot.FIND_BAR,
        EditorChromeSlot.CODE_VIEW,
        EditorChromeSlot.STATUS_BAR,
        EditorChromeSlot.OUTPUT_PANEL,
        EditorChromeSlot.SUGGESTION_STRIP,
        EditorChromeSlot.KEYS_ROW,
    )

    /** The marker text for a slot, e.g. `Phase 51.2 slot: run_action`. */
    fun markerFor(slot: EditorChromeSlot): String = "$MARKER_PREFIX ${slot.marker}"

    /**
     * The 50.1 token gap that opens a slot, as a `CodecTokens.Space` step.
     * Values are steps of the scale (never raw dp) — `CodecTokens.Space` is the
     * only scale the app has, and `EditorChromeSlotTest` pins every value here
     * against it by name, so an off-scale number cannot be typed in later.
     */
    fun gapFor(slot: EditorChromeSlot): Float = when (slot) {
        EditorChromeSlot.TAB_BAR -> CodecTokens.Space.NONE
        EditorChromeSlot.RUN_ACTION -> CodecTokens.Space.XS
        EditorChromeSlot.FIND_BAR -> CodecTokens.Space.NONE
        EditorChromeSlot.CODE_VIEW -> CodecTokens.Space.NONE
        EditorChromeSlot.STATUS_BAR -> CodecTokens.Space.NONE
        EditorChromeSlot.OUTPUT_PANEL -> CodecTokens.Space.NONE
        EditorChromeSlot.SUGGESTION_STRIP -> CodecTokens.Space.S
        EditorChromeSlot.KEYS_ROW -> CodecTokens.Space.XS
    }
}
