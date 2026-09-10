package com.codeci.ide.ui.theme

/**
 * Phase 40.5 — pure, Android-free colour-contrast maths (WCAG 2.2).
 *
 * Why this exists: the app's accent is user-configurable and the old code
 * pushed it into `colorScheme.primary` unchanged in BOTH themes. The accent
 * (`#FF6200EE`, the Android Studio template violet — the default until 40.5)
 * therefore became *text* on a near-black surface in dark mode — a measured
 * **2.25:1**, where WCAG 2.2 §1.4.3 requires 4.5:1 for body text (§1.4.11:
 * 3:1 for non-text). That is exactly the owner's report: *"it is violet but
 * not very good to read"*.
 *
 * Everything here is integer/float maths with no Compose or Android import, so
 * it runs in `:app:testDebugUnitTest` and can pin every colour pair in the app.
 *
 * Thresholds (WCAG 2.2 Level AA):
 *  - [AA_TEXT] 4.5:1 — normal text (under 18pt, i.e. under ~24px; and under
 *    14pt/≈18.67px bold);
 *  - [AA_LARGE] 3.0:1 — large text (≥18pt regular or ≥14pt bold);
 *  - [AA_NON_TEXT] 3.0:1 — UI components and graphical objects (icons,
 *    borders, focus rings, switches).
 */
object Contrast {

    const val AA_TEXT = 4.5
    const val AA_LARGE = 3.0
    const val AA_NON_TEXT = 3.0

