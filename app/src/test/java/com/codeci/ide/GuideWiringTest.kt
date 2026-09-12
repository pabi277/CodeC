package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 45 — source-level wiring pins for the guide's two layers, in the shape
 * of `SetupGateWiringTest` and `SettingsAuditTest`.
 *
 * The plans are pure and pinned by [GuidePlanTest], [CoachMarkPlanTest] and
 * [TooltipPlacementTest]; what a host JVM cannot exercise is whether the ANDROID
 * edge actually asks them, in the right order, at the right moment. Each check
 * below is one line of PART_45_1 / PART_45_2 that would otherwise regress
 * silently:
 *  - the guide is the SECOND first-launch gate — after the welcome tiles, before
 *    the Phase 44 setup divert and the NavHost;
 *  - `guide_completed` is written only by SKIP / START CODING (and reset only by
 *    "Reset tips", which touches exactly two keys);
 *  - the three doors back to the guide are wired;
 *  - the tour's ten anchors each have a publisher on the control they name, and
 *    withdraw when that control leaves composition;
 *  - the card has NO forward button and a tap outside does nothing: the
 *    highlighted control is the only way on, SKIP TOUR / Back the only way out
 *    (owner, device round: *"remove the next option … click the option where
 *    showing the guide to the next … even tap outside will not end that box"*);
 *  - the tour's copy names only labels the product really has (`demo_flask`,
 *    `app.py`, `Install`, `Packages`, `Terminal`) — the coach-mark twin of
 *    [com.codeci.ide.ui.guide.GuideVocabulary];
 *  - no third-party showcase library, no new navigation route, no copy in the
 *    Compose edge.
 */
class GuideWiringTest {

    private fun source(path: String): String = RepoFiles.mainSource(path).readText()

    private val main = "app/src/main/java/com/codeci/ide/MainActivity.kt"
    private val guideScreen = "app/src/main/java/com/codeci/ide/ui/guide/GuideScreen.kt"
    private val coachMarks = "app/src/main/java/com/codeci/ide/ui/guide/CoachMarks.kt"
    private val settingsManager = "app/src/main/java/com/codeci/ide/ui/settings/SettingsManager.kt"
    private val settingsScreen = "app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt"
    private val hub = "app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt"
    private val editor = "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
    private val drawer = "app/src/main/java/com/codeci/ide/ui/components/EditorProjectDrawer.kt"
    private val terminalScreen = "app/src/main/java/com/codeci/ide/ui/screens/TerminalScreen.kt"
    private val modules = "app/src/main/java/com/codeci/ide/ui/screens/ModulesScreen.kt"
    private val preview = "app/src/main/java/com/codeci/ide/ui/screens/WebPreviewScreen.kt"
    private val plan = "app/src/main/java/com/codeci/ide/ui/guide/CoachMarkPlan.kt"
    private val demoProjects = "app/src/main/java/com/codeci/ide/ui/projects/DemoProjects.kt"
    private val scaffold = "app/src/main/java/com/codeci/ide/ui/projects/ProjectScaffold.kt"
    private val strings = "app/src/main/res/values/strings.xml"
    private val screenRoutes = "app/src/main/java/com/codeci/ide/ui/navigation/Screen.kt"

    private fun before(src: String, first: String, then: String) {
        val a = src.indexOf(first)
        val b = src.indexOf(then)
        assertTrue("'$first' not found", a >= 0)
        assertTrue("'$then' not found", b >= 0)
        assertTrue("'$first' must come before '$then'", a < b)
    }

    // ---- 45.1 the gate -----------------------------------------------------

    @Test
    fun `the guide is the second first-launch gate, before the divert and the NavHost`() {
        val src = source(main)
        // tiles -> guide -> shell (PART_45_1 §2). The ordering is the whole
        // point: the guide explains the download the terminal is about to show,
        // and it must never compete with the Phase 44 setup bar.
        before(src, "if (firstLaunchComplete == false) {", "GuideScreen(")
        before(src, "GuideScreen(", "val setupLaunchDivert = remember {")
        before(src, "GuideScreen(", "NavHost(")
        // The gate's condition: an explicit re-open, or a flag that is false.
        assertTrue(src.contains("(guideRequested || guideCompleted == false)"))
        // Safe mode does LESS at startup, so it never shows the guide — and the
        // flag stays false, so a normal launch still does.
        assertTrue(src.contains("if (!com.codeci.ide.ui.crash.SafeMode.active &&"))
        // …and while the flag is still reading, nothing flashes.
        assertTrue(src.contains("if (guideCompleted == null && !guideRequested) {"))
    }

    @Test
    fun `the guide flag is written only by an explicit user action`() {
        val src = source(main)
        // Read once at startup (a reset affects the NEXT launch, never the
        // screen the user is on).
        assertTrue(src.contains("guideCompleted = settingsManager.guideCompletedFlow.first()"))
        // Written inside the guide's own onFinished — SKIP and START CODING are
        // the same act — and nowhere else in the app.
        assertTrue(src.contains("settingsManager.setGuideCompleted(true)"))
        val writers = RepoFiles.mainKotlinSources()
            .filter { it.name != "SettingsManager.kt" }
            .filter { it.readText().contains("setGuideCompleted(") }
            .map { it.name }
        assertEquals("setGuideCompleted has callers outside MainActivity: $writers", listOf("MainActivity.kt"), writers)
        // No timer, no implicit dismissal: the screen's only exits are the two
        // buttons and back.
        val screen = source(guideScreen)
        assertFalse("the guide must not dismiss itself on a timer", screen.contains("delay("))
        assertFalse(screen.contains("LaunchedEffect"))
        assertTrue(screen.contains("BackHandler { onFinished() }"))
    }

