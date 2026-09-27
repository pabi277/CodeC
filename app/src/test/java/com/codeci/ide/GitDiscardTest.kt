package com.codeci.ide

import com.codeci.ide.ui.projects.*
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Real Git on isolated temporary worktrees; never mutates the checkout running the tests. */
class GitDiscardTest {
    private fun git(root: File, vararg args: String): String {
        val p = ProcessBuilder(listOf("git", "--literal-pathspecs") + args).directory(root)
            .redirectErrorStream(true).start()
        val output = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "${args.toList()}: $output" }
        return output
    }
    private fun <T> repo(block: (File, GitManager) -> T): T {
        val root = Files.createTempDirectory("codec-discard-").toFile()
        try {
            git(root, "init", "-q")
            git(root, "config", "user.email", "test@example.invalid")
            git(root, "config", "user.name", "Discard Test")
            File(root, "main.txt").writeText("committed\n")
            File(root, "other.txt").writeText("other\n")
            git(root, "add", "-A"); git(root, "commit", "-qm", "base")
            val bin = listOf("/usr/bin/git", "/bin/git").map(::File).first { it.canExecute() }
            return block(root, GitManager(bin, mapOf("PATH" to "/usr/bin:/bin", "HOME" to root.path)))
        } finally { root.deleteRecursively() }
    }
    private fun refused(action: () -> Unit) {
        var refused = false
        try { action() } catch (_: Exception) { refused = true }
        assertTrue("unsafe operation must be refused", refused)
    }

    @Test fun `partially staged file restores index bytes and preserves other paths`() = repo { root, manager ->
        val file = File(root, "main.txt")
        file.writeText("staged\n"); git(root, "add", "--", "main.txt")
        file.writeText("unstaged\n")
        File(root, "other.txt").writeText("keep other edits\n")
        val before = git(root, "diff", "--cached", "--binary")
        manager.discardUnstaged(root, "main.txt")
        assertEquals("staged\n", file.readText())
        assertEquals(before, git(root, "diff", "--cached", "--binary"))
        assertEquals("keep other edits\n", File(root, "other.txt").readText())
        assertEquals(' ', manager.status(root).files.first { it.path == "main.txt" }.y)
    }
    @Test fun `unstaged deletion restores the file without changing the index`() = repo { root, manager ->
        File(root, "main.txt").delete()
        manager.discardUnstaged(root, "main.txt")
        assertEquals("committed\n", File(root, "main.txt").readText())
        assertTrue(manager.status(root).files.isEmpty())
    }
    @Test fun `literal and quoted names restore exactly one path from status`() = repo { root, manager ->
        val names = listOf("*.txt", ":(glob)*", "-flag", " leading.txt", "日本.txt", "a -> b.txt", "say\"hi.txt")
        for (name in names) {
            File(root, name).writeText("base\n")
            git(root, "add", "--", name)
        }
        git(root, "commit", "-qm", "names")
        for (name in names) File(root, name).writeText("edit\n")
        File(root, "other.txt").writeText("do not touch\n")
        for (name in names) {
            val changes = manager.status(root).files
            val change = changes.singleOrNull { it.path == name }
                ?: error("Missing exact name <$name> in $changes")
            assertTrue(GitDiscardPolicy.canDiscard(change))
            manager.discardUnstaged(root, change.path)
            assertEquals("base\n", File(root, name).readText())
            assertEquals("do not touch\n", File(root, "other.txt").readText())
        }
    }
    @Test fun `new staged and untracked files are not discarded`() = repo { root, manager ->
        val added = File(root, "new.txt").apply { writeText("added\n") }
        git(root, "add", "--", "new.txt"); added.writeText("new unstaged edits\n")
        val loose = File(root, "loose.txt").apply { writeText("loose\n") }
        refused { manager.discardUnstaged(root, "new.txt") }
        refused { manager.discardUnstaged(root, "loose.txt") }
        assertEquals("new unstaged edits\n", added.readText())
        assertEquals("loose\n", loose.readText())
    }
    @Test fun `stale staged-only and renamed paths are refused`() = repo { root, manager ->
        File(root, "main.txt").writeText("staged\n"); git(root, "add", "--", "main.txt")
        refused { manager.discardUnstaged(root, "main.txt") }
        git(root, "mv", "main.txt", "renamed.txt")
        File(root, "renamed.txt").writeText("extra\n")
        refused { manager.discardUnstaged(root, "renamed.txt") }
        assertEquals("extra\n", File(root, "renamed.txt").readText())
    }
    @Test fun `unsafe paths directories and symlinks do not touch a target`() = repo { root, manager ->
        for (name in listOf("", ".", "..", "../main.txt", "/tmp/main.txt", ".git/config", "other/.git/config", "a\\b")) {
            refused { manager.discardUnstaged(root, name) }
        }
        File(root, "folder").mkdir()
        refused { manager.discardUnstaged(root, "folder") }
        Files.createSymbolicLink(File(root, "link.txt").toPath(), File(root, "other.txt").toPath())
        refused { manager.discardUnstaged(root, "link.txt") }
        assertEquals("other\n", File(root, "other.txt").readText())
    }
    @Test fun `merge stages and gitlinks cannot be restored by discard`() = repo { root, manager ->
        val blob = git(root, "rev-parse", "HEAD:main.txt").trim()
        git(root, "update-index", "--force-remove", "main.txt")
        val proc = ProcessBuilder("git", "update-index", "--index-info").directory(root).start()
        proc.outputStream.bufferedWriter().use { it.write("100644 $blob 1\tmain.txt\n100644 $blob 2\tmain.txt\n100644 $blob 3\tmain.txt\n") }
        assertEquals(0, proc.waitFor())
        refused { manager.discardUnstaged(root, "main.txt") }
        assertEquals("committed\n", File(root, "main.txt").readText())
        val commit = git(root, "rev-parse", "HEAD").trim()
        git(root, "update-index", "--add", "--cacheinfo", "160000,$commit,module")
        refused { manager.discardUnstaged(root, "module") }
    }
    @Test fun `configured external worktree cannot redirect the destructive write`() = repo { root, manager ->
        val outside = Files.createTempDirectory("codec-discard-outside-").toFile()
        try {
            val target = File(outside, "main.txt").apply { writeText("outside stays unchanged\n") }
            git(root, "config", "core.worktree", outside.absolutePath)
            refused { manager.discardUnstaged(root, "main.txt") }
            assertEquals("outside stays unchanged\n", target.readText())
        } finally { outside.deleteRecursively() }
    }
    @Test fun `unborn staged addition stays untouched`() {
        val root = Files.createTempDirectory("codec-unborn-").toFile()
        try {
            git(root, "init", "-q")
            File(root, "new.txt").writeText("initial\n"); git(root, "add", "--", "new.txt")
            File(root, "new.txt").writeText("working\n")
            val manager = GitManager(File("/usr/bin/git"), mapOf("PATH" to "/usr/bin:/bin"))
            refused { manager.discardUnstaged(root, "new.txt") }
            assertEquals("working\n", File(root, "new.txt").readText())
        } finally { root.deleteRecursively() }
    }
}
