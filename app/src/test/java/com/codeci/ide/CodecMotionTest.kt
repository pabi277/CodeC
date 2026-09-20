package com.codeci.ide

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import com.codeci.ide.ui.theme.CodecMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50.4 — the springs, the ladder and the easing are the documented
 * ones. (Specs are pure value objects — no Compose runtime needed.)
 */
class CodecMotionTest {

    @Test
    fun `the spatial spring is soft and just under critical`() {
        assertEquals(0.8f, CodecMotion.spatialSpring.dampingRatio)
        assertEquals(380f, CodecMotion.spatialSpring.stiffness)
    }

    @Test
    fun `the effects spring never overshoots`() {
        assertEquals(Spring.DampingRatioNoBouncy, CodecMotion.effectsSpring.dampingRatio)
        assertEquals(Spring.StiffnessMedium, CodecMotion.effectsSpring.stiffness)
    }

    @Test
    fun `the panel spring matches the spatial spring`() {
        assertEquals(0.8f, CodecMotion.panelSpring.dampingRatio)
        assertEquals(380f, CodecMotion.panelSpring.stiffness)
    }

    @Test
    fun `the duration ladder is short medium long`() {
        assertEquals(150, CodecMotion.Duration.SHORT)
        assertEquals(300, CodecMotion.Duration.MEDIUM)
        assertEquals(500, CodecMotion.Duration.LONG)
    }

    @Test
    fun `the easing is a valid emphasized bezier`() {
        val easing = CodecMotion.Easing.EMPHASIZED
        assertEquals(0f, easing.transform(0f))
        assertEquals(1f, easing.transform(1f))
        val mid = easing.transform(0.5f)
        assertTrue("easing must rise monotonically through (0.5, $mid)", mid > 0f && mid < 1f)
    }

    @Test
    fun `the fade specs ride the ladder`() {
        val short = CodecMotion.fadeSpecShort as TweenSpec<*>
        val medium = CodecMotion.fadeSpecMedium as TweenSpec<*>
        assertEquals(CodecMotion.Duration.SHORT, short.durationMillis)
        assertEquals(CodecMotion.Duration.MEDIUM, medium.durationMillis)
        assertEquals(CodecMotion.fadeSpecShort, CodecMotion.crossfadeSpec)
    }

    @Test
    fun `the instant spec is a snap`() {
        assertTrue(CodecMotion.snapFloat is SnapSpec)
    }

    @Test
    fun `the shared transitions exist`() {
        // Presence, not behaviour (behaviour is pixels): every call site
        // below must resolve, so a rename breaks the build, not the look.
        assertTrue(CodecMotion.tabEnter != CodecMotion.panelEnter)
        assertTrue(CodecMotion.tabExit != CodecMotion.panelExit)
        assertTrue(CodecMotion.findEnter != CodecMotion.panelEnter)
        assertTrue(CodecMotion.findExit != CodecMotion.panelExit)
    }
}
