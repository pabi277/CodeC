package com.codeci.ide

import com.codeci.ide.ui.projects.DemoProjects
import com.codeci.ide.ui.projects.ProjectConfig
import com.codeci.ide.ui.projects.ProjectScaffold
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * **Phase 66.1 (owner, 2026-09-27: *"Yes — stay deleted"*) restored the
 * once-per-install law.** The Phase 45 device round had made the demo "always
 * present" because the guided tour taught it by name; Phase 64 removed that
 * tour, and re-seeding then only made *Delete* on the hub card look broken (the
 * list reload re-seeded the project before the card could leave) and kept the
 * hub's empty state unreachable on a device. The marker file is the gate again:
 * a `demo_flask` the user deleted stays deleted, exactly like `snake`. What
 * never changed: an existing project — the user's edits — is not touched.
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
    fun `a deleted demo_flask stays deleted - the owner's 66_1 answer`() {
        val root = projectRoot()
        assertTrue(DemoProjects.ensure(root) != null)
        File(root, "demo_flask/app.py").writeText("# my own flask app")
        assertTrue(File(root, "demo_flask").deleteRecursively())

        // Every list refresh calls ensure(); none of them may bring it back.
        repeat(3) {
            assertNull("a deleted demo must not be re-seeded (owner, 2026-09-27)", DemoProjects.ensure(root))
            assertFalse(File(root, "demo_flask").exists())
        }
        // The record of the first seed is what keeps it away.
        assertTrue(File(root, ".demo-flask-seeded-v1").isFile)
    }

    @Test
    fun `the marker is the gate - an install that seeded once never seeds again`() {
        val root = projectRoot()
        // What a previous build wrote after its first (or any later) seed.
        File(root, ".demo-flask-seeded-v1").writeText("seeded 2026-08-31")
        assertNull("the marker alone means the demo was here once and is gone by choice", DemoProjects.ensure(root))
        assertFalse(File(root, "demo_flask").exists())
    }

    @Test
    fun `an existing demo without a marker gets the marker, not a rewrite`() {
        // A demo_flask that exists while the record is missing (the record was
        // deleted, or the folder was made by hand): the folder is left alone and
        // the record is written beside it — so that deleting the folder later
        // is still final. Nothing appears inside the project.
        val root = projectRoot()
        val project = File(root, "demo_flask")
        assertTrue(project.mkdirs())
        File(project, "app.py").writeText("# my own app")
        assertNull(DemoProjects.ensure(root))
        assertEquals("# my own app", File(project, "app.py").readText())
        assertEquals(listOf("app.py"), project.list()?.sorted())
        assertTrue(File(root, ".demo-flask-seeded-v1").isFile)
        assertTrue(project.deleteRecursively())
        assertNull("…and it stays deleted", DemoProjects.ensure(root))
        assertFalse(project.exists())
    }

    @Test
    fun `a plain file named demo_flask blocks the seed, is left alone, and writes no marker`() {
        val root = projectRoot()
        File(root, "demo_flask").writeText("not a project")
        assertNull(DemoProjects.ensure(root))
        assertEquals("not a project", File(root, "demo_flask").readText())
        // Nothing was seeded, so nothing is recorded: the marker means "seeded
        // once", never "tried once".
        assertFalse(File(root, ".demo-flask-seeded-v1").exists())
        // Once the file is out of the way the first real seed happens.
        assertTrue(File(root, "demo_flask").delete())
        assertTrue(DemoProjects.ensure(root) != null)
        assertTrue(File(root, ".demo-flask-seeded-v1").isFile)
    }

    @Test
    fun `the entry file the demo names is the one the scaffold really writes`() {
        // DemoProjects.ENTRY_FILE, the scaffold and the bytes on disk must
        // agree, or RUN ▶ on the demo runs a file that is not there.
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
        assertEquals("RUN ▶ runs the demo's own entry file", DemoProjects.ENTRY_FILE, config.entry)
    }
}
