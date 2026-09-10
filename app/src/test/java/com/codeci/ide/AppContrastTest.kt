package com.codeci.ide

import com.codeci.ide.ui.theme.AccentPalette
import com.codeci.ide.ui.theme.CodecPalette
import com.codeci.ide.ui.theme.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 40.5 — the app's colours are machine-checked, not eyeballed.
 *
 * Owner report (2026-09-10): *"the color of the app's inside texts … Now it is
 * violet 💜 but not very good to read. Also correct other colors."* The cause
 * was measured, not guessed: the accent (`#FF6200EE` by default) was pushed
 * into `colorScheme.primary` **unchanged in both themes**, so in dark mode it
 * became violet text on a near-black surface at **2.25:1** (WCAG 2.2 §1.4.3
 * wants 4.5:1 for body text, §1.4.11 3:1 for icons/UI).
 *
 * This test re-measures every pair the app draws, plus the accent derivation
 * for every choice the Settings picker offers, in both themes — and audits the
 * four TextMate theme assets on disk. Any future colour edit that dips below
 * the threshold fails here instead of on the owner's phone.
 */
class AppContrastTest {

    private val darkSurfaces = listOf(
        "SURFACE_DARK" to CodecPalette.SURFACE_DARK,
        "SURFACE_PANEL" to CodecPalette.SURFACE_PANEL,
        "SURFACE_CODE" to CodecPalette.SURFACE_CODE,
        "SURFACE_GITHUB" to CodecPalette.SURFACE_GITHUB
    )

    // ---- the accent (the reported bug) -------------------------------------

    @Test
    fun `the historical default accent becomes readable in the dark theme`() {
        val seed = CodecPalette.DEFAULT_ACCENT
        // The bug, reproduced: the raw accent as dark-theme text.
        assertEquals(2.25, Contrast.ratio(seed, CodecPalette.SURFACE_DARK), 0.02)
        val roles = AccentPalette.rolesFor(seed, dark = true, surface = CodecPalette.SURFACE_DARK)
        assertTrue(
            "accent ${AccentPalette.toHex(roles.primary)} must clear 4.5:1 on the dark surface",
            Contrast.ratio(roles.primary, CodecPalette.SURFACE_DARK) >= Contrast.AA_TEXT
        )
        assertTrue(
            "onPrimary must be readable on the derived accent",
            Contrast.ratio(roles.onPrimary, roles.primary) >= Contrast.AA_TEXT
        )
        assertTrue(
            "the accent must actually change (was ${AccentPalette.toHex(seed)})",
            seed != roles.primary
        )
    }

    @Test
    fun `an accent that already passes is never restyled`() {
        val seed = CodecPalette.DEFAULT_ACCENT
        val light = AccentPalette.rolesFor(seed, dark = false, surface = 0xFFFFFBFE.toInt())
        assertEquals("light theme already passed at 7.44:1", seed, light.primary)
    }

    @Test
    fun `every accent choice is readable in both themes, hue preserved`() {
        val lightSurface = 0xFFFFFBFE.toInt()
        for (choice in CodecPalette.ACCENT_CHOICES) {
            for ((theme, dark, surface) in listOf(
                Triple("dark", true, CodecPalette.SURFACE_DARK),
                Triple("light", false, lightSurface)
            )) {
                val roles = AccentPalette.rolesFor(choice.argb, dark, surface)
                val name = "${choice.label}/$theme"
                assertTrue(
                    "$name primary ${AccentPalette.toHex(roles.primary)} " +
                        "= ${Contrast.ratio(roles.primary, surface)}:1 on the surface",
                    Contrast.ratio(roles.primary, surface) >= Contrast.AA_TEXT
                )
                assertTrue(
                    "$name onPrimary on the accent",
                    Contrast.ratio(roles.onPrimary, roles.primary) >= Contrast.AA_TEXT
                )
                assertTrue(
                    "$name onContainer on the container",
                    Contrast.ratio(roles.onContainer, roles.container) >= Contrast.AA_TEXT
                )
                // Hue survives the lightness move (greys have no meaningful hue).
                val seedHue = Contrast.hue(choice.argb)
                val delta = Math.abs(seedHue - Contrast.hue(roles.primary)).let { if (it > 180) 360 - it else it }
                assertTrue("$name hue moved by $delta deg", delta < 5.0)
            }
        }
    }

