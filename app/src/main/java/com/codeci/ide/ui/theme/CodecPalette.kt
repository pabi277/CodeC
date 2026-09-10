package com.codeci.ide.ui.theme

/**
 * Phase 40.5 — the app's own colours, in one place, with the measured
 * contrast ratio of every pair they are used in.
 *
 * Owner report (2026-09-10): *"the color of the app's inside texts … Now it is
 * violet 💜 but not very good to read. Also correct other colors."* The audit
 * found the violet text (see [AccentRoles]) plus these measurable failures on
 * the dark surfaces the app actually uses:
 *
 * | was | surface | ratio | now | ratio |
 * |---|---|---|---|---|
 * | `#666666` panel hint | `#1E1E1E` | **2.90:1** | [MUTED_TEXT] `#9AA0A6` | 6.31:1 |
 * | `#666666` panel hint | `#121212` | **3.26:1** | [MUTED_TEXT] | 7.09:1 |
 * | `#777777` panel text | `#121212` | **4.18:1** | [MUTED_TEXT] | 7.09:1 |
 * | `#8A8A8A` muted text | `#24292E` card | **4.25:1** | [MUTED_TEXT] | 5.56:1 |
 * | white on tile `#F0863C` | project tile | **2.57:1** | [TILE_ORANGE] `#B45309` | 5.02:1 |
 * | white on tile `#4CAF50` | project tile | **2.78:1** | [TILE_GREEN] `#2E7D32` | 5.13:1 |
 * | white on tile `#3E7CC1` | project tile | **4.32:1** | [TILE_BLUE] `#35699F` | 5.72:1 |
 * | white on tile `#8B5CF6` | project tile | **4.23:1** | [TILE_VIOLET] `#7C4DEB` | 5.10:1 |
 *
 * Values that already passed their threshold were left exactly as they were
 * (`#E6B33C` 8.87:1, `#66BB6A` 7.24:1, `#EF5350` 4.91:1, `#BA68C8` 4.81:1,
 * `#55FF55` 14.11:1, `#66B2FF` 8.37:1, …) — this phase fixes readability, it
 * does not restyle the app.
 *
 * `AppContrastTest` re-measures every row of this table from these constants.
 */
object CodecPalette {

    // ---- surfaces the app draws text on (dark) -----------------------------

    /** Material 3 dark baseline surface/background — the app's default canvas. */
    const val SURFACE_DARK = 0xFF1C1B1F.toInt()

    /** Terminal + Output Panel canvas. */
    const val SURFACE_PANEL = 0xFF121212.toInt()

    /** VS Code Dark+ editor/terminal-chrome surface. */
    const val SURFACE_CODE = 0xFF1E1E1E.toInt()

    /** GitHub-dark card surface (Templates hub). */
    const val SURFACE_GITHUB = 0xFF24292E.toInt()

    // ---- text ---------------------------------------------------------------

    /**
     * Muted/secondary text on any dark surface the app uses: **≥ 5.56:1** in
     * the worst case (`SURFACE_GITHUB`) and 7.09:1 on the panel canvas.
     * Replaces `#666666`, `#777777` and the `#8A8A8A` that failed on cards.
     */
    const val MUTED_TEXT = 0xFF9AA0A6.toInt()

    /** The quietest text allowed anywhere: **≥ 4.77:1** worst case. */
    const val SUBTLE_TEXT = 0xFF8B949E.toInt()

    // ---- status (already passing; pinned so they cannot drift) --------------

    const val SUCCESS = 0xFF66BB6A.toInt()   // 7.24:1 on SURFACE_DARK
    const val DANGER = 0xFFEF5350.toInt()    // 4.91:1
    const val WARNING = 0xFFE6B33C.toInt()   // 8.87:1
    const val CONFLICT = 0xFFBA68C8.toInt()  // 4.81:1
    const val INFO = 0xFF64B5F6.toInt()      // 7.73:1

    // ---- project tiles (white content on the fill) --------------------------

    /**
     * Error text on a **dark chrome** surface (output panel, terminal strip).
     *
     * `DANGER` (#EF5350) is the fill/icon red: as *text* it measured 4.17:1 on
     * the terminal strip (#292929) and 4.21:1 on the output panel header
     * (#252526) — under AA. This is the app's established error-text red
     * (#FF5555), measured 4.63:1 on the strip, 4.87:1 on the header and
     * 5.31:1 on the panel body (#1E1E1E), so small red text always clears AA.
     * On a *theme-following* surface, derive with `Contrast.ensureReadable`
     * (the status bar does) instead of assuming a dark background.
     */
    const val ERROR_TEXT = 0xFFFF5555.toInt()

    const val ON_TILE = 0xFFFFFFFF.toInt()

    const val TILE_ORANGE = 0xFFB45309.toInt() // white 5.02:1
    const val TILE_BLUE = 0xFF35699F.toInt()   // white 5.72:1
    const val TILE_VIOLET = 0xFF7C4DEB.toInt() // white 5.10:1
    const val TILE_GREEN = 0xFF2E7D32.toInt()  // white 5.13:1
    const val TILE_GRAY = 0xFF6B7280.toInt()   // white 4.83:1

