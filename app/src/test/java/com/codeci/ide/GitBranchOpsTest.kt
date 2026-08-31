package com.codeci.ide

import com.codeci.ide.ui.projects.GitBranchOps
import com.codeci.ide.ui.projects.GitBranchParser
import com.codeci.ide.ui.projects.GitBranchOps.StashMarker
import com.codeci.ide.ui.projects.GitStashParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17 — pure host tests for the branch/stash/conflict logic: no Android,
 * no process, no fixtures. These pin the git output formats recorded in
 * `GitBranchOps.kt` (research notes) so a format surprise fails here instead
 * of on the owner's device.
 */
class GitBranchOpsTest {

    // --- conflicts ---------------------------------------------------------

    @Test
    fun `all seven porcelain unmerged pairs are conflicts`() {
        for (pair in listOf("DD", "AU", "UD", "UA", "DU", "AA", "UU")) {
            assertTrue("$pair must be a conflict", GitBranchOps.isConflict(pair[0], pair[1]))
        }
    }

    @Test
    fun `ordinary states are not conflicts`() {
        for (pair in listOf("M ", " M", "MM", "A ", "AM", "AD", "D ", " D", "R ", "??", "!!")) {
            assertFalse("$pair must NOT be a conflict", GitBranchOps.isConflict(pair[0], pair[1]))
        }
    }

    @Test
    fun `AA and DD are conflicts even though neither column is U`() {
        // The two pairs a naive "either column is U" test would miss.
        assertTrue(GitBranchOps.isConflict('A', 'A'))
        assertTrue(GitBranchOps.isConflict('D', 'D'))
    }

    // --- existing branch names --------------------------------------------

    @Test
    fun `existing branch names accept repo names but reject option-like ones`() {
        assertTrue(GitBranchOps.isSafeExistingBranch("main"))
        assertTrue(GitBranchOps.isSafeExistingBranch("feature/x"))
        assertTrue(GitBranchOps.isSafeExistingBranch("fix#123")) // valid in git, rejected for NEW names
        assertFalse(GitBranchOps.isSafeExistingBranch(""))
        assertFalse(GitBranchOps.isSafeExistingBranch("   "))
        assertFalse(GitBranchOps.isSafeExistingBranch("--upload-pack=evil"))
        assertFalse(GitBranchOps.isSafeExistingBranch("main..origin"))
        assertFalse(GitBranchOps.isSafeExistingBranch("has space"))
    }

    // --- branch list parsing ----------------------------------------------

    @Test
    fun `branch parser splits local and remote and marks the current branch`() {
        val list = GitBranchParser.parse(
            listOf(
                "  feature/x",
                "* main",
                "  remotes/origin/HEAD -> origin/main",
                "  remotes/origin/develop",
                "  remotes/origin/main"
            )
        )
        assertEquals(listOf("feature/x", "main"), list.local.map { it.name })
        assertEquals(listOf("origin/develop", "origin/main"), list.remote.map { it.name })
        assertEquals("main", list.current)
        assertFalse(list.detached)
        assertTrue(list.local.first { it.name == "main" }.isCurrent)
        assertFalse(list.local.first { it.name == "feature/x" }.isCurrent)
        // The remote symref is dropped, not listed as a branch.
        assertTrue(list.branches.none { it.name.contains("HEAD") })
    }

    @Test
    fun `branch parser reports a detached HEAD instead of a branch name`() {
        val list = GitBranchParser.parse(listOf("* (HEAD detached at 1a2b3c4)", "  main"))
        assertTrue(list.detached)
        assertNull(list.current)
        assertEquals(listOf("main"), list.local.map { it.name })
    }

    @Test
    fun `branch parser handles an empty repository`() {
        val list = GitBranchParser.parse(emptyList())
        assertTrue(list.branches.isEmpty())
        assertNull(list.current)
        assertFalse(list.detached)
    }

    @Test
    fun `remote branch names expose the local name a checkout would create`() {
        val list = GitBranchParser.parse(listOf("  remotes/origin/release/1.0"))
        assertEquals("release/1.0", list.remote.first().localName)
    }

    // --- stash parsing -----------------------------------------------------

    @Test
    fun `stash parser reads the default WIP form`() {
        val entries = GitStashParser.parse(
            listOf("stash@{0}: WIP on main: 1a2b3c4 add login page")
        )
        assertEquals(1, entries.size)
        assertEquals(0, entries[0].index)
        assertEquals("stash@{0}", entries[0].ref)
        assertEquals("main", entries[0].branch)
        assertEquals("1a2b3c4 add login page", entries[0].message)
        assertNull(entries[0].codecBranch)
    }

    @Test
    fun `stash parser reads a custom message and our marker`() {
        val entries = GitStashParser.parse(
            listOf(
                "stash@{0}: On main: codec-switch: main",
                "stash@{1}: On feature/x: codec-switch: feature/x",
                "stash@{2}: WIP on main: 1a2b3c4 unrelated"
            )
        )
        assertEquals(3, entries.size)
        assertEquals("main", entries[0].codecBranch)
        assertEquals("feature/x", entries[1].codecBranch)
        assertNull(entries[2].codecBranch)
        assertEquals("stash@{2}", entries[2].ref)
    }

    @Test
    fun `stash parser ignores junk lines`() {
        assertTrue(GitStashParser.parse(listOf("", "not a stash line")).isEmpty())
    }

    // --- stash marker ------------------------------------------------------

    @Test
    fun `stash marker round trips the branch name`() {
        val message = StashMarker.message("feature/x")
        assertEquals("codec-switch: feature/x", message)
        assertEquals("feature/x", StashMarker.branchOf(message))
        assertNull(StashMarker.branchOf("WIP on main: 1a2b3c4 something"))
        assertNull(StashMarker.branchOf(null))
    }
}
