package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 73.5 — the Source Control panel re-skinned Spck-exact (the owner's
 * four screenshots): a REPOSITORY header with search / branch-menu /
 * push-menu icons, a collapsible UNSTAGED section with a count badge, an
 * install status bar (no redirect to Terminal), an inline Git Credentials
 * dialog (Settings keeps its own editor on the same store), and a
 * Commit All action that commits without pushing. Source-scan checks in
 * the same spirit as [GitGuiParityWiringTest]: no Robolectric Compose
 * render exists for this panel, so the wiring is pinned by source text
 * (single-line anchors only — no multi-line literal matching, which would
 * be a whitespace trap on every future reformat).
 *
 * Phase 73.7 — the sheet that hosted this panel is deleted (the drawer
 * hosts [GitControlPanel] directly); Commit All gained the dialog's Stage
 * All toggle and author fields, and Manage lost its sheet dismiss.
 */
class GitSpckPanelWiringTest {
    private fun source(path: String) = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/$path").readText()
    private val sheet = source("screens/GitControlView.kt")
    private val dialog = source("screens/GitCredentialsDialog.kt")
    private val viewModel = source("viewmodels/GitControlViewModel.kt")
    private val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()

    @Test fun `the header is REPOSITORY with search, branch-menu and push-menu icons`() {
        assertTrue(sheet.contains("stringResource(R.string.git_repository_title)"))
        assertTrue(sheet.contains("stringResource(R.string.git_search_changes)"))
        assertTrue(sheet.contains("stringResource(R.string.git_branch_menu_description)"))
        assertTrue(sheet.contains("stringResource(R.string.git_push_menu_description)"))
        // The branch chip keeps its own row below the header, so 73.3's
        // detached-HEAD chip stays visible without opening a menu.
        val chipRow = sheet.substringAfter("// ---- branch chip").substringBefore("if (searchingGit && state.gitInstalled && state.isRepo) {")
        assertTrue(chipRow.contains("state.status?.detached == true ->"))
    }

    @Test fun `the header search filters the UNSTAGED list by path only`() {
        assertTrue(sheet.contains("searchingGit = !searchingGit"))
        assertTrue(sheet.contains("it.path.contains(gitSearchQuery.trim(), ignoreCase = true)"))
        assertTrue(sheet.contains("itemsIndexed(visibleOthers, key = { _, change -> change.path })"))
        assertTrue(sheet.contains("stringResource(R.string.git_no_matching_changes)"))
    }

    @Test fun `the changes list is a collapsible UNSTAGED section with a count badge`() {
        assertTrue(sheet.contains("stringResource(R.string.git_unstaged_header)"))
        assertTrue(sheet.contains("unstagedExpanded = !unstagedExpanded"))
        assertTrue(sheet.contains("if (unstagedExpanded && files.isEmpty()) {"))
        assertTrue(sheet.contains("} else if (unstagedExpanded) {"))
        // Spck's "+" stage-all button is deliberately NOT here: 73.1
        // removed staging controls because commit always runs `add -A`,
        // so a stage button would be a no-op dressed as an action.
        assertFalse(sheet.contains("git_stage_all"))
    }

    @Test fun `Commit All commits locally without pushing`() {
        val fn = viewModel.substringAfter("fun commitOnly(").substringBefore("fun push(")
        assertTrue(fn.contains("runGitOperation("))
        assertTrue(fn.contains("git.stageAll(projectRoot)"))
        assertTrue(fn.contains("git.commit(projectRoot, trimmed)"))
        assertTrue(fn.contains("not pushed yet"))
        assertFalse(fn.contains("pushCapturing"))
        // Same guards as COMMIT & PUSH: a message is required, and open
        // conflicts block the commit.
        assertTrue(fn.contains("Enter a commit message"))
        assertTrue(fn.contains("before committing"))
        // Phase 73.7 — the Commit dialog's Stage All toggle makes the
        // staging conditional (off commits only what is staged).
        assertTrue(fn.contains("val note = if (stageAll) {"))
    }