    @Test
    fun `an extreme accent in an extreme theme still lands on a readable colour`() {
        // Yellow on white and near-black on near-black are the two shapes that
        // used to end in an unusable primary.
        val onLight = AccentPalette.rolesFor(0xFFFFEB3B.toInt(), dark = false, surface = 0xFFFFFFFF.toInt())
        assertTrue(Contrast.ratio(onLight.primary, 0xFFFFFFFF.toInt()) >= Contrast.AA_TEXT)
        val onDark = AccentPalette.rolesFor(0xFF0B0B0B.toInt(), dark = true, surface = 0xFF000000.toInt())
        assertTrue(Contrast.ratio(onDark.primary, 0xFF000000.toInt()) >= Contrast.AA_TEXT)
    }

    @Test
    fun `the accent hex round-trips through storage`() {
        // The Settings row writes through storageHexFor and reads back through
        // labelFor: both are pinned here, so a picker change cannot silently
        // store a different value or lose a name.
        for (choice in CodecPalette.ACCENT_CHOICES) {
            val stored = AccentPalette.storageHexFor(choice.label)!!
            assertTrue("stored form of ${choice.label}", stored.startsWith("#FF") && stored.length == 9)
            assertEquals(choice.argb, AccentPalette.parseHex(stored))
            assertEquals(choice.label, AccentPalette.labelFor(stored))
            assertEquals(choice.argb, AccentPalette.argbForLabel(choice.label))
        }
        // A custom accent (an older build's value) shows exactly what is stored,
        // never a wrong name — the picker stays lossless.
        assertEquals("#FF123456", AccentPalette.labelFor("#FF123456"))
        assertEquals("#123456", AccentPalette.labelFor("#123456"))
        assertEquals(null, AccentPalette.argbForLabel("Nope"))
        assertEquals(null, AccentPalette.storageHexFor("Nope"))
        assertEquals(0x123456, AccentPalette.rgbOf("#FF123456"))
        assertEquals(CodecPalette.DEFAULT_ACCENT, AccentPalette.parseHex("#FF6200EE"))
        assertEquals(CodecPalette.DEFAULT_ACCENT, AccentPalette.parseHex("6200EE"))
        assertEquals(null, AccentPalette.parseHex("nope"))
        assertEquals("#6200EE", AccentPalette.toHex(CodecPalette.DEFAULT_ACCENT))
    }

    // ---- every text pair the palette documents ------------------------------

    @Test
    fun `muted and subtle text clear AA on every dark surface`() {
        for ((surfaceName, surface) in darkSurfaces) {
            assertTrue(
                "MUTED_TEXT on $surfaceName",
                Contrast.ratio(CodecPalette.MUTED_TEXT, surface) >= Contrast.AA_TEXT
            )
            assertTrue(
                "SUBTLE_TEXT on $surfaceName",
                Contrast.ratio(CodecPalette.SUBTLE_TEXT, surface) >= Contrast.AA_TEXT
            )
        }
    }

    @Test
    fun `white content on every project tile clears AA`() {
        val tiles = mapOf(
            "TILE_ORANGE" to CodecPalette.TILE_ORANGE,
            "TILE_BLUE" to CodecPalette.TILE_BLUE,
            "TILE_VIOLET" to CodecPalette.TILE_VIOLET,
            "TILE_GREEN" to CodecPalette.TILE_GREEN,
            "TILE_GRAY" to CodecPalette.TILE_GRAY
        )
        for ((name, fill) in tiles) {
            assertTrue(
                "$name white letter/icon = ${Contrast.ratio(CodecPalette.ON_TILE, fill)}:1",
                Contrast.ratio(CodecPalette.ON_TILE, fill) >= Contrast.AA_TEXT
            )
        }
    }

