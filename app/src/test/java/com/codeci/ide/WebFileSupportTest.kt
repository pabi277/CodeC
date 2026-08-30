package com.codeci.ide

import com.codeci.ide.ui.utils.WebFileSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebFileSupportTest {

    @Test
    fun `isHtml recognizes html and htm only`() {
        assertTrue(WebFileSupport.isHtml("index.html"))
        assertTrue(WebFileSupport.isHtml("PAGE.HTML"))
        assertTrue(WebFileSupport.isHtml("page.htm"))
        assertFalse(WebFileSupport.isHtml("style.css"))
        assertFalse(WebFileSupport.isHtml("app.js"))
        assertFalse(WebFileSupport.isHtml("main.c"))
    }

    @Test
    fun `isWeb recognizes all four web extensions`() {
        listOf("a.html", "a.htm", "a.css", "a.js").forEach {
            assertTrue("expected web: $it", WebFileSupport.isWeb(it))
        }
        listOf("a.c", "a.txt", "a", "a.html.bak").forEach {
            assertFalse("expected not web: $it", WebFileSupport.isWeb(it))
        }
    }

    @Test
    fun `normalizeFileName keeps source and web extensions and defaults to c`() {
        // C behavior unchanged: a bare name still becomes a .c file.
        assertEquals("foo.c", WebFileSupport.normalizeFileName("foo"))
        assertEquals("main.c", WebFileSupport.normalizeFileName("main.c"))
        // Web names keep their extension.
        assertEquals("index.html", WebFileSupport.normalizeFileName("index.html"))
        assertEquals("style.css", WebFileSupport.normalizeFileName("style.css"))
        assertEquals("script.js", WebFileSupport.normalizeFileName("script.js"))
        assertEquals("page.htm", WebFileSupport.normalizeFileName("page.htm"))
    }

    @Test
    fun `normalizeFileName keeps phase 12 language extensions`() {
        // Phase 12: a .py name must survive save/rename unchanged — it used
        // to become test.py.c, reclassifying the file as C (RUN ▶ then
        // compiled it with cc instead of python3).
        assertEquals("test.py", WebFileSupport.normalizeFileName("test.py"))
        assertEquals("server.pyw", WebFileSupport.normalizeFileName("server.pyw"))
        assertEquals("script.sh", WebFileSupport.normalizeFileName("script.sh"))
        assertEquals("data.json", WebFileSupport.normalizeFileName("data.json"))
        assertEquals("README.md", WebFileSupport.normalizeFileName("README.md"))
        assertEquals("main.cpp", WebFileSupport.normalizeFileName("main.cpp"))
        assertEquals("style.scss", WebFileSupport.normalizeFileName("style.scss"))
        // Plain-text extensions are not editor languages: they keep the
        // legacy .c-append behavior (unchanged).
        assertEquals("notes.txt.c", WebFileSupport.normalizeFileName("notes.txt"))
        // Bare names still default to .c (C flow unchanged).
        assertEquals("untitled.c", WebFileSupport.normalizeFileName("untitled"))
        // Multi-dot names with an unknown final extension still get .c.
        assertEquals("a.html.bak.c", WebFileSupport.normalizeFileName("a.html.bak"))
    }

    @Test
    fun `starterContent returns the right template per file type`() {
        assertTrue(WebFileSupport.starterContent("main.c").contains("int main()"))
        assertTrue(WebFileSupport.starterContent("index.html").contains("<!doctype html>"))
        assertTrue(WebFileSupport.starterContent("style.css").contains("background"))
        assertTrue(WebFileSupport.starterContent("script.js").contains("console.log"))
        assertTrue(WebFileSupport.starterContent("test.py").contains("def main()"))
        assertTrue(WebFileSupport.starterContent("tool.pyw").contains("if __name__"))
        // A bare name (no recognized extension) defaults to the C template.
        assertTrue(WebFileSupport.starterContent("whatever").contains("int main()"))
    }

    @Test
    fun `looksLikePython detects python but never C`() {
        assertTrue(WebFileSupport.looksLikePython("def hello():\n    print(1)"))
        assertTrue(WebFileSupport.looksLikePython("import math\nprint(math.pi)"))
        assertTrue(WebFileSupport.looksLikePython("#!/data/data/com.codeci.ide/files/usr/bin/python3\nprint('hi')"))
        assertTrue(WebFileSupport.looksLikePython("class Greeter:\n    pass"))
        assertFalse(WebFileSupport.looksLikePython(""))
        assertFalse(WebFileSupport.looksLikePython("   "))
        // The C starter and real C code are never Python.
        assertFalse(WebFileSupport.looksLikePython("#include <stdio.h>\n\nint main() {\n    return 0;\n}\n"))
        assertFalse(
            WebFileSupport.looksLikePython(
                "#include <stdio.h>\nint main() {\n    printf(\"x\");\n    if (1) { return 0; }\n}\n"
            )
        )
    }
}
