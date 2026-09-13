package com.codeci.ide

import com.codeci.ide.ui.editor.EditorOpenMode
import com.codeci.ide.ui.editor.EditorOpenModePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 46.2 — the mode matrix (PART_46_2 §1). One enum, one policy, and the
 * booleans every chrome consumer reads. The compatibility story in one test:
 * every pre-46 route shape still resolves to PROJECT.
 */
class EditorOpenModeTest {

    // ---- forOpen: every route shape ---------------------------------------

    @Test
    fun `project file route is PROJECT`() {
        assertEquals(
            EditorOpenMode.PROJECT,
            EditorOpenModePolicy.forOpen(projectName = "myapp", fileName = "main.c", single = false)
        )
    }

    @Test
    fun `single flag makes the same route a SINGLE_FILE peek`() {
        assertEquals(
            EditorOpenMode.SINGLE_FILE,
            EditorOpenModePolicy.forOpen(projectName = "myapp", fileName = "main.c", single = true)
        )
    }

    @Test
    fun `scratch and bare routes are SCRATCH whatever the flag says`() {
        assertEquals(
            EditorOpenMode.SCRATCH,
            EditorOpenModePolicy.forOpen(projectName = null, fileName = "notes.py", single = false)
        )
        assertEquals(
            EditorOpenMode.SCRATCH,
            EditorOpenModePolicy.forOpen(projectName = null, fileName = null, single = false)
        )
        // A `single` flag without a project names nothing to peek into.
        assertEquals(
            EditorOpenMode.SCRATCH,
            EditorOpenModePolicy.forOpen(projectName = null, fileName = "notes.py", single = true)
        )
        assertEquals(
            EditorOpenMode.SCRATCH,
            EditorOpenModePolicy.forOpen(projectName = "myapp", fileName = null, single = true)
        )
    }

    // ---- the chrome/git/launch-state/tabs booleans per mode ----------------

    @Test
    fun `only PROJECT shows project chrome`() {
        assertTrue(EditorOpenModePolicy.showsProjectChrome(EditorOpenMode.PROJECT))
        assertFalse(EditorOpenModePolicy.showsProjectChrome(EditorOpenMode.SINGLE_FILE))
        assertFalse(EditorOpenModePolicy.showsProjectChrome(EditorOpenMode.SCRATCH))
    }

    @Test
    fun `only PROJECT writes the launch state - a peek never becomes the launch point`() {
        assertTrue(EditorOpenModePolicy.writesLaunchState(EditorOpenMode.PROJECT))
        assertFalse(EditorOpenModePolicy.writesLaunchState(EditorOpenMode.SINGLE_FILE))
        assertFalse(EditorOpenModePolicy.writesLaunchState(EditorOpenMode.SCRATCH))
    }

    @Test
    fun `only PROJECT shows git`() {
        assertTrue(EditorOpenModePolicy.showsGit(EditorOpenMode.PROJECT))
        assertFalse(EditorOpenModePolicy.showsGit(EditorOpenMode.SINGLE_FILE))
        assertFalse(EditorOpenModePolicy.showsGit(EditorOpenMode.SCRATCH))
    }

    @Test
    fun `only PROJECT keeps the project tab list - a peek is one file`() {
        assertTrue(EditorOpenModePolicy.usesProjectTabList(EditorOpenMode.PROJECT))
        assertFalse(EditorOpenModePolicy.usesProjectTabList(EditorOpenMode.SINGLE_FILE))
        assertFalse(EditorOpenModePolicy.usesProjectTabList(EditorOpenMode.SCRATCH))
    }

    // ---- statusBarPath ------------------------------------------------------

    @Test
    fun `PROJECT answers the relative path`() {
        assertEquals(
            "src/main.c",
            EditorOpenModePolicy.statusBarPath(EditorOpenMode.PROJECT, "/data/proj/myapp", "src/main.c")
        )
    }

    @Test
    fun `SCRATCH answers the bare file name`() {
        assertEquals(
            "notes.py",
            EditorOpenModePolicy.statusBarPath(EditorOpenMode.SCRATCH, null, "notes.py")
        )
    }

    @Test
    fun `SINGLE_FILE answers the real path shortened with the proj alias`() {
        assertEquals(
            "~proj/myapp/src/main.c",
            EditorOpenModePolicy.statusBarPath(EditorOpenMode.SINGLE_FILE, "/data/user/0/x/files/CodeC/projects/myapp", "src/main.c")
        )
    }

    @Test
    fun `SINGLE_FILE without a project root never invents the alias`() {
        assertEquals(
            "main.c",
            EditorOpenModePolicy.statusBarPath(EditorOpenMode.SINGLE_FILE, null, "main.c")
        )
    }

    @Test
    fun `empty paths answer nothing`() {
        assertNull(EditorOpenModePolicy.statusBarPath(EditorOpenMode.SINGLE_FILE, "/root", ""))
        assertNull(EditorOpenModePolicy.statusBarPath(EditorOpenMode.PROJECT, "/root", "  "))
    }

    @Test
    fun `spaces and unicode survive untouched`() {
        assertEquals(
            "~proj/my app/über datei.c",
            EditorOpenModePolicy.statusBarPath(
                EditorOpenMode.SINGLE_FILE,
                "/x/CodeC/projects/my app",
                "über datei.c"
            )
        )
    }
}
