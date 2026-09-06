package com.codeci.ide.ui.editor

import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.codeci.ide.ui.editor.snippets.SnippetLibrary
import com.codeci.ide.ui.utils.LanguageType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 30 — the host mirror of all three exit conditions, run against the
 * REAL vendored snippet packs (Robolectric + APK assets):
 *
 *  - 30.1 `for` in C/Python offers more than the old 1–2 snippets, `doc` in
 *    HTML still surfaces the DOCTYPE skeleton, master switch OFF ⇒ nothing.
 *  - 30.2 `ul>li*3` and `!` expand in HTML (rank 0, tap inserts a list), and
 *    Emmet never fires in a C file.
 *  - 30.3 a short prefix yields MORE THAN EIGHT candidates for the policy
 *    (the plan's named host test: prefix `i` in C), while the strip still
 *    shows at most [SuggestionStripModel.MAX_CHIPS] chips and the ghost stays
 *    top-1.
 *
 * The Phase 27 laws are re-asserted here on purpose: this is the file that
 * would fail first if Phase 30 leaked into Enter, the master switch, or the
 * single-candidate key-mode rule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompletionCapacityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The old Phase 12 cap — the number Phase 30.3 exists to beat. */
    private val oldCap = 8

    @Before
    fun setUp() {
        SnippetLibrary.reset()
        // Mirrors MainActivity/SoraEditorHost: attach the APK-asset reader.
        SnippetLibrary.attach(context)
    }

    @After
    fun tearDown() {
        SnippetLibrary.reset()
    }

    // ---- 30.1 friendly-snippets -------------------------------------------

    @Test
    fun `for in C and Python offers more snippets than the old tables`() {
        // The built-in tables offered exactly ONE `for` snippet per language
        // (`for (int i = 0; i < n; i++) {` / `for item in iterable:`).
        val c = CodeCompletionEngine.completions("for", 3, LanguageType.C, "main.c")
            .filter { it.kind == CompletionKind.SNIPPET }
        assertTrue("C snippets for `for`: ${c.map { it.label }}", c.size >= 4)
        assertTrue(c.any { it.insertText.contains("for (") || it.insertText.contains("for(") })

        val py = CodeCompletionEngine.completions("for", 3, LanguageType.PYTHON, "a.py")
            .filter { it.kind == CompletionKind.SNIPPET }
        assertTrue("Python snippets for `for`: ${py.map { it.label }}", py.size >= 2)
        assertTrue(py.any { it.insertText.contains("for ") && it.insertText.contains(":") })

        // The real widening is the pack itself: 84 C and 76 Python snippets
        // where the tables had 7 and 9.
        assertTrue(SnippetLibrary.snippetsFor(LanguageType.C).size > 40)
        assertTrue(SnippetLibrary.snippetsFor(LanguageType.PYTHON).size > 40)
    }

    @Test
    fun `doc in HTML still surfaces the DOCTYPE skeleton (22_6 regression)`() {
        val items = CodeCompletionEngine.completions("doc", 3, LanguageType.HTML, "index.html")
        assertTrue(
            "labels: ${items.map { it.label }}",
            items.any { it.kind == CompletionKind.SNIPPET && it.insertText.contains("<!DOCTYPE html>") }
        )
        // The typed form from the 22.6 test still works too.
        val typed = CodeCompletionEngine.completions("<!doc", 5, LanguageType.HTML, "index.html")
        assertTrue(typed.any { it.insertText.contains("<!DOCTYPE html>") })
    }

    @Test
    fun `master switch off leaves the strip in key mode even with fifty candidates`() {
        val items = CodeCompletionEngine.completions("i", 1, LanguageType.C, "main.c")
        assertTrue(items.size > oldCap)
        val off = SuggestionStripModel.stripContextFor(
            stripVisible = true,
            runWaiting = false,
            settings = CompletionSettings(master = false),
            items = items,
            ghost = GhostState.Hidden,
            dismissedAnchor = null,
            prefixAnchor = 0,
            hasSelection = false,
            textLength = 1,
            language = LanguageType.C
        )
        assertTrue("master off must not show chips: $off", off is StripContext.Keys)
        // 27.3: the feature is GONE entirely — the VM short-circuits on exactly
        // these two predicates before it computes anything (EditorViewModel
        // `cfg.everythingOff || !cfg.anyOn`), so no ghost is painted either.
        val offSettings = CompletionSettings(master = false)
        assertTrue(offSettings.everythingOff)
        assertFalse(offSettings.anyOn)
        // The strip-only and ghost-only switches leave the master intact.
        assertTrue(CompletionSettings(strip = false).anyOn)
        assertTrue(CompletionSettings(ghost = false).anyOn)
    }

    @Test
    fun `buffer identifiers stay a lower priority source than pack snippets`() {
        val text = "importer = 1\nimportable = 2\nimp"
        val items = CodeCompletionEngine.completions(text, text.length, LanguageType.PYTHON, "a.py")
        val firstIdentifier = items.indexOfFirst { it.kind == CompletionKind.IDENTIFIER }
        val firstSnippet = items.indexOfFirst { it.kind == CompletionKind.SNIPPET }
        assertTrue("items: ${items.map { it.kind to it.label }}", firstSnippet >= 0)
        assertTrue("items: ${items.map { it.kind to it.label }}", firstIdentifier >= 0)
        assertTrue(
            "snippets must outrank identifiers",
            firstSnippet < firstIdentifier
        )
        assertTrue(items.any { it.label == "importer" && it.kind == CompletionKind.IDENTIFIER })
    }

    @Test
    fun `json and text buffers get nothing even with packs installed`() {
        assertTrue(CodeCompletionEngine.completions("{\"", 2, LanguageType.JSON, "a.json").isEmpty())
        assertTrue(CodeCompletionEngine.completions("hello wor", 9, LanguageType.TEXT, "a.txt").isEmpty())
        assertTrue(SnippetLibrary.snippetsFor(LanguageType.JSON).isEmpty())
    }

    @Test
    fun `pack items carry the replace length and caret of their first stop`() {
        val text = "mai"
        val items = CodeCompletionEngine.completions(text, text.length, LanguageType.C, "main.c")
        val main = items.firstOrNull { it.label == "main" }
        assertNotNull("labels: ${items.map { it.label }}", main)
        // A snippet replaces only the TYPED identifier prefix (the ViewModel's
        // identifierStart), so it carries no replaceLength — only an Emmet
        // expansion does, because there the whole `ul>li*3` token goes.
        assertNull(main!!.replaceLength)
        // The resolved body parks the caret at its first stop.
        assertNotNull(main.caretOffset)
        assertTrue(main.caretOffset!! in 0..main.insertText.length)
        // Keywords and identifiers keep the Phase 27 shape (both null), and so
        // do the built-in tables a device falls back to when no pack loads.
        val keyword = CodeCompletionEngine.completions("retur", 5, LanguageType.C, "main.c")
            .firstOrNull { it.kind == CompletionKind.KEYWORD }
        if (keyword != null) {
            assertNull(keyword.replaceLength)
            assertNull(keyword.caretOffset)
        }
        // …while an Emmet item DOES replace its whole abbreviation.
        val emmet = Emmet.completionItemFor("ul>li*3", 7, LanguageType.HTML, "index.html")!!
        assertEquals(7, emmet.replaceLength)
    }

    // ---- 30.2 Emmet --------------------------------------------------------

    @Test
    fun `an emmet expansion is rank zero in an html file`() {
        val buffer = "<body>\n  ul>li*3"
        val items = CodeCompletionEngine.completions(
            buffer, buffer.length, LanguageType.HTML, "index.html"
        )
        assertTrue("items: ${items.map { it.label to it.detail }}", items.isNotEmpty())
        val top = items.first()
        assertEquals(Emmet.DETAIL, top.detail)
        assertEquals("ul>li*3", top.label)
        assertEquals(CompletionKind.SNIPPET, top.kind)
        // The caret's own two spaces of indentation carry into the expansion.
        assertTrue(
            "insert: ${top.insertText}",
            top.insertText.startsWith("<ul>\n      <li></li>")
        )
        // …and it is the ONLY emmet row (one abbreviation, one expansion).
        assertEquals(1, items.count { it.detail == Emmet.DETAIL })
    }

    @Test
    fun `tapping the expansion replaces the abbreviation and parks the caret`() {
        val buffer = "<body>\n  ul>li*3"
        val item = CodeCompletionEngine.completions(
            buffer, buffer.length, LanguageType.HTML, "index.html"
        ).first()
        // The EditorViewModel accept math, mirrored (replaceLength ⇒ start).
        val caret = buffer.length
        val start = item.replaceLength?.let { (caret - it).coerceIn(0, caret) } ?: caret
        val park = item.caretOffset ?: item.insertText.length
        val result = buffer.substring(0, start) + item.insertText + buffer.substring(caret)
        assertEquals(
            "<body>\n  <ul>\n      <li></li>\n      <li></li>\n      <li></li>\n  </ul>",
            result
        )
        val parked = start + park
        assertTrue("caret $parked of ${result.length}", parked in 0..result.length)
        // It lands inside the first <li>, ready for the item's content.
        assertTrue(result.substring(parked).startsWith("</li>"))
    }

    @Test
    fun `bang expands to the html skeleton from the strip`() {
        val buffer = "<body>\n  !"
        val items = CodeCompletionEngine.completions(
            buffer, buffer.length, LanguageType.HTML, "index.html"
        )
        assertTrue("items: ${items.map { it.label to it.detail }}", items.isNotEmpty())
        assertEquals(Emmet.DETAIL, items.first().detail)
        assertTrue(items.first().insertText.startsWith("<!DOCTYPE html>"))
    }

    @Test
    fun `emmet never fires in a C file`() {
        for (buffer in listOf("ul>li", "if (!x)", "div>p", "!", "a[href=#]{x}")) {
            val items = CodeCompletionEngine.completions(
                buffer, buffer.length, LanguageType.C, "main.c"
            )
            assertTrue(
                "`$buffer` must not expand in C: ${items.map { it.label to it.detail }}",
                items.none { it.detail == Emmet.DETAIL }
            )
        }
        // Python and a plain .js file are gated the same way.
        assertTrue(
            CodeCompletionEngine.completions("ul>li*3", 7, LanguageType.PYTHON, "a.py")
                .none { it.detail == Emmet.DETAIL }
        )
        assertTrue(
            CodeCompletionEngine.completions("ul>li*3", 7, LanguageType.JAVASCRIPT, "app.js")
                .none { it.detail == Emmet.DETAIL }
        )
        // A .jsx file does fire — the plan's "(and JSX-ish)".
        assertTrue(
            CodeCompletionEngine.completions("ul>li*3", 7, LanguageType.JAVASCRIPT, "app.jsx")
                .any { it.detail == Emmet.DETAIL }
        )
    }

    @Test
    fun `css files get emmet declarations but not inside a selector`() {
        val declaration = CodeCompletionEngine.completions(
            "  m10", 5, LanguageType.CSS, "site.css"
        )
        assertTrue(declaration.any { it.detail == Emmet.DETAIL && it.label == "m10" })
        assertEquals("margin: 10px;", declaration.first { it.detail == Emmet.DETAIL }.insertText)

        val selector = CodeCompletionEngine.completions(
            ".card, m10", 10, LanguageType.CSS, "site.css"
        )
        assertTrue(
            "a selector list is not an abbreviation: ${selector.map { it.label to it.detail }}",
            selector.none { it.detail == Emmet.DETAIL }
        )
    }

    @Test
    fun `a lone emmet candidate still gets its chip while a lone identifier does not`() {
        val buffer = "<body>\n  div>p"
        val emmetOnly = CodeCompletionEngine.completions(
            buffer, buffer.length, LanguageType.HTML, "index.html"
        ).filter { it.detail == Emmet.DETAIL }
        assertEquals(1, emmetOnly.size)
        // The ghost cannot cover an expansion (its insert never starts with the
        // typed abbreviation), so 27.2's S1 rule is lifted for this one case.
        assertTrue(GhostCompletion.compute(buffer, buffer.length, emmetOnly) is GhostState.Hidden)
        val ctx = stripOf(emmetOnly, LanguageType.HTML, buffer.length)
        assertTrue("lone emmet must show a chip: $ctx", ctx is StripContext.Suggestions)
        assertEquals(1, (ctx as StripContext.Suggestions).chips.size)

        // Every other single candidate keeps the 27.2 law: key mode.
        val single = listOf(CompletionItem("foo", "foobar", CompletionKind.IDENTIFIER))
        val keys = stripOf(single, LanguageType.C, 3)
        assertTrue("a lone identifier stays in keys: $keys", keys is StripContext.Keys)
    }

    // ---- 30.3 strip capacity -----------------------------------------------

    @Test
    fun `prefix i in a C file with snippets loaded yields more than eight candidates`() {
        // The plan's named host test (PART_30_3 §1).
        val items = CodeCompletionEngine.completions("i", 1, LanguageType.C, "main.c")
        assertTrue(
            "expected more than $oldCap candidates, got ${items.size}: ${items.map { it.label }}",
            items.size > oldCap
        )
        assertTrue("the safety cap still holds", items.size <= CodeCompletionEngine.MAX_ITEMS)
        // Snippets are what lifted the count: the old tables gave 4 snippet
        // matches for this prefix (7 candidates in total), the packs give 8.
        assertTrue(
            "snippets: ${items.count { it.kind == CompletionKind.SNIPPET }}",
            items.count { it.kind == CompletionKind.SNIPPET } > 4
        )
        // The other sources still ride along (nothing was displaced).
        assertTrue(items.any { it.kind == CompletionKind.KEYWORD })
        // Python behaves the same way, which is the 30.3 device round's prefix.
        val py = CodeCompletionEngine.completions("i", 1, LanguageType.PYTHON, "a.py")
        assertTrue("python candidates: ${py.size}", py.size > oldCap)
        assertTrue(py.size <= CodeCompletionEngine.MAX_ITEMS)
    }

    @Test
    fun `a short python prefix scrolls the strip and leaves the rest behind the chevron`() {
        val items = CodeCompletionEngine.completions("i", 1, LanguageType.PYTHON, "a.py")
        assertTrue("candidates: ${items.size}", items.size > oldCap)
        assertTrue(items.size <= CodeCompletionEngine.MAX_ITEMS)

        val chips = SuggestionStripModel.buildStripModel(items, GhostState.Hidden)
        assertTrue("chips: ${chips.size}", chips.size <= SuggestionStripModel.MAX_CHIPS)
        assertEquals(SuggestionStripModel.MAX_CHIPS, chips.size)
        // ⌄ more = everything the thumb cannot show (27.2).
        assertTrue("the chevron must have something to open", items.size > chips.size)
        // Chip labels stay thumb-sized.
        assertTrue(chips.all { it.displayLabel.length <= 18 })

        val ctx = stripOf(items, LanguageType.PYTHON, 1)
        assertTrue("expected Suggestions, got $ctx", ctx is StripContext.Suggestions)
    }

    @Test
    fun `the ghost is still only rank zero and still ghosts one line`() {
        val text = "int mai"
        val items = CodeCompletionEngine.completions(text, text.length, LanguageType.C, "main.c")
        assertTrue(items.isNotEmpty())
        val ghost = GhostCompletion.compute(text, text.length, items)
        assertTrue("expected a visible ghost, got $ghost", ghost is GhostState.Visible)
        ghost as GhostState.Visible
        // Top-1 only: the ghost paints the FIRST candidate.
        assertEquals(items.first().label, ghost.item.label)
        assertEquals("main", ghost.item.label)
        // G6: a multi-line snippet ghosts its first line only.
        assertFalse("suffix: ${ghost.suffix}", ghost.suffix.contains("\n"))
        assertTrue(ghost.suffix.isNotEmpty())
    }

    @Test
    fun `accepting the ghost parks the caret at the snippet tabstop`() {
        val text = "int mai"
        val items = CodeCompletionEngine.completions(text, text.length, LanguageType.C, "main.c")
        val ghost = GhostCompletion.compute(text, text.length, items) as GhostState.Visible
        val accepted = GhostCompletion.accept(TextFieldValue(text, TextRange(text.length)), ghost)
        assertNotNull(accepted)
        val inserted = accepted!!.text
        assertTrue("inserted: <$inserted>", inserted.startsWith("int main"))
        val declared = ghost.item.caretOffset
        assertNotNull("the pack item must declare a caret", declared)
        assertEquals(
            text.length - ghost.prefixLength + declared!!,
            accepted.selection.start
        )
        // Typing never commits: computing a ghost changed nothing (G2).
        assertEquals(text, TextFieldValue(text, TextRange(text.length)).text)
    }

    @Test
    fun `the engine honours its safety cap and per-source bounds`() {
        val identifiers = (0 until 200).joinToString(" ") { "value_$it" }
        val text = "$identifiers value_"
        val items = CodeCompletionEngine.completions(text, text.length, LanguageType.PYTHON, "a.py")
        assertTrue(items.size <= CodeCompletionEngine.MAX_ITEMS)
        assertTrue(
            "identifiers: ${items.count { it.kind == CompletionKind.IDENTIFIER }}",
            items.count { it.kind == CompletionKind.IDENTIFIER } <= CodeCompletionEngine.MAX_IDENTIFIER_ITEMS
        )
        assertTrue(
            "keywords: ${items.count { it.kind == CompletionKind.KEYWORD }}",
            items.count { it.kind == CompletionKind.KEYWORD } <= CodeCompletionEngine.MAX_KEYWORD_ITEMS
        )
        assertTrue(
            "snippets: ${items.count { it.kind == CompletionKind.SNIPPET }}",
            items.count { it.kind == CompletionKind.SNIPPET } <= CodeCompletionEngine.MAX_SNIPPET_ITEMS
        )
        // A direct-prefix identifier is still offered (the scan is windowed).
        assertTrue(items.any { it.label == "value_0" && it.kind == CompletionKind.IDENTIFIER })
    }

    @Test
    fun `every ranked list is ordered snippets first then keywords then identifiers`() {
        val text = "counter = 1\nconst"
        val items = CodeCompletionEngine.completions(text, text.length, LanguageType.PYTHON, "a.py")
        val kinds = items.map { it.kind }
        // No identifier may appear before a snippet or keyword: the tiers are
        // concatenated, and 30.3 raised the caps without reordering them.
        val lastSnippet = kinds.indexOfLast { it == CompletionKind.SNIPPET }
        val firstIdentifier = kinds.indexOfFirst { it == CompletionKind.IDENTIFIER }
        if (lastSnippet >= 0 && firstIdentifier >= 0) {
            assertTrue(
                "order: $kinds",
                lastSnippet < firstIdentifier
            )
        }
        assertTrue("the cap holds for a mixed list", items.size <= CodeCompletionEngine.MAX_ITEMS)
    }

    // ---- helpers -----------------------------------------------------------

    private fun stripOf(
        items: List<CompletionItem>,
        language: LanguageType,
        textLength: Int
    ): StripContext = SuggestionStripModel.stripContextFor(
        stripVisible = true,
        runWaiting = false,
        settings = CompletionSettings(),
        items = items,
        ghost = GhostState.Hidden,
        dismissedAnchor = null,
        prefixAnchor = 0,
        hasSelection = false,
        textLength = textLength,
        language = language
    )
}
