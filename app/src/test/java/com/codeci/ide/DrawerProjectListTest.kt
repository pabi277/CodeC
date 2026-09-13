package com.codeci.ide

import com.codeci.ide.ui.editor.DrawerProjectList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 47.1 — the in-drawer PROJECTS list (PART_47_1 §Tests): Single files
 * first, then projects alphabetically; the current context marked; the empty
 * state keeps the retired dialog's copy verbatim; a project deleted from disk
 * simply never makes it into the list the screen built.
 */
class DrawerProjectListTest {

    @Test
    fun `Single files is first, then projects alphabetically`() {
        val rows = DrawerProjectList.build(
            projectNames = listOf("zeta", "playground", "school-c", "Alpha"),
            currentContext = "playground"
        )
        assertEquals("Single files", rows.first().label)
        assertEquals(listOf("Alpha", "playground", "school-c", "zeta"), rows.drop(1).map { it.label })
    }

    @Test
    fun `Single files row carries the null context`() {
        val rows = DrawerProjectList.build(listOf("a"), null)
        assertNull(rows.first().contextName)
        assertEquals("a", rows[1].contextName)
    }

    @Test
    fun `the current context is marked - and nothing else`() {
        val rows = DrawerProjectList.build(listOf("myapp", "other"), "myapp")
        assertEquals("Single files", rows[0].label)
        assertFalse(rows[0].isCurrent)
        assertTrue(rows.first { it.label == "myapp" }.isCurrent)
        assertFalse(rows.first { it.label == "other" }.isCurrent)
    }

    @Test
    fun `scratch context marks Single files as current`() {
        val rows = DrawerProjectList.build(listOf("myapp"), null)
        assertTrue(rows.first().isCurrent)
        assertFalse(rows.first { it.label == "myapp" }.isCurrent)
    }

    @Test
    fun `no projects keeps the Single files row and the dialog's empty copy`() {
        val rows = DrawerProjectList.build(emptyList(), null)
        assertEquals(listOf("Single files"), rows.map { it.label })
        assertTrue(rows.first().isCurrent)
        assertEquals(
            "No projects yet — create one in the Projects tab, or keep working with single files here.",
            DrawerProjectList.EMPTY_PROJECTS_COPY
        )
    }
}
