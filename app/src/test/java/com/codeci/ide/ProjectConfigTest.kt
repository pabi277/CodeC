package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectConfig
import org.junit.Assert.assertEquals
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
}
