package com.codeci.ide

import com.codeci.ide.ui.editor.BracketMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 9 — bracket pair matching (`docs/chat-phase9/PART_9_EDITOR.md` §2.4). */
class BracketMatcherTest {

    @Test
    fun `matches parentheses from the opening side`() {
        val text = "func(a, b)"
        // 'f'0 ... '('4 ')'9
        assertEquals(4 to 9, BracketMatcher.findPair(text, 4))
    }

    @Test
    fun `matches from the closing side`() {
        val text = "func(a, b)"
        assertEquals(4 to 9, BracketMatcher.findPair(text, 10)) // caret just after ')'
        assertEquals(4 to 9, BracketMatcher.findPair(text, 9))  // caret on ')'
    }

    @Test
    fun `handles nesting of the same kind`() {
        val text = "{ { } }"
        assertEquals(0 to 6, BracketMatcher.findPair(text, 0))
        assertEquals(2 to 4, BracketMatcher.findPair(text, 2))
        assertEquals(2 to 4, BracketMatcher.findPair(text, 5))
    }

    @Test
    fun `brackets inside string literals are ignored`() {
        val text = "printf(\"}\");"
        // The '}' at index 8 lives inside the string; the real '(' at 6 must match ')' at 10.
        assertEquals(6 to 10, BracketMatcher.findPair(text, 6))
        assertNull(BracketMatcher.findPair(text, 8))
    }

    @Test
    fun `brackets inside comments are ignored`() {
        val text = "int x; // )\n{ }"
        val braceOpen = text.indexOf('{')
        val braceClose = text.indexOf('}')
        assertEquals(braceOpen to braceClose, BracketMatcher.findPair(text, braceOpen))
    }

    @Test
    fun `escaped quote inside a string does not end it`() {
        val text = "char q = '\\\"'; (a)"
        val open = text.indexOf('(')
        val close = text.indexOf(')')
        assertEquals(open to close, BracketMatcher.findPair(text, open))
    }

    @Test
    fun `unbalanced brackets return null`() {
        assertNull(BracketMatcher.findPair("{ [", 0))
        assertNull(BracketMatcher.findPair(")", 0))
        assertNull(BracketMatcher.findPair("(]", 0)) // mismatched kind closes nothing
    }

    @Test
    fun `cursor on non-bracket returns null`() {
        assertNull(BracketMatcher.findPair("int main()", 2))
    }

    @Test
    fun `oversized buffers are skipped by the guard`() {
        val huge = "(".repeat(BracketMatcher.MAX_SCAN_LENGTH + 10)
        assertNull(BracketMatcher.findPair(huge, 0))
    }
}
