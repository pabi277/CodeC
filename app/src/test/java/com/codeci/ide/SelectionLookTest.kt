package com.codeci.ide

import com.codeci.ide.ui.terminal.SelectionLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 103.1 round 2 — the owner's *"it is not looking good"*, as a test.
 *
 * The first device round drew the band as `theme.selection` at its stored
 * alpha. That value is a **dark** translucent colour in three of the four
 * bundled themes, so the composite differed from the grid by ~1.1:1 — a band
 * you cannot see, with two cursor-coloured circles floating over it. This
 * class pins the derive-from-the-theme rule that replaced it.
 *
 * The four themes' values are copied from `EditorThemes.kt` on purpose: if
 * someone darkens or lightens a theme, these numbers move and the test below
 * still asserts the rule rather than a snapshot.
 */
class SelectionLookTest {

    // (name, backgroundRgb, foregroundRgb)
    private val themes = listOf(
        Triple("ClassicDark", 0x121212, 0xE5E5E5),
        Triple("Dracula", 0x282A36, 0xF8F8F2),
        Triple("Monokai", 0x272822, 0xF8F8F2),
        Triple("GitHubDark", 0x24292E, 0xF8F8F2),
    )

    @Test
    fun `every bundled theme gets a band that is actually visible`() {
        for ((name, bg, fg) in themes) {
            val band = SelectionLook.bandOver(fg, bg)
            val ratio = SelectionLook.contrast(band, bg)
            assertTrue(
                "$name: the band must clear ${SelectionLook.MIN_BAND_CONTRAST}:1 " +
                    "against its own background, was $ratio",
                ratio >= SelectionLook.MIN_BAND_CONTRAST
            )
        }
    }

    @Test
    fun `the stored dark selection colour is the reason this rule exists`() {
        // The old band verbatim: Dracula's #44475A at 40 % over #282A36.
        val old = SelectionLook.composite(0x44475A, 0x282A36, 0.40f)
        val oldRatio = SelectionLook.contrast(old, 0x282A36)
        assertTrue("the old band was effectively invisible ($oldRatio:1)", oldRatio < 1.35)
        // …and the derived one is not.
        val now = SelectionLook.contrast(SelectionLook.bandOver(0xF8F8F2, 0x282A36), 0x282A36)
        assertTrue("the derived band must beat it clearly", now > oldRatio + 0.25)
    }

    @Test
    fun `the band never hides the text on top of it`() {
        // The band sits UNDER the glyphs, which keep their own foreground, so
        // the text/band contrast must stay high enough to read (3:1 for large,
        // 4.5:1 for body — the terminal is body text).
        for ((name, bg, fg) in themes) {
            val band = SelectionLook.bandOver(fg, bg)
            val glyphsOnBand = SelectionLook.contrast(fg, band)
            assertTrue(
                "$name: text must stay readable ON the band, was $glyphsOnBand",
                glyphsOnBand >= 4.5
            )
        }
    }

    @Test
    fun `a coloured cell inside the selection is still visibly selected`() {
        // An inverse or ANSI-backgrounded run draws its own background; the band
        // must remain distinguishable inside it too.
        val fg = 0xF8F8F2
        val grid = 0x282A36
        val loudCell = 0x50FA7B // Dracula's green, used as a program's own bg
        val inside = SelectionLook.bandOverCell(fg, loudCell, grid)
        assertTrue(
            "a selected coloured cell must still read as selected",
            SelectionLook.contrast(inside, loudCell) >= 1.2
        )
    }

    @Test
    fun `a theme whose foreground equals its background cannot divide by zero`() {
        val band = SelectionLook.bandOver(0x000000, 0x000000)
        assertEquals(0xFF000000.toInt(), band)
        // The WCAG ratio of a colour against itself is exactly 1.0 (not 0 —
        // the +0.05 offsets in the formula do that on purpose).
        assertEquals(1.0, SelectionLook.contrast(0x000000, 0x000000), 0.0001)
        assertEquals(1.0, SelectionLook.contrast(0xFFFFFF, 0xFFFFFF), 0.0001)
    }
}
