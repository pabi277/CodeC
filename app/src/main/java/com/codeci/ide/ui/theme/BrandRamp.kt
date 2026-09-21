package com.codeci.ide.ui.theme

/**
 * Phase 50.2 — the brand ramp: every role from ONE seed.
 *
 * Built ON Phase 40.5, not beside it: [AccentPalette.rolesFor] already does
 * the hard part (keeps the hue, moves lightness until the pair clears WCAG
 * AA) across twelve roles. This object is the plan's contract over that
 * engine — the eight roles a brand needs plus the surface tint — with a
 * measured [contrastReport] so `BrandRampTest` can prove every pair clears
 * its threshold in both themes.
 *
 * The theme itself keeps reading the twelve-role engine directly (it needs
 * the container pairs too); the test pins this facade equal to the engine,
 * so the contract cannot drift from what is on screen. The one fix the
 * theme takes from the ramp's reasoning: `surfaceTint` is the brand's
 * primary, because the template violet was still leaking through it
 * (the old base was built from the template palette, and `copy()`
 * kept the tint).
 */
object BrandRamp {

    /** M3 light baseline surface — the same literal `AppContrastTest` uses. */
    const val LIGHT_SURFACE = 0xFFFFFBFE.toInt()

    /**
     * The brand's roles for one theme. [surface] is stored, not assumed, so
     * [contrastReport] measures the pairs against the surface they were
     * corrected for.
     */
    data class Roles(
        val primary: Int,
        val onPrimary: Int,
        val container: Int,
        val onContainer: Int,
        val secondary: Int,
        val onSecondary: Int,
        val tertiary: Int,
        val onTertiary: Int,
        val surfaceTint: Int,
        val surface: Int,
    )

    /**
     * Every role derived from [seed] for one theme: secondary keeps the
     * seed's hue with less chroma, tertiary rotates it a sixth of the wheel
     * (the M3 relationship, from the user's colour — never the template's
     * purple-grey and pink).
     */
    fun rolesFor(seed: Int, dark: Boolean): Roles {
        val surface = if (dark) CodecPalette.SURFACE_DARK else LIGHT_SURFACE
        val engine = AccentPalette.rolesFor(seed, dark, surface)
        return Roles(
            primary = engine.primary,
            onPrimary = engine.onPrimary,
            container = engine.container,
            onContainer = engine.onContainer,
            secondary = engine.secondary,
            onSecondary = engine.onSecondary,
            tertiary = engine.tertiary,
            onTertiary = engine.onTertiary,
            surfaceTint = engine.primary,
            surface = surface,
        )
    }

    /**
     * Every text pair the ramp owns, measured with the same [Contrast] util
     * `AppContrastTest` uses. All seven must clear [Contrast.AA_TEXT].
     */
    fun contrastReport(r: Roles): Map<String, Double> = mapOf(
        "primary/surface" to Contrast.ratio(r.primary, r.surface),
        "onPrimary/primary" to Contrast.ratio(r.onPrimary, r.primary),
        "onContainer/container" to Contrast.ratio(r.onContainer, r.container),
        "secondary/surface" to Contrast.ratio(r.secondary, r.surface),
        "onSecondary/secondary" to Contrast.ratio(r.onSecondary, r.secondary),
        "tertiary/surface" to Contrast.ratio(r.tertiary, r.surface),
        "onTertiary/tertiary" to Contrast.ratio(r.onTertiary, r.tertiary),
    )
}
