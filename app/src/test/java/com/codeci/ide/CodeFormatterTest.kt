package com.codeci.ide

import com.codeci.ide.ui.editor.CodeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 9 — built-in indentation formatter (`docs/chat-phase9/PART_9_EDITOR.md` §2.3 fallback). */
class CodeFormatterTest {

    @Test
    fun `formats flat braces with the configured indent`() {
        val source = "int main() {\nreturn 0;\n}"
        val expected = "int main() {\n    return 0;\n}"
        assertEquals(expected, CodeFormatter.format(source, 4))
    }

    @Test
    fun `two-space indent style`() {
        val source = "void f() {\nif (x) {\ny();\n}\n}"
        val expected = "void f() {\n  if (x) {\n    y();\n  }\n}"
        assertEquals(expected, CodeFormatter.format(source, 2))
    }

    @Test
    fun `line count is always preserved`() {
        val source = "a {\n\n\n  }\nb"
        val formatted = CodeFormatter.format(source, 4)
        assertEquals(source.count { it == '\n' }, formatted.count { it == '\n' })
    }

    @Test
    fun `braces inside string literals do not change depth`() {
        val source = "int main() {\nprintf(\"}{\");\nreturn 0;\n}"
        val expected = "int main() {\n    printf(\"}{\");\n    return 0;\n}"
        assertEquals(expected, CodeFormatter.format(source, 4))
    }

    @Test
    fun `comment content is left byte-identical inside block comments`() {
        val source = "/*\n   *  keep   this   spacing\n*/\nint x;"
        val formatted = CodeFormatter.format(source, 4)
        assertEquals(source, formatted)
    }

    @Test
    fun `preprocessor lines are forced to column zero`() {
        val source = "#include <stdio.h>\nint main() {\n    return 0;\n}"
        val expected = "#include <stdio.h>\nint main() {\n    return 0;\n}"
        assertEquals(expected, CodeFormatter.format(source, 4))
    }

    @Test
    fun `case labels indent one level less than their body`() {
        val source = "switch (x) {\ncase 1:\nf();\nbreak;\n}"
        val expected = "switch (x) {\n    case 1:\n        f();\n        break;\n}"
        assertEquals(expected, CodeFormatter.format(source, 4))
    }

    @Test
    fun `closing brace dedents`() {
        val source = "void f() {\n    g();\n        h();\n}"
        val expected = "void f() {\n    g();\n    h();\n}"
        assertEquals(expected, CodeFormatter.format(source, 4))
    }

    @Test
    fun `oversized files are returned unchanged`() {
        val source = (1..(CodeFormatter.MAX_LINES + 10)).joinToString("\n") { "line$it" }
        assertEquals(source, CodeFormatter.format(source, 4))
    }

    @Test
    fun `map cursor clamps into the reindented line`() {
        val before = "x\n  y"
        val after = "x\ny"
        val cursor = before.indexOf('y')
        assertEquals(3, CodeFormatter.mapCursor(before, after, cursor))
        assertEquals(0, CodeFormatter.mapCursor(before, after, 0))
        // End-of-text caret clamps to the end of the shorter reindented line.
        assertEquals(3, CodeFormatter.mapCursor(before, after, before.length))
    }

    @Test
    fun `line bounds exclude the line break`() {
        val text = "ab\ncd"
        assertEquals(0..1, CodeFormatter.lineBounds(text, 1))
        assertEquals(3..4, CodeFormatter.lineBounds(text, 2))
        assertNull(CodeFormatter.lineBounds(text, 3))
        assertNull(CodeFormatter.lineBounds(text, 0))
    }

    @Test
    fun `empty buffer formats to empty`() {
        assertEquals("", CodeFormatter.format("", 4))
    }

    @Test
    fun `depth never goes negative on stray closers`() {
        val source = "}\nx();"
        assertEquals("}\nx();", CodeFormatter.format(source, 4))
    }
}
