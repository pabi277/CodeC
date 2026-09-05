package com.codeci.ide.ui.keyboard

import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.editor.EditorKeyDef

/**
 * Phase 28.2 — the keyboard's pure decision layer: what a cap press MEANS.
 * Everything the grid does (shift states, layer cycling, uppercase
 * resolution, popup labels) lives here as pure functions so CI pins the law;
 * the renderer only draws and forwards.
 *
 * The shift law (Unexpected/Floris convention, and what the 28.1 grid
 * omitted on purpose): a letter cap + shift types its uppercase — no
 * per-layout shifted tables, the model derives it. `⬆` tap = shift ONCE
 * (consumed by the next committed edit), `⬆` hold = LOCK, letters tap while
 * locked keeps the lock, everything else never touches the state.
 */
enum class ShiftState { OFF, ONCE, LOCKED }

/** One resolved cap press. The renderer maps these to screen actions. */
sealed interface CapAction {
    /** A document edit for `EditorKeySet.apply` (already shift-resolved). */
    data class Edit(val key: EditorKey) : CapAction
    data class SetLayer(val id: Int) : CapAction
    /** `⬆` tap: OFF ⇄ ONCE. */
    object ToggleShift : CapAction
    /** `⬆` hold: enter/leave LOCKED. */
    object ToggleLock : CapAction
    object Noop : CapAction
}

object KeyboardRouter {

    const val SHIFT_CAP = "⬆"
    const val TO_SYMBOLS_CAP = "SYM"
    const val TO_LETTERS_CAP = "ABC"

    /** The special caps' labels (they never reach the document). */
    private fun specialOf(def: EditorKeyDef): String? = when (def.label) {
        SHIFT_CAP, TO_SYMBOLS_CAP, TO_LETTERS_CAP -> def.label
        else -> null
    }

    /** Tap: shift-resolved edit (or the special cap's own action). */
    fun tapAction(def: EditorKeyDef, shift: ShiftState): CapAction = when (specialOf(def)) {
        SHIFT_CAP -> CapAction.ToggleShift
        TO_SYMBOLS_CAP -> CapAction.SetLayer(KeyboardLayers.SYMBOLS)
        TO_LETTERS_CAP -> CapAction.SetLayer(KeyboardLayers.LETTERS)
        else -> CapAction.Edit(resolveEdit(def.key, shift))
    }

    /** Hold-release on a popup cap = the popup key VERBATIM (`:` on `;`);
     * popups carry symbols/secondaries, never letter cases. The shift cap's
     * hold is the lock toggle. A special cap WITHOUT a popup is a NOOP —
     * a stray long-press must never re-fire the layer action (and an
     * ordinary cap without a popup falls through to its plain key, exactly
     * like the shipped strip's long-letter-press behavior). */
    fun popupAction(def: EditorKeyDef): CapAction = when {
        def.label == SHIFT_CAP -> CapAction.ToggleLock
        def.popup != null -> CapAction.Edit(def.popup)
        specialOf(def) != null -> CapAction.Noop
        else -> CapAction.Edit(def.key)
    }

    /** Flick up/down: the layer key verbatim (shift never applies to them);
     * specials ignore flicks entirely. */
    fun swipeAction(def: EditorKeyDef, up: Boolean): CapAction {
        if (specialOf(def) != null) return CapAction.Noop
        val key = if (up) def.swipeUp else def.swipeDown
        return key?.let { CapAction.Edit(it) } ?: CapAction.Noop
    }

    /**
     * Shift + a single ASCII letter → that letter uppercased. Everything
     * else (pairs, TAB, ⏎, space, symbols, digits, caret, delete) passes
     * through — one derivation rule, no per-layout tables.
     */
    fun resolveEdit(key: EditorKey, shift: ShiftState): EditorKey {
        if (shift == ShiftState.OFF) return key
        return when (key) {
            is EditorKey.Insert ->
                if (key.text.length == 1 && key.text[0].isAsciiLower())
                    EditorKey.Insert(key.text.uppercase()) else key
            else -> key
        }
    }

    /** The label a cap shows RIGHT NOW (letters read uppercase under shift). */
    fun displayLabel(def: EditorKeyDef, shift: ShiftState): String {
        if (shift == ShiftState.OFF) return def.label
        val l = def.label
        return if (l.length == 1 && l[0].isAsciiLower()) l.uppercase() else l
    }

    /** ONCE dies after any committed action except toggling shift itself;
     * LOCKED never dies here. */
    fun shiftAfterAction(current: ShiftState, action: CapAction): ShiftState =
        if (current == ShiftState.ONCE && action is CapAction.Edit) ShiftState.OFF else current

    fun toggleShift(current: ShiftState): ShiftState =
        if (current == ShiftState.OFF) ShiftState.ONCE else ShiftState.OFF

    fun toggleLock(current: ShiftState): ShiftState =
        if (current == ShiftState.LOCKED) ShiftState.OFF else ShiftState.LOCKED

    /**
     * The popup bubble's label for any model key — the strip renders its own
     * copy today; the grid uses this one. Kept here (pure) so the two
     * surfaces can be unified in a follow-up without changing the law.
     */
    fun popupLabel(key: EditorKey): String = when (key) {
        is EditorKey.Insert -> key.text
        is EditorKey.Pair -> key.open + key.close
        is EditorKey.Caret -> when (key.move) {
            EditorKey.Caret.Move.LINE_START -> "Home"
            EditorKey.Caret.Move.LINE_END -> "End"
            EditorKey.Caret.Move.PAGE_UP -> "PgUp"
            EditorKey.Caret.Move.PAGE_DOWN -> "PgDn"
            else -> "•"
        }
        EditorKey.Tab -> "TAB"
        EditorKey.Delete -> "⌫"
        EditorKey.DeleteWord -> "⌫W"
        EditorKey.CommentToggle -> "//"
        EditorKey.GhostAccept -> "TAB ▸"
        EditorKey.GhostAcceptWord -> "▸"
    }

    private fun Char.isAsciiLower(): Boolean = this in 'a'..'z'
}
