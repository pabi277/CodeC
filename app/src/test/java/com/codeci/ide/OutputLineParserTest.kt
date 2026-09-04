package com.codeci.ide

import com.codeci.ide.ui.editor.OutputLineParser
import com.codeci.ide.ui.editor.TestLineKind
import com.codeci.ide.ui.editor.TestOutputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputLineParserTest {

    @Test
    fun `clang style line with column parses`() {
        val diag = OutputLineParser.parseLine("src/main.c:12:4: error: expected ';' before '}' token")
        assertEquals("src/main.c", diag?.file)
        assertEquals(12, diag?.line)
        assertEquals(4, diag?.column)
        assertEquals("error", diag?.kind)
        assertEquals("expected ';' before '}' token", diag?.message)
        assertTrue(diag!!.isError)
    }

    @Test
    fun `tcc line without column parses with column zero`() {
        val diag = OutputLineParser.parseLine("main.c:3: error: missing terminating \" character")
        assertEquals("main.c", diag?.file)
        assertEquals(3, diag?.line)
        assertEquals(0, diag?.column)
        assertEquals("error", diag?.kind)
        assertEquals("missing terminating \" character", diag?.message)
        assertTrue(diag!!.isError)
    }

    @Test
    fun `fatal error is an error`() {
        val diag = OutputLineParser.parseLine("main.c:1:1: fatal error: cannot open file: 'nope.h'")
        assertTrue(diag!!.isError)
        assertEquals("fatal error", diag.kind)
    }

    @Test
    fun `warning and note are not errors`() {
        val warning = OutputLineParser.parseLine("main.c:7:9: warning: implicit declaration of function 'foo'")
        assertFalse(warning!!.isError)
        assertEquals("warning", warning.kind)
        val note = OutputLineParser.parseLine("main.c:7:9: note: did you mean 'foos'?")
        assertFalse(note!!.isError)
        assertEquals("note", note.kind)
    }

    @Test
    fun `absolute path diagnostics parse`() {
        val diag = OutputLineParser.parseLine(
            "/data/data/com.codeci.ide/files/CodeC/projects/main.c:2:5: error: 'x' undeclared"
        )
        assertEquals("/data/data/com.codeci.ide/files/CodeC/projects/main.c", diag?.file)
        assertEquals(2, diag?.line)
        assertEquals(5, diag?.column)
    }

    @Test
    fun `non diagnostic lines return null`() {
        assertNull(OutputLineParser.parseLine("Hello CodeC!"))
        assertNull(OutputLineParser.parseLine("cc: not found: t.c"))
        assertNull(OutputLineParser.parseLine("Build OK (12ms)"))
        assertNull(OutputLineParser.parseLine(""))
        assertNull(OutputLineParser.parseLine("   "))
        assertNull(OutputLineParser.parseLine("1 warning generated."))
    }

    @Test
    fun `parse extracts every diagnostic line in order`() {
        val output = """
            $ cc main.c -o bin/app
            main.c:3: error: missing terminating " character
            main.c:5:9: warning: unused variable 'x'
            Build OK
        """.trimIndent()
        val diagnostics = OutputLineParser.parse(output)
        assertEquals(2, diagnostics.size)
        assertEquals(3, diagnostics[0].line)
        assertEquals(5, diagnostics[1].line)
        assertEquals("warning", diagnostics[1].kind)
    }

    @Test
    fun `line zero or negative is rejected`() {
        assertNull(OutputLineParser.parseLine("main.c:0: error: weird"))
        assertNull(OutputLineParser.parseLine("main.c:-2: error: weird"))
    }

    @Test
    fun `device tcc missing semicolon line parses and is fixable`() {
        // Device evidence 2026-08-30 (owner transcript): the exact line the
        // Output Panel showed for a missing-';' build failure.
        val diag = OutputLineParser.parseLine(
            "/data/user/0/com.codeci.ide/files/CodeC/projects/main.c:6: error: ';' expected (got \"}\")"
        )
        assertEquals("/data/user/0/com.codeci.ide/files/CodeC/projects/main.c", diag?.file)
        assertEquals(6, diag?.line)
        assertEquals(0, diag?.column)
        assertTrue(diag!!.isError)
        assertEquals("';' expected (got \"}\")", diag.message)
        // The one-tap "Add missing ;" action must be offered for it.
        assertEquals(
            "Add missing ';'",
            com.codeci.ide.ui.editor.CompilerDiagnostics.semicolonFixLabel(diag)
        )
    }

    // ---- Phase 24.6: test-runner line classification ----------------------

    @Test
    fun `pytest PASSED and pytest 1 passed are passes`() {
        assertEquals(TestLineKind.PASS, TestOutputParser.parseLine("tests/test_hello.py::test_ok PASSED").kind)
        assertEquals(TestLineKind.PASS, TestOutputParser.parseLine("============================= 1 passed in 0.2s ============================").kind)
        assertEquals(TestLineKind.PASS, TestOutputParser.parseLine("ok 1 - test_add").kind)
    }

    @Test
    fun `pytest FAILED is a fail`() {
        assertEquals(TestLineKind.FAIL, TestOutputParser.parseLine("tests/test_hello.py::test_bad FAILED").kind)
    }

    @Test
    fun `go ok summary with a failure is a fail`() {
        assertEquals(TestLineKind.FAIL, TestOutputParser.parseLine("FAIL	example/math	0.123s").kind)
        assertEquals(TestLineKind.FAIL, TestOutputParser.parseLine("ok 1 failed").kind)
        assertEquals(TestLineKind.FAIL, TestOutputParser.parseLine("--- FAIL: TestBad (0.00s)").kind)
    }

    @Test
    fun `error lines are errors`() {
        assertEquals(TestLineKind.ERROR, TestOutputParser.parseLine("ImportError: No module named pytest").kind)
    }

    @Test
    fun `ordinary runner chatter and separators stay summary`() {
        // "----" separators / "====" are metadata, not a pass/fail line.
        assertEquals(TestLineKind.OK, TestOutputParser.parseLine("===============================================================").kind)
        assertEquals(TestLineKind.PLAIN, TestOutputParser.parseLine("collecting ... collected 2 items").kind)
    }
}
