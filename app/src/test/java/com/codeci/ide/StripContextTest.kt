package com.codeci.ide

import com.codeci.ide.ui.editor.CompletionItem
import com.codeci.ide.ui.editor.CompletionKind
import com.codeci.ide.ui.editor.CompletionSettings
import com.codeci.ide.ui.editor.CompletionSurface
import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.editor.EditorKeySet
import com.codeci.ide.ui.editor.GhostState
import com.codeci.ide.ui.editor.StripContext
import com.codeci.ide.ui.editor.SuggestionStripModel
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 27.2 — the strip law (Keys | Suggestions | Run | Hidden) and the
 * chip pipeline. Also re-pins 23.2's invariant that an interactive run owns
 * the strip, plus 27.3's dismiss-per-identifier + S1 single-candidate rule.
 */
class StripContextTest {

    private fun item(label: String, insert: String = label, kind: CompletionKind = CompletionKind.SNIPPET) =
        CompletionItem(label, insert, kind)

    private val on = CompletionSettings()

    private fun stripOf(
        items: List<CompletionItem>,
        ghost: GhostState = GhostState.Hidden,
        runWaiting: Boolean = false,
        stripVisible: Boolean = true,
        settings: CompletionSettings = on,
        dismissedAnchor: Int? = null,
        prefixAnchor: Int = 40,
        hasSelection: Boolean = false,
        textLength: Int = 100,
        language: LanguageType? = LanguageType.C,
        acceptCounts: Map<String, Int> = emptyMap()
    ) = SuggestionStripModel.stripContextFor(
        stripVisible, runWaiting, settings, items, ghost,
        dismissedAnchor, prefixAnchor, hasSelection, textLength, language, acceptCounts
    )

    // ---- buildStripModel ---------------------------------------------------

    @Test
    fun `chips cap at MAX_CHIPS and keep engine order for ties`() {
        val items = (1..12).map { item("opt$it") }
        val chips = SuggestionStripModel.buildStripModel(items, GhostState.Hidden)
        assertEquals(SuggestionStripModel.MAX_CHIPS, chips.size)
        assertEquals(listOf("opt1", "opt2", "opt3", "opt4", "opt5", "opt6", "opt7", "opt8"), chips.map { it.label })
    }

    @Test
    fun `the ghost's item is pinned as the FIRST chip (S1 coherence)`() {
        val items = listOf(item("a_first"), item("b_second"), item("c_third"))
        val ghost = GhostState.Visible("econd", items[1], 1)
        val chips = SuggestionStripModel.buildStripModel(items, ghost)
        assertEquals("b_second", chips.first().label)
        assertTrue(chips.first().ghostBacked)
        assertFalse(chips[1].ghostBacked)
    }

    @Test
    fun `recency-of-use boosts accepted labels (in-memory)`() {
        val items = listOf(item("alpha_var"), item("beta_var"))
        val chips = SuggestionStripModel.buildStripModel(
            items, GhostState.Hidden, acceptCounts = mapOf("beta_var" to 2)
        )
        assertEquals("beta_var", chips.first().label)
    }

    @Test
    fun `chip label ellipsizes past 18 chars and glyph maps the kind (S7)`() {
        val chip = item("a_very_long_snippet_label_here", kind = CompletionKind.KEYWORD)
        val chips = SuggestionStripModel.buildStripModel(listOf(chip), GhostState.Hidden)
        assertEquals(18, chips[0].displayLabel.length)
        assertTrue(chips[0].displayLabel.endsWith("…"))
        assertEquals("λ", chips[0].glyph)
        assertEquals("ƒ", SuggestionStripModel.buildStripModel(
            listOf(item("print", kind = CompletionKind.SNIPPET)), GhostState.Hidden)[0].glyph)
        assertEquals("≠", SuggestionStripModel.buildStripModel(
            listOf(item("bufferSymbol", kind = CompletionKind.IDENTIFIER)), GhostState.Hidden)[0].glyph)
    }

    // ---- stripContextFor ----------------------------------------------------

    @Test
    fun `S6 - interactive run ALWAYS wins the strip (23 point 2 preserved)`() {
        val chips = listOf(item("a"), item("b"))
        assertTrue(stripOf(chips, runWaiting = true) is StripContext.Run)
    }

