package com.codeci.ide

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.editor.DiagnosticSeverity
import com.codeci.ide.ui.editor.EditorDiagnostic
import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.editor.EditorKeySet
import com.codeci.ide.ui.editor.EditorShellUi
import com.codeci.ide.ui.editor.FontSizeZoom
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 16 — host tests for the editor shell's pure logic: the snippet-row
 * key engine (insert/caret/TAB math), the custom-snippet data model, the
 * pinch font-zoom steps, and the tab-bar/status-bar model functions.
 */
class EditorKeySetTest {

    // ---- EditorKeySet.apply ------------------------------------------------

    @Test
    fun `insert at caret lands text and caret after it`() {
        val out = EditorKeySet.apply(EditorKey.Insert("X"), TextFieldValue("ab", TextRange(0)), tabSize = 4)
        assertEquals("Xab", out.text)
        assertEquals(TextRange(1), out.selection)
    }

    @Test
    fun `insert replaces the selection`() {
        val value = TextFieldValue("abcd", TextRange(1, 3))
        val out = EditorKeySet.apply(EditorKey.Insert("Y"), value, tabSize = 4)
        assertEquals("aYd", out.text)
        assertEquals(2, out.selection.start)
        assertTrue(out.selection.collapsed)
    }

    @Test
    fun `TAB inserts the configured indent run`() {
        val out = EditorKeySet.apply(EditorKey.Tab, TextFieldValue("cd", TextRange(0)), tabSize = 4)
        assertEquals("    cd", out.text)
        assertEquals(4, out.selection.start)
    }

    @Test
    fun `TAB clamps tabSize into the editor range 2 to 8`() {
        val tiny = EditorKeySet.apply(EditorKey.Tab, TextFieldValue("", TextRange(0)), tabSize = 1)
        assertEquals("  ", tiny.text)
        val huge = EditorKeySet.apply(EditorKey.Tab, TextFieldValue("", TextRange(0)), tabSize = 10)
        assertEquals("        ", huge.text)
    }

    @Test
    fun `caret left collapses a selection to its start and clamps at zero`() {
        val collapsed = EditorKeySet.apply(
            EditorKey.Caret(EditorKey.Caret.Move.LEFT),
            TextFieldValue("abc", TextRange(1, 3)),
            tabSize = 4
        )
        assertEquals(1, collapsed.selection.start)
        val atStart = EditorKeySet.apply(
            EditorKey.Caret(EditorKey.Caret.Move.LEFT),
            TextFieldValue("abc", TextRange(0)),
            tabSize = 4
        )
        assertEquals(0, atStart.selection.start)
        val one = EditorKeySet.apply(
            EditorKey.Caret(EditorKey.Caret.Move.LEFT),
            TextFieldValue("abc", TextRange(2)),
            tabSize = 4
        )
        assertEquals(1, one.selection.start)
    }

    @Test
    fun `caret right moves to selection end and clamps at the text end`() {
        val collapsed = EditorKeySet.apply(
            EditorKey.Caret(EditorKey.Caret.Move.RIGHT),
            TextFieldValue("abc", TextRange(1, 3)),
            tabSize = 4
        )
        assertEquals(3, collapsed.selection.start)
        val atEnd = EditorKeySet.apply(
            EditorKey.Caret(EditorKey.Caret.Move.RIGHT),
            TextFieldValue("abc", TextRange(3)),
            tabSize = 4
        )
        assertEquals(3, atEnd.selection.start)
    }

    @Test
    fun `caret up and down travel by line keeping the column`() {
        val text = "int main() {\n    x = 1;\n}"
        // line 2 starts at 13; 'x' sits at 17 → column 4.
        val up = EditorKeySet.apply(
            EditorKey.Caret(EditorKey.Caret.Move.UP),
            TextFieldValue(text, TextRange(17)),
            tabSize = 4
        )
        assertEquals(4, up.selection.start)
        val down = EditorKeySet.apply(
            EditorKey.Caret(EditorKey.Caret.Move.DOWN),
            TextFieldValue(text, TextRange(4)),
            tabSize = 4
        )
        assertEquals(17, down.selection.start)
        val clampUp = EditorKeySet.apply(
            EditorKey.Caret(EditorKey.Caret.Move.UP),
            TextFieldValue(text, TextRange(0)),
            tabSize = 4
        )
        assertEquals(0, clampUp.selection.start)
        val clampDown = EditorKeySet.apply(
            EditorKey.Caret(EditorKey.Caret.Move.DOWN),
            TextFieldValue(text, TextRange(text.length - 1)),
            tabSize = 4
        )
        assertEquals(text.length, clampDown.selection.start)
    }

    // ---- keysFor / custom snippets ----------------------------------------