    @Test
    fun `the guide renders the pure plan and carries no copy of its own`() {
        val screen = source(guideScreen)
        assertTrue(screen.contains("GuidePlan.resume(savedIndex)"))
        assertTrue(screen.contains("GuidePlan.canSkip(index)"))
        assertTrue(screen.contains("GuidePlan.isLast(index)"))
        assertTrue(screen.contains("GuidePlan.next(index)"))
        assertTrue(screen.contains("GuidePlan.progress(index)"))
        assertTrue(screen.contains("rememberSaveable"))
        // The copy lives in GuidePlan (host-tested, vocabulary-pinned); a second
        // copy in the Compose edge is how a guide starts lying.
        for (slide in com.codeci.ide.ui.guide.GuidePlan.slides) {
            assertFalse("GuideScreen hard-codes slide '${slide.id}'", screen.contains(slide.title))
            assertFalse("GuideScreen hard-codes slide '${slide.id}'", screen.contains(slide.body))
        }
    }

    @Test
    fun `three doors reopen the guide, and none of them is a navigation route`() {
        // Settings → About → Help & guide.
        val settings = source(settingsScreen)
        assertTrue(settings.contains("title = \"Help & guide\""))
        assertTrue(settings.contains("onClick = onOpenGuide"))
        // Projects hub ⋮ → Guide.
        val hubSrc = source(hub)
        assertTrue(hubSrc.contains("text = { Text(\"Guide\") }"))
        assertTrue(hubSrc.contains("onOpenGuide()"))
        // Editor ☰ drawer footer → Guide.
        val drawerSrc = source(drawer)
        assertTrue(drawerSrc.contains("label = \"Guide\""))
        assertTrue(drawerSrc.contains("onClick = onOpenGuide"))
        // All three are wired in MainActivity to the same local flag.
        val doors = Regex("onOpenGuide = \\{ guideRequested = true \\}").findAll(source(main)).count()
        assertEquals("expected three wired doors, found $doors", 3, doors)
        // The guide is a screen the shell swaps out, not a nav destination: a
        // route would put it in the back stack (and under Phase 49's BackRouter).
        assertFalse("the guide must not become a navigation route", source(screenRoutes).contains("guide"))
    }

    @Test
    fun `reset tips clears exactly the two guide preferences`() {
        val store = source(settingsManager)
        val at = store.indexOf("suspend fun resetGuideTips()")
        assertTrue("resetGuideTips is gone", at >= 0)
        // Up to the function's own closing brace — a fixed window would run into
        // the next setter and count ITS key.
        val end = store.indexOf("\n    }", at)
        assertTrue("resetGuideTips has no closing brace", end > at)
        val block = store.substring(at, end)
        assertTrue(block.contains("it[GUIDE_COMPLETED] = false"))
        assertTrue(block.contains("it[COACH_MARKS_SEEN_CSV] = \"\""))
        // Nothing else is reset: no project, no file, no other preference.
        assertEquals("resetGuideTips writes more than the two guide keys", 2, Regex("it\\[").findAll(block).count())
        // One edit = one atomic write, and the audit law's shape: commit() is
        // DataStore's job here (unlike Phase 44's SharedPreferences ledger).
        assertTrue(block.contains("context.dataStore.edit {"))
        // The row that calls it lives in Settings, next to the guide door.
        val settings = source(settingsScreen)
        assertTrue(settings.contains("title = \"Reset tips\""))
        assertTrue(settings.contains("settingsManager.resetGuideTips()"))
    }

    @Test
    fun `the two guide keys are declared with show-it defaults and read outside the store`() {
        val store = source(settingsManager)
        assertTrue(store.contains("booleanPreferencesKey(\"guide_completed\")"))
        assertTrue(store.contains("stringPreferencesKey(\"coach_marks_seen_csv\")"))
        // Defaults on the show-it side: a missing flag means "never seen".
        assertTrue(store.contains("it[GUIDE_COMPLETED] ?: false"))
        assertTrue(store.contains("it[COACH_MARKS_SEEN_CSV] ?: \"\""))
        // SettingsKeysHaveReadersTest enforces this generically; the pin here
        // names the readers so a failure says which wire came loose.
        val src = source(main)
        assertTrue(src.contains("settingsManager.guideCompletedFlow.first()"))
        assertTrue(src.contains("settingsManager.coachMarksSeenCsvFlow.first()"))
        assertTrue(src.contains("settingsManager.setCoachMarksSeenCsv("))
    }

    // ---- 45.2 the coach marks ---------------------------------------------

    /**
     * The `GuideAnchor.modifier( … )` call that publishes [idExpr], as source.
     * Round 4 made these calls multi-line (an anchor publishes its click beside
     * its rect), so a pin on the old one-line shape would pass on a call that no
     * longer publishes anything.
     */
    private fun anchorCall(src: String, idExpr: String): String {
        val at = src.indexOf(idExpr)
        assertTrue("'$idExpr' is not published anywhere", at >= 0)
        val start = src.lastIndexOf("GuideAnchor.modifier(", at)
        assertTrue("'$idExpr' is not inside a GuideAnchor.modifier call", start >= 0)
        return src.substring(start, (start + 320).coerceAtMost(src.length))
    }

