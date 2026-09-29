package com.codeci.ide

import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 73.2 — the Source Control sheet's "git isn't installed" state grew a
 * one-tap Install Git button (reusing the exact Packages-tab install
 * mechanism: send the real command to the shared terminal session, then poll
 * `PkgResult`/`InstallOutcomes` — both pure and already host-tested
 * elsewhere — until it lands or fails). These are source-scan checks in the
 * same spirit as [GitDiscardWiringTest]: no Robolectric Compose render
 * exists for this sheet, so we pin the wiring by source text instead.
 */
class GitInstallWiringTest {
    private fun source(path: String) = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/$path").readText()
    private val sheet = source("screens/GitControlView.kt")
    private val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()

    @Test fun `install button only appears when the userland gate allows it`() {
        // The gate is computed once, from the same SetupGatePolicy the
        // Packages tab uses — no bespoke readiness check invented here.
        assertTrue(sheet.contains("SetupGatePolicy.can(SetupAction.INSTALL_PACKAGE, setupFacts)"))
        val branch = sheet.substringAfter("!state.gitInstalled -> {").substringBefore("!state.isRepo -> {")
        assertTrue(branch.contains("if (installGitVerdict.allowed) {"))
        assertTrue(branch.contains("GitInstallGuidance("))
        // When the gate refuses (Linux tools not ready yet), today's plain
        // message is kept byte-for-byte — the owner's explicit decision not
        // to chain into the full userland bootstrap from this sheet.
        assertTrue(branch.contains("SheetGuidance(stringResource(R.string.git_not_installed_message))"))
    }

    @Test fun `tapping install sends the same command the git package catalog entry uses`() {
        assertTrue(sheet.contains("PackageCatalog.ALL_PACKAGES.first { it.id == \"git\" }"))
        val handler = sheet.substringAfter("val onInstallGit: () -> Unit = {").substringBefore("LaunchedEffect(projectRoot) {")
        assertTrue(handler.contains("terminalViewModel.sendCommand(gitPackage.installCommand)"))
        // No confirmation dialog before the (small) install starts.
        assertFalse(handler.contains("AlertDialog"))
        // On success the sheet stays put (refresh in place) instead of
        // navigating away to Terminal, unlike a Packages row.
        assertFalse(sheet.contains("onNavigateToTerminal"))
    }

    @Test fun `install polling reuses the pure PkgResult decision, not a bespoke one`() {
        val loop = sheet.substringAfter("LaunchedEffect(installingGit) {").substringBefore("val onInstallGit")
        assertTrue(loop.contains("PkgResult.read(userlandPrefix)"))
        assertTrue(loop.contains("GitContext(context.applicationContext).gitBinary() != null"))
        assertTrue(loop.contains("InstallOutcomes.decide(gitInstallTargets, gitInstallStartedAtSec, onDisk, result)"))
        assertTrue(loop.contains("InstallOutcome.INSTALLED ->"))
        assertTrue(loop.contains("viewModel.refresh(context, projectRoot)"))
        assertTrue(loop.contains("InstallOutcome.FAILED ->"))
        assertTrue(loop.contains("gitInstallFailed = true"))
    }

    @Test fun `not-a-repo guidance grew a real init button in Phase 73_3`() {
        // Superseded by the owner's Phase 73.3 follow-up: `git init` was the
        // last normal Source Control action still requiring the terminal, so
        // it is now a button here, not text-only guidance. The 73.2 wording
        // rewrite for the message itself is unchanged (see the string test
        // below) — only the "no buttons" restriction was lifted.
        val branch = sheet.substringAfter("!state.isRepo -> {").substringBefore("else -> {")
        assertTrue(branch.contains("GitInitGuidance("))
        assertTrue(branch.contains("onInit = { viewModel.initRepo(context, projectRoot) }"))
    }

    @Test fun `new user-facing strings exist and explain git in plain words`() {
        for (name in listOf(
            "git_install_explainer", "git_install_action", "git_installing", "git_install_failed_message"
        )) {
            assertTrue(name, strings.contains("name=\"$name\""))
        }
        // Phase 73.3 — the message still names Clone as the other recovery
        // path; the old "type git init in the Terminal" instruction was
        // dropped once a real button did that instead (see
        // `GitGuiParityWiringTest`).
        val notARepo = strings.substringAfter("name=\"git_not_a_repo_message\"")
        assertTrue(notARepo.contains("Clone from GitHub"))
        assertFalse(notARepo.contains("Terminal"))
    }
}
