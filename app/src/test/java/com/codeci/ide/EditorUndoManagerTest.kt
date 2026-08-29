package com.codeci.ide

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.editor.EditorUndoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 9 — undo/redo history semantics (`docs/chat-phase9/PART_9_EDITOR.md` §2.1). */
class EditorUndoManagerTest {

    private fun tv(text: String, cursor: Int = text.length) =
        TextFieldValue(text, TextRange(cursor))

    @Test
    fun `selection-only changes create no undo step`() {
        val manager = EditorUndoManager()
        val coalesced = manager.recordChange(tv("abc", 0), tv("abc", 3), 1_000)
        assertFalse(manager.canUndo)
        assertFalse(coalesced)
    }

    @Test
    fun `distinct edits push snapshots and undo restores the previous text`() {
        val manager = EditorUndoManager()
        manager.recordChange(tv(""), tv("a", 1), 1_000)
        manager.recordChange(tv("a"), tv("ab"), 5_000) // outside the coalesce window
        assertEquals(2, manager.undoDepth())
        assertTrue(manager.canUndo)

        val afterFirst = manager.undo(tv("ab"))
        assertEquals("a", afterFirst?.text)
        val afterSecond = manager.undo(tv("a"))
        assertEquals("", afterSecond?.text)
        assertNull(manager.undo(tv("")))
    }

    @Test
    fun `typing burst within the window leaves one undo boundary`() {
        val manager = EditorUndoManager()
        manager.recordChange(tv(""), tv("w"), 1_000)
        assertTrue(manager.recordChange(tv("w"), tv("wo"), 1_100)) // coalesced
        assertTrue(manager.recordChange(tv("wo"), tv("wor"), 1_150)) // coalesced
        assertEquals(1, manager.undoDepth())
        assertEquals("", manager.undo(tv("wor"))?.text)
    }

    @Test
    fun `multi-character edit never coalesces`() {
        val manager = EditorUndoManager()
        manager.recordChange(tv(""), tv("w"), 1_000)
        manager.recordChange(tv("w"), tv("w pasted-text"), 1_050) // paste mid-burst
        assertEquals(2, manager.undoDepth())
    }

    @Test
    fun `new edits clear the redo stack and redo restores them`() {
        val manager = EditorUndoManager()
        manager.recordChange(tv("x"), tv("xy"), 1_000)
        val undone = manager.undo(tv("xy"))
        assertEquals("x", undone?.text)
        assertTrue(manager.canRedo)
        val redone = manager.redo(tv("x"))
        assertEquals("xy", redone?.text)
        assertFalse(manager.canRedo)

        manager.recordChange(tv("xy"), tv("xyz"), 9_000)
        assertFalse(manager.canRedo)
    }

    @Test
    fun `undo restores the recorded caret position`() {
        val manager = EditorUndoManager()
        manager.recordChange(tv("", 0), tv("h", 1), 1_000)
        manager.recordChange(tv("h", 1), tv("he", 2), 5_000)
        val restored = manager.undo(tv("he"))
        assertEquals("h", restored?.text)
        assertEquals(1, restored?.selection?.start)
    }

    @Test
    fun `history is capped and the oldest snapshots are dropped`() {
        val manager = EditorUndoManager(maxHistory = 4)
        var time = 10_000L
        repeat(6) { index ->
            manager.recordChange(tv("$index"), tv("$index-x"), time)
            time += 5_000
        }
        // Six snapshots pushed, cap keeps the newest four: "2", "3", "4", "5".
        assertEquals(4, manager.undoDepth())
        assertEquals("5", manager.undo(tv("6"))?.text)
        assertEquals("4", manager.undo(tv("5"))?.text)
        assertEquals("3", manager.undo(tv("4"))?.text)
        assertEquals("2", manager.undo(tv("3"))?.text)
        assertEquals(0, manager.undoDepth())
    }

    @Test
    fun `reset drops both stacks`() {
        val manager = EditorUndoManager()
        manager.recordChange(tv("a"), tv("ab"), 1_000)
        manager.undo(tv("ab"))
        assertTrue(manager.canRedo)
        manager.reset()
        assertFalse(manager.canRedo)
        assertFalse(manager.canUndo)
    }
}
