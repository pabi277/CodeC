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

    @Test
    fun `every anchor of the tour is published by the control the plan names`() {
        assertTrue(
            "beat 1: the editor's ☰ is not anchored",
            source(editor).contains("GuideAnchor.modifier(GuideAnchors.EDITOR_DRAWER)")
        )
        // Beats 2 and 3 are decided by the PURE plan, not by an `if` in the
        // drawer: the project box only where switching teaches something, the
        // file box only on the demo's own entry file.
        assertTrue(
            "beat 2: the drawer's project header is not anchored",
            source(drawer).contains("CoachMarkPlan.drawerProjectAnchor(projectName, DemoProjects.NAME)") &&
                source(drawer).contains("headerModifier.then(GuideAnchor.modifier(projectAnchorId))")
        )
        assertTrue(
            "beat 3: the demo's app.py row is not anchored",
            source(drawer).contains("CoachMarkPlan.drawerFileAnchor(") &&
                source(drawer).contains("demoEntryFile = DemoProjects.ENTRY_FILE") &&
                source(drawer).contains("GuideAnchor.modifier(guideAnchorId)")
        )
        assertTrue(
            "beat 4: RUN ▶ is not anchored",
            source(editor).contains("GuideAnchor.modifier(GuideAnchors.EDITOR_RUN)")
        )
        assertTrue(
            "beat 5: the preview's Back is not anchored",
            source(preview).contains("GuideAnchor.modifier(GuideAnchors.PREVIEW_CLOSE)")
        )
        assertTrue(
            "beat 6: the 'Show tabs' handle is not anchored",
            source(main).contains("GuideAnchor.modifier(GuideAnchors.NAV_HANDLE)")
        )
        // Beats 7 and 9 are the bottom bar's own tabs — the tour walks the user
        // to Packages and Terminal instead of hoping they wander there.
        assertTrue(
            "beats 7+9: the bottom bar does not ask the plan which tab to anchor",
            source(main).contains("CoachMarkPlan.tabAnchorFor(screen.route)") &&
                source(main).contains("tabModifier.then(GuideAnchor.modifier(tabAnchorId))")
        )
        assertTrue(
            "beat 8: the Packages install card is not anchored",
            source(modules).contains("GuideAnchors.PACKAGES_CARD") &&
                source(modules).contains("GuideAnchor.modifier(guideAnchorId)")
        )
        assertTrue(
            "beat 10: the terminal status chip is not anchored",
            source(terminalScreen).contains("GuideAnchor.modifier(GuideAnchors.TERMINAL_CHIP)")
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
        com.codeci.ide.ui.guide.GuideAnchors.DRAWER_PROJECT -> "CoachMarkPlan.drawerProjectAnchor("
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
        assertTrue(overlay.contains("CoachMarkPlan.nextStep(seen, chrome)"))
        // Tapping the control advances one step; SKIP TOUR / Back end the tour.
        assertTrue(overlay.contains("onAdvance = { onSeen(CoachMarkPlan.markSeen(seen, step.id)) }"))
        assertTrue(overlay.contains("CoachMarkPlan.markAllSeen(seen)"))
        // Back ends the TOUR before anything else (Phase 49's BackRouter inherits
        // this precedence): a box that Back merely closes would come straight
        // back, because the plan would still return the same unseen step.
        assertTrue(overlay.contains("BackHandler { endTour() }"))
        // The counter that makes it read as one flow instead of ten popups.
        assertTrue(overlay.contains("stepNumber = CoachMarkPlan.steps.indexOf(step) + 1"))
        assertTrue(overlay.contains("stepCount = CoachMarkPlan.steps.size"))
    }

    @Test
    fun `the highlighted control is the only way on, and a tap outside does nothing`() {
        val src = source(coachMarks)
        // The scrim only draws: a Canvas takes no pointer input.
        assertTrue(src.contains("Canvas(Modifier.fillMaxSize())"))
        assertTrue(src.contains("awaitFirstDown(requireUnconsumed = false)"))
        // Inside the hole the tap is NOT consumed, so the real control performs
        // its own action, and that same tap advances the tour.
        assertTrue(src.contains("if (hole.contains(down.position)) {"))
        assertTrue(src.contains("onAdvance()"))
        // Outside: the whole gesture is swallowed — no dismiss (owner: "even tap
        // outside will not end that box") and nothing reaches the UI underneath.
        assertTrue(src.contains("down.consume()"))
        assertTrue(src.contains("event.changes.forEach { it.consume() }"))
        assertTrue(src.contains("if (event.changes.none { it.pressed }) break"))
        assertFalse("a tap outside must not end the box", src.contains("onDismiss"))
        // The hole is the anchor rect padded, never a hard-coded position: the
        // plan is pure and the layout is observed (tablets, landscape, split).
        assertTrue(src.contains("anchorRect.left - padPx"))
        assertTrue(src.contains("TooltipPlacement.place("))
        assertFalse("the overlay must not hard-code a position", src.contains("IntOffset(0, 0)"))
    }

    @Test
    fun `the card has no next button, one exit, and is measured so it fits`() {
        val src = source(coachMarks)
        // "remove the next option": the card carries no forward button at all.
        // Pinned as the BUTTON, not the word: the file's own doc says "the card
        // has no NEXT/GOT IT", and a pin on the bare string would fail on that
        // sentence forever.
        assertFalse("the card still has a GOT IT button", src.contains("Text(\"GOT IT\")"))
        assertFalse("the card must not offer a NEXT", src.contains("Text(\"NEXT\")"))
        // The one button left is an exit, and it says what it exits.
        assertTrue(src.contains("Text(\"SKIP TOUR\")"))
        assertTrue(src.contains("TextButton(onClick = onSkip)"))
        assertEquals("exactly one button on the card", 1, Regex("TextButton\\(").findAll(src).count())
        // The owner's "Not showing the full box guide at one": the height that
        // places the card is MEASURED, so the clamp branch cannot push a taller
        // card over its own hole. The 150dp constant is a first-frame seed only.
        assertTrue(src.contains("onSizeChanged { cardHeightPx = it.height.toFloat() }"))
        assertTrue(src.contains("height = cardHeightPx"))
        assertTrue(src.contains("var cardHeightPx by remember(step.id)"))
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
        assertTrue(source(coachMarks).contains("drawerOpen = drawerOpen"))
        // And the pure plan is what acts on them.
        val planSrc = source(plan)
        assertTrue(planSrc.contains("if (step.inDrawer != chrome.drawerOpen) {"))
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
