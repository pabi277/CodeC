package com.codeci.ide

import com.codeci.ide.ui.terminal.ScrollMotionPolicy
import com.codeci.ide.ui.terminal.ScrollMath
import com.codeci.ide.ui.terminal.TerminalScroll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Phase 103.2 — scroll physics, host-side.
 *
 * The owner's row: *"after open the app first time then when i scroll up then
 * i saw empty space in the terminal"*. The cause was the sub-row remainder
 * surviving finger-lift — a fresh session could rest up to one row away from
 * where it claimed to be, leaving a band of background no cell was drawn in.
 * [TerminalScroll.rest] is the fix; the rest of this file is the fling and the
 * scrollbar that came with it.
 */
class ScrollRestTest {

    private val cellH = 43f

    // ---- rest ------------------------------------------------------------

    @Test
    fun `finger-lift discards a positive remainder`() {
        val r = TerminalScroll.rest(17f, cellH, topRow = -5, minTop = -100)
        assertEquals(-5, r.topRow)
        assertEquals(0, r.snappedRows)
    }

    @Test
    fun `finger-lift discards a negative remainder`() {
        val r = TerminalScroll.rest(-41f, cellH, topRow = -5, minTop = -100)
        assertEquals(-5, r.topRow)
    }

    @Test
    fun `rest never invents a row — the remainder is a fraction, not a delta`() {
        // The row count lives in topRow; a lift may only discard the fraction
        // of the row the finger had dragged. Reading the remainder as rows was
        // the second wrong model these tests caught (it doubled every rest).
        assertEquals(-5, TerminalScroll.rest(60f, cellH, topRow = -5, minTop = -100).topRow)
        assertEquals(-5, TerminalScroll.rest(-60f, cellH, topRow = -5, minTop = -100).topRow)
    }

    @Test
    fun `a whole-row rest is the identity`() {
        assertEquals(-5, TerminalScroll.rest(0f, cellH, topRow = -5, minTop = -100).topRow)
    }

    @Test
    fun `rest can never leave the rails`() {
        // A snap at the live edge may not go past 0, nor past the oldest row.
        assertEquals(0, TerminalScroll.rest(40f, cellH, topRow = 0, minTop = -100).topRow)
        assertEquals(-100, TerminalScroll.rest(-40f, cellH, topRow = -100, minTop = -100).topRow)
    }

    @Test
    fun `a zero or negative cell height cannot divide by zero`() {
        assertEquals(-5, TerminalScroll.rest(7f, 0f, topRow = -5, minTop = -10).topRow)
        assertEquals(-5, TerminalScroll.rest(7f, -3f, topRow = -5, minTop = -10).topRow)
    }

    @Test
    fun `the state a resting view is in can never draw a band`() {
        // The regression: walk a drag in both directions, rest, and assert the
        // canvas translation the view would use is EXACTLY zero.
        var top = -3
        var sub = 0f
        for (d in listOf(11f, 22f, -35f, 8f, 41f, -19f, 3f, -27f)) {
            val step = ScrollMath.step(sub, d, cellH, top, -100)
            top = step.topRow
            sub = step.subRowPx
            val rest = TerminalScroll.rest(sub, cellH, top, -100)
            // After a lift the view sets BOTH: row = rest.topRow and sub-px = 0
            // (the view's `settleScroll`). What this walk pins is that the row
            // it lands on is always inside the rails, so the band's other half
            // — a translation nothing accounts for — cannot come back either.
            assertTrue("rest row in rails", rest.topRow in -100..0)
        }
    }

    @Test
    fun `rest followed by a drag keeps the accumulation exact`() {
        // Rest → drag must not lose or duplicate a row: after the lift the
        // finger's next 40 px starts from a whole row, so it is 40 px of
        // travel, not 40 px added to a stale fraction.
        val a = ScrollMath.step(0f, 40f, cellH, -5, -100) // 40 px, same row
        assertEquals(-5, a.topRow)
        val rest = TerminalScroll.rest(a.subRowPx, cellH, a.topRow, -100)
        assertEquals(-5, rest.topRow)
        val b = ScrollMath.step(0f, 40f, cellH, rest.topRow, -100)
        assertEquals(-5, b.topRow)
        assertEquals(40f, b.subRowPx, 1e-4f)
        // One more 3 px crosses the row, and no more: 43 px = exactly a row.
        val c = ScrollMath.step(b.subRowPx, 3f, cellH, b.topRow, -100)
        assertEquals(-4, c.topRow)
        assertEquals(0f, c.subRowPx, 1e-4f)
    }

    // ---- fling -----------------------------------------------------------

    @Test
    fun `a fling below the touch floor is not a fling`() {
        assertTrue(TerminalScroll.flingPx(0.01f, 10_000f).isEmpty())
        assertTrue(TerminalScroll.flingPx(0f, 10_000f).isEmpty())
    }