    @Test
    fun `chevron toggle hides the whole strip in every state`() {
        assertEquals(StripContext.Hidden, stripOf(listOf(item("a"), item("b")), stripVisible = false))
        assertEquals(StripContext.Hidden, stripOf(emptyList(), stripVisible = false))
    }

    @Test
    fun `S1 - two or more candidates become chips`() {
        val ctx = stripOf(listOf(item("alpha"), item("alpine")))
        assertTrue(ctx is StripContext.Suggestions)
        assertEquals(2, (ctx as StripContext.Suggestions).chips.size)
    }

    @Test
    fun `S1 - a single candidate stays in key mode; the ghost covers it`() {
        val ghostItem = item("printf", "printf(")
        val ghost = GhostState.Visible("intf(", ghostItem, 2)
        val ctx = stripOf(listOf(ghostItem), ghost = ghost)
        assertTrue(ctx is StripContext.Keys)
        assertEquals(CompletionSurface.GHOST_ONLY, (ctx as StripContext.Keys).surface)
        // and without a ghost even the single match just keeps plain keys
        val noGhost = stripOf(listOf(ghostItem))
        assertTrue(noGhost is StripContext.Keys)
        assertEquals(CompletionSurface.NONE, (noGhost as StripContext.Keys).surface)
    }

    @Test
    fun `S4 - dismissal per identifier suppresses chips until the boundary moves`() {
        val items = listOf(item("alpha"), item("alpine"))
        // dismissed AT the very anchor the caret is in → suppressed
        assertTrue(stripOf(items, dismissedAnchor = 40, prefixAnchor = 40) is StripContext.Keys)
        // next identifier (different anchor) → re-armed
        assertTrue(stripOf(items, dismissedAnchor = 12, prefixAnchor = 40) is StripContext.Suggestions)
    }

    @Test
    fun `no chips with a selection, past the file cap, or with settings off`() {
        val items = listOf(item("alpha"), item("alpine"))
        assertTrue(stripOf(items, hasSelection = true) is StripContext.Keys)
        assertTrue(
            stripOf(items, textLength = com.codeci.ide.ui.editor.GhostCompletion.SOFT_FILE_CAP + 1)
                    is StripContext.Keys
        )
        assertTrue(stripOf(items, settings = on.copy(master = false)) is StripContext.Keys)
        assertTrue(stripOf(items, settings = on.copy(strip = false)) is StripContext.Keys)
    }

    // ---- dual-mood caps (27.1 G3 / 27.3 invariant 2) ------------------------

    @Test
    fun `ghost mood rewrites TAB and arrow-right only, keeping indent as popup`() {
        val defs = EditorKeySet.defaultGeneral()
        val mood = EditorKeySet.keysWithGhostMood(defs, CompletionSurface.STRIP)
        val tab = mood.first { it.label == "TAB ▸" }
        assertEquals(EditorKey.GhostAccept, tab.key)
        assertEquals(EditorKey.Tab, tab.popup) // long-press is still raw indent
        val right = mood.first { it.label == "→▸" }
        assertEquals(EditorKey.GhostAcceptWord, right.key)
        // Everything else untouched.
        assertEquals(defs.size, mood.size)
        assertEquals(defs.count { it.key is EditorKey.Insert }, mood.count { it.key is EditorKey.Insert })
    }

    @Test
    fun `no ghost mood on NONE or PANEL surfaces`() {
        val defs = EditorKeySet.defaultGeneral()
        assertEquals(defs, EditorKeySet.keysWithGhostMood(defs, CompletionSurface.NONE))
        assertEquals(defs, EditorKeySet.keysWithGhostMood(defs, CompletionSurface.PANEL))
    }

    @Test
    fun `ghost caps reaching apply() are a no-op (never a silent tab insert)`() {
        val value = androidx.compose.ui.text.input.TextFieldValue("pri", androidx.compose.ui.text.TextRange(3))
        assertEquals(value, EditorKeySet.apply(EditorKey.GhostAccept, value, 4))
        assertEquals(value, EditorKeySet.apply(EditorKey.GhostAcceptWord, value, 4))
    }
}
