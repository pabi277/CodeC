package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 44 — source-level wiring pins (the shape of `StageAllHygieneTest` and
 * `TempGcAndRunInteropTest`).
 *
 * The pure policy is pinned by [SetupGatePolicyTest]; what a host JVM cannot
 * exercise is whether the ANDROID edges actually call it. Each check below is
 * one line of the spec that would otherwise regress silently:
 *  - every `pkg`-shaped command the Packages tab fires goes through the gate;
 *  - the foreground service (and the wake lock) come up BEFORE the download,
 *    not after the first PTY;
 *  - the swap window is recorded BEFORE the first rename and closed after the
 *    second, with a rollback that quiets the ledger;
 *  - the boot repair runs in `MainActivity.onCreate`, beside the TempGc sweep;
 *  - the ledger's Android edge commits (durability is the whole point);
 *  - no second notification channel, no new permission, no new service.
 */
class SetupGateWiringTest {

    private fun source(path: String): String = RepoFiles.mainSource(path).readText()

    private val modules = "app/src/main/java/com/codeci/ide/ui/screens/ModulesScreen.kt"
    private val terminalVm = "app/src/main/java/com/codeci/ide/ui/viewmodels/TerminalViewModel.kt"
    private val main = "app/src/main/java/com/codeci/ide/MainActivity.kt"
    private val installer = "app/src/main/java/com/codeci/ide/ui/terminal/UserlandInstaller.kt"
    private val ledgerPrefs = "app/src/main/java/com/codeci/ide/ui/terminal/SetupLedgerPrefs.kt"
    private val terminalScreen = "app/src/main/java/com/codeci/ide/ui/screens/TerminalScreen.kt"
    private val fgs = "app/src/main/java/com/codeci/ide/ui/services/TerminalForegroundService.kt"

    private fun before(src: String, first: String, then: String) {
        val a = src.indexOf(first)
        val b = src.indexOf(then)
        assertTrue("'$first' not found", a >= 0)
        assertTrue("'$then' not found", b >= 0)
        assertTrue("'$first' must come before '$then'", a < b)
    }

    // ---- Packages tab -------------------------------------------------------

    @Test
    fun `the installer's Context constructor accepts and forwards the ledger`() {
        val src = source(installer)
        // `TerminalViewModel` builds the installer as
        // `UserlandInstaller(application, ledger = setupLedger)`. If the
        // secondary (Context) constructor does not declare AND forward that
        // parameter, the whole `userland` value fails to resolve and every
        // member call on it cascades into an "Unresolved reference" — exactly
        // what CI run 34692621773 reported as seven errors in one file.
        val at = src.indexOf("constructor(")
        assertTrue("no secondary constructor found", at >= 0)
        val signature = src.substring(at, src.indexOf(") : this(", at))
        assertTrue(
            "the Context constructor must accept `ledger: SetupLedger?`",
            signature.contains("ledger: SetupLedger?")
        )
        val forwarded = src.substring(at).substringAfter(") : this(").substringBefore("\n    fun ")
        assertTrue(
            "the Context constructor must forward `ledger = ledger`",
            forwarded.contains("ledger = ledger")
        )
    }

    @Test
    fun `every command the Packages tab sends is behind the gate`() {
        val src = source(modules)
        assertTrue(src.contains("SetupGatePolicy.can(SetupAction.INSTALL_PACKAGE, setupFacts)"))
        assertTrue(src.contains("SetupGatePolicy.actionForCommand(command, prefixDir)"))
        val needle = "terminalViewModel.sendCommand("
        var index = src.indexOf(needle)
        var count = 0
        while (index >= 0) {
            count++
            val context = src.substring(maxOf(0, index - 600), index)
            assertTrue(
                "sendCommand at $index is not gated (context ends: …${context.takeLast(90)})",
                context.contains("if (gated)") ||
                    context.contains("if (runBlocked)") ||
                    context.contains("verdict.allowed")
            )
            index = src.indexOf(needle, index + needle.length)
        }
        // Four call sites: the one gated send inside runGated (Quick Actions
        // and the custom command runner share it) plus the card's
        // install / run / uninstall.
        assertEquals("runGated + install + run + uninstall", 4, count)
    }

