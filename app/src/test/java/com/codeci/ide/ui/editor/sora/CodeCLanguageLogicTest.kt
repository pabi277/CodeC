package com.codeci.ide.ui.editor.sora

import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeCLanguageLogicTest {

    // ---- indentAdvanceFor ---------------------------------------------------

    @Test
    fun `c block opener adds one level`() {
        assertEquals(1, CodeCLanguage.indentAdvanceFor("int main() {"))
        assertEquals(1, CodeCLanguage.indentAdvanceFor("if (x) {   "))
    }

    /**
     * Phase 75.1 — this pin moved, with its reason. Sora's contract for
     * `Language.getIndentAdvance` is a *delta count of indent spaces* (0.24.6
     * `CodeEditor.commitText`: the copied indentation plus the delta is handed
     * straight to `TextUtils.createIndent`), so a Python level is the tab size
     * and not `1`. And the Python branch could never run at all until 75.1 —
     * the adapter called this helper without its language argument, which is
     * the "`def` indents, `for` does not" the owner reported.
     */
    @Test
    fun `a python block header asks for one level in spaces`() {
        assertEquals(4, CodeCLanguage.indentAdvanceFor("def f():", LanguageType.PYTHON))
        assertEquals(4, CodeCLanguage.indentAdvanceFor("for item in items:", LanguageType.PYTHON))
        assertEquals(
            2,
            CodeCLanguage.indentAdvanceFor("for item in items:", LanguageType.PYTHON, indentStep = 2)
        )
        assertEquals(4, CodeCLanguage.indentAdvanceFor("for i in items:  # walk", LanguageType.PYTHON))
        assertEquals(0, CodeCLanguage.indentAdvanceFor("# note:", LanguageType.PYTHON))
        assertEquals(0, CodeCLanguage.indentAdvanceFor("x:", LanguageType.PYTHON))
        // Non-python languages do not treat ':' as an opener.
        assertEquals(0, CodeCLanguage.indentAdvanceFor("case 3:", LanguageType.C))
    }

    @Test
    fun `the brace delta keeps the number it shipped with`() {
        // Phase 75.1 is a Python typing fix; the brief says the non-Python
        // brace indentation stays as it is. Its 1-space delta (a level would be
        // the tab size) is recorded in the phase doc as found-not-changed, for
        // the owner to call in its own pass.
        assertEquals(1, CodeCLanguage.indentAdvanceFor("int main() {", LanguageType.C, 4))
        assertEquals(0, CodeCLanguage.indentAdvanceFor("int main()", LanguageType.C, 4))
    }

    @Test
    fun `both Enter routes agree on which line opens a python block`() {
        // The bug was two rules that could not see each other. Whatever the
        // line, Sora's delta and the VM's level must answer the same question.
        for (line in listOf(
            "def f():",
            "for item in items:",
            "    while True:",
            "else:",
            "try:",
            "for i in x:  # c",
            "# note:",
            "x:",
            "d[\"k\"]:",
            "if x: print(1)",
            "plain = 1",
            "}",
            ""
        )) {
            val soraAdds = CodeCLanguage.indentAdvanceFor(line, LanguageType.PYTHON, 4) > 0
            assertEquals(
                "the two Enter routes disagree on: $line",
                com.codeci.ide.ui.editor.SmartTyping.opensPythonBlock(line),
                soraAdds
            )
        }
    }

    @Test
    fun `plain lines add nothing`() {
        assertEquals(0, CodeCLanguage.indentAdvanceFor("return total;"))
        assertEquals(0, CodeCLanguage.indentAdvanceFor(""))
        assertEquals(0, CodeCLanguage.indentAdvanceFor("   "))
        assertEquals(0, CodeCLanguage.indentAdvanceFor("}"))
    }

    // ---- symbolPairsFor ------------------------------------------------------

    @Test
    fun `code languages get the standard pairs`() {
        for (language in listOf(
            LanguageType.C, LanguageType.CPP, LanguageType.PYTHON,
            LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT, LanguageType.HTML,
            LanguageType.CSS, LanguageType.GO, LanguageType.JSON,
            LanguageType.SHELL
        )) {
            val pairs = CodeCLanguage.symbolPairsFor(language)
            assertNotNull(pairs.matchBestPairBySingleChar('('))
            assertNotNull(pairs.matchBestPairBySingleChar('{'))
            assertNotNull(pairs.matchBestPairBySingleChar('['))
            assertNotNull(pairs.matchBestPairBySingleChar('"'))
        }
    }

    @Test
    fun `prose formats get no pairs`() {
        for (language in listOf(LanguageType.TEXT, LanguageType.MARKDOWN)) {
            val pairs = CodeCLanguage.symbolPairsFor(language)
            assertEquals(null, pairs.matchBestPairBySingleChar('('))
            assertEquals(null, pairs.matchBestPairBySingleChar('"'))
        }
    }

    // ---- LineColumnCursor ----------------------------------------------------

    @Test
    fun `cursor walks ordered spans into line column pairs`() {
        val text = "ab\ncdef\ngh" // lines: "ab", "cdef", "gh" (length 10)
        val cursor = LineColumnCursor(text)
        // offset 0 -> (0,0); 4 -> after 'c' on line 1; 10 -> after 'h' on line 2
        assertEquals(0 to 0, cursor.advance(0))
        assertEquals(1 to 1, cursor.advance(4))
        assertEquals(2 to 2, cursor.advance(text.length))
    }

    @Test
    fun `cursor clamps backwards reads and re-reads`() {
        val text = "abc"
        val cursor = LineColumnCursor(text)
        assertEquals(0 to 3, cursor.advance(3))
        // Ordered spans never go back; a stale target is a no-op read.
        assertEquals(0 to 3, cursor.advance(1))
    }

    // ---- analyzer integration (pure tokenizer path) --------------------------

    @Test
    fun `tokenize feeds spans that the cursor can place`() {
        val text = "/* hi */ int x = 5;\nreturn x;"
        val spans = com.codeci.ide.ui.utils.MultiLanguageSyntaxHighlighter.tokenize(
            text, LanguageType.C
        )
        assertTrue(spans.isNotEmpty())
        val cursor = LineColumnCursor(text)
        var previous = 0 to 0
        for (span in spans) {
            val position = cursor.advance(span.start)
            // Spans are ordered: the line never goes back; within a line the
            // column never goes back either.
            assertTrue(
                position.first > previous.first ||
                    (position.first == previous.first && position.second >= previous.second)
            )
            previous = position
            cursor.advance(span.end)
        }
    }
}
