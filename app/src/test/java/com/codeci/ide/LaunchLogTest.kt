package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 52.2 — first paint is a diagnostic sample, not continuous shipping instrumentation. */
class LaunchLogTest {
    private val main = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/MainActivity.kt").readText()

    @Test fun `first frame log carries elapsed time and route`() {
        assertTrue(main.contains("onFirstFrame = { route ->"))
        assertTrue(main.contains("AppLogger.i("))
        assertTrue(main.contains("\"Launch\""))
        assertTrue(main.contains("firstFrameMs="))
        assertTrue(main.contains("route=${'$'}route"))
    }

    @Test fun `continuous frame callbacks were not added to the app`() {
        assertTrue(main.contains("Continuous frame instrumentation belongs in"))
        // FrameMetrics/Choreographer belong to the bench; this test prevents a
        // later speed tweak from turning :app into a permanent profiler.
        assertTrue(
            RepoFiles.mainKotlinSources()
                .filter { it.name != "MainActivity.kt" }
                .none { it.readText().contains("Choreographer.FrameCallback") },
        )
    }
}
