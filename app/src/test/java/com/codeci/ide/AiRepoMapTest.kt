package com.codeci.ide

import com.codeci.ide.ui.ai.AiRepoMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 80 (AI Level 4) — the whole-project map: what the model is told the
 * project contains, and how much.
 *
 * These cases protect the owner's Phase 79 request (*"have full knowledge of my
 * code"*) without letting the map become an unbounded request: every file when
 * it fits, a truthful elision sentence when it does not, definitions only from
 * lines that start a definition, and never a credential-shaped path.
 */
class AiRepoMapTest {

    private fun file(path: String, lines: Int, text: String? = null) =
        AiRepoMap.FileInfo(path, lines, text)

    // ---- symbols ----------------------------------------------------------

    @Test
    fun `python definitions are found and a call is not one`() {
        val text = """
            import os
            def main():
                area(3)
            class Shape:
                pass
        """.trimIndent()
        assertEquals(listOf("main", "Shape"), AiRepoMap.symbolsFor("app.py", text))
    }

    @Test
    fun `c definitions are found including a bare brace style`() {
        val text = """
            #include <stdio.h>
            int area(int r) {
                return 3 * r * r;
            }
            int perimeter(int r)
            {
                return 6 * r;
            }
        """.trimIndent()
        assertEquals(listOf("area", "perimeter"), AiRepoMap.symbolsFor("main.c", text))
    }

    @Test
    fun `kotlin definitions cover fun class and object`() {
        val text = """
            package x
            private fun helper(): Int = 1
            class Widget
            object Registry
        """.trimIndent()
        assertEquals(listOf("helper", "Widget", "Registry"), AiRepoMap.symbolsFor("Widget.kt", text))
    }

    @Test
    fun `javascript go rust and shell definitions are recognised`() {
        assertEquals(
            listOf("start", "handler"),
            AiRepoMap.symbolsFor("app.js", "function start() {}\nconst handler = (x) => x")
        )
        assertEquals(
            listOf("Main", "Config"),
            AiRepoMap.symbolsFor("main.go", "func Main() {}\ntype Config struct {")
        )
        assertEquals(
            listOf("run", "State"),
            AiRepoMap.symbolsFor("lib.rs", "pub fn run() {}\nstruct State {")
        )
        assertEquals(
            listOf("build"),
            AiRepoMap.symbolsFor("build.sh", "#!/bin/sh\nbuild() {\n  echo hi\n}")
        )
    }

    @Test
    fun `config markup and prose get no definitions`() {
        assertTrue(AiRepoMap.symbolsFor("package.json", "{\"name\":\"x\"}").isEmpty())
        assertTrue(AiRepoMap.symbolsFor("README.md", "# Title\ntext").isEmpty())
        assertTrue(AiRepoMap.symbolsFor("index.html", "<html></html>").isEmpty())
        assertTrue(AiRepoMap.symbolsFor("noextension", "anything").isEmpty())
    }

    @Test
    fun `definitions are capped per file and never repeated`() {
        val text = (1..20).joinToString("\n") { "def f$it():" }
        assertEquals(AiRepoMap.MAX_SYMBOLS_PER_FILE, AiRepoMap.symbolsFor("a.py", text).size)
        assertEquals(listOf("one"), AiRepoMap.symbolsFor("a.py", "def one():\ndef one():"))
    }

    // ---- the map ----------------------------------------------------------

    @Test
    fun `every admitted file is listed when it fits`() {
        val map = AiRepoMap.build(
            listOf(
                file("src/main.c", 84, "int main(void) { return 0; }"),
                file("README.md", 12, "# demo"),
                file("Makefile", 9, "all:\n\tcc main.c")
            )
        )
        assertTrue(map.text.contains("PROJECT MAP — 3 code/text files"))
        assertTrue(map.text.contains("src/main.c (84 lines)"))
        assertTrue(map.text.contains("README.md (12 lines)"))
        assertEquals(3, map.filesListed)
        assertEquals(3, map.filesTotal)
        assertFalse(map.elided)
        assertTrue(map.summaryLine().contains("all 3 files"))
    }

    @Test
    fun `directories are grouped and the order is deterministic`() {
        val files = listOf(
            file("src/b.py", 3, "def b():"),
            file("src/a.py", 2, "def a():"),
            file("z.py", 1, "def z():")
        )
        val first = AiRepoMap.build(files).text
        val second = AiRepoMap.build(files.reversed()).text
        assertEquals(first, second)
        assertTrue(first.contains("./\n  z.py (1 lines)"))
        assertTrue(first.contains("src/\n  src/a.py (2 lines)"))
        assertTrue(first.indexOf("src/a.py") < first.indexOf("src/b.py"))
    }

    @Test
    fun `definitions ride along when the budget allows`() {
        val map = AiRepoMap.build(listOf(file("main.c", 10, "int main(void) {\n  return 0;\n}")))
        assertTrue(map.text.contains("    main"))
        assertEquals(1, map.symbols)
        assertTrue(map.summaryLine().contains("1 definitions"))
    }

    @Test
    fun `a tight budget elides files and says so honestly`() {
        val files = (1..40).map { file("src/very_long_file_name_number_$it.py", 10, "def f$it():") }
        val map = AiRepoMap.build(files, budget = 600)
        assertTrue(map.elided)
        assertTrue(map.filesListed < map.filesTotal)
        assertTrue(map.text.contains("more files not listed (map budget)"))
        assertTrue(map.summaryLine().contains("of 40 files"))
        assertTrue(map.text.length <= 600)
    }

    @Test
    fun `a secret shaped or excluded path handed in by mistake is dropped`() {
        val map = AiRepoMap.build(
            listOf(
                file("src/main.c", 3, null),
                file(".env", 2, "API_KEY=1"),
                file("build/out.c", 2, null),
                file(".git/config", 2, null)
            )
        )
        assertEquals(listOf("src/main.c"), map.text.lines().filter { it.trim().endsWith("(3 lines)") }
            .map { it.trim().substringBefore(" (") })
        assertFalse(map.text.contains(".env"))
        assertFalse(map.text.contains("build/out.c"))
        assertFalse(map.text.contains(".git"))
        assertEquals(1, map.filesTotal)
    }

    @Test
    fun `an empty project maps to nothing rather than a header alone`() {
        val map = AiRepoMap.build(emptyList())
        assertEquals("", map.text)
        assertEquals(0, map.filesTotal)
        assertFalse(map.elided)
    }
}
