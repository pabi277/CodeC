package com.codeci.ide

import com.codeci.ide.ui.editor.FindOptions
import com.codeci.ide.ui.editor.FindOutcome
import com.codeci.ide.ui.editor.FindReplaceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 9 — find/replace engine semantics (`docs/chat-phase9/PART_9_EDITOR.md` §2.2). */
class FindReplaceTest {

    private fun matches(text: String, query: String, options: FindOptions = FindOptions()): List<IntRange> =
        when (val outcome = FindReplaceEngine.search(text, query, options)) {
            is FindOutcome.Success -> outcome.matches
            is FindOutcome.InvalidPattern -> error("unexpected invalid pattern: ${outcome.message}")
        }

    @Test
    fun `literal search is case insensitive by default`() {
        val found = matches("Foo foo FOO", "foo")
        assertEquals(listOf(0 until 3, 4 until 7, 8 until 11), found)
    }

    @Test
    fun `match case restricts to exact casing`() {
        val found = matches("Foo foo FOO", "foo", FindOptions(matchCase = true))
        assertEquals(listOf(4 until 7), found)
    }

    @Test
    fun `whole word skips substrings`() {
        val found = matches("foobar foo (foo)", "foo", FindOptions(wholeWord = true))
        assertEquals(2, found.size)
        assertEquals(7 until 10, found[0])
        assertEquals(12 until 15, found[1])
    }

    @Test
    fun `empty query yields no matches`() {
        val outcome = FindReplaceEngine.search("anything", "", FindOptions())
        assertTrue(outcome is FindOutcome.Success)
        assertTrue((outcome as FindOutcome.Success).matches.isEmpty())
    }

    @Test
    fun `invalid regex surfaces an InvalidPattern outcome`() {
        val outcome = FindReplaceEngine.search("text", "(unclosed", FindOptions(regex = true))
        assertTrue(outcome is FindOutcome.InvalidPattern)
    }

    @Test
    fun `regex search finds groups`() {
        // Kotlin MatchResult.range is inclusive; "id42" occupies 0..3, "id7" 5..7.
        val found = matches("id42 id7 x", "id(\\d+)", FindOptions(regex = true))
        assertEquals(listOf(0..3, 5..7), found)
    }

    @Test
    fun `regex whole-word wraps the pattern in word boundaries`() {
        val found = matches("cat catdog cat", "cat", FindOptions(regex = true, wholeWord = true))
        assertEquals(2, found.size)
    }

    @Test
    fun `zero-width regex matches are skipped`() {
        val found = matches("ab", "x*", FindOptions(regex = true))
        assertTrue(found.isEmpty())
    }

    @Test
    fun `next and prev wrap around the match ring`() {
        assertEquals(0, FindReplaceEngine.nextIndex(1, 2))
        assertEquals(1, FindReplaceEngine.nextIndex(0, 2))
        assertEquals(1, FindReplaceEngine.prevIndex(0, 2))
        assertEquals(0, FindReplaceEngine.prevIndex(1, 2))
        assertEquals(-1, FindReplaceEngine.nextIndex(0, 0))
        assertEquals(-1, FindReplaceEngine.prevIndex(0, 0))
        // From an unset selection, next jumps to the first match.
        assertEquals(0, FindReplaceEngine.nextIndex(-1, 3))
    }

    @Test
    fun `index for cursor prefers the forward match and wraps otherwise`() {
        val found = listOf(5 until 8, 20 until 23)
        assertEquals(0, FindReplaceEngine.indexForCursor(found, 0))
        assertEquals(0, FindReplaceEngine.indexForCursor(found, 5))
        assertEquals(1, FindReplaceEngine.indexForCursor(found, 6))
        assertEquals(0, FindReplaceEngine.indexForCursor(found, 100)) // wrap to first
        assertEquals(-1, FindReplaceEngine.indexForCursor(emptyList(), 0))
    }

    @Test
    fun `replaceOne splices exactly the active match`() {
        val text = "printf(\"hi\"); printf(\"bye\");"
        val found = matches(text, "printf")
        val replaced = FindReplaceEngine.replaceOne(text, found[1], "puts")
        assertEquals("printf(\"hi\"); puts(\"bye\");", replaced)
    }

    @Test
    fun `replace all literal handles back-to-back matches and empty replacement`() {
        val replaced = FindReplaceEngine.replaceAllLiteral(
            "aaa",
            listOf(0 until 2),
            "b"
        )
        assertEquals("ba", replaced)
        assertEquals("", FindReplaceEngine.replaceAllLiteral("foo foo", listOf(0 until 3, 4 until 7), ""))
    }

    @Test
    fun `replace all regex expands group references`() {
        val replaced = FindReplaceEngine.replaceAllRegex(
            "John Smith, Ada Lovelace",
            "(\\w+) (\\w+)",
            "$2 $1",
            matchCase = true
        )
        assertEquals("Smith John, Lovelace Ada", replaced)
    }

    @Test
    fun `replace all regex returns null on invalid pattern`() {
        assertNull(FindReplaceEngine.replaceAllRegex("abc", "(", "x", matchCase = true))
    }

    @Test
    fun `single regex replace starts at the active match position`() {
        val text = "x1 y2 z3"
        val result = FindReplaceEngine.replaceFirstRegexFrom(
            text, from = 4, query = "(\\w)(\\d)", replacement = "$2$1", matchCase = true
        )
        assertEquals("x1 y2 z3".length, 8)
        assertEquals("x1 2y z3", result?.first)
        assertEquals(6, result?.second)
    }

    @Test
    fun `overlapping candidates are consumed left to right without overlap`() {
        val found = matches("aaaa", "aa")
        assertEquals(listOf(0 until 2, 2 until 4), found)
    }
}
