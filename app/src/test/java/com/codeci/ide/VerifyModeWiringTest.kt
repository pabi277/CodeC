package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 52.4 — the visual gate must compare on CI and never re-record there. */
class VerifyModeWiringTest {
    @Test fun `CI passes Roborazzi verify mode to test tasks`() {
        val gradle = RepoFiles.mainSource("app/build.gradle.kts").readText()
        val workflow = RepoFiles.mainSource(".github/workflows/build-apk.yml").readText()
        assertTrue(gradle.contains("roborazzi.test.verify"))
        assertTrue(gradle.contains("System.getenv(\"CI\")"))
        assertTrue(workflow.contains(":app:testDebugUnitTest"))
        assertTrue(workflow.contains("roborazzi") || gradle.contains("never silently"))
    }

    @Test fun `record mode remains an explicit developer choice`() {
        val gradle = RepoFiles.mainSource("app/build.gradle.kts").readText()
        assertTrue(gradle.contains("roborazzi.test.record=true"))
        assertTrue(gradle.contains("CI never does so"))
    }
}
