package com.codeci.ide

import com.codeci.ide.ui.ai.AiOutline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 94 — `outline_file`: the map of a file without the body.
 *
 * The point of the tool is the opposite of `read_file`: it always answers, it
 * answers in a shape a model can act on (line numbers it can then read around),
 * and the answer is small enough that the request ceiling is never the reason a
 * question fails.
 */
class AiOutlineTest {

    @Test
    fun `a C file yields functions, types and its include guard is not a definition`() {
        val text = """
            #include <stdio.h>
            #define MAX 10

            struct Point { int x; int y; };

            static int clamp(int v, int lo, int hi) {
                if (v < lo) return lo;
                return v > hi ? hi : v;
            }

            int main(void) {
                printf("%d\n", clamp(5, 0, 10));
                return 0;
            }
        """.trimIndent()
        val rows = AiOutline.outline("src/main.c", text)
        val lines = rows.map { it.text }
        assertTrue("the struct is a definition", lines.any { it.contains("struct Point") })
        assertTrue(lines.any { it.contains("static int clamp") })
        assertTrue(lines.any { it.contains("int main(void)") })
        assertTrue("the preprocessor lines are not definitions", lines.none { it.contains("#include") })
        assertTrue("nor is the #define", lines.none { it.contains("#define") })
        assertTrue("nor the local if/return bodies", lines.none { it.trimStart().startsWith("if (") })
        assertTrue("rows carry a real line number", rows.all { it.line >= 1 && it.line <= text.lines().size })
    }

    @Test
    fun `a python file yields top-level and nested definitions with indentation`() {
        val text = """
            import os

            class Client:
                def __init__(self, base):
                    self.base = base

                def get(self, path):
                    return request(self.base + path)

            def main():
                Client("http://x").get("/")
        """.trimIndent()
        val rows = AiOutline.outline("app/client.py", text)
        val by = rows.associate { it.text.trim() to it.text }
        assertEquals(4, rows.size)
        assertTrue("class at column 0", by.keys.any { it.startsWith("class Client") })
        val initRow = rows.first { it.text.contains("def __init__") }.text
        assertTrue("a method is indented under its class: '$initRow'", initRow.startsWith("  def __init__"))
        val getRow = rows.first { it.text.contains("def get(") }.text
        assertTrue("and so is its sibling: '$getRow'", getRow.startsWith("  def get("))
        assertTrue(rows.first { it.text.contains("def main") }.text.startsWith("def main"))
    }

    @Test
    fun `a shell script yields its functions and skips comments`() {
        val text = """
            #!/usr/bin/env bash
            # helper script

            build() {
                echo build
            }

            # run tests
            test_all() {
                build
            }
        """.trimIndent()
        val lines = AiOutline.outline("tools/build.sh", text).map { it.text }
        assertEquals(2, lines.size)
        assertTrue(lines.any { it.contains("build()") })
        assertTrue(lines.any { it.contains("test_all()") })
        assertTrue(lines.none { it.contains("#") })
    }

    @Test
    fun `a markdown file yields its heading tree`() {
        val text = """
            # Title

            Some prose.

            ## Setup

            ```
            # not a heading, it is inside a fence
            ```

            ## Usage
        """.trimIndent()
        val lines = AiOutline.outline("README.md", text).map { it.text }
        assertTrue(lines.any { it == "# Title" })
        assertTrue(lines.any { it == "## Setup" })
        assertTrue(lines.any { it == "## Usage" })
        assertTrue("a fence comment is not a heading", lines.none { it.contains("not a heading") })
    }

    @Test
    fun `a JVM file yields methods but not control flow`() {
        val text = """
            public class Engine {
                private int count;

                public void start() {
                    if (count > 0) {
                        stop();
                    }
                    for (int i = 0; i < 3; i++) {
                        tick();
                    }
                }

                private static Engine create() {
                    return new Engine();
                }
            }
        """.trimIndent()
        val lines = AiOutline.outline("Engine.java", text).map { it.text }
        assertTrue(lines.any { it.contains("class Engine") })
        assertTrue(lines.any { it.contains("void start()") })
        assertTrue(lines.any { it.contains("Engine create()") })
        assertTrue("if is not a definition", lines.none { it.trimStart().startsWith("if (") })
        assertTrue("nor is for", lines.none { it.trimStart().startsWith("for (") })
    }

    @Test
    fun `a file with no definitions says so instead of failing`() {
        val rows = AiOutline.outline("data.txt", "just\nsome\nlines\n")
        assertTrue("an empty outline is a legal, honest answer", rows.isEmpty())
    }

    @Test
    fun `the row cap is honoured and the caller is told the real line numbers`() {
        val big = (1..300).joinToString("\n") { "void f$it(void) { }" }
        val rows = AiOutline.outline("big.c", big, maxRows = 12)
        assertEquals(12, rows.size)
        assertEquals("first row is the first definition", 1, rows.first().line)
        assertEquals("the cap is a prefix, not a sample", 12, rows.last().line)
    }
}
