package com.codeci.ide

import com.codeci.ide.ui.projects.DemoProjects
import com.codeci.ide.ui.projects.ProjectConfig
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
        assertNull(created)
        assertEquals("# my own app", app.readText())
        // Marker written: deleting the project later does not resurrect it.
        assertTrue(File(root, ".demo-flask-seeded-v1").isFile)
    }

    @Test
    fun `does not seed after the marker exists`() {
        val root = projectRoot()
        File(root, ".demo-flask-seeded-v1").writeText("seeded")
        assertNull(DemoProjects.ensure(root))
        assertTrue(!File(root, "demo_flask").exists())
    }
}
