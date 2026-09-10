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
        // Phase 37.1: the bind host comes from CODEC_SERVER_HOST, defaulting
        // to loopback — and the printed line follows the real host, which is
        // what lets ServerPortDetector tell a shared server from a private one.
        assertTrue(app.contains("Running on http://%s:%s/"))
        assertTrue(app.contains("HOST = os.environ.get(\"CODEC_SERVER_HOST\", \"127.0.0.1\")"))
        assertTrue(app.contains("app.run(host=HOST, port=PORT"))
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
        assertTrue(app.contains("Uvicorn running on http://%s:%s/"))
        assertTrue(app.contains("HOST = os.environ.get(\"CODEC_SERVER_HOST\", \"127.0.0.1\")"))
        assertTrue(app.contains("uvicorn.run(app, host=HOST, port=PORT"))
        assertTrue(app.contains("from fastapi import FastAPI"))
        assertTrue(index.contains("Welcome to CodeC FastAPI App!"))
    }

    @Test
    fun `c microservice scaffold prints the codec bind line and compiles with cc`() {
        val files = ProjectScaffold.filesFor("c-microservice")
        assertEquals(listOf("server.c"), files.map { it.relativePath })
        val content = files.single().content
        assertTrue(content.contains("CodeC server listening on http://%s:%d"))
        assertTrue(content.contains("getenv(HOST_ENV)"))
        assertTrue(content.contains("htonl(INADDR_ANY)"))
        assertTrue(content.contains("#include <sys/socket.h>"))
        assertTrue(content.contains("cc server.c -o bin/server"))
    }

    @Test
    fun `every server template defaults to loopback and honours the lan env`() {
        // The LAN switch is opt-in, so a template run without CodeC's env must
        // still bind 127.0.0.1; with the env it must print the wildcard line
        // the detector recognises. Both halves are template guarantees.
        val hosts = mapOf(
            "python-flask" to "app.py",
            "python-fastapi" to "main.py"
        )
        for ((type, file) in hosts) {
            val content = ProjectScaffold.filesFor(type).first { it.relativePath == file }.content
            assertTrue("$type must default to loopback", content.contains("\"127.0.0.1\""))
            assertTrue("$type must read the LAN env", content.contains("CODEC_SERVER_HOST"))
            assertTrue("$type must not hardcode the host in bind", !content.contains("host=\"127.0.0.1\""))
            assertTrue("$type must not hardcode the tuple bind", !content.contains("HTTPServer((\"127.0.0.1\", PORT)"))
        }
        val c = ProjectScaffold.filesFor("c-microservice").single().content
        assertTrue(c.contains("CODEC_SERVER_HOST"))
        assertTrue(c.contains("return \"127.0.0.1\";"))
        assertTrue(!c.contains("htonl(INADDR_LOOPBACK)"))
    }

    @Test
    fun `unknown type scaffolds nothing`() {
        assertTrue(ProjectScaffold.filesFor("rust").isEmpty())
    }

    @Test
    fun `auto type scaffolds nothing by design`() {
        // Auto projects get no starter files: RUN ▶ detects the type from the
        // user's own files (ProjectRunDetector).
        assertTrue(ProjectScaffold.filesFor("auto").isEmpty())
    }

    @Test
    fun `scaffold files land on disk under their relative path`() {
        val root = File.createTempFile("codec-scaffold", "").apply {
            delete()
            mkdirs()
        }
        ProjectScaffold.writeFiles("python-flask", root)
        assertTrue(File(root, "app.py").isFile)
        assertTrue(File(root, "index.html").isFile)
        assertTrue(File(root, "index.html").readText().contains("Welcome to CodeC Flask App!"))
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
