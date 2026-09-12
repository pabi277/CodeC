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
 *  - every coach-mark anchor has a publisher on the control it names, withdraws
 *    when that control leaves composition, and the overlay never swallows the tap
 *    on the control it is teaching;
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

    @Test
    fun `every anchor is published by the control the plan names`() {
        assertTrue(
            "the editor's ☰ is not anchored",
            source(editor).contains("GuideAnchor.modifier(GuideAnchors.EDITOR_DRAWER)")
        )
        assertTrue(
            "RUN ▶ is not anchored",
            source(editor).contains("GuideAnchor.modifier(GuideAnchors.EDITOR_RUN)")
        )
        assertTrue(
            "the 'Show tabs' handle is not anchored",
            source(main).contains("GuideAnchor.modifier(GuideAnchors.NAV_HANDLE)")
        )
        assertTrue(
            "the terminal status chip is not anchored",
            source(terminalScreen).contains("GuideAnchor.modifier(GuideAnchors.TERMINAL_CHIP)")
        )
        assertTrue(
            "the Packages install card is not anchored",
            source(modules).contains("GuideAnchors.PACKAGES_CARD") &&
                source(modules).contains("GuideAnchor.modifier(guideAnchorId)")
        )
        // And no anchor id is left without a publisher (a spotlight on nothing).
        val all = RepoFiles.mainKotlinSources()
            .filter { it.name != "CoachMarkPlan.kt" }
            .joinToString("\n") { it.readText() }
        for (name in listOf("EDITOR_DRAWER", "EDITOR_RUN", "NAV_HANDLE", "PACKAGES_CARD", "TERMINAL_CHIP")) {
            assertTrue("GuideAnchors.$name has no publisher in the app", all.contains("GuideAnchors.$name"))
        }
    }

    @Test
    fun `an anchor withdraws when its control leaves composition`() {
        val src = source(coachMarks)
        // Without this the "Show tabs" mark would fire at a handle that is gone:
        // the exact failure the visibility law exists to prevent.
        assertTrue(src.contains("DisposableEffect(id)"))
        assertTrue(src.contains("onDispose { GuideAnchorRegistry.withdraw(id) }"))
        assertTrue(src.contains("coordinates.boundsInWindow()"))
        // An empty rect is never a visible anchor.
        assertTrue(src.contains("if (rect.width <= 0f || rect.height <= 0f) return"))
    }

    @Test
    fun `the overlay sits above the scaffold and asks the pure plan`() {
        val src = source(main)
        // Above the scaffold, because the handle it spotlights lives in the
        // scaffold's bottomBar; at the window origin, because anchors publish
        // boundsInWindow().
        before(src, "Box(modifier = Modifier.fillMaxSize()) {", "Scaffold(")
        before(src, "Scaffold(", "GuideCoachMarks(")
        // The host asks the plan — it does not re-implement it.
        assertTrue(src.contains("surface = CoachMarkPlan.surfaceForRoute(currentDestination?.route)"))
        assertTrue(src.contains("arrivalKey = currentDestination?.route"))
        assertTrue(src.contains("CoachMarkPlan.serializeSeen(next)"))
        assertTrue(src.contains("seen = coachSeen"))
        val overlay = source(coachMarks)
        assertTrue(overlay.contains("ChromeState.of("))
        assertTrue(overlay.contains("CoachMarkPlan.stepForArrival(surface, seen, chrome, shownThisArrival)"))
        assertTrue(overlay.contains("CoachMarkPlan.markSeen(seen, step.id)"))
        // Two per arrival: the counter resets when the destination changes.
        assertTrue(overlay.contains("LaunchedEffect(arrivalKey) { shownThisArrival = 0 }"))
        // Back closes the mark before anything else (Phase 49's BackRouter
        // inherits this precedence).
        assertTrue(overlay.contains("BackHandler { finish() }"))
    }

    @Test
    fun `a coach mark never swallows the tap on the control it teaches`() {
        val src = source(coachMarks)
        // The scrim only draws: a Canvas takes no pointer input.
        assertTrue(src.contains("Canvas(Modifier.fillMaxSize())"))
        // The tap layer consumes ONLY outside the hole, so the highlighted
        // control performs its own action while the mark closes (45.2 exit 1).
        assertTrue(src.contains("awaitFirstDown(requireUnconsumed = false)"))
        assertTrue(src.contains("if (!hole.contains(down.position)) down.consume()"))
        assertTrue(src.contains("onDismiss()"))
        // The hole is the anchor rect padded, never a hard-coded position: the
        // plan is pure and the layout is observed (tablets, landscape, split).
        assertTrue(src.contains("anchorRect.left - padPx"))
        assertTrue(src.contains("TooltipPlacement.place("))
        assertFalse("the overlay must not hard-code a position", src.contains("IntOffset(0, 0)"))
    }

    @Test
    fun `marks are suppressed while another surface owns the screen`() {
        val src = source(main)
        val at = src.indexOf("blockedByForeground = exitPromptVisible ||")
        assertTrue("the blocked-by-foreground rule is gone", at >= 0)
        val block = src.substring(at, at + 500)
        assertTrue("the exit survey must block marks", block.contains("exitPromptVisible"))
        assertTrue("safe mode must block marks", block.contains("com.codeci.ide.ui.crash.SafeMode.active"))
        // A download that is actually moving blocks; CHECKING (a startup
        // transient) does not, or the marks would never appear on a fresh phone.
        assertTrue(block.contains("SetupStage.DOWNLOADING"))
        assertTrue(block.contains("SetupStage.VERIFYING"))
        assertTrue(block.contains("SetupStage.EXTRACTING"))
        assertFalse("CHECKING is not work", block.contains("SetupStage.CHECKING"))
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
