package com.codeci.ide

import com.codeci.ide.ui.projects.RepoHygiene
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 39.2 — stageAll order (untrack before add), idempotence, and the
 * abort-on-rmCached-failure rule. Exercises the pure helpers
 * [RepoHygiene.prepareForStage] composes, plus a source-level pin that
 * [GitManager.stageAll] calls them in the right order (host JVM has no
 * guaranteed git binary, so we do not shell out).
 */
class StageAllHygieneTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `prepare sequence is ensure then untrack of matching tracked paths`() {
        val root = tmp.root
        File(root, ".git/info").mkdirs()
        // Simulate a repo that already tracked junk from an earlier round.
        val tracked = listOf("main.c", "a.out", "bin/menu", ".codec/project.json", "README.md")
        val doomed = RepoHygiene.trackedViolations(tracked)
        assertEquals(listOf("a.out", "bin/menu", ".codec/project.json"), doomed)

        // ensure writes the exclude
        val added = RepoHygiene.ensure(root)
        assertTrue(added.isNotEmpty())
        assertTrue(File(root, ".git/info/exclude").isFile)

        // Second ensure is a no-op (idempotent) — stageAll on a clean tree
        // must not keep rewriting the file.
        assertTrue(RepoHygiene.ensure(root).isEmpty())

        // A second pass over the same tracked set still reports the same
        // doomed list (untrack is the caller's job; we just identify).
        assertEquals(doomed, RepoHygiene.trackedViolations(tracked))
        // After a successful untrack the tracked set no longer contains them:
        val after = tracked - doomed.toSet()
        assertTrue(RepoHygiene.trackedViolations(after).isEmpty())
    }

    @Test
    fun `nothing to untrack issues no doomed list`() {
        val tracked = listOf("main.c", "src/app.py", "README.md", "package.json")
        assertTrue(RepoHygiene.trackedViolations(tracked).isEmpty())
    }

    @Test
    fun `user gitignore covering the table means ensure writes nothing`() {
        val root = tmp.newFolder("user-wins")
        File(root, ".git").mkdirs()
        File(root, ".gitignore").writeText(RepoHygiene.PATTERNS.joinToString("\n", postfix = "\n"))
        assertTrue(RepoHygiene.ensure(root).isEmpty())
        assertFalse(File(root, ".git/info/exclude").exists())
    }

    @Test
    fun `hygiene result message only when untrack happened`() {
        val quiet = RepoHygiene.HygieneResult(addedPatterns = listOf("*.out"), untracked = emptyList())
        assertTrue(quiet.changed) // patterns were added
        assertEquals(null, quiet.userMessage()) // but no untrack → no user-facing note

        val loud = RepoHygiene.HygieneResult(emptyList(), listOf("a.out", "bin/x"))
        assertTrue(loud.changed)
        assertTrue(loud.userMessage()!!.contains("2 build outputs"))
    }

    @Test
    fun `stageAll source calls prepareForStage before git add -A`() {
        // stageAll is specified to abort when rmCached throws — we pin the
        // contract here so a future refactor that swallows the error fails
        // a source-level check: GitManager.stageAll must call prepareForStage
        // with strict=true (the default) BEFORE `git add -A`.
        val src = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/projects/GitManager.kt"
        ).readText()
        assertTrue(
            "stageAll must call prepareForStage (strict path)",
            "prepareForStage" in src,
        )
        val stageAllBody = src.substringAfter("fun stageAll").substringBefore("fun stageFile")
        assertTrue("stageAll body missing prepareForStage", "prepareForStage" in stageAllBody)
        assertTrue("stageAll body missing git add -A", "\"add\"" in stageAllBody && "\"-A\"" in stageAllBody)
        assertTrue(
            "prepareForStage must run BEFORE git add -A",
            stageAllBody.indexOf("prepareForStage") < stageAllBody.indexOf("\"add\""),
        )
        assertTrue(
            "strict=true (or default) so rmCached failure aborts",
            "strict = true" in stageAllBody || "prepareForStage(root, this)" in stageAllBody ||
                "prepareForStage(root, this," in stageAllBody,
        )
    }

    @Test
    fun `GitControlViewModel commit path uses stageAll return value`() {
        val src = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/viewmodels/GitControlViewModel.kt"
        ).readText()
        assertTrue("commitAndPush must capture stageAll's HygieneResult", "val hygiene = git.stageAll" in src)
        assertTrue("hygiene note must be surfaced", "hygiene.userMessage()" in src)
        // refresh must NOT call untrackTracked any more (moved to stageAll)
        assertFalse(
            "refresh must not untrack on its own (that lives in stageAll now)",
            "untrackTracked" in src,
        )
    }
}
