package com.codeci.ide

import com.codeci.ide.ui.editor.CodeCompletionEngine
import com.codeci.ide.ui.editor.CompletionKind
import com.codeci.ide.ui.editor.snippets.SnippetLibrary
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 12 — buffer & snippet autocomplete engine unit tests. Pure Kotlin.
 *
 * Phase 30 note: these assertions pin the BUILT-IN tables, i.e. the fallback a
 * device uses when no snippet pack could be read. The vendored MIT packs live
 * in a process-wide singleton, so the `@Before` below guarantees this file sees
 * the fallback world regardless of Gradle's test order; `CompletionCapacityTest`
 * and `SnippetLibraryTest` (Robolectric, real assets) cover the pack world.
 */
class CodeCompletionTest {

    @Before
    fun noPacksInstalled() {
        SnippetLibrary.reset()
    }

    @Test
    fun `prefix is the word fragment before the cursor`() {
        assertEquals("foo", CodeCompletionEngine.currentPrefix("def foo", 7))
        assertEquals("", CodeCompletionEngine.currentPrefix("def foo", 4))
        assertEquals("pr", CodeCompletionEngine.currentPrefix("print(", 2))
        assertEquals("", CodeCompletionEngine.currentPrefix("", 0))
        assertEquals("main", CodeCompletionEngine.currentPrefix("int main", 8))
    }

