package com.codeci.ide

import com.codeci.ide.ui.projects.DiffEngine
import com.codeci.ide.ui.projects.DiffOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 13 — host tests for the Kotlin line-diff engine behind the diff viewer. */
class DiffEngineTest {

    @Test
    fun `identical texts are all context`() {
        val lines = DiffEngine.compute("a\nb\nc\n", "a\nb\nc\n")
        assertEquals(3, lines.size)
        assertTrue(lines.all { it.op == DiffOp.CONTEXT })
        assertEquals(listOf(1, 2, 3), lines.map { it.oldNumber })
        assertEquals(listOf(1, 2, 3), lines.map { it.newNumber })
    }

    @Test
    fun `added lines appear as ADD blocks`() {
        val lines = DiffEngine.compute("a\nc\n", "a\nb\nc\n")
        assertEquals(
            listOf(DiffOp.CONTEXT, DiffOp.ADD, DiffOp.CONTEXT),
            lines.map { it.op }
        )
        assertEquals("b", lines[1].text)
        assertEquals(2, lines[1].newNumber)
        assertEquals(null, lines[1].oldNumber)
    }

    @Test
    fun `removed lines appear as REMOVE blocks`() {
        val lines = DiffEngine.compute("a\nb\nc\n", "a\nc\n")
        assertEquals(
            listOf(DiffOp.CONTEXT, DiffOp.REMOVE, DiffOp.CONTEXT),
            lines.map { it.op }
        )
        assertEquals("b", lines[1].text)
        assertEquals(2, lines[1].oldNumber)
        assertEquals(null, lines[1].newNumber)
    }

    @Test
    fun `modification interleaves removes and adds`() {
        val lines = DiffEngine.compute("int main(void) {\n    printf(\"old\");\n}\n", "int main(void) {\n    printf(\"new\");\n}\n")
        assertEquals(
            listOf(DiffOp.CONTEXT, DiffOp.REMOVE, DiffOp.ADD, DiffOp.CONTEXT),
            lines.map { it.op }
        )
        assertEquals("    printf(\"old\");", lines[1].text)
        assertEquals("    printf(\"new\");", lines[2].text)
    }

    @Test
    fun `empty old side means everything is added`() {
        val lines = DiffEngine.compute("", "line1\nline2\n")
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.op == DiffOp.ADD })
    }

    @Test
    fun `empty new side means everything is removed`() {
        val lines = DiffEngine.compute("line1\n", "")
        assertEquals(1, lines.size)
        assertEquals(DiffOp.REMOVE, lines.single().op)
    }

    @Test
    fun `both empty yields nothing`() {
        assertTrue(DiffEngine.compute("", "").isEmpty())
    }

    @Test
    fun `trailing newline does not create a phantom line`() {
        val lines = DiffEngine.compute("x\n", "x\n")
        assertEquals(1, lines.size)
        assertEquals("x", lines.single().text)
    }

    @Test
    fun `oversized middle falls back to whole-block replace`() {
        val oldText = (1..2500).joinToString("\n") { "old $it" }
        val newText = (1..2500).joinToString("\n") { "new $it" }
        val lines = DiffEngine.compute(oldText, newText)
        // The common empty prefix/suffix plus the whole-block fallback: every
        // old line removed, then every new line added.
        assertEquals(5000, lines.size)
        assertEquals(2500, lines.count { it.op == DiffOp.REMOVE })
        assertEquals(2500, lines.count { it.op == DiffOp.ADD })
    }

    @Test
    fun `change in the middle keeps surrounding line numbers correct`() {
        val oldText = (1..10).joinToString("\n") { "line $it" }
        val newText = (1..10).joinToString("\n") { if (it == 5) "changed $it" else "line $it" }
        val lines = DiffEngine.compute(oldText, newText)
        val remove = lines.first { it.op == DiffOp.REMOVE }
        val add = lines.first { it.op == DiffOp.ADD }
        assertEquals(5, remove.oldNumber)
        assertEquals(5, add.newNumber)
        assertTrue(lines.none { it.op == DiffOp.CONTEXT && (it.oldNumber == 4 && it.newNumber != 4) })
    }
}