    // ---- Projects Hub "add" sheet ------------------------------------------

    /** Violet row fill, kept: the dark tint on it measures 5.78:1. */
    const val HUB_ROW_VIOLET = 0xFFA78BFA.toInt()
    const val ON_HUB_ROW_VIOLET = 0xFF241A4F.toInt()

    /** Indigo row fill; `#6366F1` under white measured 4.47:1 — this one is 5.90:1. */
    const val HUB_ROW_INDIGO = 0xFF4C51E0.toInt()

    /** Blue row fill; `#3B82F6` under white measured 3.68:1 — this one is 5.17:1. */
    const val HUB_ROW_BLUE = 0xFF2563EB.toInt()

    // ---- the identity colour (docs/icon/codec-mark.svg) --------------------

    /** CodeC's own green — the launcher mark. **The app's default accent.** */
    const val IDENTITY_GREEN = 0xFF3DDC84.toInt()

    /**
     * One selectable accent: the colour plus the name the picker shows.
     * `AppContrastTest` re-derives both themes for **every** choice, so the
     * picker can only ever offer accents that are readable.
     */
    data class AccentChoice(val argb: Int, val label: String)

    /**
     * Every accent the Settings picker offers, in picker order — **the default
     * first** (as the old hex list did with `#FF6200EE`).
     */
    val ACCENT_CHOICES: List<AccentChoice> = listOf(
        AccentChoice(IDENTITY_GREEN, "CodeC green"),  // the launcher mark's colour — the default
        AccentChoice(0xFF6200EE.toInt(), "Violet"),   // Material violet (the historical default)
        AccentChoice(0xFF018786.toInt(), "Teal"),
        AccentChoice(0xFFB00020.toInt(), "Red"),
        AccentChoice(0xFF1976D2.toInt(), "Blue"),
        AccentChoice(0xFFFF9800.toInt(), "Orange")
    )

    /**
     * `#FF3DDC84` — CodeC's own green, used when nothing is stored.
     *
     * It used to be `#FF6200EE` (the Android Studio template violet), which the
     * owner asked to change: *"Make the green as default"*. A **stored** accent
     * is never overwritten — anyone who chose Violet in the old hex list keeps
     * it, and Violet is one tap away in the picker.
     */
    const val DEFAULT_ACCENT = IDENTITY_GREEN
}

/**
 * The twelve colour roles an accent has to fill, corrected for the theme they
 * are used in.
 *
 * Material 3 gets this from its tonal palette (primary = **tone 40 in light,
 * tone 80 in dark**); the app pushed one raw hex into `primary` for both, which
 * is how a violet intended for a light background ended up as dark-theme text
 * at 2.25:1. [rolesFor] reproduces the M3 relationship from the user's own
 * accent instead of a fixed seed.
 *
 * `secondary`/`tertiary` are derived from the same accent too (M3 derives every
 * role from one source colour: secondary is the accent with less chroma,
 * tertiary the accent rotated to a contrasting hue). Before this, those roles
 * stayed on the purple/pink template — so an app with a green accent still
 * painted its log "DEBUG" line pink and its template chips purple-grey.
 */
data class AccentRoles(
    val primary: Int,
    val onPrimary: Int,
    val container: Int,
    val onContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int
) {
    /** True when the accent needed no correction (it already passed). */
    fun isUnchanged(seed: Int): Boolean = (seed and 0xFFFFFF) == (primary and 0xFFFFFF)
}

object AccentPalette {

    /** Container lightness for a dark theme (M3-ish tone ~30 for primary container). */
    private const val DARK_CONTAINER_L = 0.30

    /** Container lightness for a light theme (tone ~90). */
    private const val LIGHT_CONTAINER_L = 0.88

    /** M3 rotates the third role a sixth of the wheel away from the accent. */
    private const val TERTIARY_HUE_SHIFT = 60.0

    /** Secondary keeps the accent's hue and drops most of its chroma. */
    private const val SECONDARY_SATURATION = 0.5

    /** Tertiary keeps more chroma than secondary, but stays a tint of the hue. */
    private const val TERTIARY_SATURATION = 0.7

    /** The accent's hue with [degrees] of rotation and [saturationScale] chroma. */
    private fun hueShifted(seed: Int, degrees: Double, saturationScale: Double): Int {
        val (hue, saturation, lightness) = Contrast.hsl(seed)
        return Contrast.fromHsl(
            hue + degrees,
            (saturation * saturationScale).coerceIn(0.15, 1.0),
            lightness
        )
    }

    /**
     * The role as **text/fill**: hue kept, lightness moved only until it clears
     * [Contrast.AA_TEXT] (4.5:1) against [surface]. A colour that already
     * clears it is returned untouched.
     */
    private fun textRole(seed: Int, dark: Boolean, surface: Int): Int =
        Contrast.readableOn(seed = seed, surface = surface, target = Contrast.AA_TEXT, preferLighter = dark)

