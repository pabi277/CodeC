package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectConfig
import com.codeci.ide.ui.projects.ProjectScaffold
import com.codeci.ide.ui.projects.ProjectTypes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 14 — starter-file templates. These are the bytes a fresh project
 * gets; the markers below are also what the device recipe greps for.
 */
class ProjectScaffoldTest {

    @Test
    fun `c scaffold keeps the historical starter exactly`() {
        val files = ProjectScaffold.filesFor("c")
        assertEquals(listOf("main.c"), files.map { it.relativePath })
        assertEquals(
            "#include <stdio.h>\n\nint main(void) {\n    printf(\"Hello, CodeC!\\n\");\n    return 0;\n}\n",
            files.single().content
        )
    }

    @Test
    fun `web scaffold ships index html`() {
        val files = ProjectScaffold.filesFor("web")
        assertEquals(listOf("index.html"), files.map { it.relativePath })
        assertTrue(files.single().content.contains("Welcome to CodeC Web!"))
    }

    @Test
    fun `flask scaffold prints the bind line, serves a live index and falls back to stdlib`() {
        val files = ProjectScaffold.filesFor("python-flask")
        assertEquals(listOf("app.py", "index.html"), files.map { it.relativePath })
        val app = files.first { it.relativePath == "app.py" }.content
        val index = files.first { it.relativePath == "index.html" }.content
        assertTrue(app.contains("Running on http://127.0.0.1:5000"))
        assertTrue(app.contains("from flask import Flask"))
        assertTrue(app.contains("HTTPServer(("))
        assertTrue(app.contains("pip install flask"))
        assertTrue(app.contains("load_page()"))
        assertTrue(index.contains("Welcome to CodeC Flask App!"))
        assertTrue(index.contains("Reload"))
    }

    @Test
    fun `fastapi scaffold prints the uvicorn bind line and serves a live index`() {
        val files = ProjectScaffold.filesFor("python-fastapi")
        assertEquals(listOf("main.py", "index.html"), files.map { it.relativePath })
        val app = files.first { it.relativePath == "main.py" }.content
        val index = files.first { it.relativePath == "index.html" }.content
        assertTrue(app.contains("Uvicorn running on http://127.0.0.1:8000"))
        assertTrue(app.contains("from fastapi import FastAPI"))
        assertTrue(index.contains("Welcome to CodeC FastAPI App!"))
    }

    @Test
    fun `c microservice scaffold prints the codec bind line and compiles with cc`() {
        val files = ProjectScaffold.filesFor("c-microservice")
        assertEquals(listOf("server.c"), files.map { it.relativePath })
        val content = files.single().content
        assertTrue(content.contains("CodeC server listening on http://127.0.0.1:8080"))
        assertTrue(content.contains("#include <sys/socket.h>"))
        assertTrue(content.contains("cc server.c -o bin/server"))
    }

    @Test
    fun `unknown type scaffolds nothing`() {
        assertTrue(ProjectScaffold.filesFor("rust").isEmpty())
    }

    @Test
    fun `scaffold files land on disk under their relative path`() {
        val root = File.createTempFile("codec-scaffold", "").apply {
            delete()
            mkdirs()
        }
        for (scaffold in ProjectScaffold.filesFor("python-flask")) {
            val file = File(root, scaffold.relativePath)
            file.parentFile?.mkdirs()
            file.writeText(scaffold.content)
        }
        val app = File(root, "app.py")
        assertTrue(app.isFile)
        assertTrue(app.readText().contains("Welcome to CodeC Flask App!"))
        root.deleteRecursively()
    }

    @Test
    fun `wizard type list matches the scaffolded config types`() {
        val ids = ProjectTypes.options.map { it.id }
        assertTrue(ids.containsAll(listOf("c", "python", "web", "python-flask", "python-fastapi", "c-microservice")))
        assertFalse(ids.contains("rust"))
        // Every wizard type has a ProjectConfig default; server types are marked.
        for (id in ids) {
            val config = ProjectConfig.defaultFor("demo", id)
            assertEquals(id, config.type)
            assertEquals(id in ProjectConfig.SERVER_TYPES, config.isServerType())
        }
    }
}
