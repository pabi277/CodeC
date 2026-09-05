package com.codeci.ide.ui.keyboard

import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.editor.EditorKeyDef

/**
 * Phase 28.2 — the CodeC Keys layout engine, PURE (Compose text data classes
 * only, no Android runtime) so every rule below is host-tested in CI.
 *
 * Design law (spec §1.1): the layout EXTENDS the 26.1 keycap model, never
 * forks it. A [KeycapModel] wraps an `EditorKeyDef` — so every cap on the
 * full keyboard carries the same edit semantics, popup and swipe layers as a
 * strip cap, and user strip edits (26.1) are understood by the same parser.
 * The two additions live HERE, not in the fork: a width weight (the plan's
 * "rows define weighted caps") and the hold-repeat policy.
 */
data class KeycapModel(
    val def: EditorKeyDef,
    /** Relative column width within the row (1 = a unit cap). */
    val widthWeight: Float = 1f,
    /** Hold with the 26.1 shared timers (150 ms initial / 40 ms repeat). */
    val repeat: Boolean = false
) {
    companion object {

        /** Wide caps read ~1.6 units; the space bar is a full thumb run. */
        fun weightFor(def: EditorKeyDef): Float = when {
            def.label == "space" -> 5f
            def.label == "SYM" || def.label == "ABC" -> 1.8f
            def.label == "⬆" -> 1.5f
            def.key is EditorKey.Caret -> 1.15f
            def.key is EditorKey.Delete || def.key is EditorKey.DeleteWord -> 1.6f
            def.wide -> 1.6f
            else -> 1f
        }

        /**
         * Repeat policy of the shipped layouts (exit condition 4): DEL and
         * the arrows travel with 26.1's shared timers — the same set the
         * strip repeats, so hold behavior is one law across surfaces.
         */
        fun repeatFor(def: EditorKeyDef): Boolean =
            def.key is EditorKey.Delete || def.key is EditorKey.Caret
    }
}

/**
 * One rendered keyboard: rows of caps at one height scale. `layout` objects
 * are what the JSON codec encodes and the renderer draws — nothing about a
 * layout is hardcoded pixels (spec §1: "No layout is hardcoded pixels").
 */
data class KeyboardLayout(
    val rows: List<List<KeycapModel>>,
    val heightScale: Float = HEIGHT_SCALE_DEFAULT
) {
    init {
        // The Settings slider clamps the same way a corrupt file can't:
        // a layout always renders inside the shared height budget.
        require(rows.isNotEmpty()) { "a keyboard needs at least one row" }
    }

    val heightScaleClamped: Float get() = heightScale.coerceIn(HEIGHT_SCALE_MIN, HEIGHT_SCALE_MAX)

    fun allCaps(): List<KeycapModel> = rows.flatten()

    companion object {
        const val HEIGHT_SCALE_DEFAULT = 1f
        const val HEIGHT_SCALE_MIN = 0.7f
        const val HEIGHT_SCALE_MAX = 1.3f

        /** Build from key defs, deriving weights/repeat from the model rules. */
        fun of(rows: List<List<EditorKeyDef>>, heightScale: Float = HEIGHT_SCALE_DEFAULT): KeyboardLayout =
            KeyboardLayout(
                rows.map { row ->
                    row.map { def ->
                        KeycapModel(def, KeycapModel.weightFor(def), KeycapModel.repeatFor(def))
                    }
                },
                heightScale
            )
    }
}

/**
 * The two shipped layers (spec §1.2): the letters layer and the dense
 * symbols layer one tap away. Kept as ints, not a sealed type, because the
 * JSON says `"layer":N` and user layouts may name their own — the UI only
 * cycles (SYM ⇄ ABC).
 */
object KeyboardLayers {
    const val LETTERS = 0
    const val SYMBOLS = 1
}