    @Test
    fun `every anchor of the tour is published by the control the plan names`() {
        assertTrue(
            "beat 1: the editor's ☰ is not anchored",
            anchorCall(source(editor), "GuideAnchors.EDITOR_DRAWER").isNotEmpty()
        )
        // Beat 2 is published in EVERY state of the drawer header — another
        // project, the demo already open, scratch mode — because the header always
        // opens the picker, and a tour the owner wants "1st to last without skip
        // anything" must not depend on which project happens to be open. Beat 3
        // stays a PURE decision: the file box only on the demo's own entry file.
        assertTrue(
            "beat 2: the drawer's project header is not anchored unconditionally",
            anchorCall(source(drawer), "GuideAnchors.DRAWER_PROJECT").isNotEmpty() &&
                !source(drawer).contains("drawerProjectAnchor") &&
                !source(drawer).contains("projectAnchorId")
        )
        assertTrue(
            "beat 3: the demo's app.py row is not anchored",
            source(drawer).contains("CoachMarkPlan.drawerFileAnchor(") &&
                source(drawer).contains("demoEntryFile = DemoProjects.ENTRY_FILE") &&
                anchorCall(source(drawer), "GuideAnchor.modifier(guideAnchorId").isNotEmpty()
        )
        assertTrue(
            "beat 4: RUN ▶ is not anchored",
            anchorCall(source(editor), "GuideAnchors.EDITOR_RUN").isNotEmpty()
        )
        assertTrue(
            "beat 5: the preview's Back is not anchored",
            anchorCall(source(preview), "GuideAnchors.PREVIEW_CLOSE").isNotEmpty()
        )
        // Beat 6 has TWO publishers and one id: the thin reveal handle while the
        // bar is hidden, and the bar itself while it is visible. Round 2 anchored
        // only the handle, so the beat existed just while the keyboard happened to
        // be up — a tour that is "1st to last" cannot depend on that.
        assertEquals(
            "beat 6: the reveal handle AND the visible bar must both anchor it",
            2,
            // The call is multi-line now (round 4 publishes a click beside the
            // rect), so the pin counts the PUBLISHERS, not one exact shape.
            source(main).split("GuideAnchor.modifier(GuideAnchors.NAV_HANDLE").size - 1
        )
        // Beats 7 and 9 are the bottom bar's own tabs — the tour walks the user
        // to Packages and Terminal instead of hoping they wander there.
        assertTrue(
            "beats 7+9: the bottom bar does not ask the plan which tab to anchor",
            source(main).contains("CoachMarkPlan.tabAnchorFor(screen.route)") &&
                source(main).contains("GuideAnchor.modifier(tabAnchorId, onClick = onTabTap)")
        )
        assertTrue(
            "beat 8: the Packages install card is not anchored",
            source(modules).contains("GuideAnchors.PACKAGES_CARD") &&
                anchorCall(source(modules), "GuideAnchor.modifier(guideAnchorId").isNotEmpty()
        )
        assertTrue(
            "beat 10: the terminal status chip is not anchored",
            anchorCall(source(terminalScreen), "GuideAnchors.TERMINAL_CHIP").isNotEmpty()
        )
        // And no anchor id is left without a publisher (a spotlight on nothing).
        val all = RepoFiles.mainKotlinSources()
            .filter { it.name != "CoachMarkPlan.kt" }
            .joinToString("\n") { it.readText() }
        for (step in com.codeci.ide.ui.guide.CoachMarkPlan.steps) {
            // Published either by naming the constant at the control, or by
            // asking one of the plan's pure helpers (the two drawer beats and the
            // two tab beats, where "which control" is a decision, not a literal).
            val byConstant = all.contains("GuideAnchors." + constantFor(step.anchorId))
            val byHelper = all.contains(helperFor(step.anchorId))
            assertTrue(
                "anchor '${step.anchorId}' has no publisher in the app",
                byConstant || byHelper
            )
        }
    }

    /** The pure helper that decides whether this anchor is published at all. */
    private fun helperFor(anchorId: String): String = when (anchorId) {
        com.codeci.ide.ui.guide.GuideAnchors.DRAWER_FILE -> "CoachMarkPlan.drawerFileAnchor("
        com.codeci.ide.ui.guide.GuideAnchors.NAV_TAB_PACKAGES,
        com.codeci.ide.ui.guide.GuideAnchors.NAV_TAB_TERMINAL -> "CoachMarkPlan.tabAnchorFor("
        else -> "\u0000nothing-publishes-it"
    }

    /** `editor_drawer` → `EDITOR_DRAWER`: the constant name for an anchor id. */
    private fun constantFor(anchorId: String): String =
        anchorId.split('_').joinToString("_") { it.uppercase() }

