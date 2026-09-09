package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectRunTarget
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 33 (first-hour UX) — RUN ▶ asks "default file or the open file" only
 * when the user has SET a default run file that differs from the open file.
 */
class ProjectRunTargetTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun projectWith(vararg names: String): File {
        val root = tmp.newFolder("proj")
        names.forEach { name ->
            val f = File(root, name)
            f.parentFile?.mkdirs()
            f.writeText("x")
        }
        return root
    }

    // ---- chooserDefault: the RUN ▶ decision -------------------------------

    @Test
    fun `a set default differing from the open file is offered`() {
        val root = projectWith("main.c", "utils.c")
        assertEquals(
            "main.c",
            ProjectRunTarget.chooserDefault(root, "main.c", "utils.c")
        )
    }

    @Test
    fun `a web default vs an open source file is offered`() {
        // The Code-with-C shape: a web project (index.html) holding C files.
        val root = projectWith("index.html", "main.c", "C Programming/01_hello.c")
        assertEquals(
            "index.html",
            ProjectRunTarget.chooserDefault(root, "index.html", "C Programming/01_hello.c")
        )
        // And a C default vs an open C file.
        assertEquals(
            "main.c",
            ProjectRunTarget.chooserDefault(root, "main.c", "C Programming/01_hello.c")
        )
    }

    @Test
    fun `the open file being the default means nothing to choose`() {
        val root = projectWith("main.c")
        assertNull(ProjectRunTarget.chooserDefault(root, "main.c", "main.c"))
    }

    @Test
    fun `no default set means nothing to choose`() {
        val root = projectWith("main.c", "utils.c")
        assertNull(ProjectRunTarget.chooserDefault(root, null, "utils.c"))
        assertNull(ProjectRunTarget.chooserDefault(root, "main.c", null))
    }

    @Test
    fun `a missing default file is not offered`() {
        val root = projectWith("utils.c")
        assertNull(ProjectRunTarget.chooserDefault(root, "main.c", "utils.c"))
    }

    @Test
    fun `a default that is not a run target is never offered`() {
        val root = projectWith("style.css", "main.c")
        assertNull(ProjectRunTarget.chooserDefault(root, "style.css", "main.c"))
    }

    @Test
    fun `a non-run-target open file is never asked about`() {
        val root = projectWith("main.c", "style.css")
        assertNull(ProjectRunTarget.chooserDefault(root, "main.c", "style.css"))
    }

    // ---- chooserEntry: raw confinement ------------------------------------

    @Test
    fun `entries stay confined to the root`() {
        val root = projectWith("main.py", "src/helper.py")
        assertEquals("main.py", ProjectRunTarget.chooserEntry(root, "main.py", "src/helper.py"))
        assertNull(ProjectRunTarget.chooserEntry(root, "../main.py", "src/helper.py"))
    }

    // ---- classification ----------------------------------------------------

    @Test
    fun `run targets are recognised`() {
        assertTrue(ProjectRunTarget.isRunTarget("main.c"))
        assertTrue(ProjectRunTarget.isRunTarget("index.html"))
        assertTrue(ProjectRunTarget.isRunTarget("src/tool.py"))
        assertTrue(ProjectRunTarget.isRunTarget("app.js"))
        assertTrue(ProjectRunTarget.isRunTarget("build.sh"))
    }

    @Test
    fun `non-run-target files are rejected`() {
        assertFalse(ProjectRunTarget.isRunTarget("style.css"))
        assertFalse(ProjectRunTarget.isRunTarget("README.md"))
        assertFalse(ProjectRunTarget.isRunTarget("data.json"))
        assertFalse(ProjectRunTarget.isRunTarget(null))
    }

    @Test
    fun `a runnable source inside a web project is recognised`() {
        // Phase 33: an HTML project can hold C/Python files; RUN must execute
        // them, not preview index.html.
        assertTrue(ProjectRunTarget.isRunnableSource("main.c"))
        assertTrue(ProjectRunTarget.isRunnableSource("src/tool.py"))
        assertTrue(ProjectRunTarget.isRunnableSource("main.cpp"))
        assertTrue(ProjectRunTarget.isRunnableSource("build.sh"))
        assertTrue(ProjectRunTarget.isRunnableSource("app.js"))
    }

    @Test
    fun `web and non-source files are not panel-runnable`() {
        assertFalse(ProjectRunTarget.isRunnableSource("index.html"))
        assertFalse(ProjectRunTarget.isRunnableSource("about.htm"))
        assertFalse(ProjectRunTarget.isRunnableSource("style.css"))
        assertFalse(ProjectRunTarget.isRunnableSource("README.md"))
        assertFalse(ProjectRunTarget.isRunnableSource(null))
    }
}
