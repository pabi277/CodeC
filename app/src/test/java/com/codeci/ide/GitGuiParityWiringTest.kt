package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 73.3 — full GUI parity for the git menu (Fetch, Log History,
 * Checkout Commit, Revert All, a general Remotes screen, plus a real
 * "Initialize repository" button) so nothing but installing git itself
 * still requires the terminal. Source-scan checks in the same spirit as
 * [GitInstallWiringTest]/[GitDiscardWiringTest]: no Robolectric Compose
 * render exists for this sheet, so the wiring is pinned by source text
 * (single-line anchors only — no multi-line literal matching, which would
 * be a whitespace trap on every future reformat).
 */
class GitGuiParityWiringTest {
    private fun source(path: String) = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/$path").readText()
    private val sheet = source("screens/GitControlView.kt")
    private val viewModel = source("viewmodels/GitControlViewModel.kt")
    private val manager = source("projects/GitManager.kt")
    private val logSheet = source("screens/GitLogSheet.kt")
    private val remotesSheet = source("screens/GitRemotesSheet.kt")
    private val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()

    @Test fun `the overflow menu only appears once git and a repository are both ready`() {
        assertTrue(sheet.contains("if (state.gitInstalled && state.isRepo) {"))
        assertTrue(sheet.contains("IconButton(onClick = { showGitMoreMenu = true })"))
        assertTrue(sheet.contains("DropdownMenu("))
        assertTrue(sheet.contains("expanded = showGitMoreMenu,"))
    }

    @Test fun `the overflow menu offers Fetch, Log History, Checkout Commit, Remotes, Credentials and Revert All`() {
        assertTrue(sheet.contains("viewModel.fetch(context, projectRoot)"))
        assertTrue(sheet.contains("logSheetMode = GitLogSheetMode.VIEW"))
        assertTrue(sheet.contains("logSheetMode = GitLogSheetMode.CHECKOUT"))
        assertTrue(sheet.contains("viewModel.loadRemotes(context, projectRoot)"))
        assertTrue(sheet.contains("showRemotesSheet = true"))
        assertTrue(sheet.contains("pendingRevertAll = true"))
        // Git Credentials is a shortcut into the existing Settings screen —
        // no second credentials editor was built.
        assertTrue(sheet.contains("stringResource(R.string.git_credentials_action)"))
        assertTrue(sheet.contains("onOpenSettings()"))
    }

    @Test fun `Revert All is disabled with nothing to discard or before the first commit`() {
        assertTrue(sheet.contains("enabled = state.status?.files?.isNotEmpty() == true &&"))
        assertTrue(sheet.contains("state.status?.noCommits != true"))
    }

    @Test fun `Revert All confirms before running, in a non-dismissable AlertDialog`() {
        assertTrue(sheet.contains("if (pendingRevertAll) {"))
        assertTrue(sheet.contains("viewModel.revertAll(context, projectRoot)"))
        // Appears at least twice: the per-file discard dialog already used
        // this pattern, Revert All's new one must too.
        assertTrue(sheet.contains("dismissOnBackPress = false"))
        assertTrue(sheet.contains("dismissOnClickOutside = false"))
        assertTrue(sheet.contains("stringResource(R.string.git_revert_all_title)"))
        assertTrue(sheet.contains("stringResource(R.string.git_revert_all_confirm)"))
    }

    @Test fun `checking out an old commit confirms first and clears state on dismiss`() {
        assertTrue(logSheet.contains("AlertDialog("))
        assertTrue(logSheet.contains("onCheckout(entry)"))
        assertTrue(logSheet.contains("stringResource(R.string.git_checkout_commit_confirm, entry.shortSha)"))
        assertTrue(sheet.contains("viewModel.clearCommits()"))
        assertTrue(sheet.contains("viewModel.checkoutCommit(context, projectRoot, entry)"))
    }

