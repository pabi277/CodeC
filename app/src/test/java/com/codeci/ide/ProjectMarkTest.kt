package com.codeci.ide

import com.codeci.ide.ui.projects.MarkSeat
import com.codeci.ide.ui.projects.ProjectMarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 59.2 — the name-derived project mark: initials, seat, and the two
 * promises the spec's *distinct logo per project* really makes.
 *
 * The promises, stated as the cases below check them:
 *
 *  1. **The same project always wears the same mark.** No time, no device, no
 *     build flavour is an input — the seat comes from a hash written out in
 *     the policy (FNV-1a), not from `String.hashCode()`, so the mark a user
 *     sees today is the mark they see after an update.
 *  2. **Different projects are told apart.** Initials separate most names;
 *     where two names share initials the seat has to do the work, which is
 *     why the seat is a hash of the *whole* name rather than of the initials.
 *     (With five seats this cannot be a guarantee for every possible pair —
 *     the case below uses real project names, and the honest limit is
 *     recorded in PART_59_2.)
 */
class ProjectMarkTest {

    // ---- initials ----------------------------------------------------------

    @Test
    fun `two words give their two first letters`() {
        assertEquals("TA", ProjectMarks.initials("todo-app"))
        assertEquals("TA", ProjectMarks.initials("todo app"))
        assertEquals("TA", ProjectMarks.initials("todo_app"))
        assertEquals("MW", ProjectMarks.initials("my.web.site"))
        assertEquals("AB", ProjectMarks.initials("  a  b  "))
    }

    @Test
    fun `one word gives its first two characters`() {
        assertEquals("SN", ProjectMarks.initials("snake"))
        assertEquals("CO", ProjectMarks.initials("CodeC"))
        assertEquals("PY", ProjectMarks.initials("py"))
        assertEquals("C", ProjectMarks.initials("c"))
    }

    @Test
    fun `initials stay inside the tile - two characters, upper case`() {
        listOf("snake", "todo-app", "a", "", "...", "my very long project name").forEach { name ->
            val initials = ProjectMarks.initials(name)
            assertTrue(
                "'$name' yields at most ${ProjectMarks.MAX_INITIALS} characters, got '$initials'",
                initials.length <= ProjectMarks.MAX_INITIALS
            )
            assertEquals("'$name' is upper case", initials, initials.uppercase())
        }
    }

    @Test
    fun `camel case is one word, not two`() {
        // The documented rule: a name's own separators are the only split. A user who
        // wants `MA` writes `my-app`.
        assertEquals("MY", ProjectMarks.initials("myApp"))
        assertEquals("MA", ProjectMarks.initials("my-app"))
    }

    @Test
    fun `a name with no letters or digits still wears something`() {
        assertEquals("", ProjectMarks.initials("..."))
        assertEquals("", ProjectMarks.initials("   "))
        assertEquals("", ProjectMarks.initials(""))
        assertEquals(
            "and the mark falls back rather than handing the view an empty tile",
            ProjectMarks.FALLBACK_INITIALS, ProjectMarks.mark("...").initials
        )
        assertEquals(
            "‘snake’ is one word, so it keeps its first two letters",
            "SN", ProjectMarks.mark("snake").initials
        )
    }

    @Test
    fun `non-latin names keep their own letters`() {
        assertEquals("НК", ProjectMarks.initials("Ниже код"))
        assertEquals(
            "a single non-Latin word keeps its own two characters",
            "日本", ProjectMarks.initials("日本")
        )
        assertTrue(
            "digits are name material too",
            ProjectMarks.initials("2048 game") == "2G"
        )
    }

    // ---- the seat ----------------------------------------------------------

    @Test
    fun `every seat is reachable and every name gets exactly one`() {
        val seats = (1..400).map { ProjectMarks.seat("project$it") }.toSet()
        assertEquals(
            "a five-seat palette should be fully used by real names, got $seats",
            MarkSeat.values().toSet(), seats
        )
    }

    @Test
    fun `the seat is stable for the same name`() {
        assertEquals(ProjectMarks.seat("snake"), ProjectMarks.seat("snake"))
        assertEquals(
            "case and surrounding space are folded - a name is one name",
            ProjectMarks.seat("Snake"),
            ProjectMarks.seat("  snake ")
        )
        // The hash is the policy's own (FNV-1a), not the JVM's: pin real values so a refactor
        // that swaps in String.hashCode() fails here (the seat a user sees must survive every
        // update, not just every run).
        assertEquals("a known name keeps its seat", MarkSeat.BLUE, ProjectMarks.seat("snake"))
        assertEquals("and another keeps its own", MarkSeat.GRAY, ProjectMarks.seat("api-server"))
        assertEquals("not the JVM's hash", MarkSeat.GREEN, ProjectMarks.seat("web-scraper"))
    }

    @Test
    fun `two projects of the same kind wear different marks`() {
        // The exit line of Phase 59, in names a user would really have: three Python
        // projects, one kind, three marks.
        val names = listOf("api-server", "data-tools", "web-scraper")
        val marks = names.map { ProjectMarks.mark(it) }
        assertEquals("three projects, three marks", 3, marks.toSet().size)
        assertTrue(
            "and the seats are not all the same seat",
            marks.map { it.seat }.toSet().size >= 2
        )
    }

    @Test
    fun `a shared set of initials is separated by the seat`() {
        // "data-tools" and "dev-test" are both DT; the seat is a hash of the whole
        // name, so the two tiles still differ in colour.
        assertEquals(ProjectMarks.initials("data-tools"), ProjectMarks.initials("dev-test"))
        assertNotEquals(ProjectMarks.seat("data-tools"), ProjectMarks.seat("dev-test"))
    }
}
