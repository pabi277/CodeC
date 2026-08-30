package com.codeci.ide

import com.codeci.ide.ui.editor.CompilerDiagnostics
import com.codeci.ide.ui.editor.DiagnosticSeverity
import com.codeci.ide.ui.services.CompilerError
import com.codeci.ide.ui.services.ErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 9 — compiler diagnostic parsing for editor squiggles (§2.5). */
class CompilerDiagnosticsTest {

    @Test
    fun `parses clang style diagnostics for the open file`() {
        val out = CompilerDiagnostics.parse(
            "main.c:3:5: error: expected ';' before '}' token",
            targetFileName = "main.c"
        )
        assertEquals(1, out.size)
        assertEquals(3, out[0].line)
        assertEquals(5, out[0].column)
        assertEquals(DiagnosticSeverity.ERROR, out[0].severity)
        assertEquals("expected ';' before '}' token", out[0].message)
    }

    @Test
    fun `absolute paths are matched by basename`() {
        val out = CompilerDiagnostics.parse(
            "/data/data/com.codeci.ide/files/usr/main.c:7:1: warning: unused variable 'x'",
            targetFileName = "src/main.c"
        )
        assertEquals(1, out.size)
        assertEquals(DiagnosticSeverity.WARNING, out[0].severity)
    }

    @Test
    fun `lines from other files are filtered out`() {
        val out = CompilerDiagnostics.parse(
            "header.h:1:1: error: boom\nmain.c:2:2: error: ok",
            targetFileName = "main.c"
        )
        assertEquals(1, out.size)
        assertEquals(2, out[0].line)
    }

    @Test
    fun `parses tcc line-only diagnostics without a column`() {
        // Device evidence 2026-08-30: the embedded `cc` (TCC) prints
        // `file:line: error: message` — no column.
        val out = CompilerDiagnostics.parse(
            "/data/user/0/com.codeci.ide/files/CodeC/projects/main.c:6: error: ';' expected (got \"}\")",
            targetFileName = "main.c"
        )
        assertEquals(1, out.size)
        assertEquals(6, out[0].line)
        assertEquals(1, out[0].column)
        assertEquals(DiagnosticSeverity.ERROR, out[0].severity)
        assertEquals("';' expected (got \"}\")", out[0].message)
    }

    @Test
    fun `fatal error counts as an error and note lines are ignored`() {
        val out = CompilerDiagnostics.parse(
            "main.c:1:1: fatal error: missing include\nmain.c:1:1: note: did you mean",
            targetFileName = "main.c"
        )
        assertEquals(1, out.size)
        assertEquals(DiagnosticSeverity.ERROR, out[0].severity)
    }

    @Test
    fun `one diagnostic per line keeps the error over a warning`() {
        val out = CompilerDiagnostics.parse(
            "main.c:4:1: warning: maybe\nmain.c:4:9: error: real problem",
            targetFileName = "main.c"
        )
        assertEquals(1, out.size)
        assertEquals(DiagnosticSeverity.ERROR, out[0].severity)
    }

    @Test
    fun `structured compiler errors are mapped and deduplicated with text parsing`() {
        val structured = listOf(CompilerError(line = 2, column = 3, message = "undeclared 'y'", type = ErrorType.ERROR))
        val out = CompilerDiagnostics.combine(
            errors = structured,
            output = "main.c:2:3: error: undeclared 'y'\nmain.c:9:2: warning: unused",
            targetFileName = "main.c"
        )
        assertEquals(2, out.size)
        assertEquals(2, out[0].line)
        assertEquals("undeclared 'y'", out[0].message)
        assertEquals(9, out[1].line)
        assertEquals(DiagnosticSeverity.WARNING, out[1].severity)
    }

    @Test
    fun `errors without line numbers are dropped`() {
        val structured = listOf(CompilerError(line = 0, column = 0, message = "linker sad", type = ErrorType.ERROR))
        assertEquals(0, CompilerDiagnostics.fromCompilerErrors(structured).size)
    }

    @Test
    fun `semicolon quick fix is suggested only for the right message`() {
        val wants = CompilerDiagnostics.semicolonFixLabel(
            com.codeci.ide.ui.editor.EditorDiagnostic(1, 1, "expected ';' before '}' token", DiagnosticSeverity.ERROR)
        )
        assertEquals("Add missing ';'", wants)
        val unrelated = CompilerDiagnostics.semicolonFixLabel(
            com.codeci.ide.ui.editor.EditorDiagnostic(1, 1, "use of undeclared identifier 'z'", DiagnosticSeverity.ERROR)
        )
        assertNull(unrelated)
    }

    @Test
    fun `semicolon fix appends and refuses when already terminated`() {
        assertEquals("int x = 5;", CompilerDiagnostics.applySemicolonFix("int x = 5"))
        // Trailing whitespace is trimmed (the ';' lands after the code), leading
        // indentation is preserved so the splice keeps the original line's indent.
        assertEquals("  call(1);", CompilerDiagnostics.applySemicolonFix("  call(1)  "))
        // already terminated -> the fix refuses and leaves the line untouched
        assertNull(CompilerDiagnostics.applySemicolonFix("  call(1);  "))
        assertNull(CompilerDiagnostics.applySemicolonFix("if (x) {"))
        assertNull(CompilerDiagnostics.applySemicolonFix(""))
    }
}
