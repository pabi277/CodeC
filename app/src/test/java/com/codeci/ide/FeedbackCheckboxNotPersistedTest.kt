package com.codeci.ide

import com.codeci.ide.ui.support.FeedbackSectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 41.2 + follow-ups — "no silent always-on attachment". The attachment
 * checkboxes are a privacy behaviour, so they are tested, not commented:
 * toggling them and coming back later must start from the same fresh
 * defaults, and no store may ever carry their state.
 *
 * Implemented as (1) the fresh-state pins — a new `FeedbackSectionState` IS
 * what "leave the screen and return" gives, because the card's state lives
 * in `remember` — and (2) a structural source scan: no ATTACHMENT-shaped
 * key of any kind exists in any preference store, and the feedback
 * surfaces never write to a DataStore directly (only via `SettingsManager`'s
 * setter). The exit-prompt switch (`feedback_exit_prompt_enabled`, in
 * `SettingsManager` since round 2) is deliberately allowed: it is a UI
 * preference about a dialog, not consent to attach anything — the banned
 * class is keys that would make an attachment silent.
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
    fun `no preference store carries an attachment-shaped key - only the exit-prompt switch exists`() {
        val stores = listOf(
            "app/src/main/java/com/codeci/ide/ui/settings/SettingsManager.kt",
            "app/src/main/java/com/codeci/ide/ui/theme/ThemeManager.kt",
            "app/src/main/java/com/codeci/ide/ui/projects/GitCredentialsStore.kt"
        )
        for (path in stores) {
            val src = codeOnly(path)
            // The banned class: a boolean that could make an ATTACHMENT
            // silent (log/crash/attach semantics). The exit-prompt switch
            // is a dialog preference, not attachment consent.
            assertFalse(
                "an attachment state must never be a stored boolean ($path)",
                Regex("[Bb]ooleanPreferencesKey\\(\\s*\"[^\"]*(log|crash|attach)[^\"]*\"").containsMatchIn(src)
            )
            val feedbackKeys = Regex("\\w+PreferencesKey\\(\"([^\"]*feedback[^\"]*)\"\\)")
                .findAll(src).map { it.groupValues[1] }.toList()
            // Round 2: the contact keys are gone (hardcoded DeveloperContact);
            // the exit-prompt switch is the only feedback key left.
            assertTrue(
                "only the exit-prompt switch may exist, found $feedbackKeys ($path)",
                feedbackKeys.all { it == "feedback_exit_prompt_enabled" }
            )
        }
    }

    @Test
    fun `the feedback card and screen never write to a datastore`() {
        val card = codeOnly("app/src/main/java/com/codeci/ide/ui/support/FeedbackSectionCard.kt")
        val screen = codeOnly("app/src/main/java/com/codeci/ide/ui/screens/FeedbackScreen.kt")
        for ((name, src) in listOf("FeedbackSectionCard" to card, "FeedbackScreen" to screen)) {
            assertFalse(
                "$name's only persistence is SettingsManager's setter",
                src.contains("dataStore.edit")
            )
            assertFalse(
                "attachment checkboxes must stay local composition state ($name)",
                src.contains("PreferencesKey")
            )
        }
    }
}