    @Test
    fun `an anchor withdraws when its control leaves composition`() {
        val src = source(coachMarks)
        // Without this the "Show tabs" mark would fire at a handle that is gone:
        // the exact failure the visibility law exists to prevent. Round 4 keys
        // the effect on the click's PRESENCE too, so an anchor that stops being
        // clickable also stops offering one to the overlay.
        assertTrue(src.contains("DisposableEffect(id, actionable)"))
        assertTrue(src.contains("onDispose { GuideAnchorRegistry.withdraw(id, owner) }"))
        assertTrue(src.contains("coordinates.boundsInWindow()"))
        // ONE id can have two publishers that are never on screen together (beat
        // 6: the bar while it is visible, the thin handle while it is hidden), and
        // a swap disposes one and composes the other in the same recomposition. A
        // withdrawal is honoured only by the site that still owns the id, or the
        // leaving site wipes the arriving one's rect and click and the beat goes
        // dark on the keyboard transition it exists to teach.
        assertTrue(src.contains("private val owners: SnapshotStateMap<String, Any>"))
        assertTrue(src.contains("val owner = remember(id) { Any() }"))
        val withdraw = src.substring(
            src.indexOf("fun withdraw(id: String, owner: Any)"),
            src.indexOf("fun rect(id: String)")
        )
        assertTrue(withdraw.contains("if (owners[id] !== owner) return"))
        // An empty rect is never a visible anchor.
        assertTrue(src.contains("if (rect.width <= 0f || rect.height <= 0f) return"))
        // Leaving composition withdraws the CLICK as well as the rect — a stale
        // lambda in a process-wide map is a tap on a control that is gone.
        assertTrue(withdraw.contains("rects.remove(id)"))
        assertTrue(withdraw.contains("actions.remove(id)"))
        assertTrue(withdraw.contains("owners.remove(id)"))
    }

    @Test
    fun `every clickable anchor publishes the click the tour performs`() {
        // Round 4, from the owner's phone: *"1st click disappear the massage and
        // i have to click 2nd time to really work but if someone don't click 2nd
        // time it just cut off the flow of tutorial"*. The overlay no longer
        // leaves the tap to Compose's pass-through (the advance recomposes the
        // host, and a click whose node is rebuilt mid-gesture never lands): each
        // anchor publishes its control's OWN click and the overlay performs it.
        // So an anchor without a click is a beat that can only dismiss its box —
        // which is the bug. These pins name the lambda each beat performs.
        val expected = listOf(
            editor to ("GuideAnchors.EDITOR_DRAWER" to "onClick = onDrawerTap"),
            drawer to ("GuideAnchors.DRAWER_PROJECT" to "onClick = onSwitchProject"),
            drawer to ("GuideAnchor.modifier(guideAnchorId" to "onClick = onOpenOrToggle"),
            editor to ("GuideAnchors.EDITOR_RUN" to "onClick = onRunTap"),
            preview to ("GuideAnchors.PREVIEW_CLOSE" to "onClick = onNavigateBack"),
            main to ("GuideAnchor.modifier(tabAnchorId" to "onClick = onTabTap"),
            modules to ("GuideAnchor.modifier(guideAnchorId" to "onClick = cardClick")
        )
        for ((path, pair) in expected) {
            val (idExpr, click) = pair
            assertTrue(
                "${path.substringAfterLast('/')} publishes $idExpr without $click",
                anchorCall(source(path), idExpr).contains(click)
            )
        }
        // Beat 6's TWO publishers, and why only one of them has a click: the
        // thin handle IS the control (tap = reveal the bar), while the bar as a
        // whole is not — a bar-wide hole resolves a tap to the tab underneath
        // (GuideTapPolicy picks the most specific anchored click), and a tap on
        // a tab the tour does not use is left to that tab.
        val handle = source(main).substring(source(main).indexOf("private fun EditorNavRevealHandle("))
        assertTrue(
            "the reveal handle must publish its own click",
            anchorCall(handle, "GuideAnchors.NAV_HANDLE").contains("onClick = onReveal")
        )
        val bar = source(main).substring(
            source(main).indexOf("private fun FlatBottomBar("),
            source(main).indexOf("private fun EditorNavRevealHandle(")
        )
        assertFalse(
            "the bar as a whole must not own a click — its tabs do",
            anchorCall(bar, "GuideAnchors.NAV_HANDLE").contains("onClick")
        )
        // Beat 8's card publishes the click of the button it is REALLY showing:
        // INSTALL, or VIEW SETUP while the userland is not usable, or nothing at
        // all when the package is installed (its buttons are RUN / UNINSTALL /
        // REINSTALL, and none of them is what the box teaches). Publishing
        // `onInstall` unconditionally would turn a tap on a highlighted card into
        // a REINSTALL the user never asked for.
        val modulesSrc = source(modules)
        val cardClick = modulesSrc.substring(
            modulesSrc.indexOf("val cardClick: (() -> Unit)? = when {"),
            modulesSrc.indexOf("val cardModifier =")
        )
        assertTrue(cardClick.contains("isInstalled -> null"))
        assertTrue(cardClick.contains("setupRefusal != null -> onViewSetup"))
        assertTrue(cardClick.contains("else -> onInstall"))
        // Beat 10 is a label, not a control: nothing to perform, and publishing
        // a fake click would be a lie the user can tap.
        assertFalse(
            "the terminal status chip has no click to publish",
            anchorCall(source(terminalScreen), "GuideAnchors.TERMINAL_CHIP").contains("onClick")
        )
        // The registry keeps ONE stable wrapper per anchor, reading the latest
        // click through rememberUpdatedState: a process-wide map must never hold
        // a lambda that captured last frame's state.
        val src = source(coachMarks)
        assertTrue(src.contains("val current by rememberUpdatedState(onClick)"))
        assertTrue(src.contains("GuideAnchorRegistry.publishAction(id, { current?.invoke() }, owner)"))
        assertTrue(src.contains("fun perform(id: String): Boolean"))
    }

