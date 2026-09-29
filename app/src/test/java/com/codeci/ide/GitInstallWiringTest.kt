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

    @Test fun `install card shows the userland state while the gate refuses, git after`() {
        // The gate is computed once, from the same SetupGatePolicy the
        // Packages tab uses — no bespoke readiness check invented here.
        assertTrue(sheet.contains("SetupGatePolicy.can(SetupAction.INSTALL_PACKAGE, setupFacts)"))
        val branch = sheet.substringAfter("!state.gitInstalled -> {").substringBefore("!state.isRepo -> {")
        // Phase 73.8 supersedes 73.2's "plain message" decision: one card
        // for both states (the owner's "like in package part").
        assertTrue(branch.contains("GitInstallCard("))
        assertTrue(branch.contains("installAllowed = installGitVerdict.allowed"))
        assertTrue(branch.contains("setupFacts = setupFacts"))
        // The userland section speaks the Terminal tab's own stage words
        // (TerminalStatusLabel — one wording, two screens) with the
        // installer's real % — and the gate's own refusal sentence as
        // the guidance.
        assertTrue(sheet.contains("TerminalStatusLabel.label("))
        assertTrue(sheet.contains("stringResource(R.string.git_userland_title)"))
        assertTrue(sheet.contains("SetupGatePolicy.refusal(SetupAction.INSTALL_PACKAGE, progress, setupFacts)"))
        assertTrue(sheet.contains("stringResource(R.string.git_userland_git_wait)"))
        // The determinate bar exists ONLY while a real download % exists.
        val userland = sheet.substringAfter("private fun UserlandInstallSection(").substringBefore("@Composable\nprivate fun GitInitGuidance(")
        assertTrue(userland.contains("if (progress.stage == SetupStage.DOWNLOADING && percent != null) {"))
        assertTrue(userland.contains("progress = { percent.coerceIn(0, 100) / 100f }"))
    }

    @Test fun `tapping install asks first, then sends the same command the git package catalog entry uses`() {
        assertTrue(sheet.contains("PackageCatalog.ALL_PACKAGES.first { it.id == \"git\" }"))
        // Phase 73.6 supersedes the 73.2 "no confirmation dialog"
        // decision: the button opens the shared prompt (the owner's
        // explicit ask-first instruction); confirming starts the install.
        assertTrue(sheet.contains("onInstall = { showInstallPrompt = true }"))
        assertTrue(sheet.contains("if (showInstallPrompt) {"))
        assertTrue(sheet.contains("GitInstallPromptDialog("))
        val handler = sheet.substringAfter("val onInstallGit: () -> Unit = {").substringBefore("LaunchedEffect(projectRoot) {")
        assertTrue(handler.contains("terminalViewModel.sendCommand(gitPackage.installCommand)"))
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
        // Phase 73.8 — the same tick reads the shared session's
        // transcript for the live installer line (only what appeared
        // after this install started) and keeps the last lines for the
        // failed state — the honest signals `pkg` never gives.
        assertTrue(loop.contains("terminalViewModel.transcriptText()"))
        assertTrue(loop.contains("gitInstallLogBaseline = transcript.length"))
        assertTrue(loop.contains("gitInstallLogLine = fresh.lineSequence()"))
        assertTrue(loop.contains("gitInstallFailTail = failTail()"))
    }

    @Test fun `the installing state is a card with elapsed time and the live installer line`() {
        // Phase 73.5 — the command still runs in the shared terminal
        // session underneath, but the user is never redirected there; the
        // bar (elapsed seconds on an indeterminate track — `pkg` reports
        // no percentage) is the whole progress surface, finishing in-panel.
        // Phase 73.8 — the bar moved into the install card (the owner's
        // "like in package part") with the installer's live last line.
        val card = sheet.substringAfter("private fun GitInstallCard(").substringBefore("private fun UserlandInstallSection(")
        assertTrue(card.contains("LinearProgressIndicator(modifier = Modifier.fillMaxWidth())"))
        assertTrue(card.contains("stringResource(R.string.git_install_progress, elapsedSec)"))
        assertTrue(card.contains("stringResource(R.string.git_install_background_note)"))
        assertFalse(card.contains("CircularProgressIndicator"))
        assertTrue(card.contains("stringResource(R.string.git_install_live_label)"))
        assertTrue(card.contains("liveLine?.let { line ->"))
        assertTrue(sheet.contains("var gitInstallElapsedSec by remember(projectRoot) { mutableStateOf(0) }"))
    }

    @Test fun `an install that ends without git on disk shows failed plus retry`() {
        // Phase 73.5 — ENDED_WITHOUT_INSTALL used to revert silently to the
        // INSTALL button; it now shows the same failed + RETRY state as a
        // non-zero exit (the user-visible truth is identical: git is still
        // missing after an install ran).
        val loop = sheet.substringAfter("LaunchedEffect(installingGit) {").substringBefore("val onInstallGit")
        val ended = loop.substringAfter("InstallOutcome.ENDED_WITHOUT_INSTALL -> {").substringBefore("InstallOutcome.WAITING -> Unit")
        assertTrue(ended.contains("installingGit = false"))
        assertTrue(ended.contains("gitInstallFailed = true"))
        // Phase 73.8 — the failed state keeps the last installer lines in
        // the box (no "open Terminal" redirect for the diagnosis).
        val card = sheet.substringAfter("private fun GitInstallCard(").substringBefore("private fun UserlandInstallSection(")
        assertTrue(card.contains("stringResource(R.string.git_install_failed_tail_label)"))
        assertTrue(card.contains("failTail.forEach { line ->"))
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
            "git_install_explainer", "git_install_action", "git_installing", "git_install_failed_message",
            "git_userland_title", "git_userland_checking", "git_userland_git_wait",
            "git_install_live_label", "git_install_failed_tail_label"
        )) {
            assertTrue(name, strings.contains("name=\"$name\""))
        }
        // Phase 73.8 — the failed message points at the box's own tail
        // lines, never at Terminal.
        val failed = strings.substringAfter("name=\"git_install_failed_message\"").substringBefore("</string>")
        assertTrue(failed.contains("last lines are below"))
        assertFalse(failed.contains("Terminal"))
        // Phase 73.3 — the message still names Clone as the other recovery
        // path; the old "type git init in the Terminal" instruction was
        // dropped once a real button did that instead (see
        // `GitGuiParityWiringTest`).
        val notARepo = strings.substringAfter("name=\"git_not_a_repo_message\"").substringBefore("</string>")
        assertTrue(notARepo.contains("Clone from GitHub"))
        assertFalse(notARepo.contains("Terminal"))
    }
}
