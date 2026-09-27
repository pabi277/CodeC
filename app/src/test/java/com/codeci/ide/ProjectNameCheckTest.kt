package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectNameCheck
import com.codeci.ide.ui.projects.ProjectNameProblem
import com.codeci.ide.ui.projects.ProjectPathUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 66.1 — the verdict a project-name field shows before the tap.
 *
 * The point of the policy is that it says, in the dialog, exactly what
 * `ProjectManager.createProject` / `renameProject` would have said afterwards
 * in a snackbar behind the dialog — so every case here is one the manager
 * really refuses (or really accepts).
 */
class ProjectNameCheckTest {

    private val existing = listOf("snake", "demo_flask", "My App")

    @Test
    fun `an empty or blank name is EMPTY - nothing to show, nothing to submit`() {
        for (raw in listOf("", "   ", "\t")) {
            val verdict = ProjectNameCheck.check(raw, existing)
            assertEquals(ProjectNameProblem.EMPTY, verdict.problem)
            assertNull(verdict.safeName)
            assertFalse(verdict.ok)
            assertFalse("an empty field is not an error state", verdict.showsError)
        }
    }

    @Test
    fun `what the sanitizer refuses is INVALID, and it is exactly the sanitizer's rule`() {
        for (raw in listOf("a/b", "a\\b", ".", "..", "bad\u0000name", "tab\tname")) {
            val verdict = ProjectNameCheck.check(raw, existing)
            assertEquals(raw, ProjectNameProblem.INVALID, verdict.problem)
            assertNull(verdict.safeName)
            assertTrue(verdict.showsError)
            assertNull("the policy must agree with the manager's own gate", ProjectPathUtils.sanitizeProjectName(raw))
        }
    }

    @Test
    fun `a name another project already has is TAKEN, trimmed`() {
        val verdict = ProjectNameCheck.check("  snake ", existing)
        assertEquals(ProjectNameProblem.TAKEN, verdict.problem)
        assertEquals("snake", verdict.safeName)
        assertTrue(verdict.showsError)
        assertFalse(verdict.ok)
    }

    @Test
    fun `case is not folded - the projects root is a case-sensitive directory`() {
        // `createProject` checks `root.exists()`; on the app's private ext4
        // storage `Snake` and `snake` are two directories, so the field must
        // not refuse a name the manager would accept.
        assertTrue(ProjectNameCheck.check("Snake", existing).ok)
    }

    @Test
    fun `a usable name is ok and carries the trimmed name the operation will use`() {
        val verdict = ProjectNameCheck.check("  todo-app  ", existing)
        assertNull(verdict.problem)
        assertEquals("todo-app", verdict.safeName)
        assertTrue(verdict.ok)
        assertFalse(verdict.showsError)
        // Spaces and Unicode are the manager's business, not the field's: it
        // accepts them, so the field does too.
        assertTrue(ProjectNameCheck.check("My Second App", existing).ok)
        assertTrue(ProjectNameCheck.check("проект", existing).ok)
    }

    @Test
    fun `renaming a project to its own name is not a collision`() {
        val same = ProjectNameCheck.check("snake", existing, current = "snake")
        assertTrue(same.ok)
        assertEquals("snake", same.safeName)
        // …but another project's name still is.
        assertEquals(
            ProjectNameProblem.TAKEN,
            ProjectNameCheck.check("demo_flask", existing, current = "snake").problem
        )
    }

    @Test
    fun `a de-duplicating flow may accept a taken name and say what it becomes`() {
        val verdict = ProjectNameCheck.check("snake", existing, allowTaken = true)
        assertTrue(verdict.ok)
        assertEquals("snake", verdict.safeName)
        assertEquals("snake_2", ProjectNameCheck.importedNameFor("snake", existing))
        assertEquals("snake_3", ProjectNameCheck.importedNameFor("snake", existing + "snake_2"))
        assertEquals("fresh", ProjectNameCheck.importedNameFor(" fresh ", existing))
        assertNull("an unusable name has no import name", ProjectNameCheck.importedNameFor("a/b", existing))
        assertNull(ProjectNameCheck.importedNameFor("   ", existing))
    }

    @Test
    fun `the import name is the scheme the ZIP and clone paths really use`() {
        // `ProjectsHub.uniqueProjectName` is the one implementation (clone,
        // share-in and now the ZIP dialog's promise all read it).
        assertEquals(
            com.codeci.ide.ui.projects.ProjectsHub.uniqueProjectName("snake", existing.toSet()),
            ProjectNameCheck.importedNameFor("snake", existing)
        )
    }

    @Test
    fun `the manager is still the last word - the policy never widens it`() {
        // Every name the policy calls INVALID is one ProjectManager.createProject
        // would refuse before touching the disk (it sanitizes with the same
        // function); this pins the shared gate by reading the manager's source.
        val manager = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/projects/ProjectManager.kt"
        ).readText()
        assertTrue(manager.contains("ProjectPathUtils.sanitizeProjectName(name)"))
        assertTrue(manager.contains("A project with that name already exists"))
    }
}
