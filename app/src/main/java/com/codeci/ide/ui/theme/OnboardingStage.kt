package com.codeci.ide.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Phase 101 — the onboarding stage, at its final contrast: **pure black, pure
 * white, and the accent kept for the gradients alone**.
 *
 * The owner's brief (2026-10-08): *"a deep black background and pure white,
 * high-contrast typography for all text … sleek, custom 3D icons and smooth,
 * modern gradients for buttons and status indicators."* That is one decision
 * stated three ways, and it is why this object looks the way it does:
 *
 * 1. **The stage is black, not "dark".** [STAGE_TOP] is `#000000` — the darkest
 *    value a display can show — with a whisper of a wash to [STAGE_BOTTOM], so
 *    the screen has depth without a second colour. White on it measures
 *    **21.00:1**, the highest ratio WCAG defines: there is no better contrast
 *    available on this device.
 * 2. **There is exactly one text colour: [ON_STAGE] = `#FFFFFF`.** Headings,
 *    body, captions, the chip's label, the skip and pause links, the agreement
 *    and its hint — all pure white. Hierarchy is carried by *size and weight*
 *    (headline / body / label), not by grey, which is also why there is no
 *    `ON_STAGE_MUTED` in this file any more. The accent never becomes text:
 *    it lives on fills, rings and indicators, where it is read as colour
 *    rather than as a word.
 * 3. **The accent is the gradient, and the gradient is the state.** The CTA
 *    ([accent]), the step icon's ring, the progress dots and the glow behind
 *    the icon all use the same `#3DDC84 → #2BC4B0` run; the ink on it
 *    ([ON_ACCENT]) clears AA on *both* ends (9.14:1 and 7.48:1).
 *
 * The tour is staged rather than themed because it runs *before* the app has a
 * theme — the theme is one of the answers the setup collects. The setup's own
 * beats stay theme-driven on purpose (S4 re-themes the app live); that seam is
 * recorded in `PART_100` and pinned by `OnboardingStyleTest`.
 *
 * Everything here is plain `Int` colour math, so `OnboardingContrastTest` can
 * measure each pair on the JVM instead of on a device.
 */
object OnboardingStage {

    /** The backdrop: pure black, top to bottom, with the faintest rising cast. */
    const val STAGE_TOP = 0xFF000000.toInt()
    const val STAGE_BOTTOM = 0xFF060A08.toInt()

    /** The content card the tour's page sits in: lifted just off the black. */
    const val STAGE_CARD = 0xFF0C100E.toInt()

    /**
     * The card's hairline — `#FFFFFF` at 20 % over [STAGE_CARD], so a card is
     * *defined* on a black stage instead of melting into it. Derived, not
     * invented: the test asserts the composite equality.
     */
    const val STAGE_STROKE = 0xFF3D403E.toInt()

    /**
     * The lane behind the progress dots — the same white, at 12 %: a status
     * track you can see is *there* without competing with the gradient that
     * fills it. Derived the same way as [STAGE_STROKE].
     */
    const val STAGE_LANE = 0xFF292D2B.toInt()

    /**
     * Type. The only text colour on the stage, and the only one this phase
     * allows: pure white, 21:1 on the backdrop and 19.16:1 on the card.
     */
    const val ON_STAGE = 0xFFFFFFFF.toInt()

    /** The eyebrow chip's own fill: a deep green-black the white label sits on. */
    const val STAGE_CHIP = 0xFF122019.toInt()

    /**
     * A control that is present but not the primary action (the tour's *Back*):
     * white at 35 %, translucent so it can sit on any stage surface.
     */
    const val STAGE_CONTROL_STROKE = 0x59FFFFFF.toInt()

    /**
     * The halo under the step's 3D icon: the accent at 22 %, drawn as a radial
     * gradient that fades to nothing — the one soft edge on the stage.
     */
    const val STAGE_GLOW = 0x383DDC84.toInt()

    /**
     * The accent gradient's two ends, and the ink that sits on them. The green
     * is CodeC's own ([CodecPalette.IDENTITY_GREEN]); the teal is its closest
     * neighbour that still carries dark ink at 7.48:1, and the two ends differ
     * by 1.22:1 so the run reads as a gradient rather than a flat fill.
     */
    const val ACCENT_FROM = CodecPalette.IDENTITY_GREEN
    const val ACCENT_TO = 0xFF2BC4B0.toInt()
    const val ON_ACCENT = 0xFF06251A.toInt()

    /** The backdrop wash: black, with a hint of depth at the bottom. */
    fun backdrop(): Brush = Brush.verticalGradient(listOf(Color(STAGE_TOP), Color(STAGE_BOTTOM)))

    /** The accent, left to right. */
    fun accent(): Brush = Brush.horizontalGradient(listOf(Color(ACCENT_FROM), Color(ACCENT_TO)))

    /** The icon's halo: the accent fading to nothing behind the step's 3D icon. */
    fun iconGlow(): Brush = Brush.radialGradient(listOf(Color(STAGE_GLOW), Color.Transparent))
}

/**
 * Phase 100 — the accent gradient for a *themed* surface: the user's own accent,
 * running `primary → tertiary` exactly as Material 3 derives those two roles.
 *
 * The setup's chip, progress bar and primary action use this, so picking a
 * different accent in Settings re-tints the flow's accents with everything else
 * — the same rule the rest of the app already follows.
 */
fun codecAccentGradient(
    primary: Color,
    tertiary: Color,
): Brush = Brush.horizontalGradient(listOf(primary, tertiary))
