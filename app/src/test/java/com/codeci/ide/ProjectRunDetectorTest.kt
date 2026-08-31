package com.codeci.ide

import com.codeci.ide.ui.projects.AutoRunPlan
import com.codeci.ide.ui.projects.ProjectRunDetector
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 14 — Auto projects (owner request): creating a project needs no type
 * selection; RUN ▶ detects the runnable type from the project's files, with
 * the actively open file taking precedence.
 */
class ProjectRunDetectorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun project(vararg files: Pair<String, String>): File {
        val root = tmp.newFolder("project")
        for ((path, content) in files) {
            val file = File(root, path)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
        return root
    }

    @Test
    fun `app py is a flask server`() {
        val root = project("app.py" to "from flask import Flask", "index.html" to "<h1>hi</h1>")
        assertEquals(AutoRunPlan.Server("python-flask"), ProjectRunDetector.detect(root, "app.py"))
    }

    @Test
    fun `main py importing fastapi is a fastapi server`() {
        val root = project(
            "main.py" to "from fastapi import FastAPI\nimport uvicorn\n",
            "index.html" to "<h1>hi</h1>"
        )
        assertEquals(AutoRunPlan.Server("python-fastapi"), ProjectRunDetector.detect(root, "main.py"))
    }

    @Test
    fun `plain main py is a python program`() {
        val root = project("main.py" to "print('hello')\n")
        assertEquals(AutoRunPlan.Project("python"), ProjectRunDetector.detect(root, "main.py"))
    }

    @Test
    fun `server c is a c microservice`() {
        val root = project("server.c" to "#include <sys/socket.h>\n")
        assertEquals(AutoRunPlan.Server("c-microservice"), ProjectRunDetector.detect(root, "server.c"))
    }

    @Test
    fun `index html is static web`() {
        val root = project("index.html" to "<h1>hi</h1>", "styles.css" to "body{}")
        assertEquals(AutoRunPlan.Web("index.html"), ProjectRunDetector.detect(root, "index.html"))
    }

    @Test
    fun `main c is a c program`() {
        val root = project("main.c" to "int main(void){return 0;}")
        assertEquals(AutoRunPlan.Project("c"), ProjectRunDetector.detect(root, "main.c"))
    }

    @Test
    fun `any c file is a c program`() {
        val root = project("helper.c" to "int helper(void){return 1;}")
        assertEquals(AutoRunPlan.Project("c"), ProjectRunDetector.detect(root, null))
    }

    @Test
    fun `first py file is a python program`() {
        val root = project("tool.py" to "print('x')")
        assertEquals(AutoRunPlan.Project("python"), ProjectRunDetector.detect(root, null))
    }

    @Test
    fun `empty project explains what to add`() {
        val root = tmp.newFolder("empty")
        val plan = ProjectRunDetector.detect(root, null)
        assertTrue(plan is AutoRunPlan.None)
        assertTrue((plan as AutoRunPlan.None).message.contains("app.py"))
    }

    @Test
    fun `active file wins over project files`() {
        val root = project(
            "app.py" to "from flask import Flask",
            "server.c" to "#include <sys/socket.h>"
        )
        assertEquals(
            AutoRunPlan.Server("c-microservice"),
            ProjectRunDetector.detect(root, "server.c")
        )
        assertEquals(
            AutoRunPlan.Server("python-flask"),
            ProjectRunDetector.detect(root, "app.py")
        )
    }

    @Test
    fun `unknown active file falls back to project scan`() {
        val root = project("app.py" to "from flask import Flask", "notes.txt" to "hi")
        assertEquals(
            AutoRunPlan.Server("python-flask"),
            ProjectRunDetector.detect(root, "notes.txt")
        )
    }

    @Test
    fun `active html wins even when a server file exists`() {
        val root = project("page.html" to "<h1>page</h1>", "server.c" to "// c")
        assertEquals(AutoRunPlan.Web("page.html"), ProjectRunDetector.detect(root, "page.html"))
    }

    @Test
    fun `fastapi detection is content based not name based`() {
        val root = project(
            "main.py" to "print('hello')\n",
            "other.py" to "from fastapi import FastAPI"
        )
        // Active main.py is plain python even though other.py imports fastapi.
        assertEquals(AutoRunPlan.Project("python"), ProjectRunDetector.detect(root, "main.py"))
    }
}
