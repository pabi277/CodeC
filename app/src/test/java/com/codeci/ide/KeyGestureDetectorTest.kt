package com.codeci.ide

import com.codeci.ide.ui.components.KeyGestureDetector
import org.junit.Assert.assertEquals
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

    @Test
    fun `short tap maps to TAP`() {
        val r = KeyGestureDetector.classify(
            durationMs = 80, dyPx = 0f, dxPx = 0f,
            hasPopup = true, hasSwipeUp = false, hasSwipeDown = false, isArrow = false
        )
        assertEquals(KeyGestureDetector.Result.TAP, r)
    }
}
