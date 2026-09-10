package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 39.1 — thin source-level pin that the collector is invoked on app
 * start and that live stamps are registered around a compile. Full
 * Robolectric lifecycle coverage is deferred; this catches a silent
 * un-wiring on the host JVM.
 */
class TempGcAndRunInteropTest {

    @Test
    fun `MainActivity cold-start invokes TempGc_sweep`() {
        val src = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/MainActivity.kt"
        ).readText()
        assertTrue("MainActivity must call TempGc.sweep on start", "TempGc.sweep" in src)
        assertTrue("MainActivity must pass LiveRunStamps.snapshot()", "LiveRunStamps.snapshot()" in src)
    }

    @Test
    fun `CompilerService registers LiveRunStamps around compile`() {
        val src = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/services/CompilerService.kt"
        ).readText()
        assertTrue("compile must LiveRunStamps.add", "LiveRunStamps.add" in src)
        assertTrue("compile must LiveRunStamps.remove in finally", "LiveRunStamps.remove" in src)
        assertTrue("compile must place artifacts via RunArtifacts.plan", "RunArtifacts.plan" in src)
        assertTrue("compile must use runs/<stamp>/ layout", "ensureRunDir" in src)
        // Legacy flat names must not be the primary write path any more.
        assertFalse(
            "must not write source_\$stamp.c at the temp root any more",
            Regex("""File\(\s*tempDir\s*,\s*"source_\$""").containsMatchIn(src),
        )
    }

    @Test
    fun `Settings Storage exposes temporary-files row and Clear`() {
        val src = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt"
        ).readText()
        assertTrue("Settings must show TempGc.measure", "TempGc.measure" in src)
        assertTrue("Settings must offer TempGc.clearIdle", "TempGc.clearIdle" in src)
        assertTrue(src.contains("temporary_files_title"))
    }

    @Test
    fun `ShellEnvironment exports PYTHONPYCACHEPREFIX`() {
        val src = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/terminal/ShellEnvironment.kt"
        ).readText()
        assertTrue("PYTHONPYCACHEPREFIX" in src)
    }
}
