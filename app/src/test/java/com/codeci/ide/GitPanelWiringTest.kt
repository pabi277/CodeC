package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 73.7 — git moved from the bottom sheet into the editor's side
 * panel (the owner's Spck screenshots): the Repository slot hosts the
 * full Source Control panel, Commit All and Push open Spck's dialogs,
 * Remotes and Log History became centered dialogs, and the hub ⋮ routes
 * to the editor with the Repository slot selected. Source-scan checks in
 * the established spirit: no Robolectric render exists for the panel, so
 * the wiring is pinned by source text (single-line anchors only).
 */
class GitPanelWiringTest {
    private fun source(path: String) = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/$path").readText()
    private val panel = source("screens/GitControlView.kt")
    private val commitDialog = source("screens/GitCommitDialog.kt")
    private val pushDialog = source("screens/GitPushDialog.kt")
    private val remotesDialog = source("screens/GitRemotesDialog.kt")
    private val logDialog = source("screens/GitLogDialog.kt")
    private val sidePanel = source("components/EditorSidePanel.kt")
    private val editor = source("screens/EditorScreen.kt")
    private val hub = source("screens/FileManagerScreen.kt")
    private val activity = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/MainActivity.kt").readText()
    private val screen = source("navigation/Screen.kt")
    private val viewModel = source("viewmodels/GitControlViewModel.kt")
    private val manager = source("projects/GitManager.kt")
    private val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()

    @Test fun `the side panel hosts the real panel in the Repository slot, with a count badge on the rail`() {
        // The 73.6 slot states are deleted with the slot UI: the editor
        // supplies the content, like the Files slot's tree.
        assertTrue(sidePanel.contains("repositoryContent: @Composable () -> Unit = {}"))
        assertTrue(sidePanel.contains("repositoryBadgeCount: Int = 0"))
        assertTrue(sidePanel.contains("RailPanel.REPOSITORY -> repositoryContent()"))
        // Spck's count badge on the Repository glyph only; zero draws none.
        assertTrue(sidePanel.contains("if (slot == RailPanel.REPOSITORY && repositoryBadgeCount > 0) {"))
        assertTrue(sidePanel.contains("BadgedBox("))
        assertTrue(sidePanel.contains("badge = { Badge { Text(\"\$repositoryBadgeCount\") } }"))
        assertFalse(sidePanel.contains("RepositoryPanelState"))
        assertFalse(sidePanel.contains("RepositorySlot("))
        assertFalse(sidePanel.contains("onOpenSourceControl"))
    }

    @Test fun `the editor hosts the panel in the slot and selects it from the footer and the route`() {
        assertTrue(editor.contains("repositoryContent = {"))
        assertTrue(editor.contains("GitControlPanel("))
        assertTrue(editor.contains("projectRoot = gitRoot"))
        // Scratch mode has no project, so it keeps a one-line empty state.
        assertTrue(editor.contains("stringResource(R.string.panel_repository_no_project)"))
        assertTrue(editor.contains("repositoryBadgeCount = gitChangeCount"))
        // The drawer's Source Control row selects the slot (the sheet door
        // and the 73.6 drawer engine are both deleted with it).
        assertTrue(editor.contains("sidePanel = RailPanel.REPOSITORY"))
        assertTrue(editor.contains("uiScope.launch { drawerState.open() }"))
        assertFalse(editor.contains("gitSheetRoot"))
        assertFalse(editor.contains("GitControlSheet("))
        assertFalse(editor.contains("runDrawerInit"))
        assertFalse(editor.contains("showGitInstallPrompt"))
        // The route hand-off applies once per session, PROJECT opens only.
        assertTrue(editor.contains("panel: String? = null"))
        assertTrue(editor.contains("if (panel == RailPanel.REPOSITORY.id && !singleFile && projectName != null) {"))
        assertTrue(editor.contains("drawerState.open()"))
    }

    @Test fun `no bottom sheet remains anywhere in git`() {
        for (src in listOf(panel, remotesDialog, logDialog, commitDialog, pushDialog)) {
            assertFalse(src.contains("ModalBottomSheet"))
        }
        assertFalse(panel.contains("fun GitControlSheet("))
        // The inline commit box + COMMIT & PUSH button are deleted with
        // the sheet (the Commit dialog asks the questions now).
        assertFalse(panel.contains("commitMessage"))
        assertFalse(panel.contains("commitAndPush"))
        assertFalse(panel.contains("git_commit_push"))
        assertFalse(panel.contains("git_commit_message_placeholder"))
    }

    @Test fun `the hub Source Control action routes to the editor panel`() {
        assertTrue(hub.contains("onProjectGitPanel: (projectName: String, relativePath: String) -> Unit"))
        assertTrue(hub.contains("fun openGitPanel(project: ProjectInfo?)"))
        assertTrue(hub.contains("openGitPanel(activeProject)"))
        assertTrue(hub.contains("HubCardAction.SOURCE_CONTROL -> openGitPanel(project)"))
        assertFalse(hub.contains("gitSheetProject"))
        assertFalse(hub.contains("GitControlSheet("))
        // The activity carries the panel hand-off on the editor route.
        assertTrue(activity.contains("navArgument(\"panel\") { nullable = true }"))
        assertTrue(activity.contains("panel = panelArg,"))
        assertTrue(activity.contains("onProjectGitPanel = { projectName, path ->"))
        assertTrue(activity.contains("panel = \"repository\""))
        // The route shape only gains the optional arg; every pre-73.7
        // createRoute call still builds the same string.
        assertTrue(screen.contains("&panel={panel}"))
        assertTrue(screen.contains("panel: String? = null"))
    }

    @Test fun `the push dialog names its remote and branch, defaulting to current behaviour`() {
        assertTrue(pushDialog.contains("remotes: List<String>,"))
        assertTrue(pushDialog.contains("branches: List<String>,"))
        assertTrue(pushDialog.contains("var remote by remember(remotes)"))
        assertTrue(pushDialog.contains("var branch by remember(branches, currentBranch)"))
        assertTrue(pushDialog.contains("stringResource(R.string.git_push_dialog_title)"))
        assertTrue(pushDialog.contains("stringResource(R.string.git_push_dialog_remotes)"))
        assertTrue(pushDialog.contains("stringResource(R.string.git_push_dialog_branches)"))
        // No remote at all: no dead Ok — the Remotes screen is offered.
        assertTrue(pushDialog.contains("stringResource(R.string.git_push_no_remote)"))
        assertTrue(pushDialog.contains("onClick = onAddRemote,"))
        assertTrue(pushDialog.contains("onClick = { onConfirm(remote, branch) }"))
        assertTrue(pushDialog.contains("enabled = remote.isNotBlank() && branch.isNotBlank() && !busy"))
        // The panel feeds names (local branches only) and pushes the pair.
        assertTrue(panel.contains("remotes = state.remotes.map { it.name }"))
        assertTrue(panel.contains("branches = state.branches?.local?.map { it.name }.orEmpty(),"))
        assertTrue(panel.contains("onConfirm = { remote, branch ->"))
        assertTrue(panel.contains("viewModel.push(context, projectRoot, remote, branch)"))
        // The ViewModel defaults both to today's behaviour.
        val fn = viewModel.substringAfter("fun push(").substringBefore("fun dismissPushError()")
        assertTrue(fn.contains("remote: String? = null, branch: String? = null"))
        assertTrue(fn.contains("val branchLabel = branch?.takeIf { it.isNotBlank() }"))
        assertTrue(fn.contains("git.pushCapturing(projectRoot, branchName = branchLabel, remoteName = remote)"))
        // The engine only lets a non-blank dialog name win; otherwise the
        // first remote, exactly as before.
        assertTrue(manager.contains("remoteName: String? = null"))
        assertTrue(manager.contains("remoteName?.trim()?.takeIf { it.isNotEmpty() } ?: firstRemote(root)"))
    }

    @Test fun `the commit dialog stages-all by default and saves its author identity`() {
        assertTrue(commitDialog.contains("stringResource(R.string.git_commit_all_action)"))
        assertTrue(commitDialog.contains("stringResource(R.string.git_commit_dialog_message_label)"))
        assertTrue(commitDialog.contains("stringResource(R.string.git_credentials_commit_name)"))
        assertTrue(commitDialog.contains("stringResource(R.string.git_credentials_email)"))
        assertTrue(commitDialog.contains("stringResource(R.string.git_commit_stage_all)"))
        assertTrue(commitDialog.contains("var stageAll by remember { mutableStateOf(true) }"))
        assertTrue(commitDialog.contains("checked = stageAll,"))
        // A third editor on the same store (after Settings and the Git
        // Credentials dialog): prefill on open, save back when changed.
        assertTrue(commitDialog.contains("GitCredentialsStore(context.applicationContext)"))
        assertTrue(commitDialog.contains("authorName = stored.authorName"))
        assertTrue(commitDialog.contains("store.save(stored.token, stored.username, authorName, authorEmail)"))
        assertTrue(commitDialog.contains("onConfirm(message.trim(), stageAll)"))
        assertTrue(commitDialog.contains("clickable(onClick = onOpenCredentials)"))
        assertTrue(commitDialog.contains("stringResource(R.string.git_dialog_ok)"))
        // The panel hosts it with the same guards the menu item has.
        assertTrue(panel.contains("GitCommitDialog("))
        assertTrue(panel.contains("state.status?.files.orEmpty().none { it.isConflict }"))
        assertTrue(panel.contains("onConfirm = { message, stageAll ->"))
        assertTrue(panel.contains("viewModel.commitOnly(context, projectRoot, message, stageAll)"))
    }

    @Test fun `remotes and log history are centered dialogs with the same content`() {
        assertTrue(remotesDialog.contains("fun GitRemotesDialog("))
        assertTrue(remotesDialog.contains("stringResource(R.string.git_remotes_search_hint)"))
        assertTrue(remotesDialog.contains("stringResource(R.string.git_remotes_copy_url)"))
        assertTrue(remotesDialog.contains("stringResource(R.string.git_remotes_delete)"))
        assertTrue(remotesDialog.contains("stringResource(R.string.git_remotes_no_match)"))
        assertTrue(remotesDialog.contains("private fun NewRemoteDialog("))
        assertTrue(remotesDialog.contains("stringResource(R.string.git_remotes_new_title)"))
        assertTrue(remotesDialog.contains("stringResource(R.string.git_remotes_remove_confirm, remoteName)"))
        assertTrue(logDialog.contains("fun GitLogDialog("))
        assertTrue(logDialog.contains("enum class GitLogDialogMode { VIEW, CHECKOUT }"))
        assertTrue(logDialog.contains("stringResource(R.string.git_checkout_commit_confirm, entry.shortSha)"))
        assertTrue(panel.contains("GitRemotesDialog("))
        assertTrue(panel.contains("GitLogDialog("))
        assertTrue(panel.contains("BranchSwitchDialog("))
    }

    @Test fun `new user-facing strings exist for the panel move and the dialogs`() {
        for (name in listOf(
            "panel_repository_no_project", "git_dialog_ok",
            "git_commit_dialog_message_label", "git_commit_stage_all",
            "git_push_dialog_title", "git_push_dialog_remotes", "git_push_dialog_branches",
            "git_push_no_remote", "git_remotes_search_hint", "git_remotes_new_title",
            "git_remotes_copy_url", "git_remotes_delete", "git_remotes_no_match"
        )) {
            assertTrue(name, strings.contains("name=\"$name\""))
        }
        // The deleted sheet's strings are deleted with it — no dead text.
        for (name in listOf(
            "git_commit_push", "git_commit_push_to", "git_commit_message_placeholder",
            "git_commit_blocked", "panel_repository_empty", "panel_repository_init",
            "panel_repository_no_branch", "panel_repository_changes", "panel_repository_open",
            "panel_repository_no_git", "panel_repository_install_git"
        )) {
            assertFalse(name, strings.contains("name=\"$name\""))
        }
    }
}
