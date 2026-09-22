package com.codeci.ide

import com.codeci.ide.ui.projects.SnakeSample
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
 * Phase 58.1 — the first open is the editor, on a snake sample we write.
 *
 * Two halves, both checkable without a phone: the **seed** (a real project the
 * wizard would recognise, written once, never overwriting a user's edits) and
 * the **page** (one self-contained file — no CDN, no font, no `src=` — because a
 * sample that needs the network is not a sample a fresh install can run).
 */
class FirstOpenSampleTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val page: String
        get() = SnakeSample.FILES.first { it.relativePath == SnakeSample.ENTRY_FILE }.content

    private val main: String
        get() = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/MainActivity.kt").readText()

    @Test
    fun `the seed writes the project the wizard would`() {
        val root = tmp.newFolder("projects")
        val project = SnakeSample.ensure(root)
        assertNotNull("a fresh projects root must be seeded", project)
        assertEquals(SnakeSample.NAME, project!!.name)
        assertTrue(File(project, SnakeSample.ENTRY_FILE).isFile)
        assertTrue(File(project, File(".codec", "project.json").path).isFile)
        assertTrue(File(project, "README.md").isFile)
        // The TYPE stays "web" on purpose: preview, hub kind and run detection
        // all key on it, so the sample behaves like any other static page.
        assertEquals("web", SnakeSample.TYPE)
        val config = File(project, ".codec/project.json").readText()
        assertTrue("the project metadata must name the type", config.contains("web"))
    }

    @Test
    fun `a second call never touches what is there`() {
        val root = tmp.newFolder("projects")
        val project = SnakeSample.ensure(root)!!
        val entry = File(project, SnakeSample.ENTRY_FILE)
        entry.writeText("<!-- the owner's own edits -->")
        assertNull("an existing project is never re-seeded", SnakeSample.ensure(root))
        assertEquals("<!-- the owner's own edits -->", entry.readText())
    }

    @Test
    fun `the page stands alone`() {
        val page = page
        for (forbidden in listOf("http://", "https://", "<script src=", "<link ", "@import", "//cdn")) {
            assertFalse("the sample must not reach outside itself: $forbidden", page.contains(forbidden))
        }
        assertFalse("no template literal may appear in the Kotlin raw string", page.contains("\${"))
        assertTrue("the game is on a canvas", page.contains("<canvas"))
        assertTrue("it says its own name", page.contains("<title>snake</title>"))
    }

    @Test
    fun `the page is playable with a thumb and with a keyboard`() {
        val page = page
        assertTrue("arrows or WASD", page.contains("ArrowUp") && page.contains("keydown"))
        assertTrue("swipes", page.contains("touchstart") && page.contains("touchend"))
        assertTrue("an on-screen pad", page.contains("class=\"pad\""))
        assertTrue("a restart a user can find", page.contains("RESTART"))
        assertTrue("a score", page.contains("id=\"score\""))
        assertTrue("walls end the round", page.contains("hitWall"))
        assertTrue("and so does the tail", page.contains("hitSelf"))
    }

    @Test
    fun `the first open lands in the editor on the sample, never on the hub`() {
        assertTrue(
            "the first launch must seed the sample",
            main.contains("SnakeSample.ensure(ProjectManager(activity).projectsRoot())"),
        )
        assertTrue(
            "and then open it in the editor",
            main.contains("Screen.Editor.createRoute(SnakeSample.ENTRY_FILE, SnakeSample.NAME)"),
        )
        // 33.1 exit 2's ordering law, kept: the launch state is saved BEFORE the
        // flag flips, so the shell that replaces the first frame opens the sample
        // and the next launch resumes it.
        val seeded = main.indexOf("SnakeSample.ensure(")
        val saved = main.indexOf("EditorLaunchState.save(activity, SnakeSample.NAME")
        val flipped = main.indexOf("settingsManager.setFirstLaunchComplete(true)")
        assertTrue("the seed must come first", seeded in 1 until saved)
        assertTrue("the save must come before the flag", saved in 1 until flipped)
    }
}
