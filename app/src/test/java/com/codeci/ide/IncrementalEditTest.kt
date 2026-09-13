package com.codeci.ide

import com.codeci.ide.ui.editor.IncrementalEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 48 device round — the CodeC Keys blink fix (PART_48_1 §Device round).
 * The planner is pure, so these walk the real delta surface: every shape a
 * keystroke / snippet / ghost accept / backspace can produce, the shapes that
 * must fall back to the atomic path, and the plan's exactness (the replaced
 * region is minimal and never overlaps the shared prefix/suffix).
 */
class IncrementalEditTest {

    private fun planOf(old: String, new: String) = IncrementalEdit.between(old, new)

    @Test
    fun `identical text plans nothing`() {
        assertNull(planOf("int main() {}", "int main() {}"))
        assertNull(planOf("", ""))
    }

    @Test
    fun `a keystroke at the end is a one-char insert`() {
        val plan = planOf("int", "int ")
        assertNotNull(plan)
        assertEquals(3, plan!!.start)
        assertEquals(3, plan.end)
        assertEquals(" ", plan.replacement)
    }

    @Test
    fun `a keystroke in the middle inserts at the caret`() {
        val plan = planOf("it main", "int main")
        assertNotNull(plan)
        assertEquals(1, plan!!.start)
        assertEquals(1, plan.end)
        assertEquals("n", plan.replacement)
    }

    @Test
    fun `backspace is a one-char delete`() {
        val plan = planOf("int ", "int")
        assertNotNull(plan)
        assertEquals(3, plan!!.start)
        assertEquals(4, plan.end)
        assertEquals("", plan.replacement)
    }

    @Test
    fun `a typed character over a selection is a one-char replace`() {
        val plan = planOf("int x = 1;", "int x = 2;")
        assertNotNull(plan)
        assertEquals(8, plan!!.start)
        assertEquals(9, plan.end)
        assertEquals("2", plan.replacement)
    }

    @Test
    fun `auto indent is a newline plus spaces insert`() {
        val plan = planOf("if (x) {", "if (x) {\n    ")
        assertNotNull(plan)
        assertEquals(8, plan!!.start)
        assertEquals(8, plan.end)
        assertEquals("\n    ", plan.replacement)
    }

    @Test
    fun `a newline delete is one delta`() {
        val plan = planOf("a\nb", "ab")
        assertNotNull(plan)
        assertEquals(1, plan!!.start)
        assertEquals(2, plan.end)
        assertEquals("", plan.replacement)
    }

    @Test
    fun `a snippet insert at the caret is one delta`() {
        val old = "int main() {\n    "
        val new = old + "printf(\"hi\");\n"
        val plan = planOf(old, new)
        assertNotNull(plan)
        assertEquals(old.length, plan!!.start)
        assertEquals(old.length, plan.end)
        assertEquals("printf(\"hi\");\n", plan.replacement)
    }

    @Test
    fun `a tiny edit in a big file stays incremental - the blink case`() {
        // The owner's exact scene: 5000-char file, one char typed at the end.
        val old = buildString { repeat(500) { append("int line$it = $it;\n") } }
        val new = old + " "
        val plan = planOf(old, new)
        assertNotNull(plan)
        assertEquals(old.length, plan!!.start)
        assertEquals(1, plan.replacement.length)
    }

    @Test
    fun `a formatter-sized rewrite falls back to the atomic path`() {
        val old = buildString { repeat(300) { append("int line$it = $it;\n") } }
        val new = buildString { repeat(300) { append("int  line$it  =  $it ;\n") } }
        assertNull(planOf(old, new))
    }

    @Test
    fun `emptying the editor is a delete of everything small enough`() {
        val plan = planOf("small", "")
        assertNotNull(plan)
        assertEquals(0, plan!!.start)
        assertEquals(5, plan.end)
        assertEquals("", plan.replacement)
    }

    @Test
    fun `filling an empty editor is one insert within budget`() {
        val plan = planOf("", "hello")
        assertNotNull(plan)
        assertEquals(0, plan!!.start)
        assertEquals(0, plan.end)
        assertEquals("hello", plan.replacement)
    }

    @Test
    fun `the plan region is exact - replay of the delta reproduces the new text`() {
        // The host applies old[0 until start] + replacement + old[end..]:
        // assert that round-trip for every fixture here.
        val fixtures = listOf(
            "int" to "int ",
            "it main" to "int main",
            "int " to "int",
            "int x = 1;" to "int x = 2;",
            "if (x) {" to "if (x) {\n    ",
            "a\nb" to "ab",
            "int main() {\n    " to "int main() {\n    printf(\"hi\");\n"
        )
        for ((old, new) in fixtures) {
            val plan = planOf(old, new) ?: error("no plan for $old -> $new")
            val rebuilt = old.substring(0, plan.start) + plan.replacement + old.substring(plan.end)
            assertEquals(new, rebuilt)
        }
    }

    @Test
    fun `prefix and suffix never overlap`() {
        // "aa" -> "a": prefix eats the first a, suffix bound stops overlap.
        val plan = planOf("aaaa", "aa")
        assertNotNull(plan)
        assertEquals(2, plan!!.start)
        assertEquals(4, plan.end)
    }

    @Test
    fun `the budget is the documented constant`() {
        assertEquals(2048, IncrementalEdit.MAX_AFFECTED_CHARS)
    }
}
