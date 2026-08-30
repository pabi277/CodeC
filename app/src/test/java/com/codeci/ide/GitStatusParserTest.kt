package com.codeci.ide

import com.codeci.ide.ui.projects.GitFileChange
import com.codeci.ide.ui.projects.GitFileState
import com.codeci.ide.ui.projects.GitManager
import com.codeci.ide.ui.projects.GitRedactor
import com.codeci.ide.ui.projects.GitStatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 13 — host tests for the `git status --porcelain=v1 -b` parser and secret redaction. */
class GitStatusParserTest {

    @Test
    fun `empty output yields empty status`() {
        val status = GitStatusParser.parse(emptyList())
        assertEquals(null, status.branch)
        assertFalse(status.detached)
        assertEquals(0, status.files.size)
    }

    @Test
    fun `branch with upstream and ahead-behind`() {
        val status = GitStatusParser.parse(
            listOf("## main...origin/main [ahead 2, behind 3]")
        )
        assertEquals("main", status.branch)
        assertEquals("origin/main", status.upstream)
        assertEquals(2, status.ahead)
        assertEquals(3, status.behind)
        assertTrue(status.files.isEmpty())
    }

    @Test
    fun `upstream without bracket info`() {
        val status = GitStatusParser.parse(listOf("## feature/login...origin/feature/login"))
        assertEquals("feature/login", status.branch)
        assertEquals("origin/feature/login", status.upstream)
        assertEquals(0, status.ahead)
        assertEquals(0, status.behind)
    }

    @Test
    fun `no commits yet variant`() {
        val status = GitStatusParser.parse(listOf("## No commits yet on main"))
        assertEquals("main", status.branch)
        assertNull(status.upstream)
    }

    @Test
    fun `detached head`() {
        val status = GitStatusParser.parse(listOf("## HEAD (no branch)"))
        assertTrue(status.detached)
        assertEquals(null, status.branch)
    }

    @Test
    fun `porcelain file codes map to states and badges`() {
        val status = GitStatusParser.parse(
            listOf(
                "## main",
                "M  modified.c",
                " M unstaged.c",
                "A  added.c",
                "D  deleted.c",
                "?? untracked.txt",
                "R  old.c -> new.c",
                "UU conflict.c"
            )
        )
        val files = status.files
        assertEquals(7, files.size)

        assertEquals(GitFileState.MODIFIED, files[0].state)
        assertEquals("M", files[0].badge)
        assertEquals("modified.c", files[0].path)

        assertEquals(GitFileState.MODIFIED, files[1].state)
        assertEquals("M", files[1].badge)

        assertEquals(GitFileState.ADDED, files[2].state)
        assertEquals("A", files[2].badge)

        assertEquals(GitFileState.DELETED, files[3].state)
        assertEquals("D", files[3].badge)

        assertEquals(GitFileState.UNTRACKED, files[4].state)
        assertEquals("?", files[4].badge)

        assertEquals(GitFileState.RENAMED, files[5].state)
        assertEquals("old.c", files[5].oldPath)
        assertEquals("new.c", files[5].path)

        assertEquals(GitFileState.UNMERGED, files[6].state)
        assertEquals("U", files[6].badge)
    }

    @Test
    fun `quoted path with spaces is unquoted`() {
        val status = GitStatusParser.parse(listOf("## main", "M  \"my notes/file one.md\""))
        assertEquals("my notes/file one.md", status.files.single().path)
    }

    @Test
    fun `quoted rename with arrow inside filename`() {
        val status = GitStatusParser.parse(
            listOf("## main", "R  \"a -> b.txt\" -> \"c.txt\"")
        )
        val change = status.files.single()
        assertEquals("a -> b.txt", change.oldPath)
        assertEquals("c.txt", change.path)
    }

    @Test
    fun `unquote handles escaped characters`() {
        assertEquals("tab\there", GitStatusParser.unquote("\"tab\\there\""))
        assertEquals("quote\"inside", GitStatusParser.unquote("\"quote\\\"inside\""))
        assertEquals("plain.txt", GitStatusParser.unquote("plain.txt"))
    }

    @Test
    fun `carriage returns are stripped`() {
        val status = GitStatusParser.parse(listOf("## main\r", "M  main.c\r"))
        assertEquals("main", status.branch)
        assertEquals("main.c", status.files.single().path)
    }

    // --- GitManager.repoNameFromUrl / isCloneableUrl ---

    @Test
    fun `repo name from url variants`() {
        assertEquals("CodeC", GitManager.repoNameFromUrl("https://github.com/pabi277/CodeC"))
        assertEquals("CodeC", GitManager.repoNameFromUrl("https://github.com/pabi277/CodeC.git"))
        assertEquals("CodeC", GitManager.repoNameFromUrl("https://github.com/pabi277/CodeC.git/"))
        assertEquals("my-repo", GitManager.repoNameFromUrl("http://gitlab.com/u/my-repo/"))
        assertNull(GitManager.repoNameFromUrl("git@github.com:pabi277/CodeC.git"))
        assertNull(GitManager.repoNameFromUrl("/local/path"))
        assertNull(GitManager.repoNameFromUrl("https://github.com/"))
    }

    @Test
    fun `cloneable url requires http(s)`() {
        assertTrue(GitManager.isCloneableUrl("https://github.com/u/r.git"))
        assertFalse(GitManager.isCloneableUrl("git@github.com:u/r.git"))
        assertFalse(GitManager.isCloneableUrl("file:///etc"))
        assertFalse(GitManager.isCloneableUrl(""))
    }

    // --- GitRedactor ---

    @Test
    fun `token literal is replaced`() {
        val redactor = GitRedactor("ghs_secrettoken123")
        assertEquals(
            "fatal: Authentication failed for '***'",
            redactor.redact("fatal: Authentication failed for 'ghs_secrettoken123'")
        )
        assertEquals("remote: *** rejected", redactor.redact("remote: ghs_secrettoken123 rejected"))
    }


    @Test
    fun `url credentials are scrubbed even without a known token`() {
        val redactor = GitRedactor(null)
        val out = redactor.redact("https://oauth2:ghs_abc@github.com/u/r.git")
        assertFalse(out.contains("ghs_abc"))
        assertTrue(out.startsWith("https://***@"))
    }

    @Test
    fun `redactAll scrubs every line`() {
        val redactor = GitRedactor("tok123")
        val lines = redactor.redactAll(listOf("a tok123", "b tok123 x", "clean"))
        assertEquals(listOf("a ***", "b *** x", "clean"), lines)
    }

    @Test
    fun `change state precedence unmerged beats other codes`() {
        val change = GitFileChange(x = 'D', y = 'U', path = "x.c")
        assertEquals(GitFileState.UNMERGED, change.state)
    }
}