    @Test
    fun `the Packages tab shows the refusal and a door to the setup`() {
        val src = source(modules)
        assertTrue(src.contains("SetupGateCard("))
        assertTrue(src.contains("VIEW SETUP"))
        assertTrue("the built-in cc card is never gated", src.contains("!item.isBuiltIn"))
        assertTrue(
            "an already-installed binary keeps its RUN while the gate is up",
            src.contains("val runBlocked = gated && !isInstalled") &&
                src.contains("!packageActionsBlocked")
        )
    }

    // ---- the ViewModel ------------------------------------------------------

    @Test
    fun `the keep-alive starts before the download, and the repair is waited for`() {
        val src = source(terminalVm)
        before(src, "SetupRecoveryGate.awaitFinished()", "userland.installIfNeeded(")
        before(src, "startSetupKeepAlive()", "userland.installIfNeeded(")
        assertTrue(
            "the session teardown must not kill the setup keep-alive",
            src.contains("} else if (!setupKeepAlive) {")
        )
    }

    @Test
    fun `the installer's own progress lines feed the observable state`() {
        val src = source(terminalVm)
        assertTrue(src.contains("publishSetup(setupTracker.observe(msg))"))
        assertTrue(src.contains("target.notice(msg)"))
        assertTrue(src.contains("SetupStateBridge.publish("))
        // Every terminal status becomes a settled stage — no spinner forever.
        for (branch in listOf(
            "is UserlandStatus.Installed ->",
            "is UserlandStatus.AlreadyInstalled ->",
            "is UserlandStatus.SkippedOffline ->",
            "is UserlandStatus.SkippedNoRelease ->",
            "is UserlandStatus.Failed ->"
        )) {
            assertTrue("missing branch $branch", src.contains(branch))
        }
        assertTrue(src.contains("setupTracker.ready("))
        assertTrue(src.contains("setupTracker.unsupported("))
        assertTrue(src.contains("setupTracker.fail("))
        assertTrue("a successful install quiets the ledger", src.contains("setupLedger.clear()"))
        assertTrue("the ledger travels with the installer", src.contains("ledger = setupLedger"))
    }

    @Test
    fun `the setup state is seeded from disk, not from optimism`() {
        val src = source(terminalVm)
        assertTrue(src.contains("initialSetupProgress("))
        assertTrue(src.contains("SetupGatePolicy.packageManagerPresent(prefix)"))
        assertTrue(src.contains("SetupPhase.SWAPPING"))
    }

    // ---- MainActivity -------------------------------------------------------

    @Test
    fun `the boot repair runs beside the TempGc sweep and still never throws`() {
        val src = source(main)
        assertTrue(src.contains("com.codeci.ide.ui.terminal.SetupRecovery.recover("))
        assertTrue(src.contains("SetupLedgerPrefs.ledger(this)"))
        assertTrue(src.contains("codec-setup-recovery"))
        assertTrue(src.contains("SetupRecoveryGate.finished()"))
        assertTrue(src.contains("SetupNoticeBridge.post(report.message)"))
        // The Phase 39.1 pin must survive next to it.
        assertTrue(src.contains("TempGc.sweep"))
        assertTrue(src.contains("LiveRunStamps.snapshot()"))
        assertTrue(src.contains("codec-temp-gc"))
    }

    @Test
    fun `the setup bar is mounted above the NavHost, in every tab's shell`() {
        val src = source(main)
        val banner = src.indexOf("SafeModeBanner(")
        val bar = src.indexOf("com.codeci.ide.ui.components.SetupBar(")
        val navHost = src.indexOf("NavHost(")
        assertTrue(banner >= 0 && bar >= 0 && navHost >= 0)
        assertTrue("the bar rides in the same column as the safe-mode banner", banner < bar)
        assertTrue("the bar is above the NavHost, never inside one tab", bar < navHost)
        assertTrue(src.contains("terminalViewModel.setupProgress.collectAsState()"))
        assertTrue(src.contains("terminalViewModel.setupFacts.collectAsState()"))
        assertTrue(src.contains("SetupNoticeBridge.message.collectAsState()"))
        assertTrue(src.contains("SetupNoticeBridge.clear()"))
    }