    /**
     * The tonal pair Material 3 uses for chips/tonal buttons: a container at
     * the theme's container lightness plus a readable colour for on it.
     * The pair is held to the same 4.5:1 (`onContainer` on `container`).
     */
    private fun containerRoles(role: Int, dark: Boolean): Pair<Int, Int> {
        val containerBase = Contrast.withLightness(role, if (dark) DARK_CONTAINER_L else LIGHT_CONTAINER_L)
        // The container must not be unreadable either: pull it towards an
        // extreme until the "on" colour clears the text threshold.
        val onContainerBase = Contrast.onColorFor(containerBase)
        val container = if (Contrast.passes(onContainerBase, containerBase, Contrast.AA_TEXT)) {
            containerBase
        } else {
            Contrast.readableOn(containerBase, onContainerBase, Contrast.AA_TEXT, preferLighter = !dark)
        }
        val onContainer = Contrast.onColorFor(container)
        return container to Contrast.ensureReadable(onContainer, container, Contrast.AA_TEXT)
    }

    /**
     * Derives every accent role for a [dark] theme on [surface].
     *
     * - `primary` is the accent itself, lightness-corrected to clear
     *   [Contrast.AA_TEXT] against [surface]; `onPrimary` is measured, not
     *   assumed (`Contrast.onColorFor`).
     * - `secondary` is the same hue with less chroma, `tertiary` the hue
     *   rotated by [TERTIARY_HUE_SHIFT] — the M3 relationship, taken from the
     *   user's colour instead of the template's purple/pink.
     * - Each role's container/on-container pair is held to the same 4.5:1.
     */
    fun rolesFor(seed: Int, dark: Boolean, surface: Int): AccentRoles {
        val primary = textRole(seed, dark, surface)
        val secondary = textRole(hueShifted(seed, 0.0, SECONDARY_SATURATION), dark, surface)
        val tertiary = textRole(hueShifted(seed, TERTIARY_HUE_SHIFT, TERTIARY_SATURATION), dark, surface)
        val (container, onContainer) = containerRoles(primary, dark)
        val (secondaryContainer, onSecondaryContainer) = containerRoles(secondary, dark)
        val (tertiaryContainer, onTertiaryContainer) = containerRoles(tertiary, dark)
        return AccentRoles(
            primary = primary,
            onPrimary = Contrast.onColorFor(primary),
            container = container,
            onContainer = onContainer,
            secondary = secondary,
            onSecondary = Contrast.onColorFor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = Contrast.onColorFor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer
        )
    }

    /** Parses `#RRGGBB` / `#AARRGGBB` (with or without `#`) — pure, no Compose. */
    fun parseHex(hex: String): Int? {
        val cleaned = hex.trim().removePrefix("#")
        return try {
            when (cleaned.length) {
                6 -> 0xFF000000.toInt() or cleaned.toLong(16).toInt()
                8 -> cleaned.toLong(16).toInt()
                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** `#RRGGBB` for a colour, for storage/preview (`accentHex`). */
    fun toHex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    /**
     * The stored form of the app's default accent — `#FF3DDC84` (CodeC green).
     * The one place the app's fallback accent is spelled out: `SettingsManager`
     * and every `collectAsState(initial = …)` read it from here, so a missing
     * value cannot mean two different colours in two places.
     */
    val DEFAULT_STORAGE_HEX: String =
        String.format("#FF%06X", CodecPalette.DEFAULT_ACCENT and 0xFFFFFF)

    /**
     * The accent a stored value means: [stored] itself, or the app default when
     * nothing was ever stored or the value cannot be parsed.
     *
     * A **stored** choice is returned verbatim — including the historical
     * `#FF6200EE`, so an accent the user picked is never silently rewritten.
     * (It is also why nobody has to migrate: the default only applies where no
     * value exists.)
     */
    fun effectiveStoredAccent(stored: String?): String =
        if (stored != null && parseHex(stored) != null) stored else DEFAULT_STORAGE_HEX

    // --- the Settings row's view of the choices (Phase 40.5) --------------
    // Kept here, not in the UI, so the picker and AppContrastTest derive the
    // labels/swatches/stored value from one source.

    /** The listed choice labels, in menu order. */
    val choiceLabels: List<String> get() = CodecPalette.ACCENT_CHOICES.map { it.label }

    /** [hex] masked to RGB (alpha dropped), for a swatch; null if unparseable. */
    fun rgbOf(hex: String): Int? = parseHex(hex)?.and(0xFFFFFF)

    /** The ARGB of a listed choice, by [label]; null when the label is unknown. */
    fun argbForLabel(label: String): Int? =
        CodecPalette.ACCENT_CHOICES.firstOrNull { it.label == label }?.argb

    /** The name to show for a stored [hex]: a listed name, else the hex itself. */
    fun labelFor(hex: String): String {
        val rgb = rgbOf(hex)
        return CodecPalette.ACCENT_CHOICES.firstOrNull { (it.argb and 0xFFFFFF) == rgb }?.label
            ?: hex
    }

    /** The value stored for a listed choice [label]: `#AARRGGBB`, or null. */
    fun storageHexFor(label: String): String? =
        argbForLabel(label)?.let { String.format("#FF%06X", it and 0xFFFFFF) }
}