    @Test
    fun `the overlay sits above the scaffold and asks the pure plan`() {
        val src = source(main)
        // Above the scaffold, because the handle it spotlights lives in the
        // scaffold's bottomBar; at the window origin, because anchors publish
        // boundsInWindow().
        before(src, "Box(modifier = Modifier.fillMaxSize()) {", "Scaffold(")
        before(src, "Scaffold(", "GuideCoachMarks(")
        // The host asks the plan — it does not re-implement it. One tour, so no
        // `surface` argument and no per-arrival counter any more.
        assertTrue(src.contains("seen = coachSeen"))
        assertTrue(src.contains("CoachMarkPlan.serializeSeen(next)"))
        assertFalse(
            "the two-marks-per-arrival host is gone",
            src.contains("arrivalKey") || src.contains("surfaceForRoute")
        )
        val overlay = source(coachMarks)
        assertTrue(overlay.contains("ChromeState.of("))
        assertTrue(overlay.contains("CoachMarkPlan.nextStep(seen, chrome, stalled)"))
        // Tapping the highlighted control advances one beat — and that is the
        // ONLY thing that moves the tour. Round 3 deleted both of round 2's
        // exits, so nothing in the product can spend a beat the user never saw.
        // Pinned as CODE, not as a word: this file's own comments quote the
        // owner's "skip" sentence, so a pin on the bare string would pass prose.
        assertTrue(overlay.contains("onAdvance = { onSeen(CoachMarkPlan.markSeen(seen, step.id)) }"))
        assertFalse("round 2's tour-ending skip is gone", overlay.contains("markAllSeen"))
        assertFalse("Back must not end the tour", overlay.contains("BackHandler {"))
        assertFalse(
            "Back must not be imported for the tour",
            overlay.contains("androidx.activity.compose.BackHandler")
        )
        // The counter that makes it read as one flow instead of ten popups.
        assertTrue(overlay.contains("stepNumber = CoachMarkPlan.steps.indexOf(step) + 1"))
        assertTrue(overlay.contains("stepCount = CoachMarkPlan.steps.size"))
        // VIEW AGAIN is the host's job: clear the one key, and start the replay
        // where beat 1 lives (the editor), not on whatever tab the tour ended on.
        assertTrue(src.contains("onReplay = {"))
        assertTrue(src.contains("val fresh = CoachMarkPlan.replay()"))
        assertTrue(src.contains("setCoachMarksSeenCsv(CoachMarkPlan.serializeSeen(fresh))"))
        assertTrue(src.contains("navController.navigate(Screen.Editor.createRoute(null))"))
        // The replay arrives the way a tab tap would — same route factory, same
        // restoreState — instead of inventing a navigation of its own.
        val replayAt = src.indexOf("onReplay = {")
        assertTrue("the host has no replay branch", replayAt >= 0)
        val replayNav = src.substring(replayAt, src.indexOf("\n        )", replayAt))
        assertTrue(replayNav.contains("restoreState = true"))
        assertTrue(replayNav.contains("navRevealed = false"))
    }

    @Test
    fun `one tap inside the hole does both halves, and a tap outside does nothing`() {
        val src = source(coachMarks)
        // The scrim only draws: a Canvas takes no pointer input.
        assertTrue(src.contains("Canvas(Modifier.fillMaxSize())"))
        assertTrue(src.contains("awaitFirstDown(requireUnconsumed = false)"))
        // Outside: the whole gesture is swallowed — no dismiss (owner: "even tap
        // outside will not end that box") and nothing reaches the UI underneath.
        assertTrue(src.contains("if (!hole.contains(down.position)) {"))
        assertTrue(src.contains("down.consume()"))
        assertTrue(src.contains("event.changes.forEach { it.consume() }"))
        assertTrue(src.contains("if (event.changes.none { it.pressed }) break"))
        assertFalse("a tap outside must not end the box", src.contains("onDismiss"))
        // Inside: the PURE policy picks the control under the finger, the
        // registry performs that control's own click, and only then does the
        // tour advance — one tap, both halves, in that order. The order is the
        // whole fix: advancing first is what let round 3 recompose the host
        // mid-gesture and lose the click it was riding on.
        assertTrue(src.contains("GuideTapPolicy.targetFor("))
        assertTrue(src.contains("targets = GuideAnchorRegistry.tapTargets()"))
        assertTrue(src.contains("GuideAnchorRegistry.perform(target.id)"))
        assertTrue(src.contains("onAdvance()"))
        val performAt = src.indexOf("GuideAnchorRegistry.perform(target.id)")
        val advanceAt = src.indexOf("onAdvance()", performAt)
        assertTrue("the overlay never performs the control's click", performAt >= 0)
        assertTrue("the tour must advance AFTER the click, not before", advanceAt > performAt)
        // A gesture that travels and lifts outside the hole is a scroll, not a
        // tap: it spends no beat and performs no click, so the box stays.
        assertTrue(src.contains("GuideTapPolicy.isTap("))
        assertTrue(src.contains("touchSlop = touchSlop"))
        assertTrue(src.contains("val touchSlop = LocalViewConfiguration.current.touchSlop"))
        // The press decides WHICH control, before anything can move.
        assertTrue(src.contains("tapX = down.position.x"))
        // The hole is the anchor rect padded, never a hard-coded position: the
        // plan is pure and the layout is observed (tablets, landscape, split).
        assertTrue(src.contains("anchorRect.left - padPx"))
        assertTrue(src.contains("TooltipPlacement.place("))
        assertFalse("the overlay must not hard-code a position", src.contains("IntOffset(0, 0)"))
    }