    @Test fun `a checked-out commit (detached HEAD) is shown, not silently dropped`() {
        // Before Phase 73.3, `state.status?.branch?.let { ... }` made the
        // whole chip vanish once HEAD detached; a `when` now covers both.
        assertTrue(sheet.contains("state.status?.detached == true ->"))
        assertTrue(sheet.contains("stringResource(R.string.git_detached_head)"))
        assertFalse(sheet.contains("state.status?.branch?.let { branch ->"))
    }

    @Test fun `the not-a-repo screen inits a real repository, not just text`() {
        assertTrue(sheet.contains("GitInitGuidance("))
        assertTrue(sheet.contains("onInit = { viewModel.initRepo(context, projectRoot) }"))
        val guidanceIndex = sheet.indexOf("!state.isRepo -> {")
        val guidanceCallIndex = sheet.indexOf("GitInitGuidance(", guidanceIndex)
        val elseIndex = sheet.indexOf("else -> {", guidanceIndex)
        assertTrue("GitInitGuidance must be called inside the !state.isRepo branch", guidanceCallIndex in guidanceIndex until elseIndex)
    }

    @Test fun `remotes can be added and removed, with a confirm before removing`() {
        assertTrue(sheet.contains("onAdd = { name, url -> viewModel.addRemote(context, projectRoot, name, url) }"))
        assertTrue(sheet.contains("onRemove = { name -> viewModel.removeRemote(context, projectRoot, name) }"))
        assertTrue(remotesSheet.contains("AlertDialog("))
        assertTrue(remotesSheet.contains("pendingRemove = entry.name"))
        assertTrue(remotesSheet.contains("stringResource(R.string.git_remotes_remove_confirm, remoteName)"))
    }

    @Test fun `every new ViewModel action goes through runGitOperation`() {
        for (fn in listOf("fun initRepo(", "fun fetch(", "fun revertAll(", "fun checkoutCommit(")) {
            val start = viewModel.indexOf(fn)
            assertTrue(fn, start >= 0)
            val nextBrace = viewModel.indexOf("runGitOperation(", start)
            val nextFun = viewModel.indexOf("\n    fun ", start + 1)
            assertTrue(fn, nextBrace in start until nextFun)
        }
    }

    @Test fun `checkoutCommit's result message explains detached HEAD in plain words`() {
        assertTrue(viewModel.contains("not on a branch"))
        assertTrue(viewModel.contains("Switch branches anytime"))
    }

    @Test fun `revertAllChanges never runs git clean and leaves untracked files alone`() {
        assertTrue(manager.contains("fun revertAllChanges(root: File) {"))
        assertTrue(manager.contains("listOf(\"reset\", \"--hard\", \"HEAD\")"))
        assertFalse(manager.contains("\"clean\""))
    }

    @Test fun `checkoutCommit and removeRemote validate their input before touching git`() {
        assertTrue(manager.contains("private val FULL_SHA = Regex(\"^[0-9a-f]{40}\$\")"))
        assertTrue(manager.contains("require(FULL_SHA.matches(safe))"))
        assertTrue(manager.contains("fun isSafeRemoteName(name: String): Boolean"))
        assertTrue(manager.contains("require(SAFE_REMOTE_NAME.matches(remote)) { \"Invalid remote name\" }"))
    }

    @Test fun `new user-facing strings exist for every parity action`() {
        for (name in listOf(
            "git_init_action", "git_init_busy", "git_more_actions", "git_fetch_action",
            "git_log_history_action", "git_checkout_commit_action", "git_checkout_commit_title",
            "git_checkout_commit_confirm", "git_log_empty", "git_remotes_action",
            "git_remotes_add_action", "git_remotes_remove_title", "git_remotes_remove_confirm",
            "git_revert_all_action", "git_revert_all_title", "git_revert_all_confirm"
        )) {
            assertTrue(name, strings.contains("name=\"$name\""))
        }
        // The destructive actions both spell out "cannot be undone" in plain words.
        assertTrue(strings.substringAfter("name=\"git_revert_all_confirm\"").contains("cannot be undone"))
    }
}
