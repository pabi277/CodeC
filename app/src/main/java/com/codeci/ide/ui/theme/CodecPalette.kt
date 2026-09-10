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

    /** CodeC's own green — the launcher mark. Offered as an accent choice. */
    const val IDENTITY_GREEN = 0xFF3DDC84.toInt()

    /**
     * One selectable accent: the colour plus the name the picker shows.
     * `AppContrastTest` re-derives both themes for **every** choice, so the
     * picker can only ever offer accents that are readable.
     */
    data class AccentChoice(val argb: Int, val label: String)

    /** Every accent the Settings picker offers, in picker order. */
    val ACCENT_CHOICES: List<AccentChoice> = listOf(
        AccentChoice(0xFF6200EE.toInt(), "Violet"),   // Material violet (the historical default)
        AccentChoice(0xFF018786.toInt(), "Teal"),
        AccentChoice(0xFFB00020.toInt(), "Red"),
        AccentChoice(0xFF1976D2.toInt(), "Blue"),
        AccentChoice(0xFFFF9800.toInt(), "Orange"),
        AccentChoice(IDENTITY_GREEN, "CodeC green")   // the launcher mark's colour
    )

    /** `#FF6200EE` — the accent used when nothing is stored (unchanged). */
    const val DEFAULT_ACCENT = 0xFF6200EE.toInt()
}

/**
 * The four colour roles an accent has to fill, corrected for the theme it is
 * used in.
 *
 * Material 3 gets this from its tonal palette (primary = **tone 40 in light,
 * tone 80 in dark**); the app pushed one raw hex into `primary` for both, which
 * is how a violet intended for a light background ended up as dark-theme text
 * at 2.25:1. [rolesFor] reproduces the M3 relationship from the user's own
 * accent instead of or from a fixed seed.
 */
data class AccentRoles(
    val primary: Int,
    val onPrimary: Int,
    val container: Int,
    val onContainer: Int
) {
    /** True when the accent needed no correction (it already passed). */
    fun isUnchanged(seed: Int): Boolean = (seed and 0xFFFFFF) == (primary and 0xFFFFFF)
}

object AccentPalette {

    /** Container lightness for a dark theme (M3-ish tone ~30 for primary container). */
    private const val DARK_CONTAINER_L = 0.30

    /** Container lightness for a light theme (tone ~90). */
    private const val LIGHT_CONTAINER_L = 0.88

    /**
     * Derives the accent roles for [dark] theme on [surface].
     *
     * - `primary` keeps the accent's **hue and saturation** and only moves its
     *   lightness until it clears [Contrast.AA_TEXT] (4.5:1) against
     *   [surface]. An accent that already clears it is returned untouched.
     * - `onPrimary` is measured, not assumed (`Contrast.onColorFor`).
     * - `container`/`onContainer` are the tonal pair Material 3 uses for
     *   tonal buttons/chips, held to the same 4.5:1.
     */
    fun rolesFor(seed: Int, dark: Boolean, surface: Int): AccentRoles {
        val primary = Contrast.readableOn(
            seed = seed,
            surface = surface,
            target = Contrast.AA_TEXT,
            preferLighter = dark
        )
        val containerBase = Contrast.withLightness(
            primary,
            if (dark) DARK_CONTAINER_L else LIGHT_CONTAINER_L
        )
        // The container must not be unreadable either: pull it towards an
        // extreme until the "on" colour clears the text threshold.
        val onContainerBase = Contrast.onColorFor(containerBase)
        val container = if (Contrast.passes(onContainerBase, containerBase, Contrast.AA_TEXT)) {
            containerBase
        } else {
            Contrast.readableOn(containerBase, onContainerBase, Contrast.AA_TEXT, preferLighter = !dark)
        }
        val onContainer = Contrast.onColorFor(container)
        return AccentRoles(
            primary = primary,
            onPrimary = Contrast.onColorFor(primary),
            container = container,
            onContainer = Contrast.ensureReadable(onContainer, container, Contrast.AA_TEXT)
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
