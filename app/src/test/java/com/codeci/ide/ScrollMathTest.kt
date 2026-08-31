package com.codeci.ide

import com.codeci.ide.ui.terminal.ScrollMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 19.2 device round 3 — pixel-smooth scrolling. Whole rows move
 * topRow; the sub-row remainder (px) is kept for canvas translation. Wrong
 * accumulation = jumps or drift, so the arithmetic is locked here.
 */
class ScrollMathTest {

    private val cellH = 43f

    @Test
    fun `sub-row drags accumulate without moving a row`() {
        // Two 20px drags = 40px < 43px: same row, remainder grows.
        val r1 = ScrollMath.step(0f, 20f, cellH, -5, -100)
        assertEquals(-5, r1.topRow)
        assertEquals(20f, r1.subRowPx, 1e-4f)
        val r2 = ScrollMath.step(r1.subRowPx, 20f, cellH, r1.topRow, -100)
        assertEquals(-5, r2.topRow)
        assertEquals(40f, r2.subRowPx, 1e-4f)
    }

    @Test
    fun `crossing a row boundary moves exactly one row and keeps the rest`() {
        val r = ScrollMath.step(40f, 10f, cellH, -5, -100)
        assertEquals(-4, r.topRow)
        assertEquals(7f, r.subRowPx, 1e-4f)
    }

    @Test
    fun `negative drags scroll into history`() {
        val r = ScrollMath.step(0f, -50f, cellH, -5, -100)
        assertEquals(-6, r.topRow)
        assertEquals(-7f, r.subRowPx, 1e-4f)
    }

    @Test
    fun `rails drop the overshoot`() {
        // At live edge (0) dragging toward newer clamps and zeroes.
        ScrollMath.step(0f, 90f, cellH, 0, -100).let {
            assertEquals(0, it.topRow)
            assertEquals(0f, it.subRowPx, 1e-4f)
        }
        // At the oldest row (-100) dragging older clamps and zeroes.
        ScrollMath.step(-10f, -80f, cellH, -100, -100).let {
            assertEquals(-100, it.topRow)
            assertEquals(0f, it.subRowPx, 1e-4f)
        }
    }

    @Test
    fun `fling-sized jumps absorb many rows at once`() {
        val r = ScrollMath.step(0f, 430f, cellH, -50, -100)
        assertEquals(-40, r.topRow)
        assertEquals(0f, r.subRowPx, 1e-4f)
    }

    @Test
    fun `remainder stays within one cell height`() {
        // Deterministic walk in both directions — invariant holds throughout.
        val deltas = listOf(37f, -53f, 21f, 67f, -11f, -89f, 44f, 5f, -30f, 60f)
        var topRow = -50
        var sub = 0f
        for (d in deltas) {
            val r = ScrollMath.step(sub, d, cellH, topRow, -100)
            topRow = r.topRow
            sub = r.subRowPx
            assertTrue("remainder in (-cellH, cellH)", sub > -cellH && sub < cellH)
            assertTrue("topRow in [-100, 0]", topRow in -100..0)
        }
    }
}