    @Test
    fun `a fresh install opens the terminal first and releases the user when setup settles`() {
        val src = source(main)
        assertTrue(src.contains("setupDiverted = true"))
        assertTrue(src.contains("Screen.Terminal.createRoute(null)"))
        assertTrue(src.contains("setupProgress.stage != com.codeci.ide.ui.terminal.SetupStage.UNSUPPORTED"))
        assertTrue(src.contains("if (!setupProgress.settled) return@LaunchedEffect"))
        assertTrue(src.contains("current.startsWith(\"terminal\")"))
    }

    // ---- the installer's swap window ---------------------------------------

    @Test
    fun `the swap window is recorded before the first rename and closed after the second`() {
        val src = source(installer)
        val swap = src.indexOf("internal fun swapPrefix(")
        assertTrue(swap >= 0)
        val body = src.substring(swap)
        before(body, "ledger?.note(SetupPhase.SWAPPING)", "prefix.renameTo(old)")
        before(body, "staged.renameTo(prefix)", "ledger?.note(SetupPhase.DONE)")
        before(body, "ledger?.note(SetupPhase.DONE)", "old.deleteRecursively()")
        assertTrue(
            "a rolled-back swap must quiet the ledger",
            body.contains("old.renameTo(prefix)") && body.contains("ledger?.clear()")
        )
        assertTrue(src.contains("ledger?.note(SetupPhase.DOWNLOADING, manifest.releaseTag)"))
        assertTrue(src.contains("ledger?.note(SetupPhase.EXTRACTING, manifest.releaseTag)"))
        // One source for the orphan names the boot sweep matches.
        assertTrue(src.contains("SetupRecovery.stagingName("))
        assertTrue(src.contains("SetupRecovery.oldPrefixName("))
        assertFalse("no hard-coded staging name left", src.contains("\".userland-staging\" +"))
    }

    @Test
    fun `the ledger stays optional so the installer's own tests are untouched`() {
        val src = source(installer)
        assertTrue(src.contains("private val ledger: SetupLedger? = null"))
    }

    // ---- the ledger's Android edge ------------------------------------------

    @Test
    fun `the ledger commits, never applies`() {
        val src = source(ledgerPrefs)
        assertTrue(src.contains(".commit()"))
        assertFalse("apply() is not durable enough for a kill marker", src.contains(".apply()"))
        assertTrue(src.contains("getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)"))
    }

    // ---- the terminal surface -----------------------------------------------

    @Test
    fun `the terminal chip is stage-aware and the warning bar is mounted`() {
        val src = source(terminalScreen)
        assertTrue(src.contains("TerminalStatusLabel.label("))
        assertTrue(src.contains("SetupGatePolicy.dontCloseText(setupProgress)"))
        assertFalse(
            "the fixed 'starting shell…' chip is gone",
            src.contains("\"starting shell…\" to Color")
        )
    }

    @Test
    fun `the notification reuses the one existing channel and service`() {
        val src = source(fgs)
        assertEquals(
            "exactly one channel is created (createNotificationChannel is not a second one)",
            1,
            Regex("(?<!create)NotificationChannel\\(").findAll(src).count()
        )
        assertTrue(src.contains("CHANNEL_ID = \"codec_terminal\""))
        assertEquals("one notification id", 1, Regex("NOTIFICATION_ID = ").findAll(src).count())
        assertTrue(src.contains("setOnlyAlertOnce(true)"))
        assertTrue(src.contains("EXTRA_STATUS"))
        assertTrue(src.contains("fun updateStatus("))
        assertTrue(
            "a refused background start must never crash the install",
            src.contains("catch (e: Exception)")
        )
    }

    // ---- the editor's install prompt ----------------------------------------

    @Test
    fun `the editor install prompt is gated too`() {
        val src = source("app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt")
        val confirm = src.indexOf("fun confirmInstall(")
        assertTrue(confirm >= 0)
        val body = src.substring(confirm, minOf(src.length, confirm + 2_500))
        before(body, "SetupGatePolicy.can(", "LanguageRunPlanner.installCommand(")
        assertTrue(body.contains("SetupStateBridge.factsOrDisk("))
        assertTrue(body.contains("failRun(ctx, setupVerdict.message"))
    }
}
