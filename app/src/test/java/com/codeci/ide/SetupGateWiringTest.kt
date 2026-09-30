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
    private val setupState = "app/src/main/java/com/codeci/ide/ui/terminal/SetupState.kt"
    private val editorVm = "app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt"
    private val noticePolicy = "app/src/main/java/com/codeci/ide/ui/editor/NoticePolicy.kt"
    private val pill = "app/src/main/java/com/codeci/ide/ui/components/PillNotice.kt"

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
    fun `the setup strip is retired and its vocabulary with it`() {
        // Phase 58.2 (owner, 2026-09-22) — *"Userland installs silently; one
        // warning when a run needs a download before userland is ready."* The
        // permanent strip is a Remove row in the roadmap, so this pin now holds
        // the REMOVAL: the composable is deleted, the three policy functions
        // that existed only to describe it are deleted, and no shell mounts a
        // bar. A later agent who rebuilds it is undoing an owner row.
        assertFalse(
            "the strip's composable must stay deleted",
            RepoFiles.mainSource(setupBar).isFile
        )
        val state = source(setupState)
        assertFalse("barText described only the strip", state.contains("fun barText("))
        assertFalse(state.contains("fun barVisible("))
        assertFalse(state.contains("fun barDismissAllowed("))
        assertTrue(
            "and the file says why, so nobody re-adds it by accident",
            state.contains("Do not re-add the strip")
        )
        val mainCode = RepoFiles.codeOnly(source(this.main))
        assertFalse(mainCode.contains("SetupBar"))
        assertFalse(mainCode.contains("setupBarText"))
        assertFalse(mainCode.contains("dismissedSetupBar"))
        assertFalse(mainCode.contains("setupBarDismissible"))
        // The ONE sentence that replaced it is the editor's pill.
        val notice = source(noticePolicy)
        assertTrue(notice.contains("fun userlandWarning("))
        assertTrue(notice.contains("NoticeKind.USERLAND_NOT_READY"))
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
        // Phase 58.2 — this census was four: the first-run DIVERT to the
        // Terminal was one of them, and it is retired with the strip (see the
        // reversal pin below). The three that remain are the three ways a user
        // still asks for the terminal themselves: the bottom bar, a project's
        // "open in terminal" hand-off, and the Packages tab's door.
        assertTrue("expected at least three terminal navigations, found $count", count >= 3)
        // The bottom tab bar itself: the Terminal tab never restores.
        assertTrue(main.contains("restoreState = screen !is Screen.Terminal"))
    }

    @Test
    fun `a cold start reads the tools from the disk, and no longer diverts`() {
        val main = source(this.main)
        assertTrue(main.contains("SetupGatePolicy.startOnTerminal("))
        assertTrue(main.contains("SetupGatePolicy.userlandUsable(prefix, phase)"))
        assertTrue(main.contains("UserlandManifest.archName() != null"))
        assertTrue(main.contains("SetupLedgerPrefs.ledger(activity).read().phase"))
        // The start destination itself stays the pre-44 one: a route with
        // arguments as `startDestination` is graph-construction risk, and a
        // navigate() after the first composition is the proven path.
        assertTrue(main.contains("val startDestination = remember(launchState, firstOpenSample) {"))
        assertTrue(main.contains("?: Screen.FileManager.route"))
        assertFalse(
            "a route with arguments must not become the graph's start destination",
            main.contains("setupFirstRun -> Screen.Terminal.createRoute(null)")
        )
        // 58.2 — and the start route no longer decides anything about the
        // setup: the read below only feeds `ResumePolicy` (no divert, no
        // "wait until settled", see the reversal pin).
        assertTrue(main.contains("setupNeedsWatching = setupLaunchDivert"))
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
            // 900: Phase 71.1 made each site's toast a 5-line SessionLabel call between the gate and the send.
            val context = src.substring(maxOf(0, index - 900), index)
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
    fun `the shell keeps the setup truth and mounts no strip`() {
        val src = source(main)
        val banner = src.indexOf("SafeModeBanner(")
        val navHost = src.indexOf("NavHost(")
        assertTrue(banner >= 0 && navHost >= 0)
        assertTrue("the safe-mode banner is still the shell's one announcement", banner < navHost)
        // Setup state remains on the operation-owning screens, not the shell.
        assertTrue(source(terminalScreen).contains("viewModel.setupProgress.collectAsState()"))
        assertTrue(source(modules).contains("terminalViewModel.setupFacts.collectAsState()"))
        assertTrue(src.contains("SetupNoticeBridge.post(report.message)"))
        // …but nothing between the banner and the NavHost is a setup surface.
        assertFalse(
            "the strip is gone from the shell",
            RepoFiles.codeOnly(src.substring(banner, navHost)).contains("SetupBar")
        )
    }

    @Test
    fun `a fresh install opens the editor on the sample, never a locked terminal`() {
        val src = source(main)
        val code = RepoFiles.codeOnly(src)
        // Phase 58.2 — the reversal §58's exit calls "not a locked terminal":
        // the first-run DIVERT is gone (a comment sits where it was), so nothing
        // navigates to the Terminal behind the user's back and no run is parked
        // behind the download.
        assertFalse("the divert flag is gone", code.contains("setupDiverted"))
        assertFalse(
            "no effect waits for the setup to settle before letting the user in",
            code.contains("if (!setupProgress.settled) return@LaunchedEffect")
        )
        // The disk read the divert used to own stays: ResumePolicy's
        // setupNeedsWatching still means "an uninstalled phone owes no card".
        assertTrue(src.contains("val setupLaunchDivert = remember {"))
        assertTrue(src.contains("setupNeedsWatching = setupLaunchDivert"))
        assertTrue(src.contains("SetupGatePolicy.startOnTerminal("))
        // After first-run agreement, the editor opens on the original offline
        // game the app writes — never on the Projects hub first.
        assertTrue(src.contains("firstOpenSample ->"))
        assertTrue(
            src.contains("Screen.Editor.createRoute(OrbitSample.ENTRY_FILE, OrbitSample.NAME)")
        )
    }

    @Test
    fun `the one warning a run owes is the editor's pill, and it obeys the install verdict`() {
        // Phase 58.2 — the owner's row in one pin: the download is offered only
        // when the setup can carry it, and when it cannot the run says ONE
        // sentence instead. The verdict is `confirmInstall`'s own, so the pill
        // and the refusal it stands in for can never disagree.
        val vm = source(editorVm)
        val prompt = vm.indexOf("private fun promptInstall(")
        assertTrue("the gate lives in promptInstall", prompt >= 0)
        val body = vm.substring(prompt, minOf(vm.length, prompt + 1_800))
        assertTrue(body.contains("SetupGatePolicy.can("))
        assertTrue(body.contains("SetupAction.INSTALL_PACKAGE"))
        assertTrue(body.contains("SetupStateBridge.factsOrDisk("))
        assertTrue("the pill is posted, not queued as a dialog", body.contains("noticeFor(warning)"))
        before(body, "NoticePolicy.userlandWarning(", "_installPrompt.value = InstallPromptState(")
        // The pill renders it, and says it in one line of the app's own words.
        assertTrue(source(pill).contains("NoticeKind.USERLAND_NOT_READY"))
        val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("name=\"notice_userland_not_ready\""))
        // The retired vocabulary must not come back as copy (comments stripped:
        // the file's own comment quotes the two sentences this forbids).
        val copy = strings.replace(Regex("(?s)<!--.*?-->"), "")
        assertFalse("no 'hang tight' came back", copy.contains("hang tight"))
        assertFalse("no \"don't close\" came back", copy.contains("close the app"))
        // …and the two things the roadmap's 58 exit says never wait on it: the
        // preview is decided before any tool is probed at all, and C's TCC ships
        // in the APK (`requiredPackage = null`), so a `.c` file cannot be gated.
        val planner = RepoFiles.codeOnly(
            RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/services/LanguageRunPlanner.kt").readText()
        )
        val decide = planner.indexOf("fun decide(")
        assertTrue("the run planner's decide() must still be there", decide >= 0)
        val decideBody = planner.substring(decide, minOf(planner.length, decide + 1_200))
        before(decideBody, "RunDecision.WebPreview(profile)", "NeedsInstall(profile, pkg)")
        // the registry is read raw on purpose: the C profile's mark is the string
        // literal `"c"`, which `codeOnly` blanks out.
        val registry = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/services/LanguageRegistry.kt"
        ).readText()
        val cProfile = registry.indexOf("extensions = listOf(\"c\")")
        assertTrue("the C profile must still be declared", cProfile >= 0)
        assertTrue(
            "C must never be gated behind a download",
            registry.substring(cProfile, cProfile + 600).contains("requiredPackage = null")
        )
    }

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

    @Test
    fun `a shell that is alive re-reads the disk facts`() {
        // Round 6's second half: the facts are re-read a FOURTH time when a
        // session goes alive, because a running bash IS the proof the userland
        // works — and it is the reading that cannot be early, since nothing is
        // alive until the prefix really runs. A stale "not usable" then heals in
        // this session instead of at the next launch, which is what the owner had
        // to do by hand ("if i refresh it it's the open the editor").
        val vm = source(terminalVm)
        val from = vm.indexOf("manager.anyAlive.collect")
        val to = vm.indexOf("Phase 44.2 (device round 1)")
        assertTrue("the anyAlive collector is gone", from >= 0 && to > from)
        val region = vm.substring(from, to)
        assertTrue(region.take(500), region.contains("refreshSetupFacts()"))
        // Off the main thread: three filesystem stats, but the collector is Main.
        assertTrue(region.take(500), region.contains("Dispatchers.IO"))
        assertTrue(region.take(500), region.contains("round 6"))
    }
}