    @Test
    fun `hub sheet rows clear the icon threshold`() {
        assertTrue(Contrast.ratio(CodecPalette.ON_HUB_ROW_VIOLET, CodecPalette.HUB_ROW_VIOLET) >= Contrast.AA_TEXT)
        assertTrue(Contrast.ratio(0xFFFFFFFF.toInt(), CodecPalette.HUB_ROW_INDIGO) >= Contrast.AA_TEXT)
        assertTrue(Contrast.ratio(0xFFFFFFFF.toInt(), CodecPalette.HUB_ROW_BLUE) >= Contrast.AA_TEXT)
    }

    @Test
    fun `status colours clear AA as text on the dark surface`() {
        val status = mapOf(
            "SUCCESS" to CodecPalette.SUCCESS,
            "DANGER" to CodecPalette.DANGER,
            "WARNING" to CodecPalette.WARNING,
            "CONFLICT" to CodecPalette.CONFLICT,
            "INFO" to CodecPalette.INFO
        )
        for ((name, colour) in status) {
            val ratio = Contrast.ratio(colour, CodecPalette.SURFACE_DARK)
            assertTrue("$name = $ratio:1", ratio >= Contrast.AA_TEXT)
        }
    }

    @Test
    fun `the old failing values are measurably worse than their replacements`() {
        // Guards the table in CodecPalette's docs against a "restore the old
        // colour" edit: each replacement is a real improvement, not a taste.
        val rows = listOf(
            Triple(0xFF666666.toInt(), CodecPalette.MUTED_TEXT, CodecPalette.SURFACE_CODE),
            Triple(0xFF777777.toInt(), CodecPalette.MUTED_TEXT, CodecPalette.SURFACE_PANEL),
            Triple(0xFF8A8A8A.toInt(), CodecPalette.MUTED_TEXT, CodecPalette.SURFACE_GITHUB),
            Triple(0xFFF0863C.toInt(), CodecPalette.TILE_ORANGE, CodecPalette.ON_TILE),
            Triple(0xFF4CAF50.toInt(), CodecPalette.TILE_GREEN, CodecPalette.ON_TILE),
            Triple(0xFF3E7CC1.toInt(), CodecPalette.TILE_BLUE, CodecPalette.ON_TILE),
            Triple(0xFF8B5CF6.toInt(), CodecPalette.TILE_VIOLET, CodecPalette.ON_TILE),
            Triple(0xFF6366F1.toInt(), CodecPalette.HUB_ROW_INDIGO, CodecPalette.ON_TILE),
            Triple(0xFF3B82F6.toInt(), CodecPalette.HUB_ROW_BLUE, CodecPalette.ON_TILE)
        )
        for ((old, new, background) in rows) {
            val before = Contrast.ratio(old, background)
            val after = Contrast.ratio(new, background)
            assertTrue(
                "${AccentPalette.toHex(old)} was $before:1, ${AccentPalette.toHex(new)} is $after:1",
                after > before && after >= Contrast.AA_NON_TEXT
            )
        }
    }

    // ---- the editor themes on disk ------------------------------------------

    @Test
    fun `every TextMate theme keeps comments and line numbers readable`() {
        val themes = RepoFiles.mainSource("app/src/main/assets/textmate/themes").listFiles()
            ?.filter { it.extension == "json" }
            .orEmpty()
        assertTrue("expected the four theme assets", themes.size >= 4)
        for (theme in themes) {
            val json = theme.readText()
            val background = jsonString(json, "editor.background")
            assertTrue("${theme.name}: no editor.background", background != null)
            val bg = requireNotNull(background)
            for (key in listOf("editorLineNumber.foreground", "editorLineNumber.activeForeground")) {
                val value = jsonString(json, key) ?: continue
                val ratio = Contrast.ratio(rgb(value), rgb(bg))
                assertTrue(
                    "${theme.name} $key $value on $bg = $ratio:1",
                    ratio >= Contrast.AA_TEXT
                )
            }
            val comment = commentColour(json)
            assertTrue("${theme.name}: no comment token colour", comment != null)
            val ratio = Contrast.ratio(rgb(requireNotNull(comment)), rgb(bg))
            assertTrue("${theme.name} comment ${comment} on $bg = $ratio:1", ratio >= Contrast.AA_TEXT)
        }
    }

