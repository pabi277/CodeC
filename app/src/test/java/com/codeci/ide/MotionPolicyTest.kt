package com.codeci.ide

import com.codeci.ide.ui.theme.CodecMotion
import com.codeci.ide.ui.theme.MotionInput
import com.codeci.ide.ui.theme.MotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50.4 — Android's "remove animations" switch (and TalkBack's
 * reduce-motion signal) make the app genuinely instant; everything else
 * animates on the shared budget.
 */
class MotionPolicyTest {

    @Test
    fun `a zero animator scale is instant`() {
        val specs = MotionPolicy.specsFor(MotionInput(0f, reduceMotion = false))
        assertSame(MotionPolicy.INSTANT, specs)
        assertFalse(specs.useSpring)
    }

    @Test
    fun `reduce motion is instant at any scale`() {
        val specs = MotionPolicy.specsFor(MotionInput(1f, reduceMotion = true))
        assertSame(MotionPolicy.INSTANT, specs)
    }

    @Test
    fun `the default platform scale animates`() {
        val specs = MotionPolicy.specsFor(MotionInput(1f, reduceMotion = false))
        assertTrue(specs.useSpring)
        assertEquals(CodecMotion.Duration.MEDIUM, specs.enterMs)
        assertEquals(CodecMotion.Duration.SHORT, specs.exitMs)
    }

    @Test
    fun `a partial scale still animates`() {
        // Only a deliberate zero silences the app — 0.5× is "slower",
        // not "off".
        val specs = MotionPolicy.specsFor(MotionInput(0.5f, reduceMotion = false))
        assertTrue(specs.useSpring)
    }

    @Test
    fun `the instant budget is zero`() {
        assertEquals(0, MotionPolicy.INSTANT.enterMs)
        assertEquals(0, MotionPolicy.INSTANT.exitMs)
        assertFalse(MotionPolicy.INSTANT.useSpring)
    }

    @Test
    fun `orNone returns the motion or nothing`() {
        val on = MotionPolicy.specsFor(MotionInput(1f, reduceMotion = false))
        val off = MotionPolicy.INSTANT
        assertEquals(CodecMotion.tabEnter, on.orNone(CodecMotion.tabEnter))
        assertEquals(CodecMotion.tabExit, on.orNone(CodecMotion.tabExit))
        assertEquals(
            androidx.compose.animation.EnterTransition.None,
            off.orNone(CodecMotion.tabEnter),
        )
        assertEquals(
            androidx.compose.animation.ExitTransition.None,
            off.orNone(CodecMotion.tabExit),
        )
    }

    @Test
    fun `floatOrSnap returns the spec or the snap`() {
        val on = MotionPolicy.specsFor(MotionInput(1f, reduceMotion = false))
        assertEquals(CodecMotion.crossfadeSpec, on.floatOrSnap(CodecMotion.crossfadeSpec))
        assertEquals(CodecMotion.snapFloat, MotionPolicy.INSTANT.floatOrSnap(CodecMotion.crossfadeSpec))
    }
}
