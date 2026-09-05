package com.codeci.ide

import com.codeci.ide.ui.editor.CompletionAction
import com.codeci.ide.ui.editor.CompletionInput
import com.codeci.ide.ui.editor.CompletionPolicy
import com.codeci.ide.ui.editor.CompletionSettings
import com.codeci.ide.ui.editor.CompletionSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 27.3 — the accept/dismiss matrix, one test per cell. Invariants:
 *  1. Enter is sacred on soft keyboards (never consumed for completion).
 *  2. TAB is dual-mood but never ambiguous ("TAB ▸" accepts, "TAB" indents,
 *     long-press ALWAYS indents).
 *  3. (Dismissal is per-identifier — pinned in StripContextTest.)
 *  4. Master switch off ⇒ the whole feature is gone.
 */
class CompletionPolicyTest {

    // ---- surfaceFor --------------------------------------------------------

    @Test
    fun `panel browse trumps everything`() {
        assertEquals(
            CompletionSurface.PANEL,
            CompletionPolicy.surfaceFor(
                ghostVisible = true, candidateCount = 5, stripEnabled = true,
                panelBrowsing = true, hasSelection = false
            )
        )
    }

    @Test
    fun `selection suppresses ghost and strip (matrix row)`() {
        assertEquals(
            CompletionSurface.NONE,
            CompletionPolicy.surfaceFor(true, 5, stripEnabled = true, panelBrowsing = false, hasSelection = true)
        )
    }

    @Test
    fun `multi-candidate with strip enabled is STRIP, otherwise GHOST_ONLY or NONE`() {
        assertEquals(
            CompletionSurface.STRIP,
            CompletionPolicy.surfaceFor(true, candidateCount = 3, stripEnabled = true, panelBrowsing = false, hasSelection = false)
        )
        // strip setting off: multi-candidate but only the ghost shows
        assertEquals(
            CompletionSurface.GHOST_ONLY,
            CompletionPolicy.surfaceFor(true, candidateCount = 3, stripEnabled = false, panelBrowsing = false, hasSelection = false)
        )
        // one candidate: the ghost alone suffices (S1)
        assertEquals(
            CompletionSurface.GHOST_ONLY,
            CompletionPolicy.surfaceFor(true, candidateCount = 1, stripEnabled = true, panelBrowsing = false, hasSelection = false)
        )
        assertEquals(
            CompletionSurface.NONE,
            CompletionPolicy.surfaceFor(false, 0, stripEnabled = true, panelBrowsing = false, hasSelection = false)
        )
    }

    // ---- decide: Enter is sacred -------------------------------------------

    @Test
    fun `Enter is NEVER consumed on any surface (soft-keyboard phone law)`() {
        for (surface in CompletionSurface.entries) {
            assertEquals(CompletionAction.NEWLINE, CompletionPolicy.decide(surface, CompletionInput.ENTER_SOFT))
        }
    }

    // ---- decide: TAB --------------------------------------------------------

    @Test
    fun `TAB tap accepts only on ghost surfaces, indents otherwise`() {
        assertEquals(CompletionAction.INDENT, CompletionPolicy.decide(CompletionSurface.NONE, CompletionInput.TAB_TAP))
        assertEquals(CompletionAction.ACCEPT_FULL, CompletionPolicy.decide(CompletionSurface.GHOST_ONLY, CompletionInput.TAB_TAP))
        assertEquals(CompletionAction.ACCEPT_FULL, CompletionPolicy.decide(CompletionSurface.STRIP, CompletionInput.TAB_TAP))
        // the sora panel owns Tab while browsing (arrows-navigated accept)
        assertEquals(CompletionAction.NOTHING, CompletionPolicy.decide(CompletionSurface.PANEL, CompletionInput.TAB_TAP))
    }

    @Test
    fun `TAB long-press is ALWAYS raw indent (escape hatch)`() {
        for (surface in CompletionSurface.entries) {
            assertEquals(CompletionAction.INDENT, CompletionPolicy.decide(surface, CompletionInput.TAB_LONG))
        }
    }

    // ---- decide: arrows -----------------------------------------------------

