package com.codeci.ide

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.editor.AcceptGranularity
import com.codeci.ide.ui.editor.CompletionKind
import com.codeci.ide.ui.editor.CompletionItem
import com.codeci.ide.ui.editor.GhostCompletion
import com.codeci.ide.ui.editor.GhostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 27.1 — the ghost-text pure logic. Pins G1 (prefix-matched suffix
 * only), G2 (typing never commits), G3 (FULL / WORD / LINE accept), G6
 * (multi-line suggestions ghost their first line only) and the instant
 * shrink filter (no engine re-run per keystroke).
 */
class GhostCompletionTest {

    private fun item(label: String, insert: String, kind: CompletionKind = CompletionKind.SNIPPET) =
        CompletionItem(label, insert, kind)

    private val printf = item("printf(...)", "printf(\"\\n\");")

    // ---- compute (G1/G2/G6) ----------------------------------------------

    @Test
    fun `ghost shows the insert suffix after the typed prefix`() {
        val text = "int main() {\n    print\n}\n"
        val caret = text.indexOf("print") + 5
        val ghost = GhostCompletion.compute(text, caret, listOf(printf))
        assertTrue(ghost is GhostState.Visible)
        assertEquals("f(\"\\n\");", (ghost as GhostState.Visible).suffix)
        assertEquals(5, ghost.prefixLength)
    }

    @Test
    fun `ghost hidden when no item starts with the prefix`() {
        val text = "value = unknow"
        val ghost = GhostCompletion.compute(text, text.length, listOf(printf))
        assertEquals(GhostState.Hidden, ghost)
    }

    @Test
    fun `ghost hidden on empty prefix or empty items`() {
        assertEquals(GhostState.Hidden, GhostCompletion.compute("x = ", 4, listOf(printf)))
        assertEquals(GhostState.Hidden, GhostCompletion.compute("pri", 3, emptyList()))
    }

    @Test
    fun `fully typed item ghosts nothing (rest is empty)`() {
        val exact = item("name", "name")
        val ghost = GhostCompletion.compute("name", 4, listOf(exact))
        assertEquals(GhostState.Hidden, ghost)
    }

    @Test
    fun `G6 multi-line insert ghosts the FIRST line only`() {
        val skel = item("int main(void) {", "int main(void) {\n    \n    return 0;\n}")
        // prefix = the leading word "int"; the rest of line 1 ghosts, line 2+ never do (G6)
        val text = "int"
        val ghost = GhostCompletion.compute(text, 3, listOf(skel))
        assertTrue(ghost is GhostState.Visible)
        assertEquals(" main(void) {", (ghost as GhostState.Visible).suffix) // no '\n' in a ghost
        assertTrue(!ghost.suffix.contains('\n'))
    }

    @Test
    fun `skip items that do not match, take the first that does`() {
        val other = item("#include <stdio.h>", "#include <stdio.h>\n")
        val ghost = GhostCompletion.compute("pri", 3, listOf(other, printf))
        assertTrue(ghost is GhostState.Visible)
        assertEquals(printf.label, (ghost as GhostState.Visible).item.label)
    }

    // ---- nextWordPiece (G3 word partial accept) ---------------------------

    @Test
    fun `word piece = identifier run`() {
        assertEquals("f", GhostCompletion.nextWordPiece("f(\"\\n\");"))
        assertEquals("intf", GhostCompletion.nextWordPiece("intf();"))
    }

    @Test
    fun `word piece = symbol run (stops at identifier or whitespace)`() {
        assertEquals("(\"\\", GhostCompletion.nextWordPiece("(\"\\n\");"))
    }

    @Test
    fun `word piece = whitespace run`() {
        assertEquals("    ", GhostCompletion.nextWordPiece("    return"))
        assertEquals(" ", GhostCompletion.nextWordPiece(" struct"))
    }

    @Test
    fun `word piece never crosses a newline (leading newline is its own piece)`() {
        assertEquals("\n", GhostCompletion.nextWordPiece("\n    x"))
        assertEquals("(", GhostCompletion.nextWordPiece("(\n)"))
    }

    // ---- accept (G3 granularities) ----------------------------------------

