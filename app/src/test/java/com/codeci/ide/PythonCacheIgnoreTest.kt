package com.codeci.ide

import com.codeci.ide.ui.projects.PythonCacheIgnore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Device-round fix tests — the python `__pycache__` exclusion policy: the
 * pure append/cover logic and the thin [ensure] IO against real temp
 * folders (root cache, one-level cache, user .gitignore winning, idempotent
 * re-runs, no-repo no-op).
 */
class PythonCacheIgnoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val root: File get() = tmp.root

    // ---- pure policy ----

    @Test
    fun splitLines_drops_comments_and_blanks() {
        assertEquals(
            listOf("build/", "*.log"),
            PythonCacheIgnore.splitLines("# comment\n\n  build/  \n\n*.log\n")
        )
        assertEquals(emptyList<String>(), PythonCacheIgnore.splitLines(null))
    }

    @Test
    fun covers_recognises_pycache_and_pyc_patterns() {
        assertTrue(PythonCacheIgnore.covers(listOf("__pycache__/")))
        assertTrue(PythonCacheIgnore.covers(listOf("**/__pycache__")))
        assertTrue(PythonCacheIgnore.covers(listOf("*.pyc")))
        assertFalse(PythonCacheIgnore.covers(listOf("bin/", "*.o")))
    }

    @Test
    fun shouldAppend_respects_existing_rules_in_either_file() {
        assertTrue(PythonCacheIgnore.shouldAppend(null, null))
        assertFalse(PythonCacheIgnore.shouldAppend("__pycache__/", null))
        assertFalse(PythonCacheIgnore.shouldAppend("# nothing here\n", "__pycache__/\n"))
        assertFalse(PythonCacheIgnore.shouldAppend(null, "*.pyc\n"))
    }

    @Test
    fun appendTo_preserves_bytes_and_adds_the_three_lines() {
        val out = PythonCacheIgnore.appendTo("keepme\n")
        assertTrue(out.startsWith("keepme\n"))
        assertTrue(out.contains("\n__pycache__/\n"))
        assertTrue(out.endsWith("*.pyo\n"))
        // Idempotence proof: re-running the policy over the result declines.
        assertFalse(PythonCacheIgnore.shouldAppend(out, null))
    }

    @Test
    fun hasCacheIn_finds_root_and_one_level_caches_only() {
        assertFalse(PythonCacheIgnore.hasCacheIn(root))
        File(root, "src").mkdirs()
        File(root, "src/__pycache__").mkdirs()
        assertTrue(PythonCacheIgnore.hasCacheIn(root))
        val deep = File(root, "src/__pycache__/deep")
        deep.mkdirs()
        assertTrue(PythonCacheIgnore.hasCacheIn(root)) // still true via parent
        val other = tmp.newFolder("no-cache")
        File(other, "a/b").mkdirs()
        assertFalse(PythonCacheIgnore.hasCacheIn(other))
    }

    // ---- ensure() IO ----

    @Test
    fun ensure_writes_the_exclude_file_for_a_real_repo() {
        File(root, ".git").mkdirs()
        File(root, "__pycache__").mkdirs()
        File(root, "__pycache__/main.cpython-314.pyc").writeText("x")

        PythonCacheIgnore.ensure(root)

        val exclude = File(root, ".git/info/exclude")
        assertTrue(exclude.isFile)
        val content = exclude.readText()
        assertTrue(content.contains("__pycache__/"))
        assertTrue(content.contains("*.pyc"))
        assertTrue(content.contains("*.pyo"))

        // Second ensure: unchanged (covers() sees the first append).
        PythonCacheIgnore.ensure(root)
        assertEquals(content, exclude.readText())
    }

    @Test
    fun ensure_skips_when_the_user_gitignore_already_covers_it() {
        File(root, ".git").mkdirs()
        File(root, "__pycache__").mkdirs()
        File(root, ".gitignore").writeText("__pycache__/\n")
        PythonCacheIgnore.ensure(root)
        assertFalse(File(root, ".git/info/exclude").exists())
    }

    @Test
    fun ensure_is_a_noop_without_a_repo_and_without_a_cache() {
        File(root, "main.py").writeText("print(1)")
        PythonCacheIgnore.ensure(root) // no .git at all
        File(root, ".git").mkdirs()
        PythonCacheIgnore.ensure(root) // repo but no cache
        assertFalse(File(root, ".git/info/exclude").exists())
    }

    @Test
    fun ensure_follows_a_gitdir_pointer_file() {
        val worktree = File(root, "proj").also { it.mkdirs() }
        val realGit = File(root, "storage/git").also { it.mkdirs() }
        File(worktree, ".git").writeText("gitdir: ${realGit.absolutePath}\n")
        File(worktree, "__pycache__").mkdirs()
        PythonCacheIgnore.ensure(worktree)
        assertTrue(File(realGit, "info/exclude").readText().contains("__pycache__/"))
    }

    @Test
    fun ensure_appends_to_an_existing_exclude_keeps_its_lines() {
        File(root, ".git/info").mkdirs()
        File(root, ".git/info/exclude").writeText("secret.txt") // no trailing newline
        File(root, "__pycache__").mkdirs()
        PythonCacheIgnore.ensure(root)
        val content = File(root, ".git/info/exclude").readText()
        assertTrue(content.startsWith("secret.txt\n"))
        assertTrue(content.contains("__pycache__/"))
    }
}
