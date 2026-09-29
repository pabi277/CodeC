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

    @Test fun `not-a-repo guidance stays text-only, no new action button`() {
        val branch = sheet.substringAfter("!state.isRepo -> {").substringBefore("else -> {")
        assertTrue(branch.contains("SheetGuidance(stringResource(R.string.git_not_a_repo_message))"))
        assertFalse(branch.contains("Button("))
        assertFalse(branch.contains("onClick"))
    }

    @Test fun `new user-facing strings exist and explain git in plain words`() {
        for (name in listOf(
            "git_install_explainer", "git_install_action", "git_installing", "git_install_failed_message"
        )) {
            assertTrue(name, strings.contains("name=\"$name\""))
        }
        // The rewritten not-a-repo message still points at both existing
        // recovery paths (clone, or `git init`) — wording changed, not the
        // recovery options themselves (owner: better wording only).
        val notARepo = strings.substringAfter("name=\"git_not_a_repo_message\"")
        assertTrue(notARepo.contains("Clone from GitHub"))
        assertTrue(notARepo.contains("git init"))
    }
}
