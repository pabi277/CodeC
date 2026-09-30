package com.codeci.ide

import com.codeci.ide.ui.ai.AiAvailability
import com.codeci.ide.ui.ai.AiBubbleEdge
import com.codeci.ide.ui.ai.AiBubblePolicy
import com.codeci.ide.ui.ai.AiBubblePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 77.1 — the floating AI button's geometry and visibility, on the host JVM. */
class AiBubblePolicyTest {

    private val w = 400f
    private val h = 800f
    private val bubble = 48f
    private val margin = 8f

    @Test
    fun `a release in the left half snaps left and in the right half snaps right`() {
        assertEquals(AiBubbleEdge.LEFT, AiBubblePolicy.snapToEdge(10f, 300f, w, h).edge)
        assertEquals(AiBubbleEdge.LEFT, AiBubblePolicy.snapToEdge(199.9f, 300f, w, h).edge)
        assertEquals(AiBubbleEdge.RIGHT, AiBubblePolicy.snapToEdge(390f, 300f, w, h).edge)
    }

    @Test
    fun `the exact middle goes right`() {
        assertEquals(AiBubbleEdge.RIGHT, AiBubblePolicy.snapToEdge(200f, 300f, w, h).edge)
    }

    @Test
    fun `the height becomes a fraction of the area`() {
        assertEquals(0.5f, AiBubblePolicy.snapToEdge(10f, 400f, w, h).yFraction, 0.0001f)
    }

    @Test
    fun `a release above the top or below the bottom is clamped into range`() {
        assertEquals(AiBubblePolicy.MIN_Y_FRACTION, AiBubblePolicy.snapToEdge(10f, -500f, w, h).yFraction, 0.0001f)
        assertEquals(AiBubblePolicy.MAX_Y_FRACTION, AiBubblePolicy.snapToEdge(10f, 5000f, w, h).yFraction, 0.0001f)
    }

    @Test
    fun `garbage or an empty area snaps to the default instead of crashing`() {
        assertEquals(AiBubblePolicy.DEFAULT, AiBubblePolicy.snapToEdge(Float.NaN, 10f, w, h))
        assertEquals(AiBubblePolicy.DEFAULT, AiBubblePolicy.snapToEdge(10f, 10f, 0f, h))
        assertEquals(AiBubblePolicy.DEFAULT, AiBubblePolicy.snapToEdge(10f, 10f, w, -1f))
    }

    @Test
    fun `the fraction survives a different area height - rotation and the keyboard`() {
        val p = AiBubblePosition(AiBubbleEdge.LEFT, 0.5f)
        val tall = AiBubblePolicy.offsetPx(p, w, 800f, null, bubble, margin)
        val short = AiBubblePolicy.offsetPx(p, w, 400f, null, bubble, margin)
        assertEquals(800f * 0.5f - bubble / 2f, tall.second, 0.001f)
        assertEquals(400f * 0.5f - bubble / 2f, short.second, 0.001f)
        assertEquals(margin, tall.first, 0.001f)
    }

    @Test
    fun `the right edge rests one margin inside the area`() {
        val x = AiBubblePolicy.offsetPx(AiBubblePosition(AiBubbleEdge.RIGHT, 0.5f), w, h, null, bubble, margin).first
        assertEquals(w - bubble - margin, x, 0.001f)
    }

    @Test
    fun `the keyboard lifts the bubble so it sits fully above it`() {
        val p = AiBubblePosition(AiBubbleEdge.RIGHT, 0.95f)
        val imeTop = 500f
        val (_, y) = AiBubblePolicy.offsetPx(p, w, h, imeTop, bubble, margin)
        assertTrue("bubble bottom ${y + bubble} must be <= $imeTop", y + bubble <= imeTop + 0.001f)
        assertEquals(imeTop - bubble, y, 0.001f)
    }

    @Test
    fun `a bubble already above the keyboard is not moved`() {
        val p = AiBubblePosition(AiBubbleEdge.LEFT, 0.2f)
        val free = AiBubblePolicy.offsetPx(p, w, h, null, bubble, margin)
        assertEquals(free, AiBubblePolicy.offsetPx(p, w, h, 700f, bubble, margin))
    }

    @Test
    fun `a tiny landscape area with the keyboard still returns an in-bounds offset`() {
        val (x, y) = AiBubblePolicy.offsetPx(AiBubblePosition(AiBubbleEdge.RIGHT, 0.95f), 300f, 40f, 30f, bubble, margin)
        assertTrue(x in 0f..300f)
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun `encode and decode round-trip every side`() {
        for (edge in AiBubbleEdge.values()) {
            val p = AiBubblePosition(edge, 0.62f)
            assertEquals("${edge.name}:0.620", AiBubblePolicy.encode(p))
            assertEquals(p, AiBubblePolicy.decode(AiBubblePolicy.encode(p)))
        }
    }

    @Test
    fun `decode turns garbage into the default and clamps a finite out-of-range height`() {
        for (bad in listOf(null, "", "  ", "UP:2", "LEFT:NaN", "LEFT", "LEFT:0.5:1", "right:0.5", "LEFT:abc", "LEFT:Infinity")) {
            assertEquals("'$bad'", AiBubblePolicy.DEFAULT, AiBubblePolicy.decode(bad))
        }
        assertEquals(AiBubblePolicy.MAX_Y_FRACTION, AiBubblePolicy.decode("LEFT:7").yFraction, 0.0001f)
        assertEquals(AiBubblePolicy.MIN_Y_FRACTION, AiBubblePolicy.decode("RIGHT:-3").yFraction, 0.0001f)
    }

    @Test
    fun `move to other side keeps the height and flips the edge`() {
        val p = AiBubblePosition(AiBubbleEdge.LEFT, 0.3f)
        assertEquals(AiBubblePosition(AiBubbleEdge.RIGHT, 0.3f), p.otherSide())
        assertEquals(p, p.otherSide().otherSide())
    }

    @Test
    fun `the bubble shows only when ready, switched on and the sheet is closed`() {
        assertTrue(AiBubblePolicy.visible(AiAvailability.READY, showSetting = true, sheetOpen = false))
        // no-nag: no key yet / no project → never
        assertFalse(AiBubblePolicy.visible(AiAvailability.NEEDS_KEY, showSetting = true, sheetOpen = false))
        assertFalse(AiBubblePolicy.visible(AiAvailability.NEEDS_PROJECT, showSetting = true, sheetOpen = false))
        assertFalse(AiBubblePolicy.visible(AiAvailability.READY, showSetting = false, sheetOpen = false))
        assertFalse(AiBubblePolicy.visible(AiAvailability.READY, showSetting = true, sheetOpen = true))
    }
}
