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

    @Test
    fun `python colon adds one level but comments do not`() {
        assertEquals(1, CodeCLanguage.indentAdvanceFor("def f():", LanguageType.PYTHON))
        assertEquals(0, CodeCLanguage.indentAdvanceFor("# note:", LanguageType.PYTHON))
        // Non-python languages do not treat ':' as an opener.
        assertEquals(0, CodeCLanguage.indentAdvanceFor("case 3:", LanguageType.C))
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
