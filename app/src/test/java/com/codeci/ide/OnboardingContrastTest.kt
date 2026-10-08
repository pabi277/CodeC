package com.codeci.ide

import com.codeci.ide.ui.theme.AccentPalette
import com.codeci.ide.ui.theme.CodecPalette
import com.codeci.ide.ui.theme.Contrast
import com.codeci.ide.ui.theme.OnboardingStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 101 — the stage's colours, measured.
 *
 * The owner's brief (2026-10-08) asks the five onboarding screens for *"a deep
 * black background and pure white, high-contrast typography for all text …
 * smooth, modern gradients for buttons and status indicators."* Every one of
 * those clauses is a number, and this class is where the numbers live:
 *
 * 1. **The background is black and the type is white** — 21.00:1, the highest
 *    ratio WCAG defines, and the stage's only text colour. Anything that is not
 *    [OnboardingStage.ON_STAGE] on this stage is not text.
 * 2. **The gradient carries its own ink** — the CTA's label has to clear AA on
 *    *both* ends of the run, for every accent the app can be themed with.
 * 3. **Themes still have to work** — the setup half is theme-driven, so its
 *    cards and bars are measured against both of M3's base schemes.
 *
 * Hairlines stay out of the thresholds on purpose: a 1 dp edge on black cannot
 * reach 3:1 without becoming a second, brighter colour, so the *borders* are
 * decoration and the *text and state glyphs* are measured. `OnboardingStyleTest`
 * pins which is which.
 */
class OnboardingContrastTest {

    /** The bar this phase holds every stage pair to: AA, plus room to spare. */
    private val WIDE = 7.0

    private val darkSurface = CodecPalette.SURFACE_DARK
    private val lightSurface = 0xFFFFFBFE.toInt() // lightColorScheme()'s surface

    private val white = OnboardingStage.ON_STAGE
    private val top = OnboardingStage.STAGE_TOP
    private val bottom = OnboardingStage.STAGE_BOTTOM
    private val card = OnboardingStage.STAGE_CARD
    private val chip = OnboardingStage.STAGE_CHIP
    private val lane = OnboardingStage.STAGE_LANE
    private val stroke = OnboardingStage.STAGE_STROKE
    private val glow = OnboardingStage.STAGE_GLOW
    private val accentFrom = OnboardingStage.ACCENT_FROM
    private val accentTo = OnboardingStage.ACCENT_TO
    private val onAccent = OnboardingStage.ON_ACCENT

    private fun assertPair(label: String, fg: Int, bg: Int, need: Double = Contrast.AA_TEXT) {
        val measured = Contrast.ratio(fg, bg)
        assertTrue(
            "$label = %.2f:1 (needs %.1f)".format(measured, need),
            measured >= need,
        )
    }

    // ---- 1. pure black, pure white --------------------------------------

    @Test
    fun `the stage is black and white on it is maximal`() {
        // The brief's two hardest clauses, as two numbers: the backdrop is the
        // darkest colour a screen can show, and the ink on it is the best ratio
        // WCAG defines. Nothing in this app can be more readable than this.
        assertEquals("the backdrop's first stop is pure black", 0xFF000000.toInt(), top)
        assertEquals("the ink is pure white", 0xFFFFFFFF.toInt(), white)
        val measured = Contrast.ratio(white, top)
        assertTrue(
            "white on pure black must measure 21:1 (measured %.2f)".format(measured),
            measured >= 20.9,
        )
        // The wash at the other end stays a wash: a stage, not a gradient poster.
        assertTrue(
            "the backdrop's two ends must stay one black surface",
            Contrast.ratio(top, bottom) < 1.2,
        )
    }

    @Test
    fun `every text surface on the stage clears AA by a wide margin`() {
        // The three surfaces a word can land on in the tour: pure black, the
        // page card, and the eyebrow chip. One ink, all three, no grey.
        assertPair("white on the stage", white, top, WIDE)
        assertPair("white on the stage's wash", white, bottom, WIDE)
        assertPair("white on the page card", white, card, WIDE)
        assertPair("white on the eyebrow chip", white, chip, WIDE)
    }

    @Test
    fun `the card's edge and the dot lane are derived from the ink`() {
        // A black stage hides a card: the edge has to be *enough* white, and the
        // only honest way to state that is a derivation rather than a hex value
        // somebody liked. Both are the ink, composited over the card.
        assertEquals(
            "STAGE_STROKE must be ON_STAGE at 20% over STAGE_CARD",
            Contrast.composite(white, card, 0.20f),
            stroke,
        )
        assertEquals(
            "STAGE_LANE must be ON_STAGE at 12% over STAGE_CARD",
            Contrast.composite(white, card, 0.12f),
            lane,
        )
        // The lane is a *track*: visible, and quieter than the accent that
        // fills it. If the accent ever stops beating the lane, the dots stop
        // meaning anything.
        assertTrue(
            "the accent must out-read the lane",
            Contrast.ratio(accentFrom, lane) > Contrast.ratio(lane, card),
        )
        // The control edge and the halo are translucent by design: one is drawn
        // over whatever it sits on, the other has to fade to nothing.
        assertEquals("the control edge is ink at 35%", 89, (OnboardingStage.STAGE_CONTROL_STROKE ushr 24) and 0xFF)
        assertEquals("the halo is the accent at 22%", 56, (glow ushr 24) and 0xFF)
        // Both sides are masked: these are ARGB ints, so the accent's own
        // `0xFF` alpha makes it a *negative* Int in Kotlin. Comparing the
        // signed value to `glow and 0x00FFFFFF` compares -12754812 to 4055172.
        assertEquals(
            "and the halo is the accent, not a second colour",
            accentFrom and 0x00FFFFFF,
            glow and 0x00FFFFFF,
        )
    }

