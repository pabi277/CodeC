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
    private val setupBar = "app/src/main/java/com/codeci/ide/ui/components/SetupBar.kt"

    private fun before(src: String, first: String, then: String) {
        val a = src.indexOf(first)
        val b = src.indexOf(then)
        assertTrue("'$first' not found", a >= 0)
        assertTrue("'$then' not found", b >= 0)
        assertTrue("'$first' must come before '$then'", a < b)
    }

    // ---- Packages tab -------------------------------------------------------

    // ---- device round 1 (owner report, 2026-09-12) -------------------------

    @Test
    fun `the setup bar is always actionable and its close button really closes it`() {
        val bar = source(setupBar)
        // "it's not closing or opening terminal": the VIEW button used to be
        // rendered only `if (inFlight || stage == FAILED)`, so the
        // settled-but-unusable state had a sentence and no way to act on it.
        assertTrue("the bar must offer the setup action", bar.contains("TextButton(onClick = onViewSetup)"))
        val before = bar.substring(0, bar.indexOf("TextButton(onClick = onViewSetup)"))
        val tail = before.substring(maxOf(0, before.length - 400))
        assertFalse(
            "the VIEW action must not be conditional on the stage (context: …${tail.takeLast(80)})",
            tail.contains("if (inFlight")
        )
        // The whole bar is one tap, not just a small button.
        assertTrue(bar.contains(".clickable(onClickLabel = \"Open the Terminal tab\") { onViewSetup() }"))
        // The ✕ is driven by a dismiss the CALLER owns (note cleared, or the
        // bar's text remembered as dismissed) — never by a no-op note clear.
        assertTrue(bar.contains("onDismiss: (() -> Unit)? = null"))
        assertTrue(bar.contains("if (onDismiss != null)"))
        assertFalse("the dead note-only dismiss is gone", bar.contains("onDismissNote"))

        val main = source(this.main)
        assertTrue(main.contains("SetupGatePolicy.barDismissAllowed(setupProgress)"))
        assertTrue(main.contains("var dismissedSetupBar by remember { mutableStateOf<String?>(null) }"))
        assertTrue(main.contains("dismissedSetupBar = setupBarText"))
        assertTrue(main.contains("setupBarText != dismissedSetupBar"))
        assertTrue(main.contains("onDismiss = if (setupBarDismissible) dismissSetupBar else null"))
    }

    @Test
    fun `every go-to-the-terminal navigation arrives at the terminal`() {
        val main = source(this.main)
        // "terminal not opening editor opening": `restoreState = true` restores
        // the WHOLE saved sub-stack for a destination, so an editor the user had
        // opened above the terminal came back on top of it. Every navigation
        // that means "show me the terminal" must not restore.
        val needle = "navigate(Screen.Terminal.createRoute("
        var index = main.indexOf(needle)
        var count = 0
        while (index >= 0) {
            count++
            val block = main.substring(index, minOf(main.length, index + 400))
            assertFalse(
                "terminal navigation at $index may restore a stale sub-stack: $block",
                block.contains("restoreState = true")
            )
            index = main.indexOf(needle, index + 1)
        }
        assertTrue("expected at least four terminal navigations, found $count", count >= 4)
        // The bottom tab bar itself: the Terminal tab never restores.
        assertTrue(main.contains("restoreState = screen !is Screen.Terminal"))
    }

    @Test
    fun `a cold start is diverted to the terminal from the disk, not from a flag`() {
        val main = source(this.main)
        assertTrue(main.contains("SetupGatePolicy.startOnTerminal("))
        assertTrue(main.contains("SetupGatePolicy.userlandUsable(prefix, phase)"))
        assertTrue(main.contains("UserlandManifest.archName() != null"))
        assertTrue(main.contains("SetupLedgerPrefs.ledger(activity).read().phase"))
        // The start destination itself stays the pre-44 one: a route with
        // arguments as `startDestination` is graph-construction risk, and a
        // navigate() after the first composition is the proven path.
        assertTrue(main.contains("val startDestination = remember(launchState) {"))
        assertTrue(main.contains("?: Screen.FileManager.route"))
        assertFalse(
            "a route with arguments must not become the graph's start destination",
            main.contains("setupFirstRun -> Screen.Terminal.createRoute(null)")
        )
        // The welcome's starter file is still opened — but never out of the
        // settled-but-unusable state, where the terminal is the only way out.
        assertTrue(main.contains("if (stuck) return@LaunchedEffect"))
    }

    @Test
    fun `an install marker alone is never accepted as ready`() {
        val vm = source(terminalVm)
        // `installIfNeeded(force = false)` answers AlreadyInstalled from the
        // MARKER; the owner's phone had a marker and no working `bin/pkg`, so
        // the stage said READY while the disk said unusable — forever.
        val at = vm.indexOf("is UserlandStatus.AlreadyInstalled ->")
        assertTrue("the AlreadyInstalled branch is gone", at >= 0)
        val block = vm.substring(at, minOf(vm.length, at + 1600))
        assertTrue(block.contains("SetupGatePolicy.userlandUsable(prefixDir, phase)"))
        assertTrue(block.contains("SetupIssue.BROKEN_USERLAND"))
        assertTrue(block.contains("setupTracker.ready(\"installed\")"))
    }

    @Test
    fun `the setup truth is re-read once the boot repair is finished`() {
        val vm = source(terminalVm)
        // The repair runs on a daemon thread and can clear a stale ledger AFTER
        // the ViewModel read it; without a second look the bar would keep
        // claiming a repair that already happened.
        assertTrue(vm.contains("refreshSetupFromDiskWhenIdle()"))
        before(vm, "SetupRecoveryGate.awaitFinished()\n            refreshSetupFromDiskWhenIdle()", "private fun refreshSetupFromDiskWhenIdle()")
        val at = vm.indexOf("private fun refreshSetupFromDiskWhenIdle()")
        val block = vm.substring(at, minOf(vm.length, at + 700))
        assertTrue("an install in flight owns the state", block.contains("if (current.inFlight) return"))
        assertTrue(block.contains("refreshSetupFacts()"))
    }

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

    // ---- Phase 45 round 4: the chrome lock --------------------------------
    // Owner, after running the tour: *"When the userland is installing and
    // unpacking the user can not access any other option and it will show a
    // sweet massage of why can't access any other option."*

    @Test
    fun `an install pauses the other tabs, and a paused tab says why`() {
        val src = source(main)
        // The bar ASKS the pure policy which tabs an install has paused; it does
        // not keep its own list of stages, and it does not guess a sentence.
        assertTrue(src.contains("val chromeLock = com.codeci.ide.ui.terminal.SetupLockPolicy.lock("))
        assertTrue(src.contains("progress = setupProgress,"))
        assertTrue(src.contains("facts = setupFacts,"))
        assertTrue(src.contains("packageInstallRunning = editorInstallRunning"))
        assertTrue(src.contains(".optionForRoute(screen.route)"))
        assertTrue(src.contains("SetupLockPolicy.option(option, chromeLock)"))
        // A paused tab does not navigate: the tap shows the sentence instead.
        val barStart = src.indexOf("private fun FlatBottomBar(")
        val barEnd = src.indexOf("private fun EditorNavRevealHandle(")
        assertTrue("the bottom bar is gone", barStart >= 0 && barEnd > barStart)
        val bar = src.substring(barStart, barEnd)
        assertTrue(bar.contains("val locked = !verdict.allowed"))
        assertTrue(bar.contains("if (locked) {"))
        assertTrue(bar.contains("verdict.message?.let(onLockMessage)"))
        assertTrue(bar.contains("onNavigate(screen)"))
        // ONE lambda for the tab and for the guided tour, which performs the click
        // of the control it spotlights (round 4): beats 7 and 9 are these tabs.
        assertTrue(bar.contains("val onTabTap: () -> Unit = {"))
        assertTrue(bar.contains("GuideAnchor.modifier(tabAnchorId, onClick = onTabTap)"))
        // A paused tab also LOOKS paused, before it is tapped.
        assertTrue(bar.contains("Icons.Default.Lock"))
        assertTrue(bar.contains("idleColor.copy(alpha = 0.45f)"))
        // The sentence has somewhere to appear: the scaffold grew a snackbar host
        // — a line of text, not a wall, and it blocks nothing.
        assertTrue(src.contains("snackbarHost = { SnackbarHost(snackbarHostState) }"))
        assertTrue(src.contains("scope.launch { snackbarHostState.showSnackbar(message) }"))
        // The sentence arrives once when the pause BEGINS, not only after a
        // refused tap — and once per episode, so the three stages of one download
        // do not stack three snackbars.
        assertTrue(src.contains("LaunchedEffect(chromeLock.locked) {"))
        assertTrue(src.contains("if (chromeLock.locked) chromeLock.message?.let(showLockMessage)"))
        // The law that keeps a pause from being a prison: the surface that SHOWS
        // the install is never paused, and the POLICY says which one that is.
        val policy = source("app/src/main/java/com/codeci/ide/ui/terminal/SetupState.kt")
        assertTrue(policy.contains("fun watchOption(reason: ChromeLockReason): ChromeOption"))
        assertTrue(policy.contains("if (reason == ChromeLockReason.PACKAGE_INSTALL) ChromeOption.EDITOR else ChromeOption.TERMINAL"))
        assertTrue(policy.contains("option == watchOption(lock.reason) -> SetupVerdict.Allowed"))
    }

    @Test
    fun `the editor reports the install only it can see, and clears it on the way out`() {
        val editor = source("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt")
        // The Output Panel's install flag is the only signal that says "one job is
        // being installed right now". `busy` alone is not enough: a run — or a
        // Flask server the tour itself starts — keeps it true for minutes, and
        // pausing the app for the user's own program would be a prison.
        assertTrue(editor.contains("val packageInstallRunning = outputState.busy && outputState.installing"))
        assertTrue(editor.contains("EditorChromeState.setInstallRunning(packageInstallRunning)"))
        // Cleared on dispose: a stale "installing" would leave the whole app
        // paused behind an install that finished with the screen.
        assertTrue(editor.contains("EditorChromeState.setInstallRunning(false)"))
        // The bridge, and why it exists: the tabs live in MainActivity, the
        // install lives in the editor, and no parameter reaches between them.
        val bridge = source("app/src/main/java/com/codeci/ide/ui/editor/EditorChromeState.kt")
        assertTrue(bridge.contains("val installRunning: StateFlow<Boolean>"))
        assertTrue(bridge.contains("fun setInstallRunning(running: Boolean)"))
        assertTrue(source(main).contains("val editorInstallRunning by EditorChromeState.installRunning.collectAsState()"))
        // The flag itself: default false, set true on the ONE install path, and
        // cleared by both of its exits.
        val vm = source("app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt")
        assertTrue(vm.contains("val installing: Boolean = false"))
        assertEquals("only confirmInstall may set installing = true", 1, vm.split("installing = true").size - 1)
        assertEquals("both exits of an install clear the flag", 2, vm.split("installing = false,").size - 1)
    }

    @Test
    fun `the editor's own chrome pauses for a package install and never for the userland`() {
        val editor = source("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt")
        // ☰ and RUN ▶ answer the lock with the SAME sentence the tabs use, from
        // the SAME pure policy — one install, one explanation.
        assertTrue(editor.contains("com.codeci.ide.ui.terminal.SetupLockPolicy.editorChromeLocked(editorLock)"))
        assertTrue(editor.contains("if (editorChromeLocked) showChromeLock() else toggleDrawer()"))
        assertTrue(editor.contains("val onRunTap: () -> Unit = {"))
        assertTrue(editor.contains("snackbarHostState.showSnackbar(message)"))
        // The edge swipe is the same option as ☰: a lock you can swipe around is
        // not a lock.
        assertTrue(editor.contains("!editorChromeLocked,"))
        // And the reason the editor is reached at all is a PACKAGE install, never
        // the userland's own: typing and `cc` do not wait for a download
        // (Phase 44.1's law, pinned by SetupGatePolicyTest).
        val policy = source("app/src/main/java/com/codeci/ide/ui/terminal/SetupState.kt")
        assertTrue(policy.contains("lock.reason == ChromeLockReason.PACKAGE_INSTALL"))
    }
}
