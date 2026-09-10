package com.codeci.ide

import com.codeci.ide.ui.theme.AccentPalette
import com.codeci.ide.ui.theme.CodecPalette
import com.codeci.ide.ui.theme.Contrast
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 40.5 — pins the *chrome* colours the way `AppContrastTest` pins the
 * palette: the translucent overlays and tinted strips are re-derived here from
 * the numbers in the UI sources, so raising an alpha (a key-cap tint from 0.5
 * to 0.6, a status-strip wash from 0.5 to 0.7) fails the build instead of
 * quietly dimming text the owner has to read.
 *
 * The theme values are Material 3's baseline roles — what `darkColorScheme()` /
 * `lightColorScheme()` hand the chrome, and what an accent is measured against
 * once [AccentPalette.rolesFor] has corrected it.
 */
class ChromeContrastTest {

    private data class Roles(
        val name: String,
        val dark: Boolean,
        val surface: Int,
        val surfaceVariant: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val outline: Int,
    )

    private val themes = listOf(
        Roles("dark", true, 0xFF1C1B1F.toInt(), 0xFF49454F.toInt(), 0xFFE6E1E5.toInt(), 0xFFCAC4D0.toInt(), 0xFF938F99.toInt()),
        Roles("light", false, 0xFFFFFBFE.toInt(), 0xFFE7E0EC.toInt(), 0xFF1C1B1F.toInt(), 0xFF49454F.toInt(), 0xFF79747E.toInt()),
    )

    private fun primaryOf(theme: Roles): Int =
        AccentPalette.rolesFor(CodecPalette.DEFAULT_ACCENT, theme.dark, theme.surface).primary

    private fun source(path: String): String = RepoFiles.mainSource(path).readText()

    private fun alphaIn(source: String, pattern: String, what: String): Float {
        val m = Regex(pattern).find(source)
        assertTrue("$what not found (looked for $pattern)", m != null)
        return m!!.groupValues[1].toFloat()
    }

    /** The strip the theme draws behind status-bar and key-cap text. */
    private fun strip(theme: Roles, alpha: Float = 0.5f): Int =
        Contrast.composite(theme.surfaceVariant, theme.surface, alpha)

    private fun require(label: String, fg: Int, bg: Int, need: Double) {
        val ratio = Contrast.ratio(fg, bg)
        assertTrue("$label = %.2f:1 (needs %.1f)".format(ratio, need), ratio >= need)
    }

    @Test
    fun `status bar text clears AA on the strip it is actually drawn on`() {
        val src = source("app/src/main/java/com/codeci/ide/ui/components/EditorStatusBar.kt")
        val stripAlpha = alphaIn(src, """surfaceVariant\.copy\(alpha = ([0-9.]+)f\)""", "status-strip alpha")
        assertTrue(
            "the status bar must correct its colours against the strip (onStrip)",
            src.contains("fun onStrip(")
        )
        for (theme in themes) {
            val bar = strip(theme, stripAlpha)
            // Everything meaningful on the strip goes through onStrip(…).
            for ((label, colour) in listOf(
                "segment text" to theme.onSurfaceVariant,
                "selection count (accent)" to primaryOf(theme),
                "error red" to 0xFFFF5555.toInt(),
                "warning amber" to 0xFFFFB347.toInt(),
            )) {
                val corrected = Contrast.ensureReadable(colour, bar, Contrast.AA_TEXT)
                require("${theme.name} status bar $label (corrected)", corrected, bar, Contrast.AA_TEXT)
            }
        }
    }

