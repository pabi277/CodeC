package com.codeci.ide

import com.codeci.ide.ui.theme.AccentPalette
import com.codeci.ide.ui.theme.BrandRamp
import com.codeci.ide.ui.theme.CodecPalette
import com.codeci.ide.ui.theme.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50.2 — the brand ramp: every role from the seed, every pair measured.
 *
 * The engine guarantees the surface pairs (`readableOn`/`ensureReadable`
 * always return a passing value); the `onPrimary`-style pairs are measured
 * here instead, because `onColorFor` picks the better extreme without a
 * promise. Verified against a Python mirror of the same maths before
 * writing: all seven pairs clear 4.5:1 for all six picker choices in both
 * themes (worst measured: exactly the 4.50 the engine lands on).
 */
class BrandRampTest {

    private val themes = listOf(true to "dark", false to "light")

    private fun surfaceFor(dark: Boolean): Int =
        if (dark) CodecPalette.SURFACE_DARK else BrandRamp.LIGHT_SURFACE

    @Test
    fun `every pair clears AA for every choice in both themes`() {
        for (choice in CodecPalette.ACCENT_CHOICES) {
            for ((dark, name) in themes) {
                val report = BrandRamp.contrastReport(BrandRamp.rolesFor(choice.argb, dark))
                for ((pair, ratio) in report) {
                    assertTrue(
                        "${choice.label} ($name): $pair is ${"%.2f".format(ratio)}:1, needs 4.5:1",
                        ratio >= Contrast.AA_TEXT,
                    )
                }
            }
        }
    }

    @Test
    fun `the report covers all seven text pairs`() {
        val report = BrandRamp.contrastReport(
            BrandRamp.rolesFor(CodecPalette.DEFAULT_ACCENT, dark = true),
        )
        assertEquals(
            setOf(
                "primary/surface", "onPrimary/primary", "onContainer/container",
                "secondary/surface", "onSecondary/secondary",
                "tertiary/surface", "onTertiary/tertiary",
            ),
            report.keys,
        )
    }

    @Test
    fun `the ramp is deterministic for a seed`() {
        for (choice in CodecPalette.ACCENT_CHOICES) {
            for ((dark, _) in themes) {
                assertEquals(
                    "ramp must be deterministic for ${choice.label}",
                    BrandRamp.rolesFor(choice.argb, dark),
                    BrandRamp.rolesFor(choice.argb, dark),
                )
            }
        }
    }

    @Test
    fun `a seed that already passes keeps its hue`() {
        // The default green already clears AA on dark: the ramp must return
        // it untouched, not a "corrected" near-twin.
        val seed = CodecPalette.DEFAULT_ACCENT
        val roles = BrandRamp.rolesFor(seed, dark = true)
        assertEquals(seed, roles.primary)
    }

    @Test
    fun `a corrected seed keeps its hue within rounding`() {
        // On light the green must darken to clear AA; hue must survive the
        // walk (fromHsl rounding aside).
        val seed = CodecPalette.DEFAULT_ACCENT
        val roles = BrandRamp.rolesFor(seed, dark = false)
        assertTrue(seed != roles.primary)
        val drift = Math.abs(Contrast.hue(seed) - Contrast.hue(roles.primary))
        assertTrue("hue drifted ${"%.2f".format(drift)}°", drift < 2.0)
    }

    @Test
    fun `secondary keeps the seed hue with less chroma`() {
        for (choice in CodecPalette.ACCENT_CHOICES) {
            val roles = BrandRamp.rolesFor(choice.argb, dark = true)
            val drift = Math.abs(Contrast.hue(choice.argb) - Contrast.hue(roles.secondary))
            assertTrue("${choice.label}: secondary hue drifted ${"%.2f".format(drift)}°", drift < 5.0)
            val (_, seedSat, _) = Contrast.hsl(choice.argb)
            val (_, secondarySat, _) = Contrast.hsl(roles.secondary)
            assertTrue(
                "${choice.label}: secondary must not gain chroma ($seedSat → $secondarySat)",
                secondarySat <= seedSat + 0.05,
            )
        }
    }

    @Test
    fun `tertiary rotates the seed hue a sixth of the wheel`() {
        for (choice in CodecPalette.ACCENT_CHOICES) {
            val roles = BrandRamp.rolesFor(choice.argb, dark = true)
            val expected = (Contrast.hue(choice.argb) + 60.0) % 360.0
            var drift = Math.abs(expected - Contrast.hue(roles.tertiary)) % 360.0
            if (drift > 180.0) drift = 360.0 - drift
            assertTrue("${choice.label}: tertiary hue drifted ${"%.2f".format(drift)}°", drift < 8.0)
        }
    }

    @Test
    fun `secondary and tertiary are never the template constants`() {
        val template = setOf(0xFFCCC2DC.toInt(), 0xFFEFB8C8.toInt(), 0xFF625B71.toInt(), 0xFF7D5260.toInt())
        for (choice in CodecPalette.ACCENT_CHOICES) {
            for ((dark, _) in themes) {
                val roles = BrandRamp.rolesFor(choice.argb, dark)
                assertTrue(
                    "${choice.label}: secondary is a template constant",
                    !template.contains(roles.secondary and 0xFFFFFF or (0xFF shl 24)),
                )
                assertTrue(
                    "${choice.label}: tertiary is a template constant",
                    !template.contains(roles.tertiary and 0xFFFFFF or (0xFF shl 24)),
                )
            }
        }
    }

    @Test
    fun `the ramp equals the engine it fronts`() {
        // The theme reads the twelve-role engine (it needs the containers);
        // this facade must never drift from it on the roles they share.
        for (choice in CodecPalette.ACCENT_CHOICES) {
            for ((dark, name) in themes) {
                val surface = surfaceFor(dark)
                val ramp = BrandRamp.rolesFor(choice.argb, dark)
                val engine = AccentPalette.rolesFor(choice.argb, dark, surface)
                assertEquals("${choice.label} ($name): primary", engine.primary, ramp.primary)
                assertEquals("${choice.label} ($name): onPrimary", engine.onPrimary, ramp.onPrimary)
                assertEquals("${choice.label} ($name): container", engine.container, ramp.container)
                assertEquals("${choice.label} ($name): onContainer", engine.onContainer, ramp.onContainer)
                assertEquals("${choice.label} ($name): secondary", engine.secondary, ramp.secondary)
                assertEquals("${choice.label} ($name): onSecondary", engine.onSecondary, ramp.onSecondary)
                assertEquals("${choice.label} ($name): tertiary", engine.tertiary, ramp.tertiary)
                assertEquals("${choice.label} ($name): onTertiary", engine.onTertiary, ramp.onTertiary)
            }
        }
    }

    @Test
    fun `surface tint is the brand primary`() {
        for (choice in CodecPalette.ACCENT_CHOICES) {
            for ((dark, _) in themes) {
                val roles = BrandRamp.rolesFor(choice.argb, dark)
                assertEquals(roles.primary, roles.surfaceTint)
            }
        }
    }

    @Test
    fun `roles carry the surface they were corrected for`() {
        val dark = BrandRamp.rolesFor(CodecPalette.DEFAULT_ACCENT, dark = true)
        val light = BrandRamp.rolesFor(CodecPalette.DEFAULT_ACCENT, dark = false)
        assertEquals(CodecPalette.SURFACE_DARK, dark.surface)
        assertEquals(BrandRamp.LIGHT_SURFACE, light.surface)
    }

    @Test
    fun `the light surface is the M3 baseline`() {
        assertEquals(0xFFFFFBFE.toInt(), BrandRamp.LIGHT_SURFACE)
    }
}
