package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 52.3 — the About line reads, but never mutates, existing stats. */
class AboutStatsTest {
    private val settings = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt"
    ).readText()
    private val stats = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/stats/StatsManager.kt"
    ).readText()
    private val streak = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/stats/StreakLine.kt"
    ).readText()

    @Test fun `About reads all existing stats flows`() {
        assertTrue(settings.contains("StatsManager(context)"))
        assertTrue(settings.contains("totalRunsFlow.collectAsState"))
        assertTrue(settings.contains("totalFilesCreatedFlow.collectAsState"))
        assertTrue(settings.contains("currentStreakFlow.collectAsState"))
        assertTrue(settings.contains("lastRunDateFlow.collectAsState"))
        assertTrue(settings.contains("StreakLine.forAbout"))
        assertTrue(settings.contains("about_progress"))
        assertTrue(stats.contains("stats_current_streak"))
    }

    @Test fun `the formatter has no storage or notification responsibility`() {
        assertFalse(streak.contains("dataStore"))
        assertFalse(streak.contains("incrementRuns"))
        assertFalse(streak.contains("NotificationManager"))
        assertFalse(streak.contains("AlertDialog"))
        assertFalse(streak.contains("Snackbar"))
        assertTrue(streak.contains("return null"))
    }
}
