package com.codeci.ide

import com.codeci.ide.ui.editor.CaretAction
import com.codeci.ide.ui.editor.CaretPlacementPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaretPlacementPolicyTest {

    @Test
    fun `open starts quiet with no caret`() {
        val result = CaretPlacementPolicy.reduce("hello\nworld", true, CaretAction.Open)
        assertFalse(result.placed)
        assertEquals(null, result.offset)
    }

    @Test
    fun `tap keeps the exact landed offset`() {
        val result = CaretPlacementPolicy.reduce("hello\nworld", false, CaretAction.TapAt(8))
        assertTrue(result.placed)
        assertEquals(8, result.offset)
    }

    @Test
    fun `tap is clamped to the buffer`() {
        assertEquals(0, CaretPlacementPolicy.reduce("abc", false, CaretAction.TapAt(-4)).offset)
        assertEquals(3, CaretPlacementPolicy.reduce("abc", false, CaretAction.TapAt(99)).offset)
    }

    @Test
    fun `first key places at the end of line one`() {
        val result = CaretPlacementPolicy.reduce("abc\ndef", false, CaretAction.KeyPress)
        assertTrue(result.placed)
        assertEquals(3, result.offset)
    }

    @Test
    fun `key on an already placed caret does not move it`() {
        val result = CaretPlacementPolicy.reduce("abc\ndef", true, CaretAction.KeyPress)
        assertTrue(result.placed)
        assertEquals(null, result.offset)
    }
}
