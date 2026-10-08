package com.codeci.ide.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Phase 100 — the onboarding stage: a deep, modern dark surface with a gradient
 * accent, used by the illustrated tour and by the setup's welcome beat.
 *
 * Four decisions, each of them checkable (`OnboardingContrastTest`,
 * `OnboardingStyleTest`):
 *
 * 1. **The tour is staged dark on purpose.** The tour runs *before* the app has
 *    a theme (the theme is one of the answers the setup collects), so it cannot
 *    take its colours from the user's palette. It uses this stage instead of the
 *    live `MaterialTheme` — a fixed, high-contrast surface that looks the same
 *    on every device and in every system mode.
 *
 *    The **setup** beats do *not* use it: S4 re-themes the app live, because
 *    "these change right here" is the beat's whole point, so its surfaces stay
 *    the theme's own (a fresh install is dark, which is the same deep surface
 *    family). The seam is deliberate and recorded in PART_100.
 *
 * 2. **Every text pair clears WCAG AA by a wide margin**, not by a hair. The
 *    tightest pair here is [ON_STAGE_MUTED] on [STAGE_CARD] at 8.9:1; the
 *    gradient's own ink is 7.5:1 on the *lighter* end of the gradient.
 *
 * 3. **The gradient is this project's own green → teal**, not a stock purple.
 *    The setup's gradients use `primary → tertiary`, so they follow whatever
 *    accent the user picked; the tour's cannot, so it uses these two.
 *
 * 4. **Nothing here is a `MaterialTheme` role.** `Color` from
 *    `ui.graphics` only, so this object is host-testable and the ratios above
 *    can be asserted without a device.
 */
object OnboardingStage {

    /** The backdrop: a vertical wash, top to bottom. */
    const val STAGE_TOP = 0xFF0A0E0C.toInt()
    const val STAGE_BOTTOM = 0xFF111A15.toInt()

    /** The content card the tour's page sits in. */
    const val STAGE_CARD = 0xFF141C17.toInt()

    /**
     * The card's hairline. `#F2F6F3` at 14 % over [STAGE_CARD] — a visible edge
     * without a second colour to keep in tune (`Contrast.composite`).
     */
    const val STAGE_STROKE = 0xFF333B36.toInt()

    /** Type. */
    const val ON_STAGE = 0xFFF2F6F3.toInt()          // 17.8:1 on the stage top
    const val ON_STAGE_MUTED = 0xFFAFBDB5.toInt()    //  9.9:1 on the stage top

    /** The eyebrow chip: its own dark fill, and mint ink that reads on it. */
    const val STAGE_CHIP = 0xFF16301F.toInt()
    const val ON_STAGE_CHIP = 0xFF9CF2C3.toInt()     // 10.8:1 on the chip

    /**
     * The accent gradient's two ends, and the ink that sits on them. The green
     * is CodeC's own ([CodecPalette.IDENTITY_GREEN]); the teal is its closest
     * neighbour that still carries dark ink at 7.5:1.
     */
    const val ACCENT_FROM = CodecPalette.IDENTITY_GREEN
    const val ACCENT_TO = 0xFF2BC4B0.toInt()
    const val ON_ACCENT = 0xFF06251A.toInt()         // 9.1:1 and 7.5:1 on the ends

    /** A control that is present but not the primary action. */
    const val STAGE_CONTROL_STROKE = 0x59F2F6F3.toInt()  // ink at 35 %

    /** The backdrop wash. */
    fun backdrop(): Brush = Brush.verticalGradient(listOf(Color(STAGE_TOP), Color(STAGE_BOTTOM)))

    /** The accent, left to right. */
    fun accent(): Brush = Brush.horizontalGradient(listOf(Color(ACCENT_FROM), Color(ACCENT_TO)))
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
