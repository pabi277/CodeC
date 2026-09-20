package com.codeci.ide.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phase 50.1 — one scale, not 801 numbers.
 *
 * The evidence (2026-09-13, `main` @ `62cfe7b`, re-verified on the Phase 50
 * branch): `grep -rho "[0-9]\+\.dp"` finds **801** literals across the app
 * and **10 distinct corner radii** (2, 4, 5, 6, 8, 9, 10, 12, 14, 16 dp) —
 * including a `9.dp` and a `4.dp` that appear exactly once. A value used once
 * is a value chosen by hand, per composable, at the moment it was written.
 *
 * The scale is **4-based** (wholly divisible down to 2): the six most-used
 * gaps (8, 16, 6, 4, 10, 12) are all on or adjacent to it, so adoption is a
 * rename, not a redesign. Off-scale values snap to the nearest step
 * (ties round up — roomier is safer for touch); display art above [Space.HUGE]
 * (hero icons, avatars) stays raw, because hero art is not chrome and no
 * design system scales it.
 *
 * Rules that keep this alive past Phase 50 (pinned by `CodecTokensTest` +
 * `TokenAdoptionTest` + `TouchTargetTest`):
 *
 * 1. Nothing new joins the scale without a test case naming it.
 * 2. In the six core surfaces (Welcome, Editor, Hub, Packages, Terminal,
 *    Settings) a gap/padding/size at or under 48 dp is written through
 *    [space]/[radius]/[elevation]/[icon], never as a raw `N.dp`.
 * 3. [MIN_TOUCH] is a floor, not a suggestion (M3 + WCAG 2.2 §2.5.8).
 *
 * `Dp` is `androidx.compose.ui.unit.Dp`, so this object compiles on the host
 * JVM for tests, exactly like [CodecPalette].
 */
object CodecTokens {

    /** Gaps, paddings, small sizes — the 4-based ladder. */
    object Space {
        const val NONE = 0f
        const val XXS = 2f
        const val XS = 4f
        const val S = 8f
        const val M = 12f
        const val L = 16f
        const val XL = 24f
        const val XXL = 32f
        const val HUGE = 48f
    }

    /** Corner radii — the five shapes the app is allowed. */
    object Radius {
        const val XS = 4f
        const val S = 8f
        const val M = 12f
        const val L = 16f
        const val XL = 28f
    }

    /** Card/sheet resting elevations. */
    object Elevation {
        const val FLAT = 0f
        const val RAISED = 1f
        const val CARD = 3f
        const val SHEET = 6f
    }

    /** One icon size per role (50.3 draws from the same ladder). */
    object Icon {
        /** Inside text, chips, list rows. */
        const val INLINE = 16f

        /** Toolbar / row actions (RUN ▶, save, ⋮). */
        const val ACTION = 20f

        /** Bottom bar, drawer headers, empty-state heroes (small). */
        const val NAV = 24f
    }

    /**
     * Minimum touch target, both axes (M3 + WCAG 2.2 §2.5.8). Every icon
     * button in the six core surfaces sits in a box at least this big.
     */
    const val MIN_TOUCH = 48f

    /** The only sanctioned way to write a gap/padding/size. */
    fun space(v: Float): Dp = v.dp

    /** The only sanctioned way to write a corner radius. */
    fun radius(v: Float): Dp = v.dp

    /** The only sanctioned way to write a resting elevation. */
    fun elevation(v: Float): Dp = v.dp

    /** The only sanctioned way to write an icon size. */
    fun icon(v: Float): Dp = v.dp
}
