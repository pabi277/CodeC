package com.codeci.ide

import com.codeci.ide.ui.theme.AccentPalette
import com.codeci.ide.ui.theme.CodecPalette
import com.codeci.ide.ui.theme.Contrast
import com.codeci.ide.ui.theme.OnboardingStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 100 — the redesign's colours, measured.
 *
 * The owner's brief asked for a minimalist modern look on a deep dark backdrop
 * with crisp high-contrast type and smooth gradient accents. "High contrast" is
 * a number, not an opinion, so every pair the redesign draws is derived here
 * from the constants in the sources — the same way `AppContrastTest` and
 * `ChromeContrastTest` pin the rest of the app.
 *
 * Three groups of pairs, and the reason each is measured against *its own*
 * threshold:
 *
 * 1. **The stage's text** (`OnboardingStage`) — the tour runs before the app has
 *    a theme, so it carries its own fixed palette. Every foreground/background
 *    pair here is text or a graphical control, and the thresholds are the wide
 *    ones ([WIDE]): the stage's whole point is that nothing on it is dim.
 * 2. **The themed gradient** (`codecAccentGradient`) — the setup's accent runs
 *    `primary → tertiary` and its label is `onPrimary`. Material's roles only
 *    promise `onPrimary` on `primary`; the *other* end of that gradient is the
 *    new claim, so it is measured for **every** accent the picker offers, in
 *    both themes.
 * 3. **The surfaces the redesign introduces** — the chosen card's tint, and the
 *    build bar's fill against its track. Both were the subject of a real fix in
 *    this phase (see the two tests that name them).
 *
 * What is deliberately *not* asserted: the hairline strokes. A 1 dp border on a
 * deep-dark surface cannot reach 3:1 without becoming a second, brighter colour
 * — that is why the card's edge is the ink itself at 14 %, and why the edges
 * are decoration while the *text* and the *icon* carry the meaning. Pinning a
 * ratio the design cannot have would only hide the decision.
 */
class OnboardingContrastTest {

    /** The bar this phase holds every stage pair to: AA, plus room to spare. */
    private val WIDE = 7.0

    private val darkSurface = CodecPalette.SURFACE_DARK
    private val lightSurface = 0xFFFFFBFE.toInt() // lightColorScheme()'s surface

    private val ink = OnboardingStage.ON_STAGE
    private val muted = OnboardingStage.ON_STAGE_MUTED
    private val card = OnboardingStage.STAGE_CARD
    private val top = OnboardingStage.STAGE_TOP
    private val bottom = OnboardingStage.STAGE_BOTTOM
    private val chip = OnboardingStage.STAGE_CHIP
    private val chipInk = OnboardingStage.ON_STAGE_CHIP
    private val accentFrom = OnboardingStage.ACCENT_FROM
    private val accentTo = OnboardingStage.ACCENT_TO
    private val onAccent = OnboardingStage.ON_ACCENT

    private fun require(label: String, fg: Int, bg: Int, need: Double = Contrast.AA_TEXT) {
        val measured = Contrast.ratio(fg, bg)
        assertTrue(
            "$label = %.2f:1 (needs %.1f)".format(measured, need),
            measured >= need,
        )
    }

    // ---- 1. the stage ----------------------------------------------------

    @Test
    fun `the stage's type clears AA on every surface it is drawn on`() {
        // Headings and body copy, on all three stage surfaces it can sit on.
        require("ink on the stage's top", ink, top, WIDE)
        require("ink on the stage's bottom", ink, bottom, WIDE)
        require("ink on the card", ink, card, WIDE)
        // Captions and hints: smaller, quieter, still far above the floor.
        require("muted ink on the card", muted, card, WIDE)
        require("muted ink on the stage's bottom", muted, bottom, WIDE)
        // The eyebrow chip is its own surface, with its own two inks.
        require("chip ink on the chip", chipInk, chip, WIDE)
        require("stage ink on the chip", ink, chip, WIDE)
        require("muted ink on the chip", muted, chip, WIDE)
    }

    @Test
    fun `the accent is readable as text on the stage it is drawn on`() {
        // The tour's links (skip, pause) and the privacy link are body text in
        // the accent, not graphics — so this is the text threshold, not 3:1.
        require("accent on the card", accentFrom, card, WIDE)
        require("accent on the stage's top", accentFrom, top, WIDE)
        require("the gradient's far end on the card", accentTo, card, WIDE)
    }

    @Test
    fun `the gradient carries dark ink at both ends`() {
        // The CTA labels sit on the gradient, and the gradient runs green ->
        // teal. The label has to clear AA on *both* ends, so both are measured:
        // the teal end is the tighter of the two, and 7.5:1 is the margin that
        // decided the teal's value.
        require("the CTA's ink on the gradient's green end", onAccent, accentFrom, WIDE)
        require("the CTA's ink on the gradient's teal end", onAccent, accentTo, WIDE)
    }

