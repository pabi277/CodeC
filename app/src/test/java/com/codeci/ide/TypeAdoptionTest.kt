package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50.3 — the scale is wired and the code face is one (source scan).
 *
 * - `MyApplicationTheme` passes `CodecType.scale()`;
 * - no `fontSize = N.sp` literal survives in the six core files (sizes come
 *   from the scale — variables like the user's editor size are untouched);
 * - the named code surfaces (status-bar path, diff, output) read
 *   `CodecType.codeFamily`;
 * - hardcoded `FontFamily.Monospace` is gone everywhere except the two
 *   font-setting maps, where "Monospace" is the user's explicit named choice
 *   (the terminal setting even distinguishes it from "JetBrains Mono").
 */
class TypeAdoptionTest {

    private val sixFiles = listOf(
        "WelcomeScreen.kt",
        "EditorScreen.kt",
        "FileManagerScreen.kt",
        "ModulesScreen.kt",
        "TerminalScreen.kt",
        "SettingsScreen.kt",
    )

    @Test
    fun `the theme passes CodecType scale`() {
        val theme = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/theme/Theme.kt",
        ).readText()
        assertTrue(
            "MyApplicationTheme must pass CodecType.scale()",
            theme.contains("typography = CodecType.scale()"),
        )
    }

    @Test
    fun `no fontSize literal in the six files`() {
        val literal = Regex("""fontSize\s*=\s*\d""")
        for (name in sixFiles) {
            val code = RepoFiles.codeOnly(
                RepoFiles.mainSource(
                    "app/src/main/java/com/codeci/ide/ui/screens/$name",
                ).readText(),
            )
            assertFalse(
                "$name still sizes text with a literal: ${literal.find(code)?.value}",
                literal.containsMatchIn(code),
            )
        }
    }

    @Test
    fun `the named code surfaces read the code family`() {
        val named = listOf(
            "app/src/main/java/com/codeci/ide/ui/components/EditorStatusBar.kt",
            "app/src/main/java/com/codeci/ide/ui/screens/GitControlView.kt",
            "app/src/main/java/com/codeci/ide/ui/components/OutputPanelView.kt",
        )
        for (path in named) {
            val code = RepoFiles.mainSource(path).readText()
            assertTrue(
                "$path must read CodecType.codeFamily",
                code.contains("CodecType.codeFamily"),
            )
        }
    }

    @Test
    fun `the code family is the bundled JetBrains Mono`() {
        val codecType = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/theme/CodecType.kt",
        ).readText()
        assertTrue(codecType.contains("jetbrainsmono_medium"))
        assertTrue(codecType.contains("jetbrainsmono_bold"))
    }

    @Test
    fun `hardcoded platform monospace survives only in the font-setting maps`() {
        val hits = RepoFiles.mainKotlinSources()
            .filter { RepoFiles.codeOnly(it.readText()).contains("FontFamily.Monospace") }
            .map { it.name }
            .sorted()
        assertEquals(
            "FontFamily.Monospace must survive only where the user chose it by name",
            listOf("EditorScreen.kt", "SettingsScreen.kt"),
            hits,
        )
    }

    @Test
    fun `Type dot kt is gone`() {
        assertFalse(
            "Type.kt must stay deleted (the template one-liner lived there)",
            RepoFiles.mainSource(
                "app/src/main/java/com/codeci/ide/ui/theme/Type.kt",
            ).exists(),
        )
    }
}
