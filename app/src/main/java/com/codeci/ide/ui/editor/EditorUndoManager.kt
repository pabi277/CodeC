package com.codeci.ide.ui.editor

import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.abs

/**
 * Phase 9 — undo/redo command history for the editor.
 *
 * Pure Kotlin (no coroutines, clock passed in explicitly) so the behavior is
 * deterministic and unit-testable on the host. One manager instance is kept
 * per editor tab/file by the [com.codeci.ide.ui.viewmodels.EditorViewModel].
 *
 * Snapshot granularity: each undo step restores a full [TextFieldValue]
 * (text + selection), matching the plan in `docs/chat-phase9/PART_9_EDITOR.md`.
 * Typing runs coalesce: consecutive single-character edits (insert or delete)
 * within [coalesceWindowMs] collapse into ONE undo step so a word typed
 * leaves a single boundary, while a pause or a multi-character edit (paste,
 * find-replace, format) always starts a new step.
 */
class EditorUndoManager(
    private val maxHistory: Int = 100,
    private val coalesceWindowMs: Long = 600L
) {
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var lastEditMs = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Records that [previous] became [current] at [nowMs]. Selection-only
     * changes never create an undo entry. Returns `true` when the edit was
     * coalesced into the previous typing run (no new snapshot pushed).
     */
    fun recordChange(previous: TextFieldValue, current: TextFieldValue, nowMs: Long): Boolean {
        if (previous.text == current.text) return false
        val singleCharEdit = abs(previous.text.length - current.text.length) <= 1
        val withinWindow = nowMs - lastEditMs in 0 until coalesceWindowMs
        val coalesced = singleCharEdit && withinWindow && undoStack.isNotEmpty()
        if (!coalesced) {
            undoStack.addLast(previous)
            if (undoStack.size > maxHistory) undoStack.removeFirst()
        }
        redoStack.clear()
        lastEditMs = nowMs
        return coalesced
    }

    /** Rewinds to the snapshot before the last (coalesced) edit, or null. */
    fun undo(current: TextFieldValue): TextFieldValue? {
        val target = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        if (redoStack.size > maxHistory) redoStack.removeFirst()
        // Break coalescing so the next typed character starts a fresh step.
        lastEditMs = 0L
        return target
    }

    /** Re-applies the last undone state, or null when nothing was undone. */
    fun redo(current: TextFieldValue): TextFieldValue? {
        val target = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        lastEditMs = 0L
        return target
    }

    /** Drops both stacks; called when a file buffer is opened or reloaded. */
    fun reset() {
        undoStack.clear()
        redoStack.clear()
        lastEditMs = 0L
    }

    /** Test helper / status readout. */
    fun undoDepth(): Int = undoStack.size
}
