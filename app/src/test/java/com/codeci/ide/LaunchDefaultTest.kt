package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 16 — the `launchDefault` run-config field: round-trips through the
 * JSON, is omitted from the bytes when null (older readers see exactly the
 * pre-Phase-16 file, same backward-compat contract as Phase 14's `port`),
 * and clearing writes the omitted form again.
 */
class LaunchDefaultTest {

    @Test
    fun `default config omits launchDefault from the JSON`() {
        val json = ProjectConfig.defaultFor("webby", "web").toJsonString()
        assertFalse(json.contains("launchDefault"))
        val back = ProjectConfig.fromJson(json, "webby")
        assertNull(back.launchDefault)
    }

    @Test
    fun `set read and clear round-trip`() {
        val set = ProjectConfig(name = "webby", type = "web")
            .copy(launchDefault = "site/index.html")
        val json = set.toJsonString()
        assertTrue(json.contains("\"launchDefault\":\"site/index.html\""))
        val read = ProjectConfig.fromJson(json, "webby")
        assertEquals("site/index.html", read.launchDefault)

        val cleared = read.copy(launchDefault = null)
        assertFalse(cleared.toJsonString().contains("launchDefault"))
        assertNull(ProjectConfig.fromJson(cleared.toJsonString(), "webby").launchDefault)
    }

    @Test
    fun `launchDefault coexists with the Phase 14 server fields`() {
        val flask = ProjectConfig.defaultFor("api", "python-flask")
            .copy(launchDefault = "static/index.html")
        val back = ProjectConfig.fromJson(flask.toJsonString(), "api")
        assertEquals(5000, back.port)
        assertEquals("http://127.0.0.1:5000", back.previewUrl)
        assertEquals("static/index.html", back.launchDefault)
    }

    @Test
    fun `blank stored value reads as unset`() {
        val json = ProjectConfig(name = "p").copy(launchDefault = "x").toJsonString()
        val blanked = json.replace("\"launchDefault\":\"x\"", "\"launchDefault\":\"\"")
        assertNull(ProjectConfig.fromJson(blanked, "p").launchDefault)
    }

    @Test
    fun `pre-phase-16 config files keep parsing unchanged`() {
        val legacy = ProjectConfig(name = "old", type = "python", entry = "main.py")
        val back = ProjectConfig.fromJson(legacy.toJsonString(), "old")
        assertEquals("python", back.type)
        assertNull(back.launchDefault)
        assertNull(back.port)
    }
}
