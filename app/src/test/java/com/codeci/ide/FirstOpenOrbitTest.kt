package com.codeci.ide

import com.codeci.ide.ui.projects.OrbitSample
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * First-run acceptance and sample contract: a fresh user gets a short, honest
 * introduction, then lands on an original one-file game that runs offline.
 */
class FirstOpenOrbitTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val page: String
        get() = OrbitSample.FILES.first { it.relativePath == OrbitSample.ENTRY_FILE }.content

    private val main: String
        get() = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/MainActivity.kt").readText()

    private val intro: String
        get() = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/FirstRunIntroScreen.kt").readText()

    @Test
    fun `the seed writes a normal web project and a human-readable readme`() {
        val root = tmp.newFolder("projects")
        val project = OrbitSample.ensure(root)
        assertNotNull("a fresh projects root must be seeded", project)
        assertEquals(OrbitSample.NAME, project!!.name)
        assertTrue(File(project, OrbitSample.ENTRY_FILE).isFile)
        assertTrue(File(project, ".codec/project.json").isFile)
        assertTrue(File(project, "README.md").readText().contains("INNER"))
        assertEquals("web", OrbitSample.TYPE)
        assertTrue(File(project, ".codec/project.json").readText().contains("web"))
    }

    @Test
    fun `seeding is once-only and never overwrites an existing project`() {
        val root = tmp.newFolder("projects")
        val project = OrbitSample.ensure(root)!!
        val entry = File(project, OrbitSample.ENTRY_FILE)
        entry.writeText("<!-- the owner's own edits -->")
        assertNull(OrbitSample.ensure(root))
        assertEquals("<!-- the owner's own edits -->", entry.readText())
    }

    @Test
    fun `a deleted starter stays deleted when the introduction is replayed`() {
        val root = tmp.newFolder("projects")
        val project = OrbitSample.ensure(root)!!
        project.deleteRecursively()
        assertNull(OrbitSample.ensure(root))
        assertFalse(File(root, OrbitSample.NAME).exists())
    }

    @Test
    fun `the game is self-contained and makes no remote requests`() {
        val page = page
        for (forbidden in listOf("http://", "https://", "<script src=", "<link ", "@import", "//cdn")) {
            assertFalse("the first-run game must not reach outside itself: $forbidden", page.contains(forbidden))
        }
        assertFalse("the Kotlin raw string has no interpolated template literal", page.contains("\${"))
        assertTrue(page.contains("<title>Orbit Shift — CodeC Arcade</title>"))
        assertTrue(page.contains("<canvas"))
        assertTrue(page.contains("requestAnimationFrame"))
        assertTrue(page.contains("prefers-reduced-motion"))
    }

    @Test
    fun `the game is usable with touch buttons and a keyboard`() {
        val page = page
        assertTrue(page.contains("data-lane=\"inner\"") && page.contains("data-lane=\"outer\""))
        assertTrue("game controls should exceed the 48px mobile touch baseline", page.contains("min-height:52px"))
        assertTrue(page.contains("pointerdown"))
        assertTrue(page.contains("\"arrowup\"") && page.contains("\"arrowdown\""))
        assertTrue(page.contains("if (startLane === \"inner\" || startLane === \"outer\") lane = startLane;"))
        assertTrue(page.contains("startButton.addEventListener(\"click\", function () { launch(lane); });"))
        assertTrue(page.contains("aria-pressed"))
        assertTrue(page.contains("aria-live=\"polite\""))
        assertTrue(page.contains("<strong>Tap a ring</strong>"))
        assertTrue(page.contains("shields = 3"))
        assertTrue(page.contains("localStorage"))
    }

    @Test
    fun `the page can scroll in CodeC's shorter embedded preview`() {
        val page = page
        assertTrue(page.contains("html { height:100%; }"))
        assertTrue(page.contains("min-height:100%"))
        assertFalse(page.contains("100vh") || page.contains("100dvh"))
        assertTrue(page.contains("aspect-ratio:1.15"))
        val body = page.substringAfter("body {").substringBefore("}")
        assertFalse(body.contains("overflow:hidden"))
    }

    @Test
    fun `first run opens with a one second logo and timed swipe stories`() {
        assertTrue(intro.contains("const val FIRST_RUN_LOGO_DURATION_MS = 1_000L"))
        assertTrue(main.contains("firstRunLaunchStartedAtMs = launchStartedAtMs"))
        assertTrue(main.contains("SystemClock.elapsedRealtime() - startedAt"))
        assertTrue(intro.contains("logoRemainingMs: Long"))
        assertTrue(intro.contains("delay(logoRemainingMs)"))
        assertTrue(intro.contains("detectHorizontalDragGestures"))
        assertTrue(intro.contains("horizontalDrag < -swipeThresholdPx"))
        assertTrue(intro.contains("CodecMotion.storyTimer(remainingMs)"))
        assertTrue(intro.contains("private const val INTRO_PAGE_COUNT = 5"))
        assertTrue(intro.contains("if (page < INTRO_PAGE_COUNT - 1) page++"))
        assertTrue(intro.contains("progressBarRangeInfo = ProgressBarRangeInfo"))
        assertTrue(intro.contains("Pause timer"))
        assertTrue(intro.contains("Keep the whole loop together."))
        assertTrue(intro.contains("Nothing downloads during this tour."))
        assertTrue(intro.contains("Meet Orbit Shift."))
    }

    @Test
    fun `the privacy acknowledgement is a real gate and is skippable only to the agreement`() {
        assertTrue(intro.contains("Skip to agreement"))
        assertTrue(intro.contains("I understand and accept this privacy summary."))
        assertTrue(intro.contains("Check this box to continue."))
        assertTrue(intro.contains("privacyAccepted"))
        assertTrue(intro.contains("Agree & start coding"))
        assertFalse("first-run education must not pre-request Android permissions", intro.contains("requestPermissions"))
    }

    @Test
    fun `only an accepted intro seeds and opens Orbit Shift`() {
        assertTrue(main.contains("FirstRunIntroScreen("))
        assertTrue(main.contains("onStart = { if (!firstRunPreparing) firstRunAccepted = true }"))
        val accepted = main.substringAfter("LaunchedEffect(firstRunAccepted, firstLaunchComplete)")
            .substringBefore("if (firstLaunchComplete == null)")
        val acceptedCode = RepoFiles.codeOnly(accepted)
        val seed = acceptedCode.indexOf("OrbitSample.ensure(root)")
        val saved = acceptedCode.indexOf("EditorLaunchState.save(activity, OrbitSample.NAME")
        val completed = acceptedCode.indexOf("settingsManager.setFirstLaunchComplete(true)")
        val acceptanceReset = acceptedCode.indexOf("firstRunAccepted = false", completed)
        assertTrue("the explicit accept action must gate the seed", main.contains("!firstRunAccepted"))
        assertTrue("the sample is seeded before launch state is saved", seed >= 0 && seed < saved)
        assertTrue("the editor launch state must be saved before completing first run", saved >= 0 && saved < completed)
        assertTrue("clear saved acceptance so Settings replay cannot bypass the gate", acceptanceReset > completed)
        assertTrue(main.contains("Screen.Editor.createRoute(OrbitSample.ENTRY_FILE, OrbitSample.NAME)"))
        assertFalse("the starting game is no longer SnakeSample", main.contains("SnakeSample"))
    }

    @Test
    fun `returning users skip onboarding and keep the normal resume decision`() {
        assertTrue(main.contains("if (firstLaunchComplete == false && !com.codeci.ide.ui.crash.SafeMode.active)"))
        assertTrue(main.contains("resumeOffer == com.codeci.ide.ui.projects.ResumeOffer.CONTINUE_IN_PLACE"))
        assertTrue(main.contains("launchState?.let { Screen.Editor.createRoute(it.fileName, it.projectName) }"))
        assertTrue(main.contains("firstLaunchComplete = settingsManager.firstLaunchCompleteFlow.first()"))
    }

    @Test
    fun `safe mode bypasses both intro and sample seeding`() {
        val introGate = "if (firstLaunchComplete == false && !com.codeci.ide.ui.crash.SafeMode.active)"
        assertTrue(main.contains(introGate))
        val safeMode = main.substringAfter("// Preserve the crash-recovery escape hatch")
            .substringBefore("// The intro is the first-run UI")
        assertTrue(safeMode.contains("!com.codeci.ide.ui.crash.SafeMode.active"))
        assertFalse(safeMode.contains("OrbitSample.ensure"))
        assertTrue(safeMode.contains("firstRunAccepted = false"))
        assertTrue(main.contains("firstLaunchComplete != false || com.codeci.ide.ui.crash.SafeMode.active"))
    }

    @Test
    fun `a fresh-install seed failure completes onboarding into the Projects fallback`() {
        assertTrue(main.contains("}.getOrDefault(false)"))
        assertTrue(main.contains("if (sampleIsLaunchable)"))
        assertTrue(main.contains("settingsManager.setFirstLaunchComplete(true)"))
        assertTrue(main.contains("else -> Screen.FileManager.route"))
        assertTrue(main.contains("firstOpenSample ->"))
    }

    @Test
    fun `the About action requests an intro replay on the next launch`() {
        val settings = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt",
        ).readText()
        assertTrue(settings.contains("Replay the CodeC introduction"))
        assertTrue(settings.contains("settingsManager.setFirstLaunchComplete(false)"))
        assertTrue(settings.contains("next launch"))
    }

    @Test
    fun `the existing first-launch preference key remains migration safe`() {
        val settings = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/settings/SettingsManager.kt").readText()
        assertTrue(settings.contains("booleanPreferencesKey(\"first_launch_complete\")"))
        assertTrue(settings.contains("firstLaunchCompleteFlow"))
    }
}
