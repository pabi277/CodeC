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
 * Phase 33 (first-hour UX) — RUN ▶ asks "main/index file or the file that is
 * open" only when the project actually has a distinct, real main file.
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

    @Test
    fun `a real main file differing from the open file is offered`() {
        val root = projectWith("main.c", "utils.c")
        assertEquals("main.c", ProjectRunTarget.chooserEntry(root, "main.c", "utils.c"))
        assertTrue(ProjectRunTarget.shouldAsk(root, "main.c", "utils.c", openRunnable = true))
    }

    @Test
    fun `the open file being the main file means nothing to choose`() {
        val root = projectWith("main.c")
        assertNull(ProjectRunTarget.chooserEntry(root, "main.c", "main.c"))
        assertFalse(ProjectRunTarget.shouldAsk(root, "main.c", "main.c", openRunnable = true))
    }

    @Test
    fun `a missing main file is not offered`() {
        val root = projectWith("utils.c")
        assertNull(ProjectRunTarget.chooserEntry(root, "main.c", "utils.c"))
        assertFalse(ProjectRunTarget.shouldAsk(root, "main.c", "utils.c", openRunnable = true))
    }

    @Test
    fun `a non-runnable open file is never asked about`() {
        val root = projectWith("index.html", "style.css")
        assertFalse(ProjectRunTarget.shouldAsk(root, "index.html", "style.css", openRunnable = false))
    }

    @Test
    fun `web entry index html vs an open html file is offered`() {
        val root = projectWith("index.html", "about.html")
        assertEquals("index.html", ProjectRunTarget.chooserEntry(root, "index.html", "about.html"))
    }

    @Test
    fun `relative and absolute-looking entries stay confined`() {
        val root = projectWith("main.py", "src/helper.py")
        assertEquals("main.py", ProjectRunTarget.chooserEntry(root, "main.py", "src/helper.py"))
        // An entry outside the root never resolves.
        assertNull(ProjectRunTarget.chooserEntry(root, "../main.py", "src/helper.py"))
    }

    @Test
    fun `a runnable source inside a web project is recognised`() {
        // Phase 33: an HTML project can hold C/Python files; RUN must execute
        // them, not preview index.html.
        assertTrue(ProjectRunTarget.isRunnableSource("main.c"))
        assertTrue(ProjectRunTarget.isRunnableSource("src/tool.py"))
        assertTrue(ProjectRunTarget.isRunnableSource("main.cpp"))
        assertTrue(ProjectRunTarget.isRunnableSource("build.sh"))
        // node is a run profile too, so a .js file is panel-runnable.
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
