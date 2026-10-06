package com.codeci.ide

import com.codeci.ide.ui.theme.Contrast
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 92 — the code block's contrast, measured instead of looked at.
 *
 * The owner's Phase 90 round said it twice: *"no visible block"* (C9) and *"in
 * light mode visible but dark mode not visible"* (C11). The cause was colour, not
 * drawing: the block's own surface was `surfaceVariant` — the **bubble's own
 * colour** — so against the bubble it measured exactly 1.00:1, and its 1 dp frame
 * was `outlineVariant`, the faintest line in the theme.
 *
 * Phase 91 moved the block to `surface` with an `outline` frame. This file keeps
 * the numbers, so the next edit that walks the block back into the bubble fails
 * the build instead of reaching his phone.
 *
 * The theme values are Material 3's baseline roles — the same ones
 * `ChromeContrastTest` measures the chrome against.
 */
class AiCodeBlockContrastTest {

    private data class Theme(val name: String, val surface: Int, val surfaceVariant: Int, val outline: Int)

    private val dark = Theme("dark", 0xFF1C1B1F.toInt(), 0xFF49454F.toInt(), 0xFF938F99.toInt())
    private val light = Theme("light", 0xFFFFFBFE.toInt(), 0xFFE7E0EC.toInt(), 0xFF79747E.toInt())
    private val themes = listOf(dark, light)

    private fun src(name: String) = RepoFiles.codeOnly(
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai/$name").readText()
    )

    @Test
    fun `the frame is a real edge against the block in both themes`() {
        for (theme in themes) {
            val ratio = Contrast.ratio(theme.outline, theme.surface)
            assertTrue(
                "${theme.name}: frame vs block = %.2f:1 (needs %.1f)".format(ratio, Contrast.AA_NON_TEXT),
                ratio >= Contrast.AA_NON_TEXT
            )
        }
    }

    @Test
    fun `in the dark theme the block's own surface is a visible step from the bubble`() {
        // The exact regression the owner hit: bubble and block were the same colour.
        val ratio = Contrast.ratio(dark.surface, dark.surfaceVariant)
        assertTrue(
            "dark: block vs bubble = %.2f:1 (needs 1.5)".format(ratio),
            ratio >= 1.5
        )
    }

    @Test
    fun `the bubble and the block are the two different roles the sheet actually draws`() {
        val sheet = src("AiChatSheet.kt")
        val bubble = sheet.substringAfter("private fun AiBubble(").substringBefore("// ---- the pinned bottom bar")
        assertTrue("the bubble is surfaceVariant", bubble.contains("MaterialTheme.colorScheme.surfaceVariant"))
        val view = src("AiMarkdownView.kt")
        assertTrue("the block's surface is surface", view.contains("val codeColor = MaterialTheme.colorScheme.surface"))
        assertTrue("its frame is outline", view.contains("val frameColor = MaterialTheme.colorScheme.outline"))
    }

    @Test
    fun `the colours that failed are not the colours in the code any more`() {
        val view = src("AiMarkdownView.kt")
        assertFalse(
            "a block painted in the bubble's own colour is invisible (1.00:1)",
            view.contains("val codeColor = MaterialTheme.colorScheme.surfaceVariant")
        )
        assertFalse(
            "outlineVariant is the faintest line in the theme (1.4:1 on the dark surface)",
            view.contains("val frameColor = MaterialTheme.colorScheme.outlineVariant")
        )
        // And the failure mode is documented in numbers, so nobody has to guess.
        assertTrue(
            "the old pair measures 1.00:1 — that is why nothing showed",
            Contrast.ratio(dark.surfaceVariant, dark.surfaceVariant) == 1.0
        )
    }

    @Test
    fun `the block still copies whole and still draws its own header`() {
        val view = src("AiMarkdownView.kt")
        assertTrue("the language and Copy strip is drawn", view.contains("AiCopy.CODE_COPY"))
        assertTrue("the divider under the strip is the same edge colour", view.contains("HorizontalDivider(color = colors.frame)"))
        assertTrue("only the drawing is capped", view.contains("MAX_DRAWN_CODE_LINES"))
    }
}
