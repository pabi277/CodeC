package com.codeci.ide

import com.codeci.ide.ui.projects.DemoProjects
import com.codeci.ide.ui.projects.ProjectConfig
import com.codeci.ide.ui.projects.ProjectScaffold
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 14 — the bundled demo project the app ships with (owner request):
 * `demo_flask` must appear in the Files tab without any wizard steps, runnable
 * with just RUN ▶ (stdlib fallback, so no extra install is required on the
 * acceptance path beyond Phase-12 python).
 *
 * **Phase 45 device round (2026-09-12) changed one law here.** The owner: *"make
 * it like demo_flask is always present so whatever user chose to start guide the
 * user to start the demo project from the editor only"*. The guided tour teaches
 * this project BY NAME (beat 2 "choose demo_flask", beat 3 "Open app.py"), so a
 * demo the user once deleted would make the tour point at a row that does not
 * exist. The seed is therefore no longer once-per-install: a missing
 * `demo_flask` is seeded again, the marker file survives only as a record of the
 * first seed, and what never changed is that an existing project — the user's
 * edits — is not touched.
 */
class DemoProjectSeedTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun projectRoot(): File = tmp.newFolder("CodeC", "projects")

    @Test
    fun `first seed creates demo_flask with config, page and readme`() {
        val root = projectRoot()
        val created = DemoProjects.ensure(root)
        assertTrue(created != null)
        assertEquals("demo_flask", created?.name)

        val project = File(root, "demo_flask")
        assertTrue(File(project, "app.py").isFile)
        assertTrue(File(project, "index.html").isFile)
        assertTrue(File(project, "README.md").isFile)
        assertTrue(File(project, ".codec/project.json").isFile)

        val config = ProjectConfig.fromJson(File(project, ".codec/project.json").readText(), "demo_flask")
        assertEquals("python-flask", config.type)
        assertEquals("app.py", config.entry)
        assertEquals("python3 app.py", config.run)
        assertEquals(5000, config.port)
        assertEquals("http://127.0.0.1:5000", config.serverPreviewUrl())
        assertTrue(config.isServerType())

        assertTrue(File(project, "index.html").readText().contains("Welcome to CodeC Flask App!"))
        assertTrue(File(project, "README.md").readText().contains("RUN"))
    }

    @Test
    fun `seed is idempotent and never overwrites the demo project`() {
        val root = projectRoot()
        val created = DemoProjects.ensure(root)
        assertTrue(created != null)

        val index = File(root, "demo_flask/index.html")
        index.writeText("<!doctype html><html><body>my edits</body></html>")
        val again = DemoProjects.ensure(root)
        assertNull(again)
        assertEquals("<!doctype html><html><body>my edits</body></html>", index.readText())
    }

    @Test
    fun `an existing demo_flask project is respected`() {
        val root = projectRoot()
        val project = File(root, "demo_flask")
        assertTrue(project.mkdirs())
        val app = File(project, "app.py")
        app.writeText("# my own app")
        val created = DemoProjects.ensure(root)
        assertNull("a project the user already has is never rewritten", created)
        assertEquals("# my own app", app.readText())
        assertEquals("nothing else may appear next to it", 1, root.listFiles()?.count { it.name == "demo_flask" })
    }

    @Test
    fun `a deleted demo_flask comes back, because the tour teaches it by name`() {
        val root = projectRoot()
        assertTrue(DemoProjects.ensure(root) != null)
        val app = File(root, "demo_flask/app.py")
        app.writeText("# my own flask app")
        File(root, "demo_flask").deleteRecursively()

        val again = DemoProjects.ensure(root)
        assertTrue("the demo is ALWAYS present (owner, Phase 45)", again != null)
        assertEquals("demo_flask", again?.name)
        assertTrue(File(root, "demo_flask/app.py").isFile)
        assertTrue(File(root, "demo_flask/index.html").isFile)
        // …and it is the shipped demo again, not the deleted one.
        assertTrue(!File(root, "demo_flask/app.py").readText().contains("# my own flask app"))
    }

    @Test
    fun `the marker is a record of the first seed, not a gate`() {
        val root = projectRoot()
        // What the old build wrote to mean "never seed again".
        File(root, ".demo-flask-seeded-v1").writeText("seeded 2026-08-31")
        val created = DemoProjects.ensure(root)
        assertTrue("a stale marker must not hide the demo", created != null)
        assertTrue(File(root, "demo_flask/app.py").isFile)
        // A successful seed leaves the record behind.
        assertTrue(File(root, ".demo-flask-seeded-v1").isFile)
    }

    @Test
    fun `a plain file named demo_flask blocks the seed and is left alone`() {
        val root = projectRoot()
        File(root, "demo_flask").writeText("not a project")
        assertNull(DemoProjects.ensure(root))
        assertEquals("not a project", File(root, "demo_flask").readText())
    }

    @Test
    fun `the entry file the tour names is the one the scaffold really writes`() {
        // Beat 3 of the tour says "Open app.py" and its anchor is published only
        // for DemoProjects.ENTRY_FILE — so the constant, the scaffold and the
        // bytes on disk must agree, or the box teaches a tap that does nothing.
        assertEquals("app.py", DemoProjects.ENTRY_FILE)
        assertEquals(
            DemoProjects.ENTRY_FILE,
            ProjectScaffold.filesFor(DemoProjects.TYPE).first().relativePath
        )
        val root = projectRoot()
        DemoProjects.ensure(root)
        assertTrue(File(root, "demo_flask/${DemoProjects.ENTRY_FILE}").isFile)
        val config = ProjectConfig.fromJson(
            File(root, "demo_flask/.codec/project.json").readText(),
            DemoProjects.NAME
        )
        assertEquals("RUN ▶ runs the file the tour told the user to open", DemoProjects.ENTRY_FILE, config.entry)
    }
}
