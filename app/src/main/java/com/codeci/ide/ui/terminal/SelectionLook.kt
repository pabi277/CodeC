package com.codeci.ide.ui.terminal

/**
 * Phase 103.1 round 2 — how a selection LOOKS, derived instead of guessed.
 *
 * The owner's first device round said it plainly: *"it is not looking good"*.
 * The cause was measurable. The band was `theme.selection` at its stored alpha,
 * and every bundled theme stores a **dark** translucent colour:
 *
 * | theme | `selection` | drawn over | contrast of the band vs the grid |
 * |---|---|---|---|
 * | Dracula | `#44475A` @ 40 % | `#282A36` | ~1.2:1 — invisible |
 * | Classic Dark | `#80CBC4` @ 40 % | `#121212` | visible, but tinted teal |
 * | Monokai | `#49483E` @ 40 % | `#272822` | ~1.1:1 — invisible |
 * | GitHub Dark | `#3B4048` @ 40 % | `#24292E` | ~1.1:1 — invisible |
 *
 * So the band was only ever legible on ONE of the four themes, and the handles
 * — `theme.cursor`, which is green on Dracula — sat on top of it as the only
 * thing visible. That is the image in the report.
 *
 * The fix keeps the Phase 40.5 law (no new colour literals, existing roles
 * only) by **deriving** the band from the theme's own foreground:
 *
 * - the band is the foreground at a low alpha, composited over the grid, so it
 *   is guaranteed to differ from the background by a fixed LUMINANCE step on
 *   every theme — a foreground is by definition the thing that contrasts with
 *   its own background;
 * - the handles use the cursor role (unchanged — it is the "where you can act"
 *   colour everywhere else in the terminal), but the STEM is drawn in the same
 *   band colour so the dot reads as attached to the text it belongs to.
 *
 * Pure on purpose: the alphas live here, `SelectionLookTest` proves the search
 * below chooses them, and the view only converts the result to a `Color`.
 */
object SelectionLook {

    /**
     * Minimum contrast (WCAG) of the composited band against the grid background.
     *
     * 1.35 is the floor at which a band reads as "this text is selected" in
     * peripheral vision on a phone without drowning the glyphs: the band sits
     * UNDER the text, so the text's own 4.5:1+ contrast is untouched (the
     * glyphs keep their own foreground colour).
     */
    const val MIN_BAND_CONTRAST = 1.35

    /**
     * The band's alpha over the grid, per theme, chosen so the composite clears
     * [MIN_BAND_CONTRAST] — see [bandAlphaFor].
     */
    val ALPHAS: FloatArray = floatArrayOf(0.30f, 0.38f, 0.46f, 0.56f)

    /** Relative luminance (WCAG 2.x) of a packed `0xAARRGGBB`, 0..1. */
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** WCAG contrast ratio between two packed opaque colours. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** `fg` at [alpha] flattened onto `bg`, packed opaque. */
    fun composite(fg: Int, bg: Int, alpha: Float): Int {
        val a = alpha.coerceIn(0f, 1f)
        fun mix(shift: Int): Int {
            val f = (fg shr shift) and 0xFF
            val b = (bg shr shift) and 0xFF
            return (f * a + b * (1f - a) + 0.5f).toInt().coerceIn(0, 255)
        }
        return OPAQUE or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /**
     * The alpha whose composite of [fg] over [bg] first clears
     * [MIN_BAND_CONTRAST]; the last entry when even that is not enough (a theme
     * whose foreground equals its background is broken, and the strongest band
     * is the honest answer rather than a silent no-op).
     */
    fun bandAlphaFor(fg: Int, bg: Int): Float {
        for (alpha in ALPHAS) {
            if (contrast(composite(fg, bg, alpha), bg) >= MIN_BAND_CONTRAST) return alpha
        }
        return ALPHAS.last()
    }

    /** The band colour for a theme's foreground over its background. */
    fun bandOver(fg: Int, bg: Int): Int = composite(fg, bg, bandAlphaFor(fg, bg))

    /**
     * The band for a run of glyphs that draws its OWN background (an inverse or
     * coloured cell). Those pixels sit inside the band, so the band has to be
     * legible against them too — otherwise a loud coloured block would swallow
     * the selection entirely.
     */
    fun bandOverCell(fg: Int, cellBg: Int, gridBg: Int): Int {
        val onGrid = bandOver(fg, gridBg)
        val alpha = bandAlphaFor(fg, cellBg)
        val onCell = composite(fg, cellBg, alpha)
        // Keep whichever of the two reads: a selected inverse cell inside a
        // selected run must still show that it is selected.
        return if (contrast(onCell, cellBg) >= contrast(onGrid, cellBg)) onCell else onGrid
    }

    private const val OPAQUE = 0xFF shl 24
}
