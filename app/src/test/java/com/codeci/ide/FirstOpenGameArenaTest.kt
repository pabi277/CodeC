package com.codeci.ide

import com.codeci.ide.ui.projects.GameArenaSample
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** First-run contract: a fast CodeC introduction leads to a real multi-file game arena. */
class FirstOpenGameArenaTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val main: String
        get() = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/MainActivity.kt").readText()

    private val intro: String
        get() = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/FirstRunIntroScreen.kt").readText()

    private fun asset(path: String): String = RepoFiles.mainSource(
        "app/src/main/assets/${GameArenaSample.ASSET_DIRECTORY}/$path",
    ).readText()

    private fun seed(root: File): File? = GameArenaSample.ensure(root, ::asset)

    @Test
    fun `the seed writes the whole offline game arena as separate project files`() {
        val root = tmp.newFolder("projects")
        val created = seed(root)
        assertNotNull("a fresh projects root must be seeded", created)
        val project = created!!
        assertEquals(GameArenaSample.NAME, project.name)
        assertEquals("web", GameArenaSample.TYPE)
        assertTrue(File(project, ".codec/project.json").isFile)
        assertTrue(File(project, "README.md").readText().contains("offline-ready game arena"))
        assertEquals(GameArenaSample.PROJECT_FILES.toSet(), project.walkTopDown()
            .filter { it.isFile && it.relativeTo(project).path != ".codec/project.json" }
            .map { it.relativeTo(project).path.replace(File.separatorChar, '/') }
            .toSet())
        assertTrue(File(project, "index.html").isFile)
        assertTrue(File(project, "styles/arena.css").isFile)
        assertTrue(File(project, "js/games/snake.js").isFile)
        assertTrue(File(project, "js/games/blocks.js").isFile)
        assertTrue(File(project, "js/games/tic-tac-toe.js").isFile)
        assertTrue(File(project, ".codec/project.json").readText().contains("web"))
    }

    @Test
    fun `seeding is once-only and never overwrites existing work`() {
        val root = tmp.newFolder("projects")
        val project = seed(root)!!
        val entry = File(project, GameArenaSample.ENTRY_FILE)
        entry.writeText("<!-- the owner's own edits -->")
        assertNull(seed(root))
        assertEquals("<!-- the owner's own edits -->", entry.readText())
    }

    @Test
    fun `a deleted arena stays deleted when the introduction is replayed`() {
        val root = tmp.newFolder("projects")
        val project = seed(root)!!
        project.deleteRecursively()
        assertNull(seed(root))
        assertFalse(File(root, GameArenaSample.NAME).exists())
    }

    @Test
    fun `the home page uses separate local styles and browser modules`() {
        val html = asset("index.html")
        assertTrue(html.contains("<link rel=\"stylesheet\" href=\"./styles/arena.css\">"))
        assertTrue(html.contains("<script type=\"module\" src=\"./js/main.js\"></script>"))
        assertFalse("CSS should live in its own project file", html.contains("<style"))
        assertFalse("game logic should not be embedded in the entry page", html.contains("<script>"))
        val modulePaths = GameArenaSample.PROJECT_FILES.filter { it.endsWith(".js") }
        val imports = Regex("""(?:from\s+|import\s*)["']([^"']+)["']""")
        for (path in modulePaths) {
            for (match in imports.findAll(asset(path))) {
                val specifier = match.groupValues[1]
                assertTrue("non-local import $specifier in $path", specifier.startsWith("."))
                val resolved = resolveImport(path, specifier)
                assertTrue("missing module $resolved imported by $path", resolved in modulePaths)
            }
        }
        assertTrue(GameArenaSample.PROJECT_FILES.contains("README.md"))
    }

    @Test
    fun `the game arena makes no network requests and documents all three games`() {
        val contents = GameArenaSample.PROJECT_FILES.associateWith(::asset)
        for ((path, content) in contents) {
            assertFalse("external network reference in $path", Regex("https?://|//cdn").containsMatchIn(content))
        }
        val html = contents.getValue("index.html")
        assertTrue(html.contains("CodeC Arcade"))
        assertTrue(html.contains("data-open-game=\"snake\""))
        assertTrue(html.contains("data-open-game=\"blocks\""))
        assertTrue(html.contains("data-open-game=\"tic-tac-toe\""))
        assertTrue(contents.getValue("js/games/snake.js").contains("pointerdown"))
        assertTrue(contents.getValue("js/games/snake.js").contains("arrowup"))
        assertTrue(contents.getValue("js/games/blocks.js").contains("highlighted top-left square"))
        assertTrue(contents.getValue("js/games/blocks.js").contains("fullLines"))
        assertTrue(contents.getValue("js/games/tic-tac-toe.js").contains("computerChoice"))
        assertTrue(contents.getValue("styles/arena.css").contains("prefers-reduced-motion"))
    }

    @Test
    fun `first run keeps the two second animated opening and introduces the arena`() {
        assertTrue(intro.contains("const val FIRST_RUN_LOGO_DURATION_MS = 2_000L"))
        assertTrue(main.contains("firstRunLaunchStartedAtMs = launchStartedAtMs"))
        assertTrue(main.contains("SystemClock.elapsedRealtime() - startedAt"))
        assertTrue(intro.contains("delay(logoRemainingMs)"))
        assertTrue(intro.contains("detectHorizontalDragGestures"))
        assertTrue(intro.contains("Meet CodeC Arcade."))
        assertTrue(intro.contains("Block Party"))
        assertTrue(intro.contains("Tic-Tac-Toe"))
        assertTrue(intro.contains("private const val INTRO_PAGE_COUNT = 5"))
        assertTrue(intro.contains("Pause timer"))
        assertTrue(intro.contains("Skip to agreement"))
    }

    @Test
    fun `the privacy acknowledgement remains a real gate`() {
        assertTrue(intro.contains("I understand and accept this privacy summary."))
        assertTrue(intro.contains("Check this box to continue."))
        assertTrue(intro.contains("privacyAccepted"))
        assertTrue(intro.contains("Agree & start coding"))
        assertFalse("first-run education must not pre-request Android permissions", intro.contains("requestPermissions"))
    }

    @Test
    fun `only accepting the intro seeds and opens the game arena`() {
        assertTrue(main.contains("FirstRunIntroScreen("))
        assertTrue(main.contains("onStart = { if (!firstRunPreparing) firstRunAccepted = true }"))
        val accepted = main.substringAfter("LaunchedEffect(firstRunAccepted, firstLaunchComplete)")
            .substringBefore("if (firstLaunchComplete == null)")
        val acceptedCode = RepoFiles.codeOnly(accepted)
        val seedAt = acceptedCode.indexOf("GameArenaSample.ensure(root)")
        val savedAt = acceptedCode.indexOf("EditorLaunchState.save(activity, GameArenaSample.NAME")
        val completedAt = acceptedCode.indexOf("settingsManager.setFirstLaunchComplete(true)")
        val resetAt = acceptedCode.indexOf("firstRunAccepted = false", completedAt)
        assertTrue("explicit privacy acceptance must gate seeding", main.contains("!firstRunAccepted"))
        assertTrue("assets seed before launch state is saved", seedAt >= 0 && seedAt < savedAt)
        assertTrue("launch state is saved before first-run completion", savedAt >= 0 && savedAt < completedAt)
        assertTrue("acceptance state resets after completion", resetAt > completedAt)
        assertTrue(main.contains("activity.assets.open(\"${GameArenaSample.ASSET_DIRECTORY}/"))
        assertTrue(main.contains("Screen.Editor.createRoute(GameArenaSample.ENTRY_FILE, GameArenaSample.NAME)"))
        assertFalse(main.contains("OrbitSample"))
    }

    @Test
    fun `returning users skip onboarding and keep the normal resume decision`() {
        assertTrue(main.contains("if (firstLaunchComplete == false && !com.codeci.ide.ui.crash.SafeMode.active)"))
        assertTrue(main.contains("resumeOffer == com.codeci.ide.ui.projects.ResumeOffer.CONTINUE_IN_PLACE"))
        assertTrue(main.contains("launchState?.let { Screen.Editor.createRoute(it.fileName, it.projectName) }"))
        assertTrue(main.contains("firstLaunchComplete = settingsManager.firstLaunchCompleteFlow.first()"))
    }

    @Test
    fun `safe mode bypasses both intro and game arena seeding`() {
        val introGate = "if (firstLaunchComplete == false && !com.codeci.ide.ui.crash.SafeMode.active)"
        assertTrue(main.contains(introGate))
        val safeMode = main.substringAfter("// Preserve the crash-recovery escape hatch")
            .substringBefore("// The intro is the first-run UI")
        assertTrue(safeMode.contains("!com.codeci.ide.ui.crash.SafeMode.active"))
        assertFalse(safeMode.contains("GameArenaSample.ensure"))
        assertTrue(safeMode.contains("firstRunAccepted = false"))
        assertTrue(main.contains("firstLaunchComplete != false || com.codeci.ide.ui.crash.SafeMode.active"))
    }

    @Test
    fun `a seed failure completes onboarding into the Projects fallback`() {
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
    fun `the first launch preference key remains migration safe`() {
        val settings = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/settings/SettingsManager.kt").readText()
        assertTrue(settings.contains("booleanPreferencesKey(\"first_launch_complete\")"))
        assertTrue(settings.contains("firstLaunchCompleteFlow"))
    }

    private fun resolveImport(importer: String, specifier: String): String {
        val path = (importer.substringBeforeLast('/', "") + "/" + specifier)
            .split('/')
            .fold(mutableListOf<String>()) { parts, segment ->
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                    else -> parts.add(segment)
                }
                parts
            }
            .joinToString("/")
        return path
    }
}
