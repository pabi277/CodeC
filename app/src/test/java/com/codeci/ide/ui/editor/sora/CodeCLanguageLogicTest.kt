package com.codeci.ide.ui.editor.sora

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.editor.SmartTyping
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeCLanguageLogicTest {

    // ---- indentAdvanceFor ---------------------------------------------------

    /**
     * Phase 75.2 — the brace delta moved from 1 to a level. The unit was
     * recorded as found-not-changed in 75.1 and the owner's device round came
     * back with *"Int main(){ not auto indenting"* and *"{} are not indenting i
     * also tryed java same"*: one space after a `{` is not an indent you can see
     * at 16 sp, and it disagreed with the VM rule, which was already answering a
     * full level for the same line.
     */
    @Test
    fun `a brace opener adds one level, on every step`() {
        assertEquals(4, CodeCLanguage.indentAdvanceFor("int main() {", LanguageType.C, 4))
        assertEquals(4, CodeCLanguage.indentAdvanceFor("if (x) {   ", LanguageType.CPP, 4))
        assertEquals(2, CodeCLanguage.indentAdvanceFor("function f() {", LanguageType.JAVASCRIPT, 2))
        assertEquals(8, CodeCLanguage.indentAdvanceFor("class A {", LanguageType.CPP, 99))
        assertEquals(2, CodeCLanguage.indentAdvanceFor("class A {", LanguageType.CPP, 1))
        // A `.java` file is TEXT to CodeC (no Java bucket, no grammar) — it is
        // still a brace file, and the rule is not grammar-dependent.
        assertEquals(4, CodeCLanguage.indentAdvanceFor("public class A {", LanguageType.TEXT, 4))
        // Only a line that ENDS with the opener indents; a closing brace adds
        // nothing, and the defaults (no language, no step) answer a level too.
        assertEquals(0, CodeCLanguage.indentAdvanceFor("} else if (x)", LanguageType.C, 4))
        assertEquals(4, CodeCLanguage.indentAdvanceFor("int main() {"))
        assertEquals(
            "a line that closes and reopens still ends with `{`, so it indents",
            4, CodeCLanguage.indentAdvanceFor("} else if (x) {", LanguageType.C, 4)
        )
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

    /**
     * Phase 75.2 — this pin was the 75.1 "found, not changed" note, and the
     * owner's device round called it: *"Int main(){ not auto indenting"*,
     * *"{} are not indenting i also tryed java same"*. What it now pins is the
     * agreement itself — the same line, both Enter routes, one level.
     */
    @Test
    fun `the brace level is the same answer on both Enter routes`() {
        for (step in listOf(2, 3, 4, 8)) {
            for (line in listOf(
                "int main() {",
                "    if (x) {",
                "function f() {",
                "} else {",
                "x = {",
                "const a = [",           // a bracket is not a block opener
                "return 0;",
                "}",
                ""
            )) {
                val sora = CodeCLanguage.indentAdvanceFor(line, LanguageType.C, step)
                val vm = SmartTyping.handleAutoIndent(
                    TextFieldValue(line, TextRange(line.length)),
                    TextFieldValue("$line\n", TextRange(line.length + 1)),
                    LanguageType.C,
                    step
                )
                val vmAdds = vm != null && vm.text.startsWith("$line\n" + " ".repeat(1))
                if (line.trimEnd().endsWith("{")) {
                    assertEquals("sora must add exactly one level ($line, step $step)", step, sora)
                    assertTrue("and the VM route must indent too ($line, step $step)", vmAdds)
                } else {
                    assertEquals("no brace, no delta: $line", 0, sora)
                }
            }
        }
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

    /**
     * The same invariant, over BOTH rules and both kinds of opener — the shape
     * of the bug the owner keeps hitting (75.1: `for` at depth; 75.2: `{}`
     * everywhere). A line either asks for a level on both keyboards or on
     * neither; the amount is pinned by the two `… one level …` tests above.
     */
    @Test
    fun `a level is asked for on both Enter routes, in every language`() {
        val lines = listOf(
            "def f():", "class A:", "if x:", "    else:", "for i in x:  # c",
            "# note:", "x:", "d[\"k\"]:", "if x: print(1)", "print('a{')",
            "int main() {", "    if (x) {", "} else {", "x = {", "};",
            "const a = [", "return 0;", "}", "", "   ", "func main() {",
            "public class A {", "body {", "h1 {", "\"key\": {"
        )
        for (language in listOf(
            LanguageType.PYTHON, LanguageType.C, LanguageType.CPP,
            LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT, LanguageType.GO,
            LanguageType.RUST, LanguageType.TEXT
        )) {
            for (step in listOf(2, 4, 8)) {
                for (line in lines) {
                    val soraAdds = CodeCLanguage.indentAdvanceFor(line, language, step) > 0
                    assertEquals(
                        "${language.name} / step $step disagrees on: $line",
                        vmAddsALevel(line, language, step),
                        soraAdds
                    )
                }
            }
        }
    }

    /** Did the VM's Enter rule add MORE than the indentation it copied? */
    private fun vmAddsALevel(line: String, language: LanguageType, step: Int): Boolean {
        val old = TextFieldValue(line, TextRange(line.length))
        val next = TextFieldValue("$line\n", TextRange(line.length + 1))
        val vm = SmartTyping.handleAutoIndent(old, next, language, step) ?: return false
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val afterBreak = vm.text.substring(line.length + 1)
        return if (line.trimEnd().endsWith("{") && afterBreak.trimStart().startsWith("}")) {
            // The empty-pair split: the level is the run before the inserted
            // newline, not the whole tail.
            afterBreak.substringBefore('\n').length > indent.length
        } else {
            afterBreak.length > indent.length
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
