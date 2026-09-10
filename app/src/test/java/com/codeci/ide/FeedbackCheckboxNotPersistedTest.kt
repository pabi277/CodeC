package com.codeci.ide

import com.codeci.ide.ui.support.FeedbackSectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 41.2 — "no silent always-on attachment". The attachment checkboxes
 * are a privacy behaviour, so they are tested, not commented: toggling them
 * and coming back later must start from the same fresh defaults, and no
 * store may ever carry their state.
 *
 * Implemented as (1) the fresh-state pins — a new `FeedbackSectionState` IS
 * what "leave the screen and return" gives, because the card's state lives
 * in `remember` — and (2) a structural source scan: no feedback-shaped key
 * of any kind exists in any preference store, and the section's card never
 * writes to a DataStore. Compose-navigation under Robolectric would test
 * the same thing far more expensively and unverifiably-before-CI (40.4's
 * law).
 */
class FeedbackCheckboxNotPersistedTest {

    @Test
    fun `a fresh state has the log box off`() {
        assertFalse(FeedbackSectionState().includeLog)
        assertFalse(FeedbackSectionState(userText = "hi").includeLog)
    }

    @Test
    fun `a fresh state has the crash box off when no crash is present`() {
        assertFalse(FeedbackSectionState().includeCrash)
    }

    @Test
    fun `leaving and returning resets both boxes to the fresh defaults`() {
        // Round 1: the user ticks everything and a crash is present.
        val round1 = FeedbackSectionState(
            userText = "it broke",
            includeLog = true,
            crashRecordPresent = true,
            includeCrash = true
        )
        assertTrue(round1.includeLog)
        assertTrue(round1.includeCrash)

        // Round 2 (a later visit): a fresh state is what the card gets.
        val round2 = FeedbackSectionState(crashRecordPresent = true)
        assertFalse("the log box is a fresh choice every report", round2.includeLog)
        assertTrue("the crash box is prefilled by the crash's PRESENCE, not by last time's tick", round2.includeCrash)

        // Round 3: the crash was cleared (CrashReportOverlay CLEAR) — nothing sticks.
        val round3 = FeedbackSectionState(crashRecordPresent = false)
        assertFalse(round3.includeCrash)
    }

    // ------------------------------------------------------------------
    // The structural pin: nothing attachment-shaped is ever stored.
    // ------------------------------------------------------------------

    private fun codeOnly(path: String): String =
        RepoFiles.mainSource(path).readLines()
            .joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `no preference store carries a feedback boolean - or any feedback key beyond the two contacts`() {
        val stores = listOf(
            "app/src/main/java/com/codeci/ide/ui/settings/SettingsManager.kt",
            "app/src/main/java/com/codeci/ide/ui/theme/ThemeManager.kt",
            "app/src/main/java/com/codeci/ide/ui/projects/GitCredentialsStore.kt",
            "app/src/main/java/com/codeci/ide/ui/support/FeedbackStore.kt"
        )
        for (path in stores) {
            val src = codeOnly(path)
            assertFalse(
                "attachment state must never be a stored boolean ($path)",
                Regex("[Bb]ooleanPreferencesKey\\(\\s*\"[^\"]*feedback[^\"]*\"").containsMatchIn(src)
            )
            val feedbackKeys = Regex("[!=(]\\s*\\w+PreferencesKey\\(\"([^\"]*feedback[^\"]*)\"\\)")
                .findAll(src).map { it.groupValues[1] }.toList()
            assertTrue(
                "only the two reply-to keys may exist, found $feedbackKeys ($path)",
                feedbackKeys.all { it == "feedback_whatsapp_number" || it == "feedback_contact_email" }
            )
        }
    }

    @Test
    fun `the feedback card never writes to a datastore`() {
        val card = codeOnly("app/src/main/java/com/codeci/ide/ui/support/FeedbackSectionCard.kt")
        assertFalse(
            "the card's only persistence is through FeedbackStore's savers",
            card.contains("dataStore.edit")
        )
        assertFalse(
            "attachment checkboxes must stay local composition state",
            card.contains("PreferencesKey")
        )
    }
}
