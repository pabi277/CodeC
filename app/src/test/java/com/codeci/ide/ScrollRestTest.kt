package com.codeci.ide

import com.codeci.ide.ui.terminal.ScrollMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 103.2 — rest position, fling, rails and the scrollbar thumb. These
 * are the pure numbers behind the owner's "blank band on first open" and
 * "fling with inertia" rows.
 */
class ScrollRestTest {

    private val cellH = 43f

    @Test
    fun `lifting the finger discards a positive or negative sub-row remainder`() {
        assertEquals(0f, ScrollMath.rest(topRow = -5, subRowPx = 12.5f, minTop = -100).subRowPx, 1e-4f)
        assertEquals(0f, ScrollMath.rest(topRow = -5, subRowPx = -31f, minTop = -100).subRowPx, 1e-4f)
        assertEquals(-5, ScrollMath.rest(topRow = -5, subRowPx = -31f, minTop = -100).topRow)
    }

    @Test
    fun `rest is the identity for a whole-row position`() {
        val r = ScrollMath.rest(topRow = -3, subRowPx = 0f, minTop = -10)
        assertEquals(-3, r.topRow)
        assertEquals(0f, r.subRowPx, 1e-4f)
    }

    @Test
    fun `rest clamps to the rails`() {
        assertEquals(-100, ScrollMath.rest(topRow = -200, subRowPx = 0f, minTop = -100).topRow)
        assertEquals(0, ScrollMath.rest(topRow = 7, subRowPx = 0f, minTop = -100).topRow)
    }

    @Test
    fun `a fresh terminal with no history never keeps a blank-band remainder`() {
        // The owner's first-open bug: dragging "older" with nothing to scroll
        // left a negative remainder and drew an empty band at the top.
        val r = ScrollMath.step(subRowPx = 0f, deltaPx = -10f, cellH = cellH, topRow = 0, minTop = 0)
        assertEquals(0, r.topRow)
        assertEquals(0f, r.subRowPx, 1e-4f)
    }

    @Test
    fun `the live edge drops a remainder that points past it`() {
        val r = ScrollMath.step(subRowPx = 0f, deltaPx = 10f, cellH = cellH, topRow = 0, minTop = -100)
        assertEquals(0, r.topRow)
        assertEquals(0f, r.subRowPx, 1e-4f)
    }

    @Test
    fun `the oldest row drops a remainder that points past it`() {
        val r = ScrollMath.step(subRowPx = 0f, deltaPx = -10f, cellH = cellH, topRow = -100, minTop = -100)
        assertEquals(-100, r.topRow)
        assertEquals(0f, r.subRowPx, 1e-4f)
    }

    @Test
    fun `a slow lift is a stop, not a fling`() {
        assertNull(ScrollMath.planFling(velocityPxPerS = 40f, animatorScale = 1f))
        assertNull(ScrollMath.planFling(velocityPxPerS = -149f, animatorScale = 1f))
    }

    @Test
    fun `a fast lift flings in its own direction with exponential travel`() {
        val up = ScrollMath.planFling(velocityPxPerS = 2000f, animatorScale = 1f)
        assertNotNull(up)
        assertEquals(2000f * ScrollMath.FLING_TAU_S, up!!.totalPx, 1e-2f)
        assertTrue(up.durationMs > 0L)
        val down = ScrollMath.planFling(velocityPxPerS = -2000f, animatorScale = 1f)!!
        assertTrue(down.totalPx < 0f)
    }

    @Test
    fun `a wild flick is clamped so it cannot travel thousands of rows`() {
        val f = ScrollMath.planFling(velocityPxPerS = 1_000_000f, animatorScale = 1f)!!
        assertEquals(ScrollMath.FLING_MAX_PX_PER_S * ScrollMath.FLING_TAU_S, f.totalPx, 1e-1f)
    }

    @Test
    fun `reduced motion turns the fling into an instant jump`() {
        val f = ScrollMath.planFling(velocityPxPerS = 2000f, animatorScale = 0f)!!
        assertEquals(0L, f.durationMs)
        assertEquals(2000f * ScrollMath.FLING_TAU_S, ScrollMath.flingOffset(f, elapsedMs = 0L), 1e-2f)
    }

    @Test
    fun `fling travel is monotonic and lands exactly on its total`() {
        val f = ScrollMath.planFling(velocityPxPerS = 3000f, animatorScale = 1f)!!
        var last = 0f
        for (t in 0L..f.durationMs step 16L) {
            val p = ScrollMath.flingOffset(f, t)
            assertTrue(p >= last - 1e-3f)
            last = p
        }
        assertEquals(f.totalPx, ScrollMath.flingOffset(f, f.durationMs + 500L), 1e-3f)
    }

    @Test
    fun `a fling integrated frame by frame stops at the oldest row without overshoot`() {
        val minTop = -5
        val f = ScrollMath.planFling(velocityPxPerS = -8000f, animatorScale = 1f)!!
        var topRow = -3
        var sub = 0f
        var applied = 0f
        var t = 0L
        while (t <= f.durationMs + 16L) {
            val travelled = ScrollMath.flingOffset(f, t)
            val r = ScrollMath.step(sub, travelled - applied, cellH, topRow, minTop)
            applied = travelled
            topRow = r.topRow
            sub = r.subRowPx
            assertTrue("never past the oldest row", topRow >= minTop)
            assertTrue("never past the live edge", topRow <= 0)
            if (topRow == minTop) break
            t += 16L
        }
        assertEquals(minTop, topRow)
        assertEquals(0f, sub, 1e-4f)
    }

    @Test
    fun `thumb is absent without history`() {
        assertNull(ScrollMath.thumb(trackPx = 480f, scrollbackCount = 0, rows = 24, topRow = 0, minLengthPx = 32f))
    }

    @Test
    fun `thumb length is the visible share and its offset follows the scroll position`() {
        val track = 480f
        val top = ScrollMath.thumb(track, scrollbackCount = 24, rows = 24, topRow = -24, minLengthPx = 32f)!!
        val mid = ScrollMath.thumb(track, scrollbackCount = 24, rows = 24, topRow = -12, minLengthPx = 32f)!!
        val live = ScrollMath.thumb(track, scrollbackCount = 24, rows = 24, topRow = 0, minLengthPx = 32f)!!
        assertEquals(240f, top.lengthPx, 1e-3f)
        assertEquals(0f, top.offsetPx, 1e-3f)
        assertEquals(120f, mid.offsetPx, 1e-3f)
        assertEquals(240f, live.offsetPx, 1e-3f)   // bottom of the free track
    }

    @Test
    fun `a very deep history keeps a grabbable thumb`() {
        val t = ScrollMath.thumb(480f, scrollbackCount = 100_000, rows = 24, topRow = 0, minLengthPx = 32f)!!
        assertEquals(32f, t.lengthPx, 1e-3f)
    }
}
