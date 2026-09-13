package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectEntryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 46.2 — the "Open in editor" entry-file rule (PART_46_2 §4): the
 * launch default wins; a deleted default falls through; newest source beats
 * first source; an empty project yields nothing; binaries are never chosen.
 */
class ProjectEntryFileTest {

    private fun c(rel: String, mtime: Long) = ProjectEntryFile.Candidate(rel, mtime)

    @Test
    fun `the launch default wins when it still exists`() {
        val candidates = listOf(
            c("main.c", 100), c("app.py", 200), c("util/helper.c", 50)
        )
        assertEquals("app.py", ProjectEntryFile.pick(candidates, "app.py"))
    }

    @Test
    fun `a deleted launch default falls through to the newest source`() {
        val candidates = listOf(
            c("main.c", 100), c("app.py", 200)
        )
        // `gone.c` was set as the default and then deleted: no crash, no lie.
        assertEquals("app.py", ProjectEntryFile.pick(candidates, "gone.c"))
    }

    @Test
    fun `a launch default pointing at a non-source file falls through`() {
        val candidates = listOf(c("main.c", 100), c("logo.png", 999))
        assertEquals("main.c", ProjectEntryFile.pick(candidates, "logo.png"))
    }

    @Test
    fun `newest source beats first source alphabetically`() {
        val candidates = listOf(
            c("aaa.c", 10), c("zzz.py", 20), c("mmm.c", 5)
        )
        assertEquals("zzz.py", ProjectEntryFile.pick(candidates, null))
    }

    @Test
    fun `equal mtimes fall back to alphabetical order`() {
        val candidates = listOf(
            c("b.c", 10), c("a.c", 10)
        )
        assertEquals("a.c", ProjectEntryFile.pick(candidates, null))
    }

    @Test
    fun `no source files yields nothing`() {
        assertNull(ProjectEntryFile.pick(emptyList(), null))
        assertNull(ProjectEntryFile.pick(listOf(c("logo.png", 1), c("data.json", 2)), null))
    }

    @Test
    fun `binary and asset files are never chosen even when newest`() {
        val candidates = listOf(
            c("latest.png", 999_999), c("oldest.c", 1)
        )
        assertEquals("oldest.c", ProjectEntryFile.pick(candidates, null))
    }

    @Test
    fun `html counts as a source - a web project opens on its page`() {
        val candidates = listOf(c("index.html", 5), c("style.css", 9))
        assertEquals("index.html", ProjectEntryFile.pick(candidates, null))
    }

    @Test
    fun `nested paths compare by their full relative path`() {
        val candidates = listOf(
            c("src/util.c", 3), c("src/main.c", 3), c("app.py", 2)
        )
        // Newest first (both util.c and main.c at 3), then alphabetical on the
        // full relative path: src/main.c before src/util.c.
        assertEquals("src/main.c", ProjectEntryFile.pick(candidates, null))
    }
}