    @Test
    fun `a tour card has no button at all, and the only buttons are at the end`() {
        val src = source(coachMarks)
        // Round 1: "remove the next option". Round 3: "You add the skip option
        // and it's not a trough guide mean it got cut". Both are pinned as the
        // BUTTON, never as the word — this file's own comments quote the owner's
        // sentences, so a pin on the bare string would fail on prose forever.
        assertFalse("the card still has a GOT IT button", src.contains("Text(\"GOT IT\")"))
        assertFalse("the card must not offer a NEXT", src.contains("Text(\"NEXT\")"))
        assertFalse("the tour must not offer a SKIP", src.contains("Text(\"SKIP TOUR\")"))
        assertFalse("no onSkip parameter survives", src.contains("onSkip"))
        // A mid-tour card is copy and a hole, nothing else: the way on is the
        // highlighted control, and there is no way out until the end.
        val tourCard = src.substring(src.indexOf("fun CoachMarkOverlay("))
        assertFalse("a mid-tour card carries no button", tourCard.contains("Button("))
        // "At the end option to close and view again": the finish card is the one
        // card with buttons, and it is drawn only after the last beat.
        val finishCard = src.substring(
            src.indexOf("fun TourFinishedCard("),
            src.indexOf("fun CoachMarkOverlay(")
        )
        assertTrue(finishCard.contains("Text(\"VIEW AGAIN\")"))
        assertTrue(finishCard.contains("Text(\"CLOSE\")"))
        assertTrue(finishCard.contains("TextButton(onClick = onViewAgain)"))
        assertTrue(finishCard.contains("Button(onClick = onClose)"))
        // The owner's "Not showing the full box guide at one": the height that
        // places the card is MEASURED, so the clamp branch cannot push a taller
        // card over its own hole. The 150dp constant is a first-frame seed only.
        assertTrue(src.contains("onSizeChanged { cardHeightPx = it.height.toFloat() }"))
        assertTrue(src.contains("height = cardHeightPx"))
        assertTrue(src.contains("var cardHeightPx by remember(step.id)"))
    }

    @Test
    fun `the tour does not go silent where the picker closed the drawer`() {
        // Beat 2's tap opens the project picker, and the editor has always closed
        // the drawer to do it. Beat 3 is a row inside that drawer, so the walk the
        // owner dictated would stop dead after the pick — no box, nothing to tap,
        // and a lesson that only appears if the user guesses ☰. The host asks the
        // PURE plan whether the beat it waits for is behind the drawer, and the
        // editor reopens it after a project is chosen. Nothing else changes: with
        // no tour mid-flight the flag is false and the picker behaves as before.
        val main = source(main)
        assertTrue(
            "the host must ask the plan, not keep its own idea of the tour",
            main.contains("tourWaitsInDrawer = CoachMarkPlan.nextBeatIsInDrawer(coachSeen)")
        )
        val editorSrc = source(editor)
        assertTrue(
            "EditorScreen must take the fact as a parameter with a safe default",
            editorSrc.contains("tourWaitsInDrawer: Boolean = false")
        )
        val at = editorSrc.indexOf("viewModel.switchContext(context, project.name)")
        assertTrue("the picker's project-chosen branch is gone", at >= 0)
        val afterPick = editorSrc.substring(at, at + 700)
        assertTrue(
            "the drawer must reopen after a pick, and only while the tour waits there",
            afterPick.contains("if (tourWaitsInDrawer) {") &&
                afterPick.contains("uiScope.launch { drawerState.open() }")
        )
        // And the drawer still closes for the picker itself: that behaviour is not
        // the tour's to change.
        assertTrue(editorSrc.contains("uiScope.launch { drawerState.close() }"))
    }