    @Test
    fun `general set starts with TAB and offers brackets as pairs`() {
        // Phase 22.5 — the four bracket families and both quote styles are
        // single PAIR caps now (owner: "make pair in single key"), so the
        // row is shorter and each tap yields a balanced construct.
        val labels = EditorKeySet.keysFor(null).map { it.label }
        assertEquals("TAB", labels.first())
        assertTrue(labels.containsAll(listOf("()", "{}", "[]", "<>", "\"\"", "''", ";", "/", "=", "←", "→", "↑", "↓")))
    }

    @Test
    fun `a pair key inserts both halves and lands the caret between them`() {
        val out = EditorKeySet.apply(EditorKey.Pair("(", ")"), TextFieldValue("ab", TextRange(1)), tabSize = 4)
        assertEquals("a()b", out.text)
        assertEquals(TextRange(2), out.selection)
        assertTrue(out.selection.collapsed)
    }

    @Test
    fun `a pair key surrounds a selection and keeps it selected`() {
        val value = TextFieldValue("say hello there", TextRange(4, 9))
        val out = EditorKeySet.apply(EditorKey.Pair("\"", "\""), value, tabSize = 4)
        assertEquals("say \"hello\" there", out.text)
        // The original word stays selected, now inside the quotes.
        assertEquals("hello", out.text.substring(out.selection.start, out.selection.end))
    }

    @Test
    fun `every bracket family is available as a pair`() {
        val pairs = EditorKeySet.keysFor(null)
            .mapNotNull { it.key as? EditorKey.Pair }
            .map { it.open to it.close }
        assertTrue(pairs.containsAll(listOf("(" to ")", "{" to "}", "[" to "]", "<" to ">")))
    }

    @Test
    fun `language tails extend the general set only`() {
        val base = EditorKeySet.keysFor(null).size
        val c = EditorKeySet.keysFor(LanguageType.C)
        val py = EditorKeySet.keysFor(LanguageType.PYTHON)
        assertEquals(base + 1, c.size)
        assertEquals("->", c.last().label)
        assertEquals(base + 2, py.size)
        assertEquals(":", py[base].label)
    }

    @Test
    fun `the JS template literal tail is a pair`() {
        val js = EditorKeySet.keysFor(LanguageType.JAVASCRIPT)
        val backtick = js.first { it.label == "``" }
        assertEquals(EditorKey.Pair("`", "`"), backtick.key)
    }

    @Test
    fun `custom snippets parse label equals text lines`() {
        val keys = EditorKeySet.parseCustomSnippets("# a comment\nhi=hello world\nnoequals line\n\nlog=x\n")
        assertEquals(2, keys.size)
        assertEquals("hi", keys[0].label)
        assertEquals("hello world", (keys[0].key as EditorKey.Insert).text)
        assertEquals("log", keys[1].label)
        assertEquals("x", (keys[1].key as EditorKey.Insert).text)
    }

    @Test
    fun `custom snippets append after the language set`() {
        val keys = EditorKeySet.keysFor(LanguageType.C, "hi=hi\n")
        assertEquals("hi", keys.last().label)
        assertEquals("->", keys[keys.size - 2].label)
    }

    // ---- FontSizeZoom -------------------------------------------------------

    @Test
    fun `zoom snaps to half points and clamps to the readable range`() {
        assertEquals(14.5f, FontSizeZoom.applyZoom(14f, 1.05f), 0.0001f)
        assertEquals(8f, FontSizeZoom.applyZoom(8f, 0.5f), 0.0001f)
        assertEquals(30f, FontSizeZoom.applyZoom(30f, 2f), 0.0001f)
        assertEquals(14f, FontSizeZoom.applyZoom(14f, Float.NaN), 0.0001f)
        assertEquals(14f, FontSizeZoom.applyZoom(14f, -1f), 0.0001f)
    }

    // ---- EditorShellUi (tab bar + status bar models) ------------------------

    @Test
    fun `tab model derives the dirty dot from the buffers`() {
        val clean = EditorShellUi.tabModel("src/main.c", "abc", "abc")
        assertFalse(clean.isDirty)
        assertEquals("main.c", clean.name)
        assertEquals("src/main.c", clean.path)
        val dirty = EditorShellUi.tabModel("src/main.c", "abc\n", "abc")
        assertTrue(dirty.isDirty)
    }

    @Test
    fun `status bar counts severities and picks the first error`() {
        val diagnostics = listOf(
            EditorDiagnostic(5, 1, "warn", DiagnosticSeverity.WARNING),
            EditorDiagnostic(9, 3, "second", DiagnosticSeverity.ERROR),
            EditorDiagnostic(3, 2, "first", DiagnosticSeverity.ERROR)
        )
        assertEquals(2, EditorShellUi.errorCount(diagnostics))
        assertEquals(1, EditorShellUi.warningCount(diagnostics))
        assertEquals(3, EditorShellUi.firstError(diagnostics)?.line)
        assertEquals(2, EditorShellUi.firstError(diagnostics)?.column)
        assertNull(EditorShellUi.firstError(listOf(diagnostics[0])))
        assertNull(EditorShellUi.firstError(emptyList()))
    }
}