    @Test
    fun `the card's edge is the ink itself, not a second colour`() {
        // A stage with one ink and one accent is a stage that cannot drift. The
        // hairline is `ON_STAGE` at 14 % over the card, composited — exactly.
        assertEquals(
            "STAGE_STROKE must be ON_STAGE at 14% over STAGE_CARD",
            Contrast.composite(ink, card, 0.14f),
            OnboardingStage.STAGE_STROKE,
        )
        // The non-primary control edge is the same ink at 35 %, and it stays
        // translucent: it is drawn over whatever it sits on.
        val alpha = (OnboardingStage.STAGE_CONTROL_STROKE ushr 24) and 0xFF
        assertEquals("the control edge is ink at 35%", 89, alpha)
        assertTrue(
            "the control edge must stay translucent, so it can sit on any stage surface",
            alpha < 255,
        )
    }

    @Test
    fun `the backdrop is a wash, and the accent is a gradient`() {
        // Two ends that are close enough to read as one surface...
        val backdrop = Contrast.ratio(top, bottom)
        assertTrue("the backdrop's two ends must stay one surface (%.2f:1)".format(backdrop), backdrop < 1.5)
        assertTrue(
            "the backdrop's top is the darker end",
            Contrast.relativeLuminance(top) < Contrast.relativeLuminance(bottom),
        )
        // ...while the accent's two ends are far enough apart to read as a
        // gradient rather than a flat fill.
        val accent = Contrast.ratio(accentFrom, accentTo)
        assertTrue("the accent's ends must differ visibly (%.2f:1)".format(accent), accent >= 1.2)
    }

    // ---- 2. the themed gradient (the setup's half) -----------------------

    /** Both themes' surfaces, as `Theme.kt` hands them to [AccentPalette]. */
    private val themes = listOf(
        "dark" to (true to darkSurface),
        "light" to (false to lightSurface),
    )

    @Test
    fun `the setup's gradient label clears AA on both ends, for every accent`() {
        // The setup's CTA paints `primary -> tertiary` and labels it with
        // `onPrimary`. Material only derives `onPrimary` for `primary`, so the
        // tertiary end is this phase's own promise — and the picker offers six
        // accents in two themes, which is twelve gradients to prove.
        for ((themeName, theme) in themes) {
            val (dark, surface) = theme
            for (choice in CodecPalette.ACCENT_CHOICES) {
                val roles = AccentPalette.rolesFor(choice.argb, dark, surface)
                require("$themeName ${choice.label}: onPrimary on primary", roles.onPrimary, roles.primary)
                require("$themeName ${choice.label}: onPrimary on tertiary", roles.onPrimary, roles.tertiary)
                // The ink is the theme's, so it must also be the *better* of the
                // two extremes on both ends — a lighter label that merely squeaks
                // past 4.5:1 is the bug this phase is avoiding.
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
        // Phase 100 moved the bar's track from `surfaceVariant` to `surface`.
        // The reason is measurable: every accent is lightness-corrected against
        // `surface` (that is `AccentPalette.rolesFor`'s contract), so against
        // `surface` the fill always clears AA — while against `surfaceVariant`
        // the same fill measured **2.46:1** for Violet and Teal in the dark
        // theme, under the 3:1 WCAG 2.2 §1.4.11 floor. A progress bar whose fill
        // cannot be told from its track is not a progress bar.
        for ((themeName, theme) in themes) {
            val (dark, surface) = theme
            for (choice in CodecPalette.ACCENT_CHOICES) {
                val roles = AccentPalette.rolesFor(choice.argb, dark, surface)
                require("$themeName ${choice.label}: the fill's first end", roles.primary, surface, Contrast.AA_NON_TEXT)
                require("$themeName ${choice.label}: the fill's last end", roles.tertiary, surface, Contrast.AA_NON_TEXT)
            }
        }
    }

    // ---- 3. the surfaces the redesign introduces -------------------------

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
                require("$themeName ${choice.label}: the card's title", onSurface, tinted)
                require("$themeName ${choice.label}: the card's note", onSurfaceVariant, tinted)
                require("$themeName ${choice.label}: the card's sample line", onSurfaceVariant, tinted)
                require("$themeName ${choice.label}: the tick on the chosen card", roles.primary, tinted, Contrast.AA_NON_TEXT)
            }
        }
    }

    @Test
    fun `the resting card keeps the theme's own pair`() {
        // The unselected card adds no colour of its own: it is `surfaceVariant`
        // with the theme's two inks on it. (In M3's dark baseline
        // `outlineVariant` and `surfaceVariant` are the *same* value, so a
        // resting card is defined by its fill, not by a border — which is
        // exactly why the chosen card's state is carried by the tick first and
        // the gradient edge second, and why both are measured above.)
        for ((themeName, theme) in themes) {
            val (dark, _) = theme
            val surfaceVariant = if (dark) 0xFF49454F.toInt() else 0xFFE7E0EC.toInt()
            val onSurface = if (dark) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
            val onSurfaceVariant = if (dark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
            require("$themeName: the resting card's title", onSurface, surfaceVariant)
            require("$themeName: the resting card's note", onSurfaceVariant, surfaceVariant)
        }
    }
}
