package com.codeci.ide.ui.editor

/** User action that can establish a caret in a newly opened buffer. */
sealed interface CaretAction {
    data object Open : CaretAction
    data class TapAt(val offset: Int) : CaretAction
    data object KeyPress : CaretAction
}

data class CaretPlacement(
    val placed: Boolean,
    val offset: Int?
)

/**
 * Phase 35.4 — opening a file is deliberately not an editor interaction.
 * The first tap owns its exact offset; a key press gets a deterministic,
 * useful origin so a keyboard can never edit a nowhere-caret.
 */
object CaretPlacementPolicy {

    fun reduce(
        text: String,
        wasPlaced: Boolean,
        action: CaretAction
    ): CaretPlacement = when (action) {
        CaretAction.Open -> CaretPlacement(placed = false, offset = null)
        is CaretAction.TapAt -> CaretPlacement(
            placed = true,
            offset = action.offset.coerceIn(0, text.length)
        )
        CaretAction.KeyPress -> {
            if (wasPlaced) {
                CaretPlacement(placed = true, offset = null)
            } else {
                // End of the first visual line: predictable and never a
                // hidden jump into the middle of a large file.
                CaretPlacement(placed = true, offset = text.indexOf('\n').let { if (it < 0) text.length else it })
            }
        }
    }
}
