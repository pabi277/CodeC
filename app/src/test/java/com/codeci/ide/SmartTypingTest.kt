package com.codeci.ide

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.editor.SmartTyping
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 26.2 — Smart typing host tests (pure, no Android).
 */
class SmartTypingTest {

    @Test
    fun `typeOver moves caret over matching closer instead of inserting`() {
        val old = TextFieldValue("()", TextRange(1))
        // old caret between '(' and ')', next char is ')'
        val incoming = ")"
        val res = SmartTyping.handleTypeOver(old, incoming, SmartTyping.Config(), LanguageType.C)
        assertEquals(TextRange(2), res?.selection)
        assertEquals("()", res?.text)
    }

    @Test
    fun `typeOver returns null when next char is not matcher`() {
        val old = TextFieldValue("ab", TextRange(1))
        val res = SmartTyping.handleTypeOver(old, ")", SmartTyping.Config(), LanguageType.C)
        assertEquals(null, res)
    }

    @Test
    fun `wrapSelection surrounds selection with pair`() {
        val old = TextFieldValue("hello world", TextRange(6, 11)) // select "world"
        val res = SmartTyping.handleWrapSelection(old, "(", SmartTyping.Config())
        assertEquals("hello (world)", res?.text)
        // selection should be inside parens: start 7, end 12 ("world" kept selected)
        assertEquals(TextRange(7, 12), res?.selection)
    }

    @Test
    fun `wrapSelection respects config toggle off`() {
        val old = TextFieldValue("hello world", TextRange(6, 11))
        val res = SmartTyping.handleWrapSelection(old, "(", SmartTyping.Config(wrapSelection = false))
        assertEquals(null, res)
    }

    @Test
    fun `empty pair backspace deletes both sides`() {
        val old = TextFieldValue("()", TextRange(1))
        val res = SmartTyping.handleEmptyPairBackspace(old, SmartTyping.Config(), LanguageType.C)
        assertEquals("", res?.text)
        assertEquals(TextRange(0), res?.selection)
    }

    @Test
    fun `deletePrevWord deletes word before caret leaving dot`() {
        val value = TextFieldValue("foo.bar", TextRange(7))
        val res = SmartTyping.deletePrevWord(value)
        assertEquals("foo.", res.text)
        assertEquals(4, res.selection.start)
    }

    @Test
    fun `deletePrevWord with whitespace before deletes whitespace run`() {
        val value = TextFieldValue("foo  bar", TextRange(8))
        // caret after "bar", should delete "bar"
        val res = SmartTyping.deletePrevWord(value)
        assertEquals("foo  ", res.text)
    }

    @Test
    fun `autoIndent after open brace indents next line`() {
        // Simulate old "{" + caret after it, then user presses Enter -> newValue has \n after '{'
        val old = TextFieldValue("{", TextRange(1))
        val newValue = TextFieldValue("{\n", TextRange(2))
        val res = SmartTyping.handleAutoIndent(old, newValue, LanguageType.C, tabSize = 4, config = SmartTyping.Config())
        // Should have added indent after newline (but old line indent is empty, so extra = 4 spaces)
        assertEquals("{\n    ", res?.text)
    }

    @Test
    fun `autoIndent copies previous line indent`() {
        // Simulate pressing Enter after a line that starts with 4 spaces.
        val old = TextFieldValue("    x = 1;", TextRange(10))
        val newValue = TextFieldValue("    x = 1;\n", TextRange(11))
        val res = SmartTyping.handleAutoIndent(old, newValue, LanguageType.C, tabSize = 4, config = SmartTyping.Config())
        // Should indent the new line with the same 4 spaces.
        assertEquals("    x = 1;\n    ", res?.text)
        assertEquals(15, res?.selection?.start)
    }

    @Test
    fun `transform dispatches typeOver for closer at boundary`() {
        val old = TextFieldValue("()", TextRange(1))
        val newValue = TextFieldValue("())", TextRange(2)) // naive insert of ')'
        val lang = LanguageType.C
        val out = SmartTyping.transform(old, newValue, lang, tabSize = 4, config = SmartTyping.Config())
        // typeOver should move caret to 2 without inserting extra )
        assertEquals("()", out.text)
        assertEquals(2, out.selection.start)
    }

    @Test
    fun `transform leaves string aware content untouched when disabled`() {
        val old = TextFieldValue("\"hello\"", TextRange(1))
        val newValue = TextFieldValue("\"hhello\"", TextRange(2))
        val out = SmartTyping.transform(old, newValue, LanguageType.C, config = SmartTyping.Config(stringAware = false))
        // no smart rule for 'h', so should return newValue unchanged
        assertEquals(newValue.text, out.text)
    }
}
