package com.codeci.ide

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.editor.EditorLineOps
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 24.3 — pure hardware-shortcut text operations. */
class EditorLineOpsTest {

    @Test
    fun `commentPrefixFor maps languages`() {
        assertEquals("#", EditorLineOps.commentPrefixFor(LanguageType.PYTHON))
        assertEquals("#", EditorLineOps.commentPrefixFor(LanguageType.SHELL))
        assertEquals("//", EditorLineOps.commentPrefixFor(LanguageType.C))
        assertEquals("//", EditorLineOps.commentPrefixFor(LanguageType.JAVASCRIPT))
        assertEquals("//", EditorLineOps.commentPrefixFor(LanguageType.CPP))
        assertEquals("//", EditorLineOps.commentPrefixFor(LanguageType.JSON))
        assertEquals("<!--", EditorLineOps.commentPrefixFor(LanguageType.HTML_CSS))
        assertEquals("//", EditorLineOps.commentPrefixFor(null))
    }

    @Test
    fun `toggle adds slash comment to single line`() {
        val before = TextFieldValue("int main() {\n  return 0;\n}", TextRange(10))
        val after = EditorLineOps.toggleLineComment(before, "//")
        assertEquals("//int main() {\n  return 0;\n}", after?.text)
    }

    @Test
    fun `toggle comments every line a multi-line selection touches`() {
        val before = TextFieldValue("int main() {\n  return 0;\n}", TextRange(0, 29))
        val after = EditorLineOps.toggleLineComment(before, "//")
        assertEquals("//int main() {\n//  return 0;\n//}", after?.text)
    }

    @Test
    fun `toggle adds shell comment to selected lines`() {
        val before = TextFieldValue("echo hi\necho bye", TextRange(0, 8))
        val after = EditorLineOps.toggleLineComment(before, "#")
        assertEquals("#echo hi\n#echo bye", after?.text)
    }

    @Test
    fun `toggle removes existing slash prefix`() {
        val before = TextFieldValue("//int main() {\n//  return 0;\n//}", TextRange(5))
        val after = EditorLineOps.toggleLineComment(before, "//")
        assertEquals("int main() {\n//  return 0;\n//}", after?.text)
    }

    @Test
    fun `toggle leaves blank lines alone`() {
        val before = TextFieldValue("  a\n\n  b", TextRange(0, 8))
        val after = EditorLineOps.toggleLineComment(before, "//")
        assertEquals("//  a\n\n//  b", after?.text)
    }

    @Test
    fun `toggle on empty buffer returns null`() {
        assertNull(EditorLineOps.toggleLineComment(TextFieldValue("", TextRange(0)), "//"))
    }

    @Test
    fun `duplicate duplicates a single line and moves caret`() {
        val before = TextFieldValue("one\ntwo", TextRange(0))
        val after = EditorLineOps.duplicateLine(before)
        assertEquals("one\none\ntwo", after.text)
        assertEquals(TextRange(4), after.selection)
    }

    @Test
    fun `duplicate preserves blank line`() {
        val before = TextFieldValue("\n", TextRange(0))
        val after = EditorLineOps.duplicateLine(before)
        assertEquals("\n\n", after.text)
    }

    @Test
    fun `duplicate leaves empty buffer unchanged`() {
        val before = TextFieldValue("", TextRange(0))
        assertEquals(before, EditorLineOps.duplicateLine(before))
    }
}