    @Test
    fun `FULL accept replaces the prefix with the whole insert`() {
        val text = "int main() {\n    pri\n}\n"
        val caret = text.indexOf("pri") + 3
        val ghost = GhostCompletion.compute(text, caret, listOf(printf)) as GhostState.Visible
        val next = GhostCompletion.accept(TextFieldValue(text, TextRange(caret)), ghost, AcceptGranularity.FULL)!!
        assertTrue(next.text.contains("printf(\"\\n\");"))
        assertEquals(next.text.indexOf("printf(\"\\n\");") + "printf(\"\\n\");".length, next.selection.start)
    }

    @Test
    fun `WORD accept appends one piece at a time, prefix kept`() {
        val text = "pri"
        val ghost = GhostCompletion.compute(text, 3, listOf(printf)) as GhostState.Visible
        var v = GhostCompletion.accept(TextFieldValue(text, TextRange(3)), ghost, AcceptGranularity.WORD)!!
        assertEquals("printf", v.text) // ntf piece appended
        assertEquals(6, v.selection.start)
        // Recompute against the live buffer and accept the next piece —
        // symbols up to the next identifier char.
        val g2 = GhostCompletion.compute(v.text, 6, listOf(printf)) as GhostState.Visible
        v = GhostCompletion.accept(v, g2, AcceptGranularity.WORD)!!
        assertEquals("printf(\"\\", v.text)
    }

    @Test
    fun `LINE accept fills to and including the next newline`() {
        val body = "std::cout << x << std::endl;\nreturn 0;"
        val it = item("cpp line", body)
        // caret at end of "std": the prefix is "std", LINE fills the rest
        // of the first inserted line (+ the trailing newline).
        val ghost = GhostCompletion.compute("std", 3, listOf(it)) as GhostState.Visible
        val next = GhostCompletion.accept(
            TextFieldValue("std", TextRange(3)), ghost, AcceptGranularity.LINE
        )!!
        assertEquals(body.substringBefore('\n') + "\n", next.text)
    }

    @Test
    fun `multi-line FULL accept keeps all lines, caret after insert`() {
        val skel = item("def function():", "def function():\n    ")
        val text = "def"
        val ghost = GhostCompletion.compute(text, 3, listOf(skel)) as GhostState.Visible
        val next = GhostCompletion.accept(TextFieldValue(text, TextRange(3)), ghost, AcceptGranularity.FULL)!!
        assertEquals("def function():\n    ", next.text)
        assertEquals(next.text.length, next.selection.start)
    }

    @Test
    fun `accept never applies to a selection or a stale ghost`() {
        val text = "printx"
        val ghost = GhostCompletion.compute("print", 5, listOf(printf))
        // selection exists
        assertNull(GhostCompletion.accept(TextFieldValue(text, TextRange(0, 3)), ghost))
        // caret moved past what the ghost expects (ghost computed for "print")
        assertNull(GhostCompletion.accept(TextFieldValue("printx", TextRange(6)), ghost))
        assertNull(GhostCompletion.accept(TextFieldValue("", TextRange(0)), GhostState.Hidden))
    }

    // ---- filterForPrefix (instant shrink, G2 latency) ---------------------

    @Test
    fun `instant shrink narrows by grown prefix without the engine`() {
        val items = listOf(
            item("printf(...)", "printf(\"\\n\");"),
            item("int main(void) {", "int main(void) {\n    \n    return 0;\n}"),
            item("for (int i = 0; i < n; i++) {", "for (int i = 0; i < n; i++) {\n    \n}")
        )
        // Only printf matches "p" (the other labels have no p-initial word).
        assertEquals(1, GhostCompletion.filterForPrefix(items, "p").size)
        assertEquals(listOf("printf(...)"), GhostCompletion.filterForPrefix(items, "prin").map { it.label })
        assertEquals(emptyList<CompletionItem>(), GhostCompletion.filterForPrefix(items, "prix"))
        assertEquals(emptyList<CompletionItem>(), GhostCompletion.filterForPrefix(items, ""))
    }

    @Test
    fun `fully typed inline item (insertText == prefix) drops out of the strip`() {
        val kw = item("while", "while", CompletionKind.KEYWORD)
        assertEquals(emptyList<CompletionItem>(), GhostCompletion.filterForPrefix(listOf(kw), "while"))
        // but a longer insert stays (there is still something to insert)
        val call = item("print", "print(")
        assertEquals(1, GhostCompletion.filterForPrefix(listOf(call), "print").size)
    }
}
