package com.codeci.ide

import com.codeci.ide.ui.editor.OutputLineParser
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
}
