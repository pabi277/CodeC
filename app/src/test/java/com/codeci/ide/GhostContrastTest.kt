package com.codeci.ide

import com.codeci.ide.ui.theme.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 69.2 — the ghost's contrast law, pinned from BOTH ends.
 *
 * The owner's report (2026-09-28, verbatim): *"Sometimes the ghost suggestions
 * text are way too real i think as i wrote the wrong word then about a second
 * it vanished the ghost suggestions fix it"*. Measured on the four shipped
 * editor themes the 27.1 ghost was already faint (1.8:1-2.0:1 against the
 * background, where real code text is 11:1-14:1), so the missing cue was never
 * brightness — it was SHAPE, and 69.2 added the suggestion box for it. This
 * test keeps the corrected pair honest:
 *
 *  - the box must be visible (a hint of a surface) and must never reach the 3:1
 *    of a real UI component, so it can only ever read as a suggestion;
 *  - the ghost text must stay far below real code text, so "too real" cannot
 *    come back through a colour tweak;
 *  - the ghost text must still be legible INSIDE its own box.
 *
 * The colours are re-derived from `EditorThemes.kt` (the shipped palette) and
 * the two alphas are read out of `EditorScreen.kt`, exactly the way
 * `ChromeContrastTest` reads the chrome's alphas — raising one fails the build
 * instead of quietly changing what the owner reads.
 */
class GhostContrastTest {

    private data class Theme(val name: String, val comment: Int, val text: Int, val background: Int)

    private val themesSource = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/theme/EditorThemes.kt"
    ).readText()

    private val screen = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
    ).readText()

    private fun argb(hex: String): Int = hex.toLong(16).toInt()

    /** The four shipped editor themes, straight from the palette file. */
    private fun themes(): List<Theme> =
        Regex("""val (\w+Theme) = EditorThemeColors\(([\s\S]*?)\n\)""")
            .findAll(themesSource)
            .map { m ->
                val block = m.groupValues[2]
                fun value(field: String): Int = argb(
                    Regex("""$field = Color\(0x([0-9A-Fa-f]{8})\)""")
                        .find(block)!!
                        .groupValues[1]
                )
                Theme(m.groupValues[1], value("comment"), value("text"), value("background"))
            }
            .toList()

    private fun alphaIn(pattern: String, what: String): Float {
        val m = Regex(pattern).find(screen)
        assertTrue("$what not found (looked for $pattern)", m != null)
        return m!!.groupValues[1].toFloat()
    }

    @Test
    fun `the two ghost alphas are the ones this law was audited against`() {
        assertEquals(
            0.38f,
            alphaIn("""ghostColorArgb = editorColors\.comment\.copy\(alpha = ([0-9.]+)f\)""", "ghost alpha"),
            0.0001f,
        )
        assertEquals(
            0.18f,
            alphaIn("""ghostChipArgb = editorColors\.comment\.copy\(alpha = ([0-9.]+)f\)""", "chip alpha"),
            0.0001f,
        )
    }

    @Test
    fun `the ghost text can never be mistaken for real code, on any theme`() {
        val ghostAlpha = 0.38f
        val all = themes()
        assertEquals("the four shipped editor themes must all be covered", 4, all.size)
        for (theme in all) {
            val ghost = Contrast.composite(theme.comment, theme.background, ghostAlpha)
            val ghostRatio = Contrast.ratio(ghost, theme.background)
            val textRatio = Contrast.ratio(theme.text, theme.background)
            assertTrue(
                "${theme.name}: ghost ${"%.2f".format(ghostRatio)}:1 is invisible",
                ghostRatio >= 1.4,
            )
            assertTrue(
                "${theme.name}: ghost ${"%.2f".format(ghostRatio)}:1 is too heavy for a hint",
                ghostRatio <= 2.6,
            )
            assertTrue(
                "${theme.name}: ghost must stay far below real text " +
                    "(${"%.2f".format(ghostRatio)}:1 vs ${"%.2f".format(textRatio)}:1)",
                ghostRatio < textRatio * 0.3,
            )
        }
    }

    @Test
    fun `the suggestion box is visible and is never a filled component`() {
        val chipAlpha = 0.18f
        for (theme in themes()) {
            val chip = Contrast.composite(theme.comment, theme.background, chipAlpha)
            val chipRatio = Contrast.ratio(chip, theme.background)
            val ghost = Contrast.composite(theme.comment, theme.background, 0.38f)
            assertTrue(
                "${theme.name}: the box ${"%.2f".format(chipRatio)}:1 is invisible",
                chipRatio >= 1.15,
            )
            assertTrue(
                "${theme.name}: the box ${"%.2f".format(chipRatio)}:1 reads as a filled chip",
                chipRatio < Contrast.AA_NON_TEXT,
            )
            assertTrue(
                "${theme.name}: the ghost must stay legible inside its own box",
                Contrast.ratio(ghost, chip) >= 1.15,
            )
        }
    }
}
