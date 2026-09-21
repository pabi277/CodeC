package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50.2 — the Android Studio template palette is deleted and cannot
 * come back (source scan). `Color.kt` held the six constants; `Theme.kt`
 * built its base scheme from them — including the `surfaceTint` nobody
 * overrode, which washed every card and sheet violet even with a green
 * accent.
 */
class TemplatePaletteRemovedTest {

    private val templateNames = listOf(
        "Purple80", "PurpleGrey80", "Pink80",
        "Purple40", "PurpleGrey40", "Pink40",
    )

    @Test
    fun `no template colour name survives in production code`() {
        val hits = mutableListOf<String>()
        for (file in RepoFiles.mainKotlinSources()) {
            val code = RepoFiles.codeOnly(file.readText())
            for (name in templateNames) {
                if (Regex("""\b$name\b""").containsMatchIn(code)) {
                    hits.add("${file.name}: $name")
                }
            }
        }
        assertTrue(
            "template palette references survive:\n" + hits.joinToString("\n"),
            hits.isEmpty(),
        )
    }

    @Test
    fun `Color dot kt is gone`() {
        assertFalse(
            "Color.kt must stay deleted (the template palette lived there)",
            RepoFiles.mainSource(
                "app/src/main/java/com/codeci/ide/ui/theme/Color.kt",
            ).exists(),
        )
    }

    @Test
    fun `the theme builds its base from plain M3`() {
        val theme = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/theme/Theme.kt",
        ).readText()
        assertTrue(theme.contains("darkColorScheme()"))
        assertTrue(theme.contains("lightColorScheme()"))
    }

    @Test
    fun `the theme tints surfaces with the brand primary`() {
        val theme = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/theme/Theme.kt",
        ).readText()
        assertTrue(
            "surfaceTint must be the corrected brand primary, not the base default",
            theme.contains("surfaceTint = Color(roles.primary)"),
        )
    }

    @Test
    fun `the theme takes an explicit brand mode`() {
        val theme = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/theme/Theme.kt",
        ).readText()
        assertTrue(theme.contains("brandMode: BrandMode"))
        assertTrue(
            "the legacy dynamicColor flag must not come back",
            !theme.contains("dynamicColor"),
        )
    }
}