    @Test
    fun `a key-cap tint keeps the cap label readable in both themes`() {
        val src = source("app/src/main/java/com/codeci/ide/ui/keyboard/CodecKeyboard.kt")
        // The cap's tints are declared together (`val tintAlpha: Float? = when {…}`);
        // reading them from there means raising one fails this test.
        val tintBlock = Regex("""val tintAlpha: Float\? = when \{(.*?)\n    \}""", RegexOption.DOT_MATCHES_ALL)
            .find(src)?.groupValues?.get(1)
        assertTrue(
            "the cap tints must be declared together (val tintAlpha: Float? = when {…})",
            tintBlock != null
        )
        val tints = Regex("""([0-9.]+)f""").findAll(tintBlock!!).map { it.groupValues[1].toFloat() }.toList()
        assertTrue("no accent cap tints found in CodecKeyboard.kt", tints.isNotEmpty())
        val strongest = tints.max()
        // The cap's own colour is the single source for what is drawn on it: the
        // label, the corner hint and the dot all correct against it. Without
        // that, a LIGHT accent (the default green) put near-white onSurface text
        // on a light green cap — 3.43:1, measured on the dark theme.
        assertTrue(
            "the keyboard must derive its cap colours from the cap it draws (capRgb)",
            src.contains("val capRgb = Contrast.composite(") && src.contains("fun onCap(")
        )
        for (theme in themes) {
            val cap = Contrast.composite(theme.surface, strip(theme), 0.9f)
            val activeCap = Contrast.composite(primaryOf(theme), strip(theme), strongest)
            // Plain cap: onSurface is used raw, so it must clear AA on its own.
            require("${theme.name} keycap label on a plain cap", theme.onSurface, cap, Contrast.AA_TEXT)
            // Tinted cap: the label is corrected against the tint, so the
            // corrected colour must clear AA — and for a light accent the raw
            // one would not, which is why the correction is load-bearing.
            val label = Contrast.ensureReadable(theme.onSurface, activeCap, Contrast.AA_TEXT)
            require("${theme.name} keycap label on the strongest tint ($strongest)", label, activeCap, Contrast.AA_TEXT)
            // The corner dot is a graphic: onSurface where it clears 3:1, else
            // the readable extreme (which the source picks at runtime).
            val dot = if (Contrast.ratio(theme.onSurface, activeCap) >= Contrast.AA_NON_TEXT) {
                theme.onSurface
            } else {
                Contrast.onColorFor(activeCap)
            }
            require("${theme.name} keycap dot on the strongest tint", dot, activeCap, Contrast.AA_NON_TEXT)
            // The corner hint and the held-release preview are text on the cap.
            val hint = Contrast.ensureReadable(primaryOf(theme), cap, Contrast.AA_TEXT)
            require("${theme.name} keycap corner hint (corrected)", hint, cap, Contrast.AA_TEXT)
        }
        // …and the case the default green makes unavoidable: a light accent in
        // the dark theme must NEED that correction.
        val darkTheme = themes.first { it.dark }
        val greenCap = Contrast.composite(primaryOf(darkTheme), strip(darkTheme), strongest)
        assertTrue(
            "a light accent tint must need a corrected label (raw onSurface was " +
                "%.2f:1)".format(Contrast.ratio(darkTheme.onSurface, greenCap)),
            Contrast.ratio(darkTheme.onSurface, greenCap) < Contrast.AA_TEXT
        )
    }

    @Test
    fun `bottom navigation labels clear AA when idle in both themes`() {
        val src = source("app/src/main/java/com/codeci/ide/MainActivity.kt")
        val idle = alphaIn(
            src,
            """val idleColor = MaterialTheme\.colorScheme\.onSurfaceVariant\.copy\(alpha = ([0-9.]+)f\)""",
            "bottom-nav idle alpha"
        )
        for (theme in themes) {
            val idleColour = Contrast.composite(theme.onSurfaceVariant, theme.surface, idle)
            require("${theme.name} bottom-nav idle label at alpha $idle", idleColour, theme.surface, Contrast.AA_TEXT)
        }
    }

