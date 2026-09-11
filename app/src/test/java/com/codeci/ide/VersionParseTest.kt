package com.codeci.ide

import com.codeci.ide.ui.services.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 42.1 — the `versionName` shape parser, pinned separately because the
 * Phase 29 CI-run-number suffix ("1.3.16 (3419922)") is EXACTLY what a naive
 * `==` comparison breaks on (spec: PART_42_1 Tests plan).
 */
class VersionParseTest {

    @Test
    fun `the versionName shapes this app actually ships`() {
        assertEquals(listOf(1, 3, 16), UpdatePolicy.Version.parse("1.3.16")?.parts)
        assertEquals(
            "Phase 29's CI run number rides in parentheses and must not break parsing",
            listOf(1, 3, 16),
            UpdatePolicy.Version.parse("1.3.16 (3419922)")?.parts
        )
        assertEquals(listOf(1, 4), UpdatePolicy.Version.parse("1.4")?.parts)
        assertEquals(listOf(2), UpdatePolicy.Version.parse("2")?.parts)
        assertEquals(listOf(1, 3, 17), UpdatePolicy.Version.parse("app-v1.3.17")?.parts)
        assertEquals(
            "whitespace around the whole string is tolerated (BuildConfig never has it; a hand-edited value might)",
            listOf(1, 3, 17),
            UpdatePolicy.Version.parse("  1.3.17 ")?.parts
        )
    }

    @Test
    fun `garbage parses to null, never to a lucky version`() {
        assertNull(UpdatePolicy.Version.parse(null))
        assertNull(UpdatePolicy.Version.parse(""))
        assertNull(UpdatePolicy.Version.parse("garbage"))
        assertNull(UpdatePolicy.Version.parse("1.3.x"))
        assertNull(UpdatePolicy.Version.parse("(3419922)"))
        assertNull(UpdatePolicy.Version.parse("1.3.16 (3419922"))
        assertNull(UpdatePolicy.Version.parse("userland-v1"))
        assertNull(UpdatePolicy.Version.parse("v1.3.17"))
        assertNull(UpdatePolicy.Version.parse("1.3.-1"))
    }

    @Test
    fun `comparison is numeric per part - the x dot 9 vs x dot 10 trap`() {
        val a = UpdatePolicy.Version.parse("1.3.9")!!
        val b = UpdatePolicy.Version.parse("1.3.10")!!
        assertTrue("string compare says 1.3.9 > 1.3.10; the whole point of this type is that it does not", a < b)
        assertTrue(
            UpdatePolicy.Version.parse("1.4")!! > UpdatePolicy.Version.parse("1.3.17")!!
        )
        assertTrue(
            UpdatePolicy.Version.parse("2")!! > UpdatePolicy.Version.parse("1.9.9")!!
        )
        assertEquals(0, UpdatePolicy.Version.parse("1.3")!!.compareTo(UpdatePolicy.Version.parse("1.3.0")!!))
    }

    @Test
    fun `text round-trips the version without the run number`() {
        assertEquals("1.3.16", UpdatePolicy.Version.parse("1.3.16 (3419922)")?.text)
        assertEquals("1.3.17", UpdatePolicy.Version.parse("app-v1.3.17")?.text)
    }
}