    @Test
    fun `the accent reads as a gradient and carries dark ink at both ends`() {
        val ends = Contrast.ratio(accentFrom, accentTo)
        assertTrue("the accent's ends must differ visibly (%.2f:1)".format(ends), ends >= 1.2)
        // The CTA's label sits on the run, so both ends are measured: the teal
        // end is the tighter one and decided the teal's value.
        assertPair("the CTA's ink on the green end", onAccent, accentFrom, WIDE)
        assertPair("the CTA's ink on the teal end", onAccent, accentTo, WIDE)
    }

    // ---- 2. the themed half still has to work ---------------------------

    /** Both themes' surfaces, as `Theme.kt` hands them to [AccentPalette]. */
    private val themes = listOf(
        "dark" to (true to darkSurface),
        "light" to (false to lightSurface),
    )

    @Test
    fun `the setup's gradient label clears AA on both ends, for every accent`() {
        // The setup's CTA paints `primary -> tertiary` and labels it with
        // `onPrimary`. Material only derives `onPrimary` for `primary`, so the
        // tertiary end is this project's own promise — and the picker offers six
        // accents in two themes, which is twelve gradients to prove.
        for ((themeName, theme) in themes) {
            val (dark, surface) = theme
            for (choice in CodecPalette.ACCENT_CHOICES) {
                val roles = AccentPalette.rolesFor(choice.argb, dark, surface)
                assertPair("$themeName ${choice.label}: onPrimary on primary", roles.onPrimary, roles.primary)
                assertPair("$themeName ${choice.label}: onPrimary on tertiary", roles.onPrimary, roles.tertiary)
                assertEquals(
                    "$themeName ${choice.label}: onPrimary must be measured, not assumed",
                    Contrast.onColorFor(roles.primary),
                    roles.onPrimary,
                )
            }
        }
    }

    @Test
    fun `the build bar's fill clears the non-text floor on its track`() {
        // Phase 100 moved the bar's track from `surfaceVariant` to `surface`,
        // where the fill measured 2.46:1 for Violet and Teal in the dark theme.
        for ((themeName, theme) in themes) {
            val (dark, surface) = theme
            for (choice in CodecPalette.ACCENT_CHOICES) {
                val roles = AccentPalette.rolesFor(choice.argb, dark, surface)
                assertPair("$themeName ${choice.label}: the fill's first end", roles.primary, surface, Contrast.AA_NON_TEXT)
                assertPair("$themeName ${choice.label}: the fill's last end", roles.tertiary, surface, Contrast.AA_NON_TEXT)
            }
        }
    }

    @Test
    fun `a resting option card has an edge you can see`() {
        // Phase 101 changed the option cards' resting edge from `outlineVariant`
        // to `outline`: in M3's dark baseline `outlineVariant` *is*
        // `surfaceVariant`, so the border was the same colour as the card and a
        // resting card on a dark screen had no edge at all. This is the pin that
        // "well-defined cards" resolves to - the edge against the page it is
        // drawn on, in both themes.
        val edges = listOf(
            Triple("dark", 0xFF938F99.toInt(), darkSurface),
            Triple("light", 0xFF79747E.toInt(), lightSurface),
        )
        for ((themeName, outline, surface) in edges) {
            assertPair("$themeName: the resting card's edge against the page", outline, surface, Contrast.AA_NON_TEXT)
        }
        // And the resting *fill* is still the theme's own surfaceVariant, which
        // is what separates a card from the page even where an edge is subtle.
        for ((themeName, theme) in themes) {
            val (dark, _) = theme
            val surfaceVariant = if (dark) 0xFF49454F.toInt() else 0xFFE7E0EC.toInt()
            val onSurface = if (dark) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
            val onSurfaceVariant = if (dark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
            assertPair("$themeName: the resting card's title", onSurface, surfaceVariant)
            assertPair("$themeName: the resting card's note", onSurfaceVariant, surfaceVariant)
        }
    }

    @Test
    fun `the chosen card's tint keeps its text at AA and its tick visible`() {
        // The pick card is `primary` at 12 % over the surface. The tint is a
        // *background*: the title, the note and the sample line are body text
        // and need 4.5:1, while the tick and the 2 dp gradient edge are
        // graphical and need 3:1.
        for ((themeName, theme) in themes) {
            val (dark, surface) = theme
            val onSurface = if (dark) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
            val onSurfaceVariant = if (dark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
            for (choice in CodecPalette.ACCENT_CHOICES) {
                val roles = AccentPalette.rolesFor(choice.argb, dark, surface)
                val tinted = Contrast.composite(roles.primary, surface, 0.12f)
                assertPair("$themeName ${choice.label}: the card's title", onSurface, tinted)
                assertPair("$themeName ${choice.label}: the card's note", onSurfaceVariant, tinted)
                assertPair("$themeName ${choice.label}: the card's sample line", onSurfaceVariant, tinted)
                assertPair("$themeName ${choice.label}: the tick on the chosen card", roles.primary, tinted, Contrast.AA_NON_TEXT)
            }
        }
    }
}
