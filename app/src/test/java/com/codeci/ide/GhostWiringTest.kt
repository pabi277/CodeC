package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 69.2 — the ghost's two fixes as wiring pins (owner, verbatim,
 * 2026-09-28): *"Sometimes the ghost suggestions text are way too real i
 * think as i wrote the wrong word then about a second it vanished the ghost
 * suggestions fix it"*, answered with *"A — Both: unmistakable look + stop it
 * vanishing (recommended)"*.
 *
 * The pure halves live in `GhostCompletionTest` (the hold) and
 * `GhostContrastTest` (the two colours); this file pins the wiring a screenshot
 * would otherwise be the only witness of: the box really is drawn, both colours
 * really come from the active editor theme, only the USER's own scrolls hide
 * the ghost, and the hold really is consulted by the model refresh.
 */
class GhostWiringTest {

    private val host = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/editor/sora/SoraEditorHost.kt"
    ).readText()

    private val renderer = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/editor/sora/GhostHintRenderer.kt"
    ).readText()

    private val screen = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
    ).readText()

    private val viewModel = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt"
    ).readText()

    @Test
    fun `the ghost is painted inside its own suggestion box`() {
        val code = RepoFiles.codeOnly(renderer)
        assertTrue(
            "the box is the cue that says \"a suggestion, not your typing\"",
            code.contains("canvas.drawRoundRect("),
        )
        assertTrue(
            "the box colour must be the themed chip colour",
            code.contains("localPaint.color = chipColorArgb"),
        )
        assertTrue(
            "the text keeps the G5 colour law (comment colour at 38 %)",
            code.contains("localPaint.color = ghostColorArgb"),
        )
        assertTrue("the ghost text itself is still drawn", code.contains("canvas.drawText(hint.text"))
        assertTrue(
            "the renderer takes both colours",
            code.contains("class GhostHintRenderer(") && code.contains("chipColorArgb: Int"),
        )
    }

    @Test
    fun `both ghost colours come from the active editor theme`() {
        assertTrue(
            "the ghost text stays the theme's comment colour at 38 % (G5)",
            screen.contains("ghostColorArgb = editorColors.comment.copy(alpha = 0.38f).toArgb()"),
        )
        assertTrue(
            "the box is the same colour at 18 % (69.2)",
            screen.contains("ghostChipArgb = editorColors.comment.copy(alpha = 0.18f).toArgb()"),
        )
        val code = RepoFiles.codeOnly(host)
        assertTrue(
            "an editor theme switch must repaint both colours",
            code.contains("ghostHintRenderer.ghostColorArgb = ghostColorArgb") &&
                code.contains("ghostHintRenderer.chipColorArgb = ghostChipArgb"),
        )
        assertTrue(
            "the renderer is registered with both colours",
            code.contains("GhostHintRenderer(ghostColorArgb, ghostChipArgb)"),
        )
    }

    @Test
    fun `only the user's own scrolls hide the ghost`() {
        val code = RepoFiles.codeOnly(host)
        val call = "viewModel.onCompletionScroll()"
        assertEquals("exactly one hide-on-scroll call site", 1, Regex(Regex.escape(call)).findAll(code).count())
        val idx = code.indexOf(call)
        assertTrue("the hide-on-scroll call site is gone", idx > 0)
        val guard = code.substring((idx - 400).coerceAtLeast(0), idx)
        // G4 (69.2) — a thumb drag or a fling hides it; the editor's own
        // caret-follow scroll (CAUSE_MAKE_POSITION_VISIBLE, which the 48/69 air
        // rule triggers when the keyboard or the chrome settles) must not.
        assertTrue("the user's drag must still hide the ghost", guard.contains("ScrollEvent.CAUSE_USER_DRAG"))
        assertTrue("the user's fling must still hide the ghost", guard.contains("ScrollEvent.CAUSE_USER_FLING"))
        assertFalse(
            "the editor's own caret reveal must never hide the ghost",
            guard.contains("CAUSE_MAKE_POSITION_VISIBLE"),
        )
        assertTrue(
            "the ignored causes are named for the reader",
            host.contains("CAUSE_MAKE_POSITION_VISIBLE"),
        )
    }

    @Test
    fun `a still-correct ghost survives a background refresh`() {
        val code = RepoFiles.codeOnly(viewModel)
        assertEquals(
            "exactly one hold call site",
            1,
            Regex("""GhostCompletion\.heldWhenStillValid\(""").findAll(code).count(),
        )
        assertTrue(
            "the hold applies only when the fresh pass found nothing",
            code.contains("if (fresh is GhostState.Hidden) {"),
        )
        assertTrue(
            "the fresh pass is still the primary answer",
            code.contains("val fresh = GhostCompletion.compute(text, caret, items)"),
        )
    }
}