    @Test fun `the credentials dialog edits all four stored values through the shared store`() {
        // A second editor, not a second store: Settings reads and writes
        // these same keys, so whichever screen saves last wins.
        assertTrue(dialog.contains("GitCredentialsStore(context.applicationContext)"))
        assertTrue(dialog.contains("store.stored()"))
        assertTrue(dialog.contains("store.save(token, username, commitName, email)"))
        assertTrue(dialog.contains("stringResource(R.string.git_credentials_username)"))
        assertTrue(dialog.contains("stringResource(R.string.git_credentials_commit_name)"))
        assertTrue(dialog.contains("stringResource(R.string.git_credentials_email)"))
        assertTrue(dialog.contains("stringResource(R.string.git_credentials_token)"))
        assertTrue(dialog.contains("PasswordVisualTransformation()"))
    }

    @Test fun `the credentials dialog matches Spck's shape - provider row, Manage link, token link, Cancel and Ok`() {
        assertTrue(dialog.contains("stringResource(R.string.git_credentials_title)"))
        assertTrue(dialog.contains("stringResource(R.string.git_credentials_provider_label)"))
        assertTrue(dialog.contains("stringResource(R.string.git_credentials_provider_name)"))
        assertTrue(dialog.contains("stringResource(R.string.git_credentials_manage)"))
        assertTrue(dialog.contains("clickable(onClick = onManage)"))
        assertTrue(dialog.contains("stringResource(R.string.git_credentials_create_link)"))
        assertTrue(dialog.contains("GitErrors.TOKEN_HELP_URL"))
        assertTrue(dialog.contains("stringResource(R.string.cancel)"))
        assertTrue(dialog.contains("stringResource(R.string.git_credentials_ok)"))
    }

    @Test fun `saving credentials refreshes the panel, Manage leaves for Settings`() {
        assertTrue(sheet.contains("if (showCredentialsDialog) {"))
        assertTrue(sheet.contains("GitCredentialsDialog("))
        assertTrue(sheet.contains("onSaved = { viewModel.refresh(context, projectRoot) }"))
        val manage = sheet.substringAfter("onManage = {").substringBefore("onSaved = {")
        assertTrue(manage.contains("showCredentialsDialog = false"))
        // Phase 73.7 — the sheet is gone, so Manage no longer dismisses
        // one: it closes the dialog and jumps straight to Settings.
        assertFalse(manage.contains("onDismiss()"))
        assertTrue(manage.contains("onOpenSettings()"))
    }

    @Test fun `new user-facing strings exist for the Spck panel`() {
        for (name in listOf(
            "git_repository_title", "git_search_changes", "git_branch_menu_description",
            "git_push_menu_description", "git_branches_action", "git_refresh_files_action",
            "git_commit_all_action", "git_provider_action", "git_branch_menu_header",
            "git_unstaged_header", "git_collapse_section", "git_expand_section",
            "git_no_matching_changes", "git_install_progress", "git_install_background_note",
            "git_credentials_title", "git_credentials_provider_label", "git_credentials_provider_name",
            "git_credentials_manage", "git_credentials_username", "git_credentials_commit_name",
            "git_credentials_email", "git_credentials_token", "git_credentials_show",
            "git_credentials_hide", "git_credentials_create_prefix", "git_credentials_create_link",
            "git_credentials_ok", "git_credentials_saved"
        )) {
            assertTrue(name, strings.contains("name=\"$name\""))
        }
        // The install bar shows elapsed seconds; the note promises the user
        // can stay in the panel while it runs.
        val progress = strings.substringAfter("name=\"git_install_progress\"").substringBefore("</string>")
        assertTrue(progress.contains("%1\$ds"))
        val note = strings.substringAfter("name=\"git_install_background_note\"").substringBefore("</string>")
        assertTrue(note.contains("stay here"))
    }
}
