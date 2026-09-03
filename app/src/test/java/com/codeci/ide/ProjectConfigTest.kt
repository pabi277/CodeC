package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectConfigTest {
    @Test
    fun `project config round trips the run schema`() {
        val original = ProjectConfig(
            name = "calculator",
            type = "c",
            entry = "src/main.c",
            build = "cc -I include src/main.c src/calc.c -o bin/app",
            run = "./bin/app",
            clean = "rm -rf bin/app"
        )

        val restored = ProjectConfig.fromJson(original.toJsonString(), "fallback")
        assertEquals(original, restored)
    }

    @Test
    fun `defaults provide useful presets`() {
        assertEquals("python3 main.py", ProjectConfig.defaultFor("demo", "python").run)
        assertEquals("index.html", ProjectConfig.defaultFor("site", "web").entry)
    }

    // ---- Phase 14: server presets, port/previewUrl schema ------------------

    @Test
    fun `flask preset configures port five thousand and a preview url`() {
        val config = ProjectConfig.defaultFor("flask_app", "python-flask")
        assertEquals("app.py", config.entry)
        assertEquals("python3 app.py", config.run)
        assertEquals(5000, config.port)
        assertEquals("http://127.0.0.1:5000", config.previewUrl)
        assertEquals("http://127.0.0.1:5000", config.serverPreviewUrl())
        assertTrue(config.isServerType())
    }

    @Test
    fun `fastapi and c microservice presets are server types`() {
        val fastapi = ProjectConfig.defaultFor("api", "python-fastapi")
        assertEquals(8000, fastapi.port)
        assertEquals("http://127.0.0.1:8000", fastapi.serverPreviewUrl())
        assertTrue(fastapi.isServerType())

        val micro = ProjectConfig.defaultFor("svc", "c-microservice")
        assertEquals("mkdir -p bin && gcc server.c -o bin/server", micro.build)
        assertEquals("./bin/server", micro.run)
        assertEquals(8080, micro.port)
        assertTrue(micro.isServerType())
    }

    @Test
    fun `plain c project is not a server type`() {
        assertFalse(ProjectConfig.defaultFor("demo", "c").isServerType())
        assertNull(ProjectConfig.defaultFor("demo", "c").serverPreviewUrl())
    }

    @Test
    fun `auto project has no preset commands and is not a server type`() {
        val config = ProjectConfig.defaultFor("demo", "auto")
        assertEquals("auto", config.type)
        assertEquals("", config.build)
        assertEquals("", config.run)
        assertEquals("", config.entry)
        assertFalse(config.isServerType())
        assertNull(config.serverPreviewUrl())
    }

    @Test
    fun `config round trips port and preview url`() {
        val original = ProjectConfig.defaultFor("api", "python-fastapi")
        val restored = ProjectConfig.fromJson(original.toJsonString(), "fallback")
        assertEquals(original, restored)
    }

    @Test
    fun `legacy json without port fields still parses`() {
        val legacy = """{"version":1,"name":"demo","type":"c","entry":"main.c",
            "build":"mkdir -p bin && cc main.c -o bin/app","run":"./bin/app","clean":"rm -rf bin/app"}"""
        val config = ProjectConfig.fromJson(legacy, "demo")
        assertEquals("c", config.type)
        assertNull(config.port)
        assertNull(config.previewUrl)
        assertEquals("http://127.0.0.1:5000", ProjectConfig.defaultFor("f", "python-flask").serverPreviewUrl())
    }

    @Test
    fun `preview url falls back to the configured port`() {
        val config = ProjectConfig.defaultFor("api", "python-fastapi").copy(previewUrl = null)
        assertEquals("http://127.0.0.1:8000", config.serverPreviewUrl())
    }
}