    @Test
    fun `suggestion chips keep their glyph and label readable`() {
        for (theme in themes) {
            val roles = AccentPalette.rolesFor(CodecPalette.DEFAULT_ACCENT, theme.dark, theme.surface)
            // Filled (ghost-backed) chip: onPrimary on the opaque accent.
            require("${theme.name} suggestion ghost glyph", roles.onPrimary, roles.primary, Contrast.AA_TEXT)
            // Idle chip: onSurface on surfaceVariant at 45%.
            val chip = Contrast.composite(theme.surfaceVariant, theme.surface, 0.45f)
            require("${theme.name} suggestion idle glyph", theme.onSurface, chip, Contrast.AA_TEXT)
        }
    }

    @Test
    fun `fixed dark chrome text clears AA on the panel it uses`() {
        // The output panel and the share panel keep dark backgrounds in both
        // app themes, so their tokens are measured once — and pinned here.
        val pairs = listOf(
            Triple("output header legend", CodecPalette.MUTED_TEXT, 0xFF252526.toInt()),
            Triple("output body hint", CodecPalette.MUTED_TEXT, 0xFF1E1E1E.toInt()),
            Triple("output panel background", CodecPalette.MUTED_TEXT, 0xFF121212.toInt()),
            Triple("share panel URL", CodecPalette.MUTED_TEXT, 0xFF1B1F24.toInt()),
            Triple("QR fallback", CodecPalette.MUTED_TEXT, 0xFF2A2A2A.toInt()),
            Triple("vscode comment", 0xFF6A9955.toInt(), 0xFF1E1E1E.toInt()),
            Triple("terminal strip status", CodecPalette.ERROR_TEXT, 0xFF292929.toInt()),
            Triple("terminal strip warning", 0xFFB347.toInt() or 0xFFFF0000.toInt(), 0xFF292929.toInt()),
        )
        for ((label, fg, bg) in pairs) {
            require(label, fg, bg, Contrast.AA_TEXT)
        }
        // The value this phase replaced: proof it was the problem, kept as a
        // regression guard so it cannot come back as "good enough".
        val old = Contrast.ratio(0xFF8A8A8A.toInt(), 0xFF252526.toInt())
        assertTrue("the old legend #8A8A8A on #252526 must stay below AA (was %.2f:1)".format(old), old < Contrast.AA_TEXT)
    }

    @Test
    fun `accent graphics and boundaries clear the non-text threshold`() {
        for (theme in themes) {
            val graphics = listOf(
                "accent chip border" to primaryOf(theme),
                "accent icon" to primaryOf(theme),
                "conflict border" to CodecPalette.CONFLICT,
                "outline border" to theme.outline,
            )
            for ((label, colour) in graphics) {
                require("${theme.name} $label", colour, theme.surface, Contrast.AA_NON_TEXT)
            }
        }
        // White content on every project tile.
        for ((label, tile) in listOf(
            "orange" to CodecPalette.TILE_ORANGE,
            "blue" to CodecPalette.TILE_BLUE,
            "violet" to CodecPalette.TILE_VIOLET,
            "green" to CodecPalette.TILE_GREEN,
            "gray" to CodecPalette.TILE_GRAY,
        )) {
            require("white content on the $label tile", CodecPalette.ON_TILE, tile, Contrast.AA_NON_TEXT)
        }
    }

    @Test
    fun `the theme carries the accent roles, not a raw hex`() {
        val theme = source("app/src/main/java/com/codeci/ide/ui/theme/Theme.kt")
        assertTrue(
            "Theme.kt must derive primary/onPrimary/primaryContainer from rolesFor",
            theme.contains("AccentPalette.rolesFor(") &&
                theme.contains("onPrimary = Color(roles.onPrimary)") &&
                theme.contains("primaryContainer = Color(roles.container)")
        )
        assertTrue(
            "secondary/tertiary must come from the accent too (no template purple/pink left)",
            theme.contains("secondary = Color(roles.secondary)") &&
                theme.contains("tertiary = Color(roles.tertiary)") &&
                theme.contains("onSecondaryContainer = Color(roles.onSecondaryContainer)")
        )
        assertTrue(
            "the raw-accent-into-primary path must be gone",
            !theme.contains("primary = accentColor")
        )
    }
}
