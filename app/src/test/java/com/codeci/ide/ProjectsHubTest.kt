package com.codeci.ide

import com.codeci.ide.ui.projects.AutoRunPlan
import com.codeci.ide.ui.projects.ProjectConfig
import com.codeci.ide.ui.projects.ProjectHubEntry
import com.codeci.ide.ui.projects.ProjectHubFilter
import com.codeci.ide.ui.projects.ProjectHubKind
import com.codeci.ide.ui.projects.ProjectHubStats
import com.codeci.ide.ui.projects.ProjectsHub
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 15 — host-JVM tests for the Projects Hub engine: type/kind mapping,
 * the chip filters, name search, subtitle + relative-age formatting, the
 * cheap git metadata parsers (`.git/HEAD`, `ls-remote`, `.git/config`), the
 * clone-dialog name dedup, and the branch-name gate. Everything here mirrors
 * what the Compose screen renders, so a green CI run proves the hub's logic
 * before any device round.
 */
class ProjectsHubTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    private fun entry(
        name: String,
        kind: ProjectHubKind = ProjectHubKind.GENERIC,
        isGit: Boolean = false,
        branch: String? = null,
        fileCount: Int = 3,
        lastModified: Long = 0L,
        hasChanges: Boolean? = null
    ) = ProjectHubEntry(name, kind, isGit, branch, fileCount, lastModified, hasChanges)

    // ---- kind mapping ----

    @Test
    fun `declared config types map to hub kinds`() {
        assertEquals(ProjectHubKind.C, ProjectsHub.kindOfConfigType("c"))
        assertEquals(ProjectHubKind.PY, ProjectsHub.kindOfConfigType("python"))
        assertEquals(ProjectHubKind.WEB_STATIC, ProjectsHub.kindOfConfigType("web"))
        assertEquals(ProjectHubKind.WEB_STATIC, ProjectsHub.kindOfConfigType("static-web"))
        assertEquals(ProjectHubKind.PY_SERVER, ProjectsHub.kindOfConfigType("python-flask"))
        assertEquals(ProjectHubKind.PY_SERVER, ProjectsHub.kindOfConfigType("Python-FastAPI"))
        assertEquals(ProjectHubKind.C_SERVER, ProjectsHub.kindOfConfigType("c-microservice"))
        assertEquals(ProjectHubKind.GENERIC, ProjectsHub.kindOfConfigType("auto"))
        assertEquals(ProjectHubKind.GENERIC, ProjectsHub.kindOfConfigType("rust"))
    }

    @Test
    fun `auto projects adopt the run detector plan`() {
        val autoConfig = ProjectConfig(name = "x", type = "auto")
        assertEquals(
            ProjectHubKind.PY_SERVER,
            ProjectsHub.kindFor(autoConfig, AutoRunPlan.Server("python-flask"), autoDetected = true)
        )
        assertEquals(
            ProjectHubKind.C_SERVER,
            ProjectsHub.kindFor(autoConfig, AutoRunPlan.Server("c-microservice"), autoDetected = true)
        )
        assertEquals(
            ProjectHubKind.WEB_STATIC,
            ProjectsHub.kindFor(autoConfig, AutoRunPlan.Web("index.html"), autoDetected = true)
        )
        assertEquals(
            ProjectHubKind.PY,
            ProjectsHub.kindFor(autoConfig, AutoRunPlan.Project("python"), autoDetected = true)
        )
        assertEquals(
            ProjectHubKind.GENERIC,
            ProjectsHub.kindFor(autoConfig, AutoRunPlan.None("no files"), autoDetected = true)
        )
        // Without a plan the auto project still renders (empty GENERIC card).
        assertEquals(ProjectHubKind.GENERIC, ProjectsHub.kindFor(autoConfig, null, autoDetected = true))
        // A declared type is trusted unless flagged stale (autoDetected).
        val cConfig = ProjectConfig(name = "x", type = "c")
        assertEquals(ProjectHubKind.C, ProjectsHub.kindFor(cConfig, AutoRunPlan.Server("python-flask")))
        assertEquals(ProjectHubKind.C, ProjectsHub.kindFor(cConfig, null))
    }

    @Test
    fun `stale c placeholders re-detect - the cloned-repo mislabel fix`() {
        val cConfig = ProjectConfig(name = "x", type = "c")
        // Entry present → declared C stands even without any plan.
        assertTrue(ProjectsHub.shouldAutoDetect("auto", true))
        assertTrue(ProjectsHub.shouldAutoDetect("C", false))
        assertFalse(ProjectsHub.shouldAutoDetect("c", true))
        assertFalse(ProjectsHub.shouldAutoDetect("python", false))
        assertFalse(ProjectsHub.shouldAutoDetect("web", false))

        // A "c" placeholder over an html repo must show Web, not C…
        assertEquals(
            ProjectHubKind.WEB_STATIC,
            ProjectsHub.kindFor(cConfig, AutoRunPlan.Web("index.html"), autoDetected = true)
        )
        // …and an unrecognizable repo must degrade to generic gray, never lie C.
        assertEquals(
            ProjectHubKind.GENERIC,
            ProjectsHub.kindFor(cConfig, AutoRunPlan.None("no files"), autoDetected = true)
        )
        // A genuine C project (entry exists → not autoDetected) is untouched.
        assertEquals(ProjectHubKind.C, ProjectsHub.kindFor(cConfig, null, autoDetected = false))
        // Auto projects keep answering to the detector.
        val autoConfig = ProjectConfig(name = "x", type = "auto")
        assertEquals(
            ProjectHubKind.PY_SERVER,
            ProjectsHub.kindFor(autoConfig, AutoRunPlan.Server("python-flask"), autoDetected = true)
        )
    }

    // ---- filter membership ----

    @Test
    fun `chip membership follows kind and git state`() {
        val flask = entry("demo_flask", ProjectHubKind.PY_SERVER, isGit = true, branch = "main")
        assertTrue(flask.filters.containsAll(setOf(ProjectHubFilter.ALL, ProjectHubFilter.GIT, ProjectHubFilter.PYTHON, ProjectHubFilter.WEB)))

        val cProgram = entry("hello-c", ProjectHubKind.C, isGit = true)
        assertTrue(cProgram.filters.containsAll(setOf(ProjectHubFilter.ALL, ProjectHubFilter.GIT, ProjectHubFilter.C)))
        assertFalse(cProgram.filters.contains(ProjectHubFilter.WEB))

        val staticSite = entry("site", ProjectHubKind.WEB_STATIC)
        assertEquals(setOf(ProjectHubFilter.ALL, ProjectHubFilter.WEB), staticSite.filters)

        val plain = entry("scratch", ProjectHubKind.GENERIC)
        assertEquals(setOf(ProjectHubFilter.ALL), plain.filters)

        val microservice = entry("api", ProjectHubKind.C_SERVER)
        assertTrue(microservice.filters.containsAll(setOf(ProjectHubFilter.C, ProjectHubFilter.WEB)))
    }

    @Test
    fun `filterEntries combines chip filter and case-insensitive name search`() {
        val entries = listOf(
            entry("demo_flask", ProjectHubKind.PY_SERVER, isGit = true),
            entry("hello-c", ProjectHubKind.C, isGit = true),
            entry("Portfolio-Site", ProjectHubKind.WEB_STATIC),
            entry("notes", ProjectHubKind.GENERIC)
        )

        assertEquals(4, ProjectsHub.filterEntries(entries, ProjectHubFilter.ALL, "").size)
        assertEquals(
            listOf("demo_flask", "hello-c"),
            ProjectsHub.filterEntries(entries, ProjectHubFilter.GIT, null).map { it.name }
        )
        assertEquals(
            listOf("demo_flask"),
            ProjectsHub.filterEntries(entries, ProjectHubFilter.PYTHON, null).map { it.name }
        )
        assertEquals(
            listOf("demo_flask", "Portfolio-Site"),
            ProjectsHub.filterEntries(entries, ProjectHubFilter.WEB, null).map { it.name }
        )
        assertEquals(
            listOf("Portfolio-Site"),
            ProjectsHub.filterEntries(entries, ProjectHubFilter.ALL, "PORT").map { it.name }
        )
        assertEquals(
            emptyList<String>(),
            ProjectsHub.filterEntries(entries, ProjectHubFilter.C, "flask").map { it.name }
        )
        assertEquals(
            listOf("demo_flask"),
            ProjectsHub.filterEntries(entries, ProjectHubFilter.GIT, " demo_ ").map { it.name }
        )
    }

    // ---- subtitle & relative age ----

    @Test
    fun `subtitle shows branch only for git projects and counts files singular`() {
        val now = 1_700_000_000_000L
        val git = entry("p", ProjectHubKind.C, isGit = true, branch = "main", fileCount = 1, lastModified = now - 2 * day)
        assertEquals(listOf("main", "1 file", "2 days ago"), ProjectsHub.subtitleSegments(git, now))

        val plain = entry("p", ProjectHubKind.C, isGit = false, fileCount = 8, lastModified = now - hour)
        assertEquals(listOf("8 files", "1 hour ago"), ProjectsHub.subtitleSegments(plain, now))

        val detached = entry("p", ProjectHubKind.C, isGit = true, branch = "HEAD", fileCount = 0, lastModified = now - minute)
        assertEquals(listOf("HEAD", "0 files", "1 min ago"), ProjectsHub.subtitleSegments(detached, now))

        // Zero timestamp (brand-new/undetermined) drops the age segment entirely.
        val fresh = entry("p", ProjectHubKind.GENERIC, fileCount = 0, lastModified = 0L)
        assertEquals(listOf("0 files"), ProjectsHub.subtitleSegments(fresh, now))
    }

    @Test
    fun `relativeAge buckets are human and monotonic`() {
        val now = 1_700_000_000_000L
        assertEquals("just now", ProjectsHub.relativeAge(now - 30_000L, now))
        assertEquals("just now", ProjectsHub.relativeAge(now + 5 * day, now)) // clock skew clamps
        assertEquals("59 min ago", ProjectsHub.relativeAge(now - 59 * minute, now))
        assertEquals("1 hour ago", ProjectsHub.relativeAge(now - 61 * minute, now))
        assertEquals("3 hours ago", ProjectsHub.relativeAge(now - 3 * hour, now))
        assertEquals("yesterday", ProjectsHub.relativeAge(now - 25 * hour, now))
        assertEquals("6 days ago", ProjectsHub.relativeAge(now - 6 * day, now))
        assertEquals("1 week ago", ProjectsHub.relativeAge(now - 8 * day, now))
        assertEquals("3 weeks ago", ProjectsHub.relativeAge(now - 22 * day, now))
        assertEquals("1 month ago", ProjectsHub.relativeAge(now - 40 * day, now))
        assertEquals("1 year ago", ProjectsHub.relativeAge(now - 400 * day, now))
        assertEquals("2 years ago", ProjectsHub.relativeAge(now - 800 * day, now))
        assertEquals("", ProjectsHub.relativeAge(0L, now))
    }

    // ---- cheap git metadata readers ----

    @Test
    fun `branchFromHeadFile understands refs, detached shas, and garbage`() {
        assertEquals("main", ProjectsHub.branchFromHeadFile("ref: refs/heads/main\n"))
        assertEquals("feature/nested", ProjectsHub.branchFromHeadFile("ref: refs/heads/feature/nested"))
        assertEquals("HEAD", ProjectsHub.branchFromHeadFile("0".repeat(40)))
        assertEquals("HEAD", ProjectsHub.branchFromHeadFile("1a2b3c4"))
        assertNull(ProjectsHub.branchFromHeadFile(null))
        assertNull(ProjectsHub.branchFromHeadFile("   "))
        assertNull(ProjectsHub.branchFromHeadFile("not a ref at all"))
    }

    @Test
    fun `branchNamesFromLsRemote keeps heads and drops tags`() {
        val lines = listOf(
            "aaaa1111\trefs/heads/main",
            "bbbb2222\trefs/heads/dev",
            "cccc3333\trefs/tags/v1.0",
            "dddd4444\trefs/heads/release/2.0",
            "",
            "   ",
            "no-tab-here"
        )
        assertEquals(listOf("main", "dev", "release/2.0"), ProjectsHub.branchNamesFromLsRemote(lines))
        assertEquals(emptyList<String>(), ProjectsHub.branchNamesFromLsRemote(emptyList()))
    }

    @Test
    fun `remoteUrlFromConfig prefers origin and falls back to the first remote`() {
        val config = """
            [core]
            repositoryformatversion = 0
            [remote "origin"]
            url = https://github.com/octocat/Spoon-Knife.git
            fetch = +refs/heads/*:refs/remotes/origin/*
            [branch "main"]
            remote = origin
        """.trimIndent()
        assertEquals(
            "https://github.com/octocat/Spoon-Knife.git",
            ProjectsHub.remoteUrlFromConfig(config)
        )

        val noOrigin = """
            [remote "upstream"]
            url = https://example.com/u/r.git
        """.trimIndent()
        assertEquals("https://example.com/u/r.git", ProjectsHub.remoteUrlFromConfig(noOrigin))

        assertNull(ProjectsHub.remoteUrlFromConfig("[core]\n\tbare = false"))
        assertNull(ProjectsHub.remoteUrlFromConfig(null))
        // A non-url key inside the remote section must not leak out.
        val oddKey = """
            [remote "origin"]
            proxy = socks5://host
        """.trimIndent()
        assertNull(ProjectsHub.remoteUrlFromConfig(oddKey))
    }

    // ---- clone dialog helpers ----

    @Test
    fun `uniqueProjectName dedups like the Phase 8-13 imports`() {
        assertEquals("repo", ProjectsHub.uniqueProjectName("repo", emptySet()))
        assertEquals("repo_2", ProjectsHub.uniqueProjectName("repo", setOf("repo")))
        assertEquals("repo_4", ProjectsHub.uniqueProjectName("repo", setOf("repo", "repo_2", "repo_3")))
        // Spaces are fine for a project folder; path escapes fall back.
        assertEquals("My Repo", ProjectsHub.uniqueProjectName("  My Repo  ", setOf("other")))
        assertEquals("project", ProjectsHub.uniqueProjectName("../escape me", setOf()))
        assertEquals("project", ProjectsHub.uniqueProjectName("a/b", setOf()))
        assertEquals("project_2", ProjectsHub.uniqueProjectName("a/b", setOf("project")))
    }

    @Test
    fun `isValidBranchName gates what may reach git argv`() {
        assertTrue(ProjectsHub.isValidBranchName("main"))
        assertTrue(ProjectsHub.isValidBranchName("feature/nested-1.2"))
        assertTrue(ProjectsHub.isValidBranchName("release_x+2"))
        assertFalse(ProjectsHub.isValidBranchName(""))
        assertFalse(ProjectsHub.isValidBranchName("   "))
        assertFalse(ProjectsHub.isValidBranchName("--upload-pack=evil"))
        assertFalse(ProjectsHub.isValidBranchName("main~2"))
        assertFalse(ProjectsHub.isValidBranchName("a b"))
        assertFalse(ProjectsHub.isValidBranchName("bad..name"))
        assertFalse(ProjectsHub.isValidBranchName("double//slash"))
        assertFalse(ProjectsHub.isValidBranchName("trailing/"))
        assertFalse(ProjectsHub.isValidBranchName(".leading-dot"))
        assertFalse(ProjectsHub.isValidBranchName("branch.lock"))
        assertFalse(ProjectsHub.isValidBranchName("refs/heads/main"))
        assertFalse(ProjectsHub.isValidBranchName("x".repeat(201)))
    }

    @Test
    fun `hasChangesFromPorcelain ignores branch lines`() {
        assertFalse(ProjectsHub.hasChangesFromPorcelain(listOf("## main...origin/main [ahead 2]")))
        assertFalse(ProjectsHub.hasChangesFromPorcelain(emptyList()))
        assertTrue(ProjectsHub.hasChangesFromPorcelain(listOf("## main", " M main.c")))
        assertTrue(ProjectsHub.hasChangesFromPorcelain(listOf("?? notes.txt")))
    }

    // ---- disk scan ----

    @Test
    fun `scan counts project files and skips git and codec metadata`() {
        val root = File.createTempFile("hub-scan", "").apply { delete(); mkdirs() }
        try {
            File(root, "main.c").writeText("int main(){}")
            File(root, "src").mkdirs()
            File(root, "src/util.c").writeText("x")
            File(root, ".git").mkdirs()
            File(root, ".git/HEAD").writeText("ref: refs/heads/main")
            File(root, ".codec").mkdirs()
            File(root, ".codec/project.json").writeText("{}")

            val result = ProjectHubStats.scan(root)
            assertEquals(2, result.fileCount)
            assertTrue(result.lastModified > 0L)

            // Newer file wins the timestamp race.
            val future = System.currentTimeMillis() + 60_000L
            File(root, "main.c").setLastModified(future)
            assertEquals(future, ProjectHubStats.scan(root).lastModified)

            val empty = File(root, "empty").apply { mkdirs() }
            val emptyScan = ProjectHubStats.scan(empty)
            assertEquals(0, emptyScan.fileCount)

            assertEquals(0, ProjectHubStats.scan(File(root, "missing")).fileCount)
        } finally {
            root.deleteRecursively()
        }
    }
}
