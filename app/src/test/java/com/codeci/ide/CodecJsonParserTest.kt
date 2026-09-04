package com.codeci.ide

import com.codeci.ide.ui.projects.CodecJsonParser
import com.codeci.ide.ui.projects.CodecOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 24.9 — `.codec.json` project override parser. Pure Kotlin; no
 * Android imports.
 */
class CodecJsonParserTest {

    @Test
    fun `parses build and run into an override`() {
        val parsed = CodecJsonParser.parse(
            """{"build":"gcc main.c utils.c -o app -lm","run":"./app"}"""
        )
        assertEquals("gcc main.c utils.c -o app -lm", parsed?.build)
        assertEquals("./app", parsed?.run)
        assertNull(parsed?.formatter)
    }

    @Test
    fun `parses only run when build is absent`() {
        val parsed = CodecJsonParser.parse("""{"run":"python3 main.py"}""")
        assertNull(parsed?.build)
        assertEquals("python3 main.py", parsed?.run)
    }

    @Test
    fun `parses formatter field`() {
        val parsed = CodecJsonParser.parse("""{"formatter":"black -S ."}""")
        assertEquals("black -S .", parsed?.formatter)
    }

    @Test
    fun `empty object returns null`() {
        assertNull(CodecJsonParser.parse("{}"))
    }

    @Test
    fun `blank and malformed input return null`() {
        assertNull(CodecJsonParser.parse(""))
        assertNull(CodecJsonParser.parse("  \n"))
        assertNull(CodecJsonParser.parse("{not json"))
        assertNull(CodecJsonParser.parse("{\"build\":"))
        assertNull(CodecJsonParser.parse("[1,2]"))
    }

    @Test
    fun `toJson writes only present fields`() {
        val json = CodecJsonParser.toJson(CodecOverride("make", "./app", null))
        val parsed = CodecJsonParser.parse(json)
        assertEquals("make", parsed?.build)
        assertEquals("./app", parsed?.run)
    }

    @Test
    fun `toJson escapes quotes and backslashes`() {
        val json = CodecJsonParser.toJson(CodecOverride("printf \"hi\"", null, null))
        assertEquals("printf \"hi\"", CodecJsonParser.parse(json)?.build)
    }

    @Test
    fun `toJson of empty override writes a usable empty object`() {
        assertEquals("{}", CodecJsonParser.toJson(CodecOverride.EMPTY))
    }
}
