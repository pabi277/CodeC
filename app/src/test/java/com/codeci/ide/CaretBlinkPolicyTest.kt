package com.codeci.ide

import com.codeci.ide.ui.editor.CaretBlinkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class CaretBlinkPolicyTest {

    @Test
    fun `typing is solid even when the clock says idle`() {
        assertEquals(CaretBlinkPolicy.Mode.SOLID, CaretBlinkPolicy.mode(10_000, true))
    }

    @Test
    fun `recent edit stays solid through the rearm window`() {
        assertEquals(CaretBlinkPolicy.Mode.SOLID, CaretBlinkPolicy.mode(0, false))
        assertEquals(CaretBlinkPolicy.Mode.SOLID, CaretBlinkPolicy.mode(499, false))
    }

    @Test
    fun `idle caret blinks after the rearm window`() {
        assertEquals(CaretBlinkPolicy.Mode.BLINK, CaretBlinkPolicy.mode(500, false))
        assertEquals(CaretBlinkPolicy.Mode.BLINK, CaretBlinkPolicy.mode(5_000, false))
    }
}