    /** WCAG 2.2 relative luminance of an opaque ARGB colour. */
    fun relativeLuminance(argb: Int): Double {
        val r = channel(argb, 16)
        val g = channel(argb, 8)
        val b = channel(argb, 0)
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    /** WCAG 2.2 contrast ratio, always >= 1.0 (order does not matter). */
    fun ratio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    fun passes(fg: Int, bg: Int, target: Double = AA_TEXT): Boolean = ratio(fg, bg) >= target

    /**
     * Flattens a translucent colour onto [bg] (`Color.copy(alpha = …)` over a
     * surface). Needed because several app colours are drawn at 12–60 % alpha,
     * and the *composited* colour is what the user actually sees.
     */
    fun composite(fg: Int, bg: Int, alpha: Float): Int {
        val a = alpha.coerceIn(0f, 1f)
        fun mix(shift: Int): Int {
            val f = channel(fg, shift)
            val b = channel(bg, shift)
            return (f * a + b * (1f - a) + 0.5f).toInt().coerceIn(0, 255)
        }
        return opaque(mix(16), mix(8), mix(0))
    }

    fun opaque(r: Int, g: Int, b: Int): Int =
        0xFF shl 24 or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    /** Hue in degrees [0, 360) — used to prove a corrected accent keeps its hue. */
    fun hue(argb: Int): Double {
        val r = channel(argb, 16) / 255.0
        val g = channel(argb, 8) / 255.0
        val b = channel(argb, 0) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        if (delta == 0.0) return 0.0
        val h = when (max) {
            r -> ((g - b) / delta) % 6.0
            g -> (b - r) / delta + 2.0
            else -> (r - g) / delta + 4.0
        }
        return ((h * 60.0) + 360.0) % 360.0
    }

    /** HSL lightness [0, 1]. */
    fun lightness(argb: Int): Double {
        val r = channel(argb, 16) / 255.0
        val g = channel(argb, 8) / 255.0
        val b = channel(argb, 0) / 255.0
        return (maxOf(r, g, b) + minOf(r, g, b)) / 2.0
    }

    /** Same hue and saturation, new HSL lightness. */
    fun withLightness(argb: Int, lightness: Double): Int {
        val (h, s, _) = hsl(argb)
        return fromHsl(h, s, lightness.coerceIn(0.0, 1.0))
    }

    /** `(hue°, saturation, lightness)` — saturation/lightness in [0, 1]. */
    fun hsl(argb: Int): Triple<Double, Double, Double> {
        val r = channel(argb, 16) / 255.0
        val g = channel(argb, 8) / 255.0
        val b = channel(argb, 0) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2.0
        val delta = max - min
        if (delta == 0.0) return Triple(hue(argb), 0.0, l)
        val s = delta / (1.0 - Math.abs(2.0 * l - 1.0))
        return Triple(hue(argb), s.coerceIn(0.0, 1.0), l)
    }

    fun fromHsl(hue: Double, saturation: Double, lightness: Double): Int {
        val s = saturation.coerceIn(0.0, 1.0)
        val l = lightness.coerceIn(0.0, 1.0)
        val c = (1.0 - Math.abs(2.0 * l - 1.0)) * s
        val h = ((hue % 360.0) + 360.0) % 360.0 / 60.0
        val x = c * (1.0 - Math.abs(h % 2.0 - 1.0))
        val (r1, g1, b1) = when (h.toInt()) {
            0 -> Triple(c, x, 0.0)
            1 -> Triple(x, c, 0.0)
            2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c)
            4 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        val m = l - c / 2.0
        return opaque(
            ((r1 + m) * 255.0 + 0.5).toInt(),
            ((g1 + m) * 255.0 + 0.5).toInt(),
            ((b1 + m) * 255.0 + 0.5).toInt()
        )
    }

    /**
     * The smallest change to [seed]'s lightness that makes it readable on
     * [surface], preserving hue and saturation.
     *
     * - Already readable ⇒ returned **unchanged** (so a colour that passes is
     *   never restyled — the light theme keeps the exact accent the user chose).
     * - [preferLighter] (dark themes) walks up; otherwise walks down.
     *
     * The scan is 0.5 %-granular and monotonic in lightness, so it always lands
     * on the *first* passing value rather than the most extreme one.
     */
    fun readableOn(
        seed: Int,
        surface: Int,
        target: Double = AA_TEXT,
        preferLighter: Boolean = true
    ): Int {
        val opaqueSeed = opaque(channel(seed, 16), channel(seed, 8), channel(seed, 0))
        if (passes(opaqueSeed, surface, target)) return opaqueSeed
        val (h, s, l) = hsl(opaqueSeed)
        val steps = 200
        // Walk towards the extreme in 0.5 %-ish steps: up for a dark theme,
        // down for a light one.
        val delta = if (preferLighter) (1.0 - l) / steps else -l / steps
        var candidate = opaqueSeed
        var i = 1
        while (i <= steps) {
            candidate = fromHsl(h, s, l + delta * i)
            if (passes(candidate, surface, target)) return candidate
            i++
        }
        // Terminal fallback: the *better* of the two extremes, not the one the
        // walk happened to head for. A mid-luminance surface (a light accent
        // tint, e.g. the default green over a dark theme at 50 %) is beaten by
        // black even though its walk was "lighter"; returning white there
        // measured 4.43:1 — short of the 4.5 this function promises. One of
        // white/black is always >= 4.58:1 on any background.
        return bestExtremeFor(surface)
    }

    /**
     * [fg] forced to clear [target] on [bg], moving towards whichever extreme
     * ([background] is further from) helps more. Used for "on container"
     * colours, where the hue stays but the lightness has to be paid for.
     */
    fun ensureReadable(fg: Int, bg: Int, target: Double = AA_TEXT): Int {
        if (passes(fg, bg, target)) return fg
        // Pick the direction by the ratio the two *extremes* actually achieve,
        // so the walk can always reach [target] (the previous comparison used
        // #101014, the "on" colour, which is not the darkest reachable).
        return readableOn(fg, bg, target, preferLighter = ratio(0xFFFFFFFF.toInt(), bg) >= ratio(0xFF000000.toInt(), bg))
    }

    /** Whichever of pure white / pure black measures better on [background]. */
    fun bestExtremeFor(background: Int): Int {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        return if (ratio(white, background) >= ratio(black, background)) white else black
    }

    /**
     * The better of a light and a dark "on" colour for [background] — the
     * `onPrimary` question. Picks by measured ratio, never by guesswork.
     */
    fun onColorFor(
        background: Int,
        light: Int = 0xFFFFFFFF.toInt(),
        dark: Int = 0xFF101014.toInt()
    ): Int = if (ratio(light, background) >= ratio(dark, background)) light else dark

    private fun channel(argb: Int, shift: Int): Int = (argb shr shift) and 0xFF

    private fun linear(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
}
