package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 73.6 — the editor drawer's Repository panel went full GUI. Its
 * Initialize button used to close the drawer and type `git init` into the
 * visible shell (wrong folder — the shell runs in `projects/`, not the
 * project — and no git check, so the first tap met "no git command").
 * Initialize now runs the engine's `init` in place; when git is missing,
 * the shared install prompt asks first ("Git is not installed, do you
 * want to install it?"), then the same background install + status bar
 * the sheet uses takes over and auto-continues into the pending
 * initialize. Source-scan checks in the established spirit: no
 * Robolectric render exists for the drawer, so the wiring is pinned by
 * source text (single-line anchors only).
 */
class GitDrawerInstallWiringTest {
    private fun source(path: String) = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/$path").readText()
    private val editor = source("screens/EditorScreen.kt")
    private val panel = source("components/EditorSidePanel.kt")
    private val prompt = source("screens/GitInstallPromptDialog.kt")
    private val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()

    @Test fun `the drawer Initialize button runs the engine, never the terminal`() {
        // The terminal-typing path is gone from the whole screen.
        assertFalse(editor.contains("onOpenInTerminal(\"git init\")"))
        val tap = editor.substringAfter("onInitializeRepository = {").substringBefore("onInstallGit = { showGitInstallPrompt = true },")
        assertTrue(tap.contains("runDrawerInit(root)"))
        assertFalse(tap.contains("onOpenInTerminal"))
        assertFalse(tap.contains("drawerState.close()"))
    }

    @Test fun `drawer init asks to install when git is missing, opens the sheet otherwise`() {
        val fn = editor.substringAfter("fun runDrawerInit(root: File) {").substringBefore("LaunchedEffect(drawerInstallingGit) {")
        assertTrue(fn.contains(".manager()"))
        assertTrue(fn.contains("drawerPendingInitRoot = root"))
        assertTrue(fn.contains("showGitInstallPrompt = true"))
        assertTrue(fn.contains("git.isRepository(root)"))
        assertTrue(fn.contains("git.init(root)"))
        assertTrue(fn.contains("openSourceControl()"))
        assertTrue(fn.contains("viewModel.refreshGitMeta(context)"))
        // Failures are classified into plain words, not raw stderr.
        assertTrue(fn.contains("GitErrors.classify(error.message, error.exitCode, hasToken = false).display()"))
    }

    @Test fun `the drawer install reuses the sheet mechanism, then auto-continues into init`() {
        assertTrue(editor.contains("SetupGatePolicy.can(SetupAction.INSTALL_PACKAGE, drawerSetupFacts)"))
        assertTrue(editor.contains("PackageCatalog.ALL_PACKAGES.first { it.id == \"git\" }"))
        assertTrue(editor.contains("drawerTerminalViewModel.sendCommand(drawerGitPackage.installCommand)"))
        // Bounded by the Search slot below: the polling loop plus the
        // elapsed ticker, neither of which shares these anchors.
        val loop = editor.substringAfter("LaunchedEffect(drawerInstallingGit) {").substringBefore("// The panel's Search slot")
        assertTrue(loop.contains("PkgResult.read(drawerUserlandPrefix)"))
        assertTrue(loop.contains("InstallOutcomes.decide(drawerGitInstallTargets, drawerGitInstallStartedAtSec, onDisk, result)"))
        assertTrue(loop.contains("InstallOutcome.INSTALLED ->"))
        // The pending initialize is consumed exactly once, on success.
        assertTrue(loop.contains("drawerPendingInitRoot?.let { root ->"))
        assertTrue(loop.contains("drawerPendingInitRoot = null"))
        assertTrue(loop.contains("runDrawerInit(root)"))
    }

    @Test fun `the install prompt dialog asks the owners question with Install and Cancel`() {
        assertTrue(prompt.contains("stringResource(R.string.git_install_prompt_title)"))
        assertTrue(prompt.contains("stringResource(R.string.git_install_prompt_message)"))
        assertTrue(prompt.contains("stringResource(R.string.git_install_prompt_action)"))
        assertTrue(prompt.contains("stringResource(R.string.cancel)"))
        assertTrue(prompt.contains("onConfirm: () -> Unit"))
        assertTrue(prompt.contains("onDismiss: () -> Unit"))
        // Both doors host it: the drawer and the sheet.
        assertTrue(editor.contains("GitInstallPromptDialog("))
        val sheet = source("screens/GitControlView.kt")
        assertTrue(sheet.contains("GitInstallPromptDialog("))
        assertTrue(sheet.contains("onInstall = { showInstallPrompt = true }"))
    }

    @Test fun `the drawer panel shows install, progress, retry and init-busy states`() {
        assertTrue(panel.contains("val gitInstalled: Boolean = true"))
        assertTrue(panel.contains("val installingGit: Boolean = false"))
        assertTrue(panel.contains("val installFailed: Boolean = false"))
        assertTrue(panel.contains("val initializing: Boolean = false"))
        assertTrue(panel.contains("if (!state.gitInstalled) {"))
        assertTrue(panel.contains("stringResource(R.string.panel_repository_no_git)"))
        assertTrue(panel.contains("stringResource(R.string.panel_repository_install_git)"))
        assertTrue(panel.contains("LinearProgressIndicator(modifier = Modifier.fillMaxWidth())"))
        assertTrue(panel.contains("stringResource(R.string.git_install_progress, state.installElapsedSec)"))
        assertTrue(panel.contains("stringResource(R.string.git_install_failed_message)"))
        assertTrue(panel.contains("stringResource(R.string.install_label_retry)"))
        assertTrue(panel.contains("if (state.initializing) {"))
        assertTrue(panel.contains("stringResource(R.string.git_init_busy)"))
        // Gate refused: the same plain message as the sheet, no button.
        assertTrue(panel.contains("if (!state.canInstallGit) {"))
        assertTrue(panel.contains("stringResource(R.string.git_not_installed_message)"))
        // The empty-repo state and its Initialize button are untouched.
        assertTrue(panel.contains("stringResource(R.string.panel_repository_empty)"))
        assertTrue(panel.contains("stringResource(R.string.panel_repository_init)"))
    }

    @Test fun `new user-facing strings exist for the prompt and drawer install`() {
        for (name in listOf(
            "git_install_prompt_title", "git_install_prompt_message", "git_install_prompt_action",
            "panel_repository_no_git", "panel_repository_install_git"
        )) {
            assertTrue(name, strings.contains("name=\"$name\""))
        }
        val message = strings.substringAfter("name=\"git_install_prompt_message\"").substringBefore("</string>")
        assertTrue(message.contains("Do you want to install it now?"))
    }
}
