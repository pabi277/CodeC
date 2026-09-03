package com.codeci.ide

import com.codeci.ide.ui.editor.CodeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 22.5 — the cursor readout math that runs on EVERY keystroke and caret
 * move. `refreshDecorationsNow` used to build the line number with
 * `text.take(cursor).count { it == '\n' }`, which allocates a copy of the
 * whole prefix — at the end of a long file, a full-file copy per character.
 * It now counts in place and derives the line start from the same single
 * pass. This test pins the arithmetic that replaced it so the optimization
 * cannot silently change the Ln/Col readout.
 */
class EditorCursorMathTest {

    /** Mirrors the in-place scan in `EditorViewModel.refreshDecorationsNow`. */
    private fun lineAndColumn(text: String, cursor: Int): Pair<Int, Int> {
        var line = 1
        var lineStart = 0
        for (i in 0 until cursor) {
            if (text[i] == '\n') {
                line++
                lineStart = i + 1
            }
        }
        return line to (cursor - lineStart + 1)
    }

    /** The original implementation, kept as the oracle. */
    private fun reference(text: String, cursor: Int): Pair<Int, Int> {
        val line = text.take(cursor).count { it == '\n' } + 1
        val lineStart = CodeFormatter.lineStartOffset(text, line)
        return line to (cursor - lineStart + 1)
    }

    @Test
    fun `start of buffer is line 1 column 1`() {
        assertEquals(1 to 1, lineAndColumn("abc\ndef", 0))
    }

    @Test
    fun `column counts from the line start`() {
        assertEquals(1 to 3, lineAndColumn("abc\ndef", 2))
        assertEquals(2 to 1, lineAndColumn("abc\ndef", 4))
        assertEquals(2 to 4, lineAndColumn("abc\ndef", 7))
    }

    @Test
    fun `the caret sitting on a newline still belongs to the line it ends`() {
        assertEquals(1 to 4, lineAndColumn("abc\ndef", 3))
    }

    @Test
    fun `consecutive blank lines advance the line number`() {
        assertEquals(4 to 1, lineAndColumn("a\n\n\nb", 4))
    }

    @Test
    fun `in-place scan agrees with the allocating reference across a long file`() {
        val text = (1..500).joinToString("\n") { "int line$it = $it; // filler text" }
        // Every line start, every line end, and a scatter of interior points.
        val probes = buildList {
            var offset = 0
            text.split("\n").forEach { line ->
                add(offset)
                add(offset + line.length / 2)
                add(offset + line.length)
                offset += line.length + 1
            }
        }.filter { it in 0..text.length }
        probes.forEach { cursor ->
            assertEquals("cursor=$cursor", reference(text, cursor), lineAndColumn(text, cursor))
        }
    }

    @Test
    fun `empty buffer is line 1 column 1`() {
        assertEquals(1 to 1, lineAndColumn("", 0))
    }
}