    @Test
    fun `the Kotlin mirrors agree with the theme assets they describe`() {
        // The Compose editor and the TextMate asset are two renderings of the
        // same theme: if the comment colour drifts between them, the file the
        // user sees in the app stops matching the theme they picked. Pinned
        // both to the asset and to the value this repair chose.
        val expected = listOf(
            "vscode-dark-plus" to "#6A9955", // upstream VS Code value, already AA (5.00:1)
            "monokai" to "#9C9678",
            "dracula" to "#8DA0D0",
            "github-dark" to "#8B949E",
        )
        for ((asset, value) in expected) {
            val json = RepoFiles.mainSource("app/src/main/assets/textmate/themes/$asset.json").readText()
            val inAsset = commentColour(json)
            assertTrue("$asset: no comment token colour in the asset", inAsset != null)
            assertEquals(
                "$asset: the Compose mirror drifted from the asset's comment colour",
                inAsset,
                ThemeMirrors.commentOf(asset)
            )
            assertEquals("$asset: the pinned comment colour changed", value, ThemeMirrors.commentOf(asset))
        }
    }

    // ---- helpers -------------------------------------------------------------

    /** The value of `"key": "#RRGGBB"` in a TextMate theme (flat scan). */
    private fun jsonString(json: String, key: String): String? {
        val marker = "\"$key\""
        val at = json.indexOf(marker)
        if (at < 0) return null
        val afterColon = json.indexOf(':', at + marker.length)
        if (afterColon < 0) return null
        val open = json.indexOf('"', afterColon)
        if (open < 0) return null
        val close = json.indexOf('"', open + 1)
        if (close < 0) return null
        return json.substring(open + 1, close)
    }

    /** The first `comment` token colour in a TextMate theme. */
    private fun commentColour(json: String): String? {
        val tokens = json.lastIndexOf("\"tokenColors\"")
        if (tokens < 0) return null
        val body = json.substring(tokens)
        val commentAt = body.indexOf("\"comment\"")
        if (commentAt < 0) return null
        return jsonString(body.substring(commentAt), "foreground")
    }

    private fun rgb(hex: String): Int =
        AccentPalette.parseHex(hex) ?: error("not a colour: $hex")
}

/**
 * Reads the `comment =` colours out of `EditorThemes.kt` (the Compose mirror of
 * the TextMate assets) so the two can never silently disagree.
 */
private object ThemeMirrors {

    fun commentOf(themeKey: String): String {
        val source = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/theme/EditorThemes.kt").readText()
        val valName = when (themeKey) {
            "vscode-dark-plus" -> "VSCodeDarkPlusTheme"
            "monokai" -> "MonokaiTheme"
            "dracula" -> "DraculaTheme"
            "github-dark" -> "GitHubDarkTheme"
            else -> error("unknown theme $themeKey")
        }
        val at = source.indexOf("val $valName = EditorThemeColors(")
        if (at < 0) error("$valName not found in EditorThemes.kt")
        // A block ends on its own `)` line — cutting at the first `)` would
        // stop inside `background = Color(0xFF…)`.
        val block = source.substring(at, source.indexOf("\n)", at))
        val comment = Regex("""comment = Color\(0x([0-9A-Fa-f]{8})\)""")
            .find(block)?.groupValues?.get(1)
            ?: error("no comment colour in $valName")
        return "#" + comment.takeLast(6)
    }
}
