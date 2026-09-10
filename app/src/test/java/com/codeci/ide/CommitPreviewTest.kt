package com.codeci.ide

import com.codeci.ide.ui.projects.RepoHygiene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 39.2 — "what will be committed" projection from a captured
 * `git status --porcelain=v1` fixture, incl. renames and the 15-line cap.
 */
class CommitPreviewTest {

    @Test
    fun `empty status yields empty preview`() {
        val p = RepoHygiene.commitPreview(listOf("## main"))
        assertEquals(0, p.total)
        assertTrue(p.staged.isEmpty())
        assertFalse(p.truncated)
    }

    @Test
    fun `staged modified added deleted show up with their status letter`() {
        val p = RepoHygiene.commitPreview(
            listOf(
                "## main...origin/main",
                "M  src/main.c",
                "A  src/new.c",
                "D  src/gone.c",
            )
        )
        assertEquals(3, p.total)
        assertEquals(listOf("M", "A", "D"), p.staged.map { it.status })
        assertEquals(listOf("src/main.c", "src/new.c", "src/gone.c"), p.staged.map { it.path })
    }

    @Test
    fun `rename surfaces as old arrow new`() {
        val p = RepoHygiene.commitPreview(listOf("## main", "R  old.txt -> new.txt"))
        assertEquals(1, p.total)
        assertEquals("R", p.staged.single().status)
        val path = p.staged.single().path
        assertTrue("path=$path", path.contains("old.txt") && path.contains("new.txt"))
    }

    @Test
    fun `unstaged and untracked are not in the staged preview`() {
        val p = RepoHygiene.commitPreview(
            listOf(
                "## main",
                " M only-worktree.c",
                "?? brand-new.c",
                "M  actually-staged.c",
            )
        )
        assertEquals(1, p.total)
        assertEquals("actually-staged.c", p.staged.single().path)
    }

    @Test
    fun `truncates at the limit and reports the remainder`() {
        val lines = listOf("## main") + (1..20).map { "A  f$it.c" }
        val p = RepoHygiene.commitPreview(lines, limit = 15)
        assertEquals(15, p.staged.size)
        assertEquals(20, p.total)
        assertTrue(p.truncated)
    }

    @Test
    fun `hygiene note is carried through`() {
        val p = RepoHygiene.commitPreview(
            listOf("## main", "M  a.c"),
            hygieneNote = "Removed 2 build outputs from the repo",
        )
        assertEquals("Removed 2 build outputs from the repo", p.hygieneNote)
    }
}
