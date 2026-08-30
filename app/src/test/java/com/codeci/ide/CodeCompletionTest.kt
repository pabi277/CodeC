package com.codeci.ide

import com.codeci.ide.ui.editor.CodeCompletionEngine
import com.codeci.ide.ui.editor.CompletionKind
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 12 — buffer & snippet autocomplete engine unit tests. Pure Kotlin.
 */
class CodeCompletionTest {

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

    @Test
    fun `completions are capped`() {
        val text = "aab aac aad aae aaf aag aah aai aaj aak aal aam aaan aao aap"
        val items = CodeCompletionEngine.completions("$text a", text.length + 2, LanguageType.PYTHON)
        assertTrue(items.size <= CodeCompletionEngine.MAX_ITEMS)
    }
}