    @Test
    fun `the stall guard passes a beat without spending it, and the end is earned`() {
        val src = source(coachMarks)
        // "Every beat waits" is only safe because of this: a control that can
        // never appear (no Python, so no Flask preview; the tab bar never hidden,
        // so no reveal handle) would otherwise park the tour on that beat with a
        // scrim over the app and no button on the card. The guard times the beat
        // the PLAN names, in memory, and the plan passes it without marking seen.
        assertTrue(src.contains("CoachMarkPlan.waitingOn(seen, chrome, stalled)"))
        assertTrue(src.contains("LaunchedEffect(waiting?.id)"))
        assertTrue(src.contains("delay(CoachMarkPlan.STALL_GUARD_MS)"))
        assertTrue(src.contains("stalled = stalled + id"))
        assertTrue(src.contains("var stalled by remember { mutableStateOf(setOf<String>()) }"))
        // One writer of the seen set, and it is the tap on the control: a stalled
        // beat stays a lesson for the next pass.
        assertEquals(
            "the seen set has exactly one writer",
            1,
            Regex("onSeen\\(").findAll(src).count()
        )
        // The finish card is earned by a tour this composition watched run, not by
        // the preference — or an install that starts complete would be greeted by
        // "that is the whole tour" on every launch.
        assertTrue(src.contains("CoachMarkPlan.isFinished(seen, stalled)"))
        assertTrue(src.contains("var ranThisSession by remember { mutableStateOf(!finished) }"))
        assertTrue(src.contains("LaunchedEffect(finished) { if (!finished) ranThisSession = true }"))
        assertTrue(
            src.contains("if (finished && ranThisSession && !finishClosed && !blockedByForeground)")
        )
        // VIEW AGAIN resets the guard too, so a replay really is all ten beats.
        val replay = src.substring(
            src.indexOf("onViewAgain = {"),
            src.indexOf("modifier = modifier", src.indexOf("onViewAgain = {"))
        )
        assertTrue(replay.contains("stalled = emptySet()"))
        assertTrue(replay.contains("finishClosed = false"))
        assertTrue(replay.contains("onReplay()"))
    }

    @Test
    fun `the tour's copy names only labels the product really has`() {
        // The coach-mark twin of GuideVocabulary: a box that names a project, a
        // file or a button the app does not have is a lie the user can tap.
        val copy = com.codeci.ide.ui.guide.CoachMarkPlan.steps
        // A box is its title AND its body: "Open app.py" names the file in the
        // title, and pinning only the body would demand the copy say it twice.
        fun bodyOf(anchorId: String): String = copy.first { it.anchorId == anchorId }
            .let { it.title + " " + it.body }
        val demo = source(demoProjects)
        val names = com.codeci.ide.ui.projects.DemoProjects.NAME
        val entry = com.codeci.ide.ui.projects.DemoProjects.ENTRY_FILE
        // demo_flask is the bundled demo's real name, and the box says it.
        assertTrue(demo.contains("const val NAME = \"$names\""))
        assertTrue(bodyOf(com.codeci.ide.ui.guide.GuideAnchors.DRAWER_PROJECT).contains(names))
        // app.py is really what the scaffold writes for python-flask.
        assertTrue(demo.contains("const val ENTRY_FILE = \"$entry\""))
        assertTrue(
            "the scaffold no longer writes $entry for the demo's type",
            source(scaffold).contains("ScaffoldFile(\"$entry\", FLASK_APP)")
        )
        assertTrue(bodyOf(com.codeci.ide.ui.guide.GuideAnchors.DRAWER_FILE).contains(entry))
        // "Install" is the real confirm label of the Phase 21.2 prompt (a dialog
        // the scrim cannot point into, so the RUN box names the button instead).
        assertTrue(source(strings).contains("<string name=\"install_prompt_confirm\">Install</string>"))
        assertTrue(bodyOf(com.codeci.ide.ui.guide.GuideAnchors.EDITOR_RUN).contains("Install"))
        // The two tabs the tour walks to are named by their real titles.
        val routes = source(screenRoutes)
        assertTrue(routes.contains("\"Packages\""))
        assertTrue(routes.contains("\"Terminal\""))
        assertTrue(bodyOf(com.codeci.ide.ui.guide.GuideAnchors.NAV_TAB_PACKAGES).contains("Packages"))
        assertTrue(bodyOf(com.codeci.ide.ui.guide.GuideAnchors.NAV_TAB_TERMINAL).contains("Terminal"))
        // The chip's own three words are the labels Phase 44.1 really renders.
        assertTrue(
            "the chip box names states the terminal does not render",
            bodyOf(com.codeci.ide.ui.guide.GuideAnchors.TERMINAL_CHIP).contains("downloading")
        )
    }

    @Test
    fun `marks are suppressed while another surface owns the screen`() {
        val src = source(main)
        val at = src.indexOf("blockedByForeground = exitPromptVisible ||")
        assertTrue("the blocked-by-foreground rule is gone", at >= 0)
        // 1000, not 500: round 4 added the chrome lock to the list, and the
        // comment that says why is part of the block being pinned.
        val block = src.substring(at, at + 1000)
        assertTrue("the exit survey must block marks", block.contains("exitPromptVisible"))
        assertTrue("safe mode must block marks", block.contains("com.codeci.ide.ui.crash.SafeMode.active"))
        // A download that is actually moving blocks; CHECKING (a startup
        // transient) does not, or the marks would never appear on a fresh phone.
        assertTrue(block.contains("SetupStage.DOWNLOADING"))
        assertTrue(block.contains("SetupStage.VERIFYING"))
        assertTrue(block.contains("SetupStage.EXTRACTING"))
        assertFalse("CHECKING is not work", block.contains("SetupStage.CHECKING"))
        // Round 4: the chrome lock pauses the tour too. A box on a tab an install
        // has paused could be spent by a tap that only shows the "hang tight"
        // sentence — and a beat spent is a lesson the tour never teaches again.
        assertTrue("an install that pauses the tabs must pause the tour", block.contains("chromeLock.locked"))
    }

