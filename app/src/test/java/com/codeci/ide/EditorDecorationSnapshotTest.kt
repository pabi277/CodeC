package com.codeci.ide

import androidx.compose.ui.text.TextRange
import com.codeci.ide.ui.editor.EditorDecorationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDecorationSnapshotTest {

    @Test
    fun `snapshot keeps cursor line and column math with brackets`() {
        val snapshot = EditorDecorationSnapshot.calculate("one\ncall()", TextRange(9))
        assertEquals(2, snapshot.line)
        assertEquals(6, snapshot.column)
        assertEquals(listOf(8..8, 9..9), snapshot.bracketRanges)
        assertTrue(snapshot.currentLineRange != null)
    }

    @Test
    fun `snapshot treats a caret at the newline as the preceding line`() {
        val snapshot = EditorDecorationSnapshot.calculate("abc\ndef", TextRange(3))
        assertEquals(1, snapshot.line)
        assertEquals(4, snapshot.column)
    }
}
