package com.codeci.ide

import com.codeci.ide.ui.components.KeyGestureDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 26.1 — pure gesture classification host test.
 */
class KeyGestureDetectorTest {

    @Test
    fun `classifies long press as popup when hasPopup`() {
        val r = KeyGestureDetector.classify(
            durationMs = 350, dyPx = 0f, dxPx = 0f,
            hasPopup = true, hasSwipeUp = false, hasSwipeDown = false, isArrow = false
        )
        assertEquals(KeyGestureDetector.Result.POPUP, r)
    }

    @Test
    fun `classifies swipe up when vertical drag dominates`() {
        val r = KeyGestureDetector.classify(
            durationMs = 100, dyPx = -100f, dxPx = 10f,
            hasPopup = false, hasSwipeUp = true, hasSwipeDown = false, isArrow = false
        )
        assertEquals(KeyGestureDetector.Result.SWIPE_UP, r)
    }

    @Test
    fun `classifies hold repeat for arrows after initial delay`() {
        val r = KeyGestureDetector.classify(
            durationMs = 200, dyPx = 0f, dxPx = 0f,
            hasPopup = false, hasSwipeUp = false, hasSwipeDown = false, isArrow = true
        )
        assertEquals(KeyGestureDetector.Result.HOLD_REPEAT, r)
    }

    // ---- Phase 69.3 — a drag that scrolls is never a key --------------------

    @Test
    fun `a tap under the slop is not a scroll`() {
        // The cap must still fire for a real tap, jitter included: 8 dp of
        // slop on a 3x phone is 24 px, and a thumb wobble stays under it.
        assertFalse(KeyGestureDetector.isScrollDx(dxPx = 0f, dyPx = 0f, slopPx = 24f))
        assertFalse(KeyGestureDetector.isScrollDx(dxPx = 10f, dyPx = -6f, slopPx = 24f))
        assertFalse(KeyGestureDetector.isScrollDx(dxPx = 24f, dyPx = 0f, slopPx = 24f))
    }

    @Test
    fun `past the slop the row owns the gesture, in either direction`() {
        // The owner: *"even i want to drag for other keys it's types which
        // ever i am scrolling"*. The row starts scrolling AT the slop, so the
        // cap gives up the tap there too — never 20 dp later.
        assertTrue(KeyGestureDetector.isScrollDx(dxPx = 25f, dyPx = 0f, slopPx = 24f))
        assertTrue(KeyGestureDetector.isScrollDx(dxPx = -25f, dyPx = 0f, slopPx = 24f))
        assertTrue(KeyGestureDetector.isScrollDx(dxPx = 120f, dyPx = 4f, slopPx = 24f))
    }

    @Test
    fun `a vertical drag is a swipe candidate, not a scroll`() {
        assertFalse("the swipe layers still own the vertical drag", KeyGestureDetector.isScrollDx(dxPx = 25f, dyPx = -60f, slopPx = 24f))
        assertTrue("level with the vertical travel, the row still wins", KeyGestureDetector.isScrollDx(dxPx = 25f, dyPx = -25f, slopPx = 24f))
    }

    @Test
    fun `short tap maps to TAP`() {
        val r = KeyGestureDetector.classify(
            durationMs = 80, dyPx = 0f, dxPx = 0f,
            hasPopup = true, hasSwipeUp = false, hasSwipeDown = false, isArrow = false
        )
        assertEquals(KeyGestureDetector.Result.TAP, r)
    }
}