    @Test
    fun `no box is ever cut behind a dialog or a closed drawer`() {
        // The scrim lives in the activity window; an AlertDialog is a window of
        // its own. Without these two signals a box would be drawn UNDER the
        // prompt the user is reading — the owner's "not consistent with flow".
        val editorSrc = source(editor)
        assertTrue(
            "the editor must report its modals",
            editorSrc.contains("EditorChromeState.setDialogOpen(editorModalOpen)")
        )
        val at = editorSrc.indexOf("val editorModalOpen =")
        assertTrue("the editor's modal list is gone", at >= 0)
        val end = editorSrc.indexOf("\n    LaunchedEffect(editorModalOpen)", at)
        assertTrue("the modal list is not followed by its publisher", end > at)
        val modals = editorSrc.substring(at, end)
        for (flag in listOf(
            "showContextPicker",   // the project picker beat 2 opens
            "installPrompt != null", // the Install? prompt beat 4 opens
            "runChooserDefault != null", // the RUN ▶ chooser beat 4 can open
            "showUnsavedDialog",
            "showSaveToProject"
        )) {
            assertTrue("a dialog the editor can open is not reported: $flag", modals.contains(flag))
        }
        assertTrue(
            "the editor must report its drawer",
            editorSrc.contains("EditorChromeState.setDrawerOpen(editorDrawerOpen)") &&
                editorSrc.contains("drawerState.currentValue == DrawerValue.Open")
        )
        assertTrue(
            "leaving the editor must clear both signals",
            editorSrc.contains("EditorChromeState.setDialogOpen(false)") &&
                editorSrc.contains("EditorChromeState.setDrawerOpen(false)")
        )
        // The host hands them to the plan: the dialog suppresses everything, the
        // drawer decides which beats may show.
        val main = source(main)
        assertTrue(main.contains("val editorDialogOpen by EditorChromeState.dialogOpen.collectAsState()"))
        assertTrue(main.contains("val editorDrawerOpen by EditorChromeState.drawerOpen.collectAsState()"))
        val blockedAt = main.indexOf("blockedByForeground = exitPromptVisible ||")
        assertTrue(blockedAt >= 0)
        assertTrue(
            "a dialog must block the tour",
            main.substring(blockedAt, blockedAt + 400).contains("editorDialogOpen ||")
        )
        assertTrue(main.contains("drawerOpen = editorDrawerOpen,"))
        // The route is the stall guard's only input: it is what tells "this
        // control is missing" from "this control lives on a screen the user has
        // not reached yet".
        assertTrue(main.contains("route = currentDestination?.route,"))
        assertTrue(source(coachMarks).contains("drawerOpen = drawerOpen"))
        // And the pure plan is what acts on them — in BOTH of its decisions, the
        // box and the stall guard: a drawer beat with the drawer shut must neither
        // be cut nor be timed out and skipped.
        val planSrc = source(plan)
        assertEquals(
            "nextStep and waitingOn both gate on the drawer state",
            2,
            planSrc.split("if (step.inDrawer != chrome.drawerOpen) return null").size - 1
        )
        assertTrue(planSrc.contains("val inDrawer: Boolean = false"))
    }

    // ---- the house rules ---------------------------------------------------

    @Test
    fun `the guide adds no dependency, no permission and no DataStore key without a reader`() {
        // No third-party showcase/onboarding library: the overlay is a Canvas and
        // a Card (PART_45_2's rejection of compose-intro-showcase).
        val guideImports = listOf(guideScreen, coachMarks, "app/src/main/java/com/codeci/ide/ui/guide/GuidePlan.kt",
            "app/src/main/java/com/codeci/ide/ui/guide/CoachMarkPlan.kt")
            .flatMap { path ->
                Regex("^import\\s+(.+)$", RegexOption.MULTILINE)
                    .findAll(source(path)).map { it.groupValues[1].trim() }
            }
        val foreign = guideImports.filter {
            !it.startsWith("androidx.") && !it.startsWith("com.codeci.") && !it.startsWith("kotlin") && !it.startsWith("java.")
        }
        assertEquals("the guide pulled in a foreign dependency: $foreign", emptyList<String>(), foreign)
        // No new permission for a guide.
        val manifest = RepoFiles.mainSource("app/src/main/AndroidManifest.xml").readText()
        assertEquals(
            "a guide needs no permission",
            0,
            Regex("uses-permission[^>]*GUIDE").findAll(manifest).count()
        )
        // The dependency catalogue is untouched by this phase: no showcase lib.
        val catalog = RepoFiles.mainSource("gradle/libs.versions.toml").readText()
        for (banned in listOf("showcase", "intro", "onboarding", "appintro", "tooltip")) {
            assertFalse("libs.versions.toml gained '$banned'", catalog.lowercase().contains(banned))
        }
    }

    @Test
    fun `phase 44's setup surfaces are untouched by the guide`() {
        // The gate the guide must not weaken: the setup bar is still rendered
        // from the pure policy, and the terminal-first divert still reads the
        // disk. (SetupGateWiringTest pins the detail; this is the "the guide did
        // not move it" check.)
        val src = source(main)
        assertTrue(src.contains("com.codeci.ide.ui.components.SetupBar("))
        assertTrue(src.contains("SetupGatePolicy.startOnTerminal("))
        assertTrue(src.contains("SetupGatePolicy.barDismissAllowed(setupProgress)"))
        before(src, "GuideScreen(", "com.codeci.ide.ui.components.SetupBar(")
    }
}