    @Test
    fun `arrow right accepts the next WORD only with a ghost, else moves caret`() {
        assertEquals(CompletionAction.ACCEPT_WORD, CompletionPolicy.decide(CompletionSurface.GHOST_ONLY, CompletionInput.ARROW_RIGHT))
        assertEquals(CompletionAction.ACCEPT_WORD, CompletionPolicy.decide(CompletionSurface.STRIP, CompletionInput.ARROW_RIGHT))
        assertEquals(CompletionAction.MOVE_CARET, CompletionPolicy.decide(CompletionSurface.NONE, CompletionInput.ARROW_RIGHT))
        assertEquals(CompletionAction.MOVE_CARET, CompletionPolicy.decide(CompletionSurface.PANEL, CompletionInput.ARROW_RIGHT))
    }

    @Test
    fun `up and down browse the PANEL but never the strip (strip is untargeted)`() {
        assertEquals(CompletionAction.BROWSE_PANEL, CompletionPolicy.decide(CompletionSurface.PANEL, CompletionInput.ARROW_UP))
        assertEquals(CompletionAction.BROWSE_PANEL, CompletionPolicy.decide(CompletionSurface.PANEL, CompletionInput.ARROW_DOWN))
        for (surface in listOf(CompletionSurface.NONE, CompletionSurface.GHOST_ONLY, CompletionSurface.STRIP)) {
            assertEquals(CompletionAction.MOVE_CARET, CompletionPolicy.decide(surface, CompletionInput.ARROW_UP))
            assertEquals(CompletionAction.MOVE_CARET, CompletionPolicy.decide(surface, CompletionInput.ARROW_DOWN))
        }
        assertEquals(CompletionAction.MOVE_CARET, CompletionPolicy.decide(CompletionSurface.STRIP, CompletionInput.ARROW_LEFT))
    }

    // ---- decide: ESCAPE / unmatched chars -----------------------------------

    @Test
    fun `ESCAPE clears the visible surface only`() {
        assertEquals(CompletionAction.NOTHING, CompletionPolicy.decide(CompletionSurface.NONE, CompletionInput.ESCAPE))
        assertEquals(CompletionAction.REJECT_GHOST, CompletionPolicy.decide(CompletionSurface.GHOST_ONLY, CompletionInput.ESCAPE))
        assertEquals(CompletionAction.DISMISS_STRIP, CompletionPolicy.decide(CompletionSurface.STRIP, CompletionInput.ESCAPE))
        assertEquals(CompletionAction.CLOSE_PANEL, CompletionPolicy.decide(CompletionSurface.PANEL, CompletionInput.ESCAPE))
    }

    @Test
    fun `an unmatched character is NEVER swallowed, on any surface (S3)`() {
        for (surface in CompletionSurface.entries) {
            assertEquals(CompletionAction.INSERT_CHAR, CompletionPolicy.decide(surface, CompletionInput.UNMATCHED_CHAR))
        }
    }

    // ---- labels tell the truth (invariant 2) --------------------------------

    @Test
    fun `TAB cap label flips only on accepting surfaces`() {
        assertEquals("TAB", CompletionPolicy.tabCapLabel(CompletionSurface.NONE))
        assertEquals("TAB ▸", CompletionPolicy.tabCapLabel(CompletionSurface.GHOST_ONLY))
        assertEquals("TAB ▸", CompletionPolicy.tabCapLabel(CompletionSurface.STRIP))
        assertEquals("TAB", CompletionPolicy.tabCapLabel(CompletionSurface.PANEL))
        assertEquals("→", CompletionPolicy.rightCapLabel(CompletionSurface.NONE))
        assertEquals("→▸", CompletionPolicy.rightCapLabel(CompletionSurface.STRIP))
    }

    // ---- settings law (invariant 4) ------------------------------------------

    @Test
    fun `master off means the whole feature is off`() {
        assertFalse(CompletionSettings().everythingOff)
        assertTrue(CompletionSettings(master = false).everythingOff)
        assertFalse(CompletionSettings(master = true, ghost = false, strip = false, panel = false).anyOn)
        assertTrue(CompletionSettings(master = true, ghost = false, strip = true, panel = false).anyOn)
    }
}
