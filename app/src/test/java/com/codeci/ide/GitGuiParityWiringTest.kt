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
    private val logDialog = source("screens/GitLogDialog.kt")
    private val remotesDialog = source("screens/GitRemotesDialog.kt")
    private val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()

    @Test fun `the two header menus only appear once git and a repository are both ready`() {
        // Phase 73.5 superseded the 73.3 overflow menu with Spck's two
        // screenshot menus (branch menu + push menu); the readiness gate
        // is unchanged.
        assertTrue(sheet.contains("if (state.gitInstalled && state.isRepo) {"))
        assertTrue(sheet.contains("IconButton(onClick = { showBranchMenu = true })"))
        assertTrue(sheet.contains("IconButton(onClick = { showPushMenu = true })"))
        assertTrue(sheet.contains("DropdownMenu("))
        assertTrue(sheet.contains("expanded = showBranchMenu,"))
        assertTrue(sheet.contains("expanded = showPushMenu,"))
        assertFalse(sheet.contains("showGitMoreMenu"))
    }

    @Test fun `the branch menu offers Branches, Remotes, Log History and Refresh Files`() {
        val menu = sheet.substringAfter("expanded = showBranchMenu,").substringBefore("expanded = showPushMenu,")
        assertTrue(menu.contains("showBranchDialog = true"))
        assertTrue(menu.contains("viewModel.loadRemotes(context, projectRoot)"))
        assertTrue(menu.contains("showRemotesDialog = true"))
        assertTrue(menu.contains("logDialogMode = GitLogDialogMode.VIEW"))
        assertTrue(menu.contains("viewModel.refresh(context, projectRoot)"))
        assertTrue(menu.contains("stringResource(R.string.git_branches_action)"))
        assertTrue(menu.contains("stringResource(R.string.git_refresh_files_action)"))
    }

    @Test fun `the push menu offers Commit All, Revert All, Checkout Commit, Fetch, Pull, Push, Credentials and Provider`() {
        // Bounded by the branch-chip row that follows the header, so the
        // bottom PULL button below cannot satisfy these assertions.
        // Phase 73.7 — Commit All and Push open Spck's dialogs (the menu
        // names the action; the dialog asks the questions), so the pins
        // below assert the dialog opens, not a direct ViewModel call.
        val menu = sheet.substringAfter("expanded = showPushMenu,").substringBefore("// ---- branch chip")
        assertTrue(menu.contains("stringResource(R.string.git_branch_menu_header, pushMenuBranch)"))
        assertTrue(menu.contains("showCommitDialog = true"))
        assertTrue(menu.contains("pendingRevertAll = true"))
        assertTrue(menu.contains("logDialogMode = GitLogDialogMode.CHECKOUT"))
        assertTrue(menu.contains("viewModel.fetch(context, projectRoot)"))
        assertTrue(menu.contains("viewModel.pull(context, projectRoot)"))
        assertTrue(menu.contains("viewModel.loadBranches(context, projectRoot)"))
        assertTrue(menu.contains("showPushDialog = true"))
        // Phase 73.5 — Git Credentials AND Provider both open the new
        // inline dialog (screenshot 2); only the dialog's own Manage link
        // still jumps to Settings.
        assertTrue(menu.contains("stringResource(R.string.git_credentials_action)"))
        assertTrue(menu.contains("stringResource(R.string.git_provider_action)"))
        assertTrue(menu.contains("showCredentialsDialog = true"))
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
        assertTrue(logDialog.contains("AlertDialog("))
        assertTrue(logDialog.contains("onCheckout(entry)"))
        assertTrue(logDialog.contains("stringResource(R.string.git_checkout_commit_confirm, entry.shortSha)"))
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
        assertTrue(remotesDialog.contains("AlertDialog("))
        assertTrue(remotesDialog.contains("pendingRemove = entry.name"))
        assertTrue(remotesDialog.contains("stringResource(R.string.git_remotes_remove_confirm, remoteName)"))
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
        val revertConfirm = strings.substringAfter("name=\"git_revert_all_confirm\"").substringBefore("</string>")
        assertTrue(revertConfirm.contains("cannot be undone"))
    }
}
