package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 71.1 — source pins for the screen edges of the Packages/Terminal part
 * (the pure halves have their own tests: `PkgResultTest`, `PackagePinsTest`,
 * `SessionLabelTest`, `PkgIndexAndResultTest`).
 */
class Phase71PackagesTerminalWiringTest {

    private fun code(path: String) =
        RepoFiles.codeOnly(RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/$path").readText())

    /** Comments and string literals intact — for pins on a literal. */
    private fun raw(path: String) =
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/$path").readText()

    private val modules get() = code("screens/ModulesScreen.kt")
    private val terminal get() = code("screens/TerminalScreen.kt")

    @Test
    fun `the row learns the real result from pkg, off the main thread`() {
        val m = modules
        assertTrue(m.contains("PkgResult.read("))
        assertTrue(m.contains("InstallOutcomes.decide("))
        assertTrue(m.contains("Dispatchers.IO"))
        assertTrue("a failure must reach the label policy", m.contains("failed = installFailed"))
        assertFalse("the label may no longer be hard-wired to 'never failed'", m.contains("failed = false"))
        assertTrue(m.contains("InstallOutcome.FAILED"))
        assertTrue(m.contains("InstallOutcome.ENDED_WITHOUT_INSTALL"))
    }

    @Test
    fun `a stale result cannot fail a new install`() {
        // startedAt is stamped at the tap and handed to the verdict.
        val m = modules
        assertTrue(m.contains("installStartedAtSec = System.currentTimeMillis() / 1000"))
        assertTrue(m.contains("installFailed = false"))
    }

    @Test
    fun `INSTALLING is a door to the terminal and never a dead button`() {
        val m = modules
        assertFalse(m.contains("enabled = !installInFlight"))
        assertTrue(m.contains("if (installInFlight) onViewProgress else onInstall"))
        assertTrue(m.contains("onViewProgress = onNavigateToTerminal"))
    }

    @Test
    fun `a failed install says one sentence and offers RETRY`() {
        val m = modules
        assertTrue(m.contains("installFailedText"))
        assertTrue(m.contains("R.string.install_failed_retry"))
        val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("name=\"install_failed_retry\""))
        assertTrue(strings.contains("Tap RETRY"))
    }

    @Test
    fun `the pin is stored, read, arranged and named`() {
        val m = modules
        assertTrue(m.contains("settingsManager.pinnedPackagesFlow"))
        assertTrue(m.contains("settingsManager.setPinnedPackages("))
        assertTrue("both the sectioned list and the search list arrange by pin", Regex("PackagePins\\.arrange\\(").findAll(m).count() == 2)
        assertTrue(m.contains("IconButton(onClick = onTogglePin)"))
        assertTrue(m.contains("R.string.package_pin_action"))
        assertTrue(m.contains("R.string.package_unpin_action"))
        val store = raw("settings/SettingsManager.kt")
        assertTrue(store.contains("stringPreferencesKey(\"pinned_packages\")"))
    }

    @Test
    fun `a pinned card is not also listed in its section`() {
        assertTrue(modules.contains("unpinnedItems.filter"))
        assertTrue(raw("screens/ModulesScreen.kt").contains("key = { \"pinned_\${it.id}\" }"))
    }

    @Test
    fun `the userland button asks before it closes every session`() {
        val t = terminal
        assertTrue(t.contains("IconButton(onClick = { confirmUserland = true })"))
        assertEquals(
            "viewModel.installUserland() is called once, from the dialog's confirm button",
            1,
            Regex("viewModel\\.installUserland\\(\\)").findAll(t).count(),
        )
        val dialog = t.substringAfter("if (confirmUserland)")
        assertTrue(dialog.substringBefore("closeTarget?.let").contains("viewModel.installUserland()"))
        val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("closes all terminal sessions"))
    }

    @Test
    fun `the dialog's words follow whether the tools work`() {
        val t = terminal
        assertTrue(t.contains("setupFacts.usable"))
        assertTrue(t.contains("R.string.terminal_userland_confirm_title"))
        assertTrue(t.contains("R.string.terminal_userland_setup_title"))
    }

    @Test
    fun `sessions are named in the top bar and in every handoff toast`() {
        assertTrue(terminal.contains("SessionLabel.titled("))
        val m = raw("screens/ModulesScreen.kt")
        assertTrue(Regex("SessionLabel\\.sentence\\(").findAll(m).count() >= 4)
        for (literal in listOf("\"Installing \${item.name}…\"", "\"Launching \${item.name}…\"", "\"Uninstalling \${item.name}…\"")) {
            assertFalse("$literal must go through SessionLabel", m.contains("Toast.makeText(context, $literal"))
        }
    }

    @Test
    fun `the terminal buffer keeps the cursor on its content across a keyboard toggle`() {
        val buffer = code("terminal/TerminalBuffer.kt")
        assertTrue(buffer.contains("blankRowsBelowCursor("))
        assertTrue(buffer.contains("val owed = rows - r"))
    }

    @Test
    fun `the userland stamp did not move`() {
        // Editing pkg does not need a bootstrap bump: ShellBootstrap rewrites
        // bin/pkg every time a shell is prepared. BOOTSTRAP_VERSION stays 27.
        assertTrue(raw("terminal/ShellEnvironment.kt").contains("const val BOOTSTRAP_VERSION = \"27\""))
    }
}
