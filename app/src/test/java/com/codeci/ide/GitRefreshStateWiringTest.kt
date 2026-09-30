package com.codeci.ide

import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 73.4 — device report: "Auto install not working… I click the
 * initialize [button] [but there is] no installed git". [GitControlViewModel.refresh]
 * used to have one try/catch around the whole body, and the catch
 * unconditionally set `gitInstalled = true` — even when the exception came
 * from acquiring the manager itself ([com.codeci.ide.ui.projects.GitContext.manager],
 * which only reaches shell/credential setup AFTER it already found a `git`
 * binary on disk; a failure there is a shell-prep problem, not evidence git
 * is installed) — and never corrected `isRepo` back from its stale/default
 * value. A beginner whose device threw there never saw the Install Git
 * button at all: the sheet reported `gitInstalled = true, isRepo = false`
 * and jumped straight to "Initialize repository" on top of a project where
 * git was never actually usable.
 *
 * This is a source-scan wiring test (no Robolectric/coroutine harness exists
 * for this ViewModel in this repo) pinning the corrected shape: manager()
 * acquisition is its own try/catch that reports "not installed" on failure,
 * and the git-status catch re-derives `isRepo` instead of leaving it stale.
 */
class GitRefreshStateWiringTest {
    private val vm = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/viewmodels/GitControlViewModel.kt"
    ).readText()
    private val refresh = vm
        .substringAfter("fun refresh(context: Context, projectRoot: File, finalMessage: String? = null) {")
        .substringBefore("\n    fun pull(context: Context, projectRoot: File) {")

    @Test fun `acquiring the manager is its own try-catch, separate from running git`() {
        // The manager() call must be wrapped by a try whose catch runs
        // BEFORE the `if (git == null)` check — not swallowed by the same
        // catch that wraps git.isRepository()/git.status() below.
        val beforeNullCheck = refresh.substringBefore("if (git == null) {")
        assertTrue(beforeNullCheck.contains("val git = try {"))
        assertTrue(beforeNullCheck.contains("gitContext(context).manager()"))
        assertTrue(beforeNullCheck.contains("} catch (e: Exception) {"))
    }

    @Test fun `a failure acquiring the manager is reported as not-installed, not as installed`() {
        val managerCatch = refresh
            .substringAfter("val git = try {")
            .substringBefore("if (git == null) {")
        assertTrue(managerCatch.contains("gitInstalled = false"))
        assertTrue(managerCatch.contains("isRepo = false"))
        // The old bug: this exact catch used to hardcode gitInstalled = true.
        assertFalse(managerCatch.contains("gitInstalled = true"))
    }

    @Test fun `the null-manager branch still reports not-installed (untouched)`() {
        val nullBranch = refresh
            .substringAfter("if (git == null) {")
            .substringBefore("try {\n                val isRepo")
        assertTrue(nullBranch.contains("gitInstalled = false"))
        assertTrue(nullBranch.contains("isRepo = false"))
    }

    @Test fun `the git-status catch re-derives isRepo instead of leaving it stale`() {
        val statusCatch = refresh.substringAfterLast("} catch (e: Exception) {")
        // Re-derives isRepo off a fresh, pure filesystem check — the same
        // one isRepository() itself is (see GitManagerTest), so it cannot
        // fail the same way the exception above already did.
        assertTrue(statusCatch.contains("git.isRepository(projectRoot)"))
        assertTrue(statusCatch.contains("isRepo = stillRepo"))
        assertTrue(statusCatch.contains("gitInstalled = true"))
        // The old bug: this catch used to set gitInstalled = true and never
        // touch isRepo at all — assert the literal old one-liner is gone.
        assertFalse(
            refresh.contains(
                "_state.value = _state.value.copy(\n                    loading = false,\n                    gitInstalled = true,\n                    message = friendly(e, hasToken = false).display()\n                )"
            )
        )
    }

    @Test fun `both catches classify the exception through the same friendly() helper`() {
        val managerCatch = refresh
            .substringAfter("val git = try {")
            .substringBefore("if (git == null) {")
        val statusCatch = refresh.substringAfterLast("} catch (e: Exception) {")
        assertTrue(managerCatch.contains("friendly(e, hasToken = false).display()"))
        assertTrue(statusCatch.contains("friendly(e, hasToken = false).display()"))
    }
}
