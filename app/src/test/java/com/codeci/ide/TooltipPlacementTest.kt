package com.codeci.ide

import com.codeci.ide.ui.guide.GuideRect
import com.codeci.ide.ui.guide.GuideSize
import com.codeci.ide.ui.guide.TooltipPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 45.2 — where the coach mark's card goes. Pure maths, so the three
 * vertical cases (anchor at the top, in the middle, at the bottom) and the two
 * horizontal clamps are pinned here instead of being eyeballed on one phone in
 * one orientation (PART_45_2: tablets, landscape and split screen are a named
 * risk — the plan must not hard-code positions).
 *
 * Units are opaque: the Android edge passes window pixels, these cases use a
 * 360x640 "dp-like" box and a 640x360 landscape one because the ratios are what
 * the rule is about.
 */
class TooltipPlacementTest {

    private val portrait = GuideSize(width = 360f, height = 640f)
    private val landscape = GuideSize(width = 640f, height = 360f)
    private val tooltip = GuideSize(width = 260f, height = 120f)
    private val gap = 12f
    private val margin = 16f

    private fun place(
        anchor: GuideRect,
        screen: GuideSize = portrait,
        tip: GuideSize = tooltip
    ) = TooltipPlacement.place(anchor, screen, tip, gap, margin)

    @Test
    fun `an anchor near the top puts the card below it`() {
        val p = place(GuideRect(left = 16f, top = 40f, right = 96f, bottom = 100f))
        assertTrue(p.below)
        assertEquals(112.0, p.top.toDouble(), 0.001) // anchor.bottom + gap
        // Centred on the anchor, then clamped to the left margin: the anchor
        // hugs the left edge, so the card cannot be centred.
        assertEquals(16.0, p.left.toDouble(), 0.001)
    }

    @Test
    fun `an anchor in the middle puts the card below it and centred`() {
        val p = place(GuideRect(left = 140f, top = 300f, right = 220f, bottom = 340f))
        assertTrue(p.below)
        assertEquals(352.0, p.top.toDouble(), 0.001)
        assertEquals(50.0, p.left.toDouble(), 0.001) // centerX 180 - half the card width
    }

    @Test
    fun `an anchor near the bottom puts the card above it`() {
        val p = place(GuideRect(left = 140f, top = 560f, right = 220f, bottom = 620f))
        assertFalse(p.below)
        assertEquals(428.0, p.top.toDouble(), 0.001) // anchor.top - gap - card height
        assertEquals(50.0, p.left.toDouble(), 0.001)
    }

    @Test
    fun `when neither side fits the card is clamped inside the screen`() {
        val tall = GuideSize(width = 260f, height = 600f)
        val p = place(GuideRect(left = 140f, top = 300f, right = 220f, bottom = 340f), tip = tall)
        assertFalse("it did not fit below", p.below)
        assertEquals(24.0, p.top.toDouble(), 0.001) // screen.height - card - margin
        assertTrue(p.top >= margin)
        assertTrue(p.top + tall.height <= portrait.height + margin)
    }

    @Test
    fun `the card never hangs off the right edge`() {
        val p = place(GuideRect(left = 320f, top = 300f, right = 380f, bottom = 340f))
        // centerX 350 - 130 = 220, but the right-most legal left is
        // 360 - 260 - 16 = 84.
        assertEquals(84.0, p.left.toDouble(), 0.001)
        assertTrue(p.left + tooltip.width <= portrait.width - margin + 0.001f)
    }

    @Test
    fun `the card never hangs off the left edge`() {
        val p = place(GuideRect(left = 0f, top = 300f, right = 40f, bottom = 340f))
        assertEquals(16.0, p.left.toDouble(), 0.001)
        assertTrue(p.left >= margin)
    }

    @Test
    fun `a landscape box flips the same anchor to above`() {
        val p = place(
            GuideRect(left = 300f, top = 300f, right = 380f, bottom = 340f),
            screen = landscape
        )
        assertFalse("340 + 12 + 120 does not fit in 360", p.below)
        assertEquals(168.0, p.top.toDouble(), 0.001)
        assertEquals(210.0, p.left.toDouble(), 0.001) // centerX 340 - 130, inside 640 - 260 - 16
    }

    @Test
    fun `a card wider than the screen is pinned to the margin, never negative`() {
        val wide = GuideSize(width = 700f, height = 120f)
        val p = place(
            GuideRect(left = 300f, top = 40f, right = 380f, bottom = 100f),
            screen = landscape,
            tip = wide
        )
        assertEquals(16.0, p.left.toDouble(), 0.001)
        assertTrue(p.left >= 0f)
    }

    @Test
    fun `a degenerate anchor still places without dividing by zero`() {
        // The registry never publishes an empty rect, but the maths must not
        // depend on that: a zero-size anchor at the origin behaves like a point.
        val p = place(GuideRect(left = 0f, top = 0f, right = 0f, bottom = 0f))
        assertTrue(p.below)
        assertEquals(12.0, p.top.toDouble(), 0.001)
        assertEquals(16.0, p.left.toDouble(), 0.001)
    }

    @Test
    fun `the placement is a pure function of its inputs`() {
        val anchor = GuideRect(left = 140f, top = 300f, right = 220f, bottom = 340f)
        assertEquals(place(anchor), place(anchor))
        assertEquals(
            place(anchor),
            TooltipPlacement.place(anchor, portrait, tooltip, gap, margin)
        )
    }
}
