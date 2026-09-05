package com.codeci.bench.keys

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28.1 — the spike grid's pure model. These tests pin the claim the
 * whole part hangs on: the grid inserts through the PRODUCTION key model
 * (`EditorKeySet.apply`, mirrored verbatim), and DEL — the one cap the model
 * does not have yet — behaves like the editor backspace the IME does today.
 */
class CodecKeyGridTest {

    @Test fun `rows are qwerty 3 plus the special row`() {
        val rows = CodecKeyGrid.rows()
        assertEquals(4, rows.size)
        assertEquals(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), rows[0].map { it.label })
        assertEquals(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"), rows[1].map { it.label })
        assertEquals(listOf("z", "x", "c", "v", "b", "n", "m"), rows[2].map { it.label })
        assertEquals(listOf("TAB", "DEL", "⏎", "space"), rows[3].map { it.label })
    }

    @Test fun `all 26 letters are present exactly once`() {
        val letters = CodecKeyGrid.letterRows.flatten().map { it.label }
        assertEquals(26, letters.size)
        assertEquals(('a'..'z').map { it.toString() }.sorted(), letters.sorted())
    }

    @Test fun `only DEL repeats and only space is wide`() {
        for (cap in CodecKeyGrid.allCaps()) {
            if (cap.label == "DEL") {
                assertTrue(cap.holdRepeat)
                assertTrue(cap.backspace)
            } else {
                assertFalse("cap ${cap.label} must not repeat in the spike", cap.holdRepeat)
                assertEquals(cap.wide, cap.label == "space")
            }
        }
    }

    @Test fun `find resolves letters TAB DEL space and enter`() {
        // GridKeycap is a data class — equality on the singleton definition.
        assertEquals(GridKeycap.TAB, CodecKeyGrid.find("TAB"))
        assertEquals(GridKeycap.DEL, CodecKeyGrid.find("DEL"))
        assertEquals(GridKeycap.SPACE, CodecKeyGrid.find("space"))
        assertEquals(GridKeycap.ENTER, CodecKeyGrid.find("⏎"))
        assertEquals(GridKeycap.letter('q'), CodecKeyGrid.find("q"))
        assertNull(CodecKeyGrid.find("1"))
        assertNull(CodecKeyGrid.find("nope"))
    }

    @Test fun `letter insert appends after the caret and lands the caret`() {
        val start = TextFieldValue("ab", TextRange(2))
        val next = CodecKeyGrid.commit(GridKeycap.letter('c'), start)
        assertEquals("abc", next.text)
        assertEquals(3, next.selection.start)
    }

    @Test fun `letter insert replaces the selection like EditorKeySet`() {
        val start = TextFieldValue("hello", TextRange(0, 5))
        val next = CodecKeyGrid.commit(GridKeycap.letter('x'), start)
        assertEquals("x", next.text)
        assertEquals(1, next.selection.start)
    }

    @Test fun `tab cap inserts four spaces - the app indent law`() {
        val start = TextFieldValue("", TextRange(0))
        val next = CodecKeyGrid.commit(GridKeycap.TAB, start)
        assertEquals("    ", next.text)
    }

    @Test fun `enter cap inserts a newline`() {
        val next = CodecKeyGrid.commit(GridKeycap.ENTER, TextFieldValue("a", TextRange(1)))
        assertEquals("a\n", next.text)
    }

    @Test fun `backspace deletes the char before the caret`() {
        val next = CodecKeyGrid.backspace(TextFieldValue("abc", TextRange(3)))
        assertEquals("ab", next.text)
        assertEquals(2, next.selection.start)
    }

    @Test fun `backspace on a selection deletes the selection`() {
        val next = CodecKeyGrid.backspace(TextFieldValue("abcdef", TextRange(1, 4)))
        assertEquals("aef", next.text)
    }

    @Test fun `backspace at the document start is a no-op not a crash`() {
        val value = TextFieldValue("abc", TextRange(0))
        val next = CodecKeyGrid.backspace(value)
        assertEquals("abc", next.text)
    }

    @Test fun `expectedText folds the model over a press sequence`() {
        val caps = listOf(
            GridKeycap.letter('i'), GridKeycap.letter('n'), GridKeycap.letter('t'),
            GridKeycap.SPACE, GridKeycap.letter('m'), GridKeycap.ENTER, GridKeycap.TAB,
            GridKeycap.letter('x'), GridKeycap.DEL, GridKeycap.DEL
        )
        // "int m\n    x" with the trailing 'x' deleted by the DEL:
        assertEquals("int m\n    ", CodecKeyGrid.expectedText(caps))
    }
}