    @Test
    fun `python snippets appear after trigger word with empty prefix`() {
        val items = CodeCompletionEngine.completions("def ", 4, LanguageType.PYTHON)
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it.kind == CompletionKind.SNIPPET })
        assertTrue(items.any { it.label.startsWith("def function") })
    }

    @Test
    fun `python prefix matches snippets and keywords`() {
        val items = CodeCompletionEngine.completions("im", 2, LanguageType.PYTHON)
        assertTrue(items.any { it.kind == CompletionKind.SNIPPET && it.label.startsWith("import") })
        assertTrue(items.any { it.kind == CompletionKind.KEYWORD && it.label == "import" })
    }

    @Test
    fun `python print prefix surfaces the print snippet`() {
        val items = CodeCompletionEngine.completions("pr", 2, LanguageType.PYTHON)
        assertTrue(items.any { it.kind == CompletionKind.SNIPPET && it.label.startsWith("print") })
    }

    @Test
    fun `buffer identifiers are suggested`() {
        val text = "myVar = 5\nmyFunc()\nmy"
        val items = CodeCompletionEngine.completions(text, text.length, LanguageType.PYTHON)
        assertTrue(items.any { it.label == "myVar" && it.kind == CompletionKind.IDENTIFIER })
        assertTrue(items.any { it.label == "myFunc" && it.kind == CompletionKind.IDENTIFIER })
        // The buffer symbols themselves are not offered as completions.
        assertTrue(items.none { it.label == "my" })
    }

    @Test
    fun `c main and include snippets match by word prefix`() {
        val main = CodeCompletionEngine.completions("int main", 8, LanguageType.C)
        assertTrue(main.any { it.kind == CompletionKind.SNIPPET && it.label.startsWith("int main") })
        val include = CodeCompletionEngine.completions("#inc", 4, LanguageType.C)
        assertTrue(include.any { it.kind == CompletionKind.SNIPPET && it.label.startsWith("#include") })
    }

    @Test
    fun `c for prefix matches both snippet and keyword`() {
        val items = CodeCompletionEngine.completions("for", 3, LanguageType.C)
        assertTrue(items.any { it.kind == CompletionKind.SNIPPET && it.label.startsWith("for (int i") })
        assertTrue(items.any { it.kind == CompletionKind.KEYWORD && it.label == "for" })
    }

    @Test
    fun `c trigger word with empty prefix shows snippets`() {
        val items = CodeCompletionEngine.completions("printf", 6, LanguageType.C)
        // prefix is "printf" (non-empty) → word match on the printf snippet.
        assertTrue(items.any { it.kind == CompletionKind.SNIPPET && it.label.startsWith("printf") })
    }

    @Test
    fun `shell if trigger shows the if snippet`() {
        val items = CodeCompletionEngine.completions("if ", 3, LanguageType.SHELL)
        assertTrue(items.any { it.kind == CompletionKind.SNIPPET && it.label.startsWith("if [") })
    }

    @Test
    fun `text and json languages produce no completions`() {
        assertTrue(CodeCompletionEngine.completions("hello", 5, LanguageType.TEXT).isEmpty())
        assertTrue(CodeCompletionEngine.completions("{\"a\": 1}", 8, LanguageType.JSON).isEmpty())
    }

    // ---- Phase 22.6: language coverage + bounded scan ---------------------

    @Test
    fun `html gets a skeleton and element snippets`() {
        val items = CodeCompletionEngine.completions("<!doc", 5, LanguageType.HTML)
        assertTrue(items.any { it.kind == CompletionKind.SNIPPET && it.label.contains("DOCTYPE") })
        val div = CodeCompletionEngine.completions("<div", 4, LanguageType.HTML)
        assertTrue(div.any { it.kind == CompletionKind.SNIPPET && it.label.contains("div") })
    }

    @Test
    fun `css rule and media query are offered on an html or css buffer`() {
        val media = CodeCompletionEngine.completions("@med", 4, LanguageType.CSS)
        assertTrue(media.any { it.kind == CompletionKind.SNIPPET && it.label.contains("@media") })
    }

    @Test
    fun `markdown gets its own snippets`() {
        val items = CodeCompletionEngine.completions("head", 4, LanguageType.MARKDOWN)
        assertTrue(items.any { it.kind == CompletionKind.SNIPPET && it.label.contains("Heading") })
        val table = CodeCompletionEngine.completions("tabl", 4, LanguageType.MARKDOWN)
        assertTrue(table.any { it.kind == CompletionKind.SNIPPET && it.label.contains("table") })
    }

    @Test
    fun `html and markdown used to return nothing at all`() {
        // Regression guard for the Phase 22.6 gap: both fell through to the
        // `else -> emptyList()` branch, so the popup never appeared on a
        // .html or .md file even though Web Preview runs HTML directly.
        assertTrue(CodeCompletionEngine.completions("<ul", 3, LanguageType.HTML).isNotEmpty())
        assertTrue(CodeCompletionEngine.completions("bold", 4, LanguageType.MARKDOWN).isNotEmpty())
    }

    @Test
    fun `snippet matching ignores case`() {
        // Phase 22.6 — found while writing the HTML tests: matching was
        // case-SENSITIVE, so lowercase `doc` never surfaced `<!DOCTYPE html>`.
        // On a phone keyboard you type lowercase; requiring the user to guess
        // a snippet's capitalization defeats a prefix search.
        val lower = CodeCompletionEngine.completions("<!doc", 5, LanguageType.HTML)
        val upper = CodeCompletionEngine.completions("<!DOC", 5, LanguageType.HTML)
        assertTrue(lower.any { it.label.contains("DOCTYPE") })
        assertTrue(upper.any { it.label.contains("DOCTYPE") })
    }

    @Test
    fun `identifier scan stays near the caret on a very long buffer`() {
        // Phase 22.6 — the scan is windowed so the per-keystroke cost is
        // bounded by SCAN_WINDOW, not by the file size. An identifier far
        // outside the window must not be offered...
        val filler = "x".repeat(CodeCompletionEngine.SCAN_WINDOW * 2)
        val text = "farAwayIdentifier = 1\n$filler\nfar"
        val items = CodeCompletionEngine.completions(text, text.length, LanguageType.PYTHON)
        assertTrue(items.none { it.label == "farAwayIdentifier" })
    }

    @Test
    fun `identifier scan still finds nearby symbols in a long buffer`() {
        // ...while a symbol just above the caret still is.
        val filler = "y".repeat(CodeCompletionEngine.SCAN_WINDOW * 2)
        val text = "$filler\nnearbyIdentifier = 1\nnear"
        val items = CodeCompletionEngine.completions(text, text.length, LanguageType.PYTHON)
        assertTrue(items.any { it.label == "nearbyIdentifier" })
    }

    @Test
    fun `the accept span covers what a word-run scan cannot see (device round 2026-09-07)`() {
        // Owner report: in C, typing `#in` and tapping the suggestion wrote
        // `##include<stdio.h>` — the accept replaced only the word run `in`
        // and left the `#` behind. Every accept surface (strip chip, sora
        // panel, ghost) now uses ONE rule: the identifier run, or the longer
        // tail of the current line that the insert text continues.
        assertEquals(3, CodeCompletionEngine.replaceSpanLength("#in", 3, "#include <stdio.h>\n"))
        assertEquals(7, CodeCompletionEngine.replaceSpanLength("int mai", 7, "int main(void) {"))
        assertEquals(4, CodeCompletionEngine.replaceSpanLength("@med", 4, "@media screen and ("))
        // Case-insensitive, because matching is (22.6 law): `<!doc` must give
        // way to `<!DOCTYPE html>` completely, not leave `<<!DOCTYPE …`.
        assertEquals(5, CodeCompletionEngine.replaceSpanLength("<!doc", 5, "<!DOCTYPE html>\n"))
        // The identifier run stays the FLOOR when the insert does not continue
        // what was typed (`head` → `# `, `mai` → `int main(…)`).
        assertEquals(4, CodeCompletionEngine.replaceSpanLength("head", 4, "# "))
        assertEquals(3, CodeCompletionEngine.replaceSpanLength("mai", 3, "int main(void) {"))
        // It never crosses a newline and never eats a member-access dot.
        assertEquals(3, CodeCompletionEngine.replaceSpanLength("int x;\nmai", 10, "main()"))
        assertEquals(4, CodeCompletionEngine.replaceSpanLength("obj.meth", 8, "method()"))
        assertEquals(0, CodeCompletionEngine.replaceSpanLength("", 0, "#include <stdio.h>"))
        // The ghost's own alignment is unchanged: CASE-SENSITIVE (it paints the
        // literal suffix) and never mid-word (27.1).
        assertEquals(7, CodeCompletionEngine.alignedTailLength("int mai", 7, "int main(void) {"))
        assertEquals(0, CodeCompletionEngine.alignedTailLength("<!doc", 5, "<!DOCTYPE html>\n"))
        assertEquals(0, CodeCompletionEngine.alignedTailLength("mai", 3, "int main(void) {"))
        assertEquals(3, CodeCompletionEngine.alignedTailLength("#in", 3, "#include <stdio.h>\n"))
    }

    @Test
    fun `completions are capped`() {
        val text = "aab aac aad aae aaf aag aah aai aaj aak aal aam aaan aao aap"
        val items = CodeCompletionEngine.completions("$text a", text.length + 2, LanguageType.PYTHON)
        assertTrue(items.size <= CodeCompletionEngine.MAX_ITEMS)
    }
}