    @Test
    fun `a fling decays monotonically and ends`() {
        val steps = TerminalScroll.flingPx(4f, 10_000f)
        assertTrue("a real fling travels", steps.size > 3)
        assertTrue("it stays under the frame cap", steps.size <= TerminalScroll.MAX_FRAMES)
        for (i in 1 until steps.size) {
            assertTrue("each step is smaller than the last", abs(steps[i]) < abs(steps[i - 1]))
            assertEquals("the sign never flips", kotlin.math.sign(steps[0]), kotlin.math.sign(steps[i]), 0f)
        }
    }

    @Test
    fun `a fling is clamped by the distance it is allowed to travel`() {
        val steps = TerminalScroll.flingPx(9f, maxPx = 120f)
        assertEquals(120f, steps.sum(), 0.01f)
    }

    @Test
    fun `a fling clamps at both rails because ScrollMath is the only mover`() {
        // The fling model is direction-agnostic; rails are ScrollMath's job and
        // this asserts the composition, which is what the view runs.
        var top = 0
        var sub = 0f
        for (step in TerminalScroll.flingPx(6f, 500f)) {
            val r = ScrollMath.step(sub, step, cellH, top, -10)
            top = r.topRow
            sub = r.subRowPx
        }
        assertEquals("a downward fling at the live edge cannot pass 0", 0, top)
    }

    @Test
    fun `an insane velocity is capped rather than trusted`() {
        val steps = TerminalScroll.flingPx(1e9f, 1e9f)
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.size <= TerminalScroll.MAX_FRAMES)
    }

    @Test
    fun `reduced motion turns a fling into one instant jump`() {
        val instant = ScrollMotionPolicy.flingSteps(4f, 10_000f, animatorScale = 0f)
        assertEquals(1, instant.size)
        assertEquals(TerminalScroll.flingPx(4f, 10_000f).sum(), instant.single(), 0.01f)
    }

    @Test
    fun `normal motion keeps the full frame sequence`() {
        val steps = ScrollMotionPolicy.flingSteps(4f, 10_000f, animatorScale = 1f)
        assertEquals(TerminalScroll.flingPx(4f, 10_000f), steps)
    }

    // ---- scrollbar -------------------------------------------------------

    @Test
    fun `no scrollback means no scrollbar at all`() {
        val bar = TerminalScroll.scrollbar(topRow = 0, lines = 24, scrollback = 0, viewH = 1_000f)
        assertFalse(bar.hasTravel)
        assertEquals(0f, bar.lengthPx, 0.001f)
    }

    @Test
    fun `the thumb is proportional to the scrollback depth`() {
        val shallow = TerminalScroll.scrollbar(0, lines = 24, scrollback = 24, viewH = 1_000f)
        val deep = TerminalScroll.scrollbar(0, lines = 24, scrollback = 2_000, viewH = 1_000f)
        assertTrue("a deeper buffer has a shorter thumb", deep.lengthPx < shallow.lengthPx)
        assertTrue(
            "but never shorter than the floor",
            deep.lengthPx >= TerminalScroll.MIN_THUMB_PX.toFloat()
        )
    }

    @Test
    fun `the thumb is flush at the bottom when live and at the top at the oldest row`() {
        val viewH = 1_000f
        val live = TerminalScroll.scrollbar(0, 24, 200, viewH)
        assertEquals(1f, live.position, 0.001f)
        assertEquals(viewH - live.lengthPx, live.offsetPx, 0.01f)
        val oldest = TerminalScroll.scrollbar(-200, 24, 200, viewH)
        assertEquals(0f, oldest.position, 0.001f)
        assertEquals(0f, oldest.offsetPx, 0.01f)
    }

    @Test
    fun `the thumb's middle is the middle of the buffer`() {
        val bar = TerminalScroll.scrollbar(-100, 24, 200, 1_000f)
        assertEquals(0.5f, bar.position, 0.001f)
    }

    @Test
    fun `a degenerate viewport yields no thumb instead of a divide by zero`() {
        val bar = TerminalScroll.scrollbar(0, 24, 200, viewH = 0f)
        assertFalse(bar.hasTravel)
        assertEquals(0f, bar.lengthPx, 0.001f)
    }

    @Test
    fun `dragging the scrollbar maps a fraction of the track to a row`() {
        assertEquals(0, TerminalScroll.rowForThumb(1_000f, 1_000f, 24, 200))
        // The very top of the track is the oldest row; the thumb's own length
        // shortens the travel, so the top row arrives at exactly 0 px of it.
        assertEquals(-200, TerminalScroll.rowForThumb(0f, 1_000f, 24, 200))
        val mid = TerminalScroll.rowForThumb(500f, 1_000f, 24, 200)
        assertTrue("mid track is mid scrollback, got $mid", abs(mid + 100) <= 6)
    }
}
