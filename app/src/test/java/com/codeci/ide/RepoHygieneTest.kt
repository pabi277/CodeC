package com.codeci.ide

import com.codeci.ide.ui.projects.RepoHygiene
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 39.2 — golden table, missingLines / user-wins, matches for every
 * language row, and the critical false cases (build.gradle.kts must NOT
 * match build/; out/main.c is a source under a folder called out — the
 * directory pattern matches the folder, not a file named out).
 */
class RepoHygieneTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---- golden table ----

    @Test
    fun `table has the expected size and required entries`() {
        val patterns = RepoHygiene.PATTERNS.toSet()
        // Floor: the plan says ~55-65; pin a floor so silent drops fail.
        assertTrue("table too small: ${patterns.size}", patterns.size >= 50)
        assertTrue("table too big: ${patterns.size}", patterns.size <= 80)
        for (required in listOf(
            "*.out", "*.o", "*.so", "*.a", "*.d", "bin/", "CMakeFiles/", "compile_commands.json",
            "__pycache__/", "*.pyc", ".venv/", "venv/", ".pytest_cache/", ".mypy_cache/", ".ruff_cache/",
            "node_modules/", "dist/", ".next/", "*.tsbuildinfo",
            "*.class", "build/", "target/", ".gradle/", "out/",
            ".DS_Store", "Thumbs.db", "*.swp", "*~",
            ".codec/", ".codec.json", ".codec-tmp/", "codec-*.tmp",
        )) {
            assertTrue("missing required pattern: $required", required in patterns)
        }
    }

    @Test
    fun `every pattern carries a reason and at least one lang tag`() {
        for (p in RepoHygiene.EXCLUDE_LINES) {
            assertTrue(p.pattern, p.reason.isNotBlank())
            assertTrue(p.pattern, p.langs.isNotEmpty())
        }
    }

    // ---- missingLines / user wins ----

    @Test
    fun `missingLines on empty exclude returns the full table`() {
        val missing = RepoHygiene.missingLines(null, null)
        assertEquals(RepoHygiene.EXCLUDE_LINES.size, missing.size)
    }

    @Test
    fun `missingLines skips patterns already in exclude or gitignore`() {
        val existing = "bin/\n*.out\n"
        val gitignore = "node_modules/\n"
        val missing = RepoHygiene.missingLines(existing, gitignore).map { it.pattern }.toSet()
        assertFalse("bin/" in missing)
        assertFalse("*.out" in missing)
        assertFalse("node_modules/" in missing)
        assertTrue(".codec/" in missing)
    }

    @Test
    fun `missingLines is empty when gitignore already covers everything`() {
        val all = RepoHygiene.PATTERNS.joinToString("\n")
        val missing = RepoHygiene.missingLines(null, all)
        assertTrue(missing.isEmpty())
    }

    @Test
    fun `user negation bang-a-out keeps a-out out of exclude`() {
        // User wants a.out tracked. CodeC must not add *.out (or any rule
        // that would hide it) — the bang is the signal.
        // Our rule: userWantsTracked on *.out when gitignore has !a.out
        // OR the exact pattern. We check the helper and missingLines.
        assertTrue(RepoHygiene.userWantsTracked(listOf("!a.out"), "*.out"))
        assertTrue(RepoHygiene.userWantsTracked(listOf("!a.out"), "a.out"))
        val missing = RepoHygiene.missingLines(null, "!a.out\n").map { it.pattern }
        // *.out must NOT be added (user wins). Other patterns still may.
        assertFalse("*.out" in missing)
    }

    @Test
    fun `appendTo preserves existing bytes and is idempotent via missingLines`() {
        val out = RepoHygiene.appendTo("keepme", RepoHygiene.EXCLUDE_LINES.take(3))
        assertTrue(out.startsWith("keepme\n"))
        assertTrue(out.contains(RepoHygiene.NOTE))
        val again = RepoHygiene.missingLines(out, null)
        assertTrue(again.none { it.pattern in RepoHygiene.EXCLUDE_LINES.take(3).map { p -> p.pattern } })
    }

    // ---- matches: true cases ----

    @Test
    fun `matches every language row the plan names`() {
        val yes = listOf(
            "a.out",
            "bin/menu",
            "foo.o",
            "libfoo.so",
            "libbar.a",
            "main.d",
            "CMakeFiles/3.22/CMakeCCompiler.cmake",
            "CMakeCache.txt",
            "compile_commands.json",
            "__pycache__/x.pyc",
            "mod.pyc",
            ".venv/lib/python3/site-packages/z.py",
            "venv/bin/activate",
            ".pytest_cache/v/cache/nodeids",
            "node_modules/left-pad/index.js",
            "dist/bundle.js",
            ".next/BUILD_ID",
            "app.class",
            "build/app/intermediates/y",
            "target/classes/A.class",
            ".gradle/caches/modules-2",
            "out/production/main",
            ".DS_Store",
            "Thumbs.db",
            "notes.swp",
            "file~",
            ".codec/project.json",
            ".codec.json",
            ".codec-tmp/x",
            "codec-scratch.tmp",
        )
        for (path in yes) {
            assertTrue("should match: $path", RepoHygiene.matches(path))
        }
    }

    // ---- matches: false cases (the interesting half) ----

    @Test
    fun `matches is false for files a user must never lose`() {
        val no = listOf(
            "about.html",
            "src/index.ts",
            "build.gradle.kts",   // must NOT match build/
            "build.py",           // must NOT match build/
            "target.c",           // must NOT match target/
            "node.js",            // must NOT match node_modules/
            "main.c",
            "README.md",
            "src/app.kt",
            "docs/guide.md",
            "package.json",
            "tsconfig.json",
        )
        for (path in no) {
            assertFalse("must NOT match: $path", RepoHygiene.matches(path))
        }
    }

    @Test
    fun `directory patterns need the trailing slash semantics`() {
        // build/ matches build/foo and the dir itself, not build.gradle.kts
        assertTrue(RepoHygiene.patternMatches("build/foo", "build/"))
        assertTrue(RepoHygiene.patternMatches("build", "build/"))
        assertFalse(RepoHygiene.patternMatches("build.gradle.kts", "build/"))
        assertFalse(RepoHygiene.patternMatches("mybuild/x", "build/"))
    }

    @Test
    fun `star-ext patterns match the leaf only`() {
        assertTrue(RepoHygiene.patternMatches("src/a.out", "*.out"))
        assertTrue(RepoHygiene.patternMatches("a.out", "*.out"))
        assertFalse(RepoHygiene.patternMatches("a.out/extra", "*.out"))
    }

    // ---- explain ----

    @Test
    fun `explain names the pattern and the source`() {
        val fromTable = RepoHygiene.explain("a.out")
        assertTrue(fromTable.matched)
        assertEquals("*.out", fromTable.pattern)
        assertEquals("CodeC table", fromTable.source)

        val fromGitignore = RepoHygiene.explain("notes.txt", gitignoreText = "notes.txt\n")
        assertTrue(fromGitignore.matched)
        assertEquals(".gitignore", fromGitignore.source)

        val fromExclude = RepoHygiene.explain("bin/menu", excludeText = "bin/\n")
        assertTrue(fromExclude.matched)
        assertEquals(".git/info/exclude", fromExclude.source)

        val free = RepoHygiene.explain("main.c")
        assertFalse(free.matched)
        assertNull(free.pattern)
    }

    // ---- ensure IO ----

    @Test
    fun `ensure writes the exclude file for a real repo`() {
        val root = tmp.root
        File(root, ".git").mkdirs()
        val added = RepoHygiene.ensure(root)
        assertTrue(added.isNotEmpty())
        val exclude = File(root, ".git/info/exclude")
        assertTrue(exclude.isFile)
        val content = exclude.readText()
        assertTrue(content.contains(".codec/"))
        assertTrue(content.contains("__pycache__/"))
        assertTrue(content.contains("*.out"))
        // Second ensure: nothing new.
        assertTrue(RepoHygiene.ensure(root).isEmpty())
        assertEquals(content, exclude.readText())
    }

    @Test
    fun `ensure is a noop when gitignore covers the whole table`() {
        val root = tmp.newFolder("covered")
        File(root, ".git").mkdirs()
        File(root, ".gitignore").writeText(RepoHygiene.PATTERNS.joinToString("\n", postfix = "\n"))
        assertTrue(RepoHygiene.ensure(root).isEmpty())
        assertFalse(File(root, ".git/info/exclude").exists())
    }

    @Test
    fun `ensure is a noop without a repo`() {
        val root = tmp.newFolder("norepo")
        File(root, "main.c").writeText("int main(){return 0;}")
        assertTrue(RepoHygiene.ensure(root).isEmpty())
    }

    @Test
    fun `ensure follows a gitdir pointer file`() {
        val worktree = File(tmp.root, "proj").also { it.mkdirs() }
        val realGit = File(tmp.root, "storage/git").also { it.mkdirs() }
        File(worktree, ".git").writeText("gitdir: ${realGit.absolutePath}\n")
        val added = RepoHygiene.ensure(worktree)
        assertTrue(added.isNotEmpty())
        assertTrue(File(realGit, "info/exclude").readText().contains(".codec/"))
    }

    @Test
    fun `ensure does not duplicate a user bang-a-out`() {
        val root = tmp.newFolder("bang")
        File(root, ".git").mkdirs()
        File(root, ".gitignore").writeText("!a.out\n")
        RepoHygiene.ensure(root)
        val content = File(root, ".git/info/exclude").readText()
        assertFalse(content.contains("*.out\n") || content.endsWith("*.out"))
        // But other patterns still land.
        assertTrue(content.contains(".codec/"))
    }

    @Test
    fun `trackedViolations filters to matching paths only`() {
        val tracked = listOf("main.c", "a.out", "bin/menu", "src/app.py", ".codec/project.json")
        val doomed = RepoHygiene.trackedViolations(tracked)
        assertEquals(listOf("a.out", "bin/menu", ".codec/project.json"), doomed)
    }

    @Test
    fun `commitPreview projects staged names and truncates`() {
        val lines = buildList {
            add("## main")
            add("M  src/a.c")
            add("A  src/b.c")
            add("R  old.txt -> new.txt")
            // unstaged / untracked should not appear (x is space or ?)
            add(" M dirty.c")
            add("?? scratch.txt")
            // pad past the limit
            repeat(20) { add("A  extra$it.c") }
        }
        val preview = RepoHygiene.commitPreview(lines, limit = 5)
        assertEquals(5, preview.staged.size)
        assertTrue(preview.truncated)
        assertTrue(preview.total > 5)
        assertTrue(preview.staged.any { it.path.contains("→") || it.path.contains("->") || it.path == "new.txt" || "old.txt" in it.path })
    }

    @Test
    fun `hygieneResult userMessage is singular and plural`() {
        val one = RepoHygiene.HygieneResult(emptyList(), listOf("a.out"))
        assertTrue(one.userMessage()!!.startsWith("Removed 1 "))
        val many = RepoHygiene.HygieneResult(emptyList(), listOf("a.out", "bin/x", ".codec/project.json"))
        assertTrue(many.userMessage()!!.startsWith("Removed 3 "))
        assertNull(RepoHygiene.HygieneResult(emptyList(), emptyList()).userMessage())
    }

    // ---- legacy delegates still work ----

    @Test
    fun `BuildArtifactIgnore and PythonCacheIgnore delegates still function`() {
        @Suppress("DEPRECATION")
        assertTrue(com.codeci.ide.ui.projects.BuildArtifactIgnore.matchesPatterns("a.out"))
        @Suppress("DEPRECATION")
        assertTrue(com.codeci.ide.ui.projects.BuildArtifactIgnore.EXCLUDE_LINES.contains(".codec/"))
        @Suppress("DEPRECATION")
        assertTrue(com.codeci.ide.ui.projects.PythonCacheIgnore.covers(listOf("__pycache__/")))
        @Suppress("DEPRECATION")
        assertTrue(com.codeci.ide.ui.projects.PythonCacheIgnore.shouldAppend(null, null))
    }
}
