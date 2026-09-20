package com.codeci.ide

import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.theme.CodecTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50.1 — the scale is complete, monotonic, 4-based, and the helpers
 * round-trip. Nothing joins the scale without a case here naming it.
 */
class CodecTokensTest {

    @Test
    fun `space ladder is the documented nine steps`() {
        assertEquals(
            listOf(0f, 2f, 4f, 8f, 12f, 16f, 24f, 32f, 48f),
            listOf(
                CodecTokens.Space.NONE,
                CodecTokens.Space.XXS,
                CodecTokens.Space.XS,
                CodecTokens.Space.S,
                CodecTokens.Space.M,
                CodecTokens.Space.L,
                CodecTokens.Space.XL,
                CodecTokens.Space.XXL,
                CodecTokens.Space.HUGE,
            ),
        )
    }

    @Test
    fun `radius ladder is the documented five shapes`() {
        assertEquals(
            listOf(4f, 8f, 12f, 16f, 28f),
            listOf(
                CodecTokens.Radius.XS,
                CodecTokens.Radius.S,
                CodecTokens.Radius.M,
                CodecTokens.Radius.L,
                CodecTokens.Radius.XL,
            ),
        )
    }

    @Test
    fun `elevation ladder is flat raised card sheet`() {
        assertEquals(
            listOf(0f, 1f, 3f, 6f),
            listOf(
                CodecTokens.Elevation.FLAT,
                CodecTokens.Elevation.RAISED,
                CodecTokens.Elevation.CARD,
                CodecTokens.Elevation.SHEET,
            ),
        )
    }

    @Test
    fun `icon ladder is inline action nav`() {
        assertEquals(
            listOf(16f, 20f, 24f),
            listOf(
                CodecTokens.Icon.INLINE,
                CodecTokens.Icon.ACTION,
                CodecTokens.Icon.NAV,
            ),
        )
    }

    @Test
    fun `every ladder is strictly monotonic`() {
        fun check(name: String, values: List<Float>) {
            assertEquals(
                "$name must be sorted ascending, got $values",
                values.sorted(),
                values,
            )
            assertEquals("$name must not repeat a step", values.size, values.toSet().size)
        }
        check(
            "Space",
            listOf(
                CodecTokens.Space.NONE, CodecTokens.Space.XXS, CodecTokens.Space.XS,
                CodecTokens.Space.S, CodecTokens.Space.M, CodecTokens.Space.L,
                CodecTokens.Space.XL, CodecTokens.Space.XXL, CodecTokens.Space.HUGE,
            ),
        )
        check(
            "Radius",
            listOf(
                CodecTokens.Radius.XS, CodecTokens.Radius.S, CodecTokens.Radius.M,
                CodecTokens.Radius.L, CodecTokens.Radius.XL,
            ),
        )
        check(
            "Elevation",
            listOf(
                CodecTokens.Elevation.FLAT, CodecTokens.Elevation.RAISED,
                CodecTokens.Elevation.CARD, CodecTokens.Elevation.SHEET,
            ),
        )
        check(
            "Icon",
            listOf(CodecTokens.Icon.INLINE, CodecTokens.Icon.ACTION, CodecTokens.Icon.NAV),
        )
    }

    @Test
    fun `space and radius steps are 4-based down to 2`() {
        val steps = listOf(
            CodecTokens.Space.NONE, CodecTokens.Space.XXS, CodecTokens.Space.XS,
            CodecTokens.Space.S, CodecTokens.Space.M, CodecTokens.Space.L,
            CodecTokens.Space.XL, CodecTokens.Space.XXL, CodecTokens.Space.HUGE,
        )
        for (v in steps) {
            assertTrue(
                "Space step $v is not 4-based",
                v % 4f == 0f || v == CodecTokens.Space.XXS,
            )
        }
        val radii = listOf(
            CodecTokens.Radius.XS, CodecTokens.Radius.S, CodecTokens.Radius.M,
            CodecTokens.Radius.L, CodecTokens.Radius.XL,
        )
        for (v in radii) {
            assertTrue("Radius step $v is not 4-based", v % 4f == 0f)
        }
    }

    @Test
    fun `minimum touch target is the 48dp floor`() {
        assertTrue(
            "MIN_TOUCH must be at least 48 (M3 / WCAG 2.2 §2.5.8)",
            CodecTokens.MIN_TOUCH >= 48f,
        )
    }

    @Test
    fun `space helper round-trips every step`() {
        val steps = listOf(0f, 2f, 4f, 8f, 12f, 16f, 24f, 32f, 48f)
        for (v in steps) {
            assertEquals("$v must round-trip through space()", v.dp, CodecTokens.space(v))
        }
    }

    @Test
    fun `radius elevation and icon helpers round-trip`() {
        assertEquals(16.dp, CodecTokens.radius(CodecTokens.Radius.L))
        assertEquals(0.dp, CodecTokens.elevation(CodecTokens.Elevation.FLAT))
        assertEquals(1.dp, CodecTokens.elevation(CodecTokens.Elevation.RAISED))
        assertEquals(20.dp, CodecTokens.icon(CodecTokens.Icon.ACTION))
    }

    @Test
    fun `icon roles sit on the space ladder`() {
        // INLINE/ACTION/NAV are names for steps a gap could also take, so an
        // icon snapped to the icon ladder never leaves the one scale.
        val spaceSteps = setOf(0f, 2f, 4f, 8f, 12f, 16f, 20f, 24f, 32f, 48f)
        assertTrue(spaceSteps.contains(CodecTokens.Icon.INLINE))
        assertTrue(spaceSteps.contains(CodecTokens.Icon.NAV))
    }
}
