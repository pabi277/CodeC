package com.codeci.ide

import com.codeci.ide.ui.terminal.AltScroll
import com.codeci.ide.ui.terminal.MouseModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 104.3 — alternate-screen scroll decisions (pure). */
class AltScrollTest {

    @Test
    fun `alt-scroll is active only on the alternate screen with mode 1007 and no mouse tracking`() {
        assertTrue(AltScroll.active(altScreen = true, mouseMode = MouseModes.ALT_SCROLL))
        assertFalse(AltScroll.active(altScreen = false, mouseMode = MouseModes.ALT_SCROLL))
        assertFalse(AltScroll.active(altScreen = true, mouseMode = 0))
        // Mouse tracking owns the wheel, so 1007 does not apply.
        assertFalse(AltScroll.active(altScreen = true, mouseMode = MouseModes.ALT_SCROLL or MouseModes.NORMAL))
    }

    @Test
    fun `a pixel delta under one row carries as remainder and emits nothing`() {
        val s = AltScroll.steps(deltaPx = 5f, remainderPx = 0f, cellH = 20f)
        assertEquals(0, s.rows)
        assertEquals(5f, s.remainderPx, 0.001f)
    }

    @Test
    fun `remainder accumulates across events until a whole row`() {
        val a = AltScroll.steps(12f, 0f, 20f)
        val b = AltScroll.steps(12f, a.remainderPx, 20f)
        assertEquals(0, a.rows)
        assertEquals(1, b.rows)
        assertEquals(4f, b.remainderPx, 0.001f)
    }

    @Test
    fun `positive deltas go down and negative go up, symmetric about zero`() {
        assertEquals(2, AltScroll.steps(45f, 0f, 20f).rows)
        assertEquals(-2, AltScroll.steps(-45f, 0f, 20f).rows)
        assertEquals(-5f, AltScroll.steps(-45f, 0f, 20f).remainderPx, 0.001f)
    }

    @Test
    fun `a non-positive cell height emits nothing and clears the remainder`() {
        val s = AltScroll.steps(100f, 7f, 0f)
        assertEquals(0, s.rows)
        assertEquals(0f, s.remainderPx, 0f)
    }
}
