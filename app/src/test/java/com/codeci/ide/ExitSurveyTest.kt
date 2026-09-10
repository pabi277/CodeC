package com.codeci.ide

import com.codeci.ide.ui.support.ExitSurvey
import com.codeci.ide.ui.support.FeedbackDraft
import com.codeci.ide.ui.support.FeedbackInput
import com.codeci.ide.ui.support.FeedbackStore
import com.codeci.ide.ui.support.FeedbackContacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 41 follow-up (owner, after device round 1) — the exit survey's
 * pure decisions and the shipped defaults:
 *  - the owner's number/email ship in the APK (the Phase 42 open question,
 *    decided: DEFAULT_WHATSAPP_NUMBER = +91 62967 46606);
 *  - a rating renders as stars + an info-line fragment, and a rating the
 *    user did not tap (0, null, out of range) never appears in a report;
 *  - the wiring pins: the exit dialog + Feedback screen exist and are
 *    navigable, the back-at-root path shows the prompt, and neither the
 *    dialog nor the screen touches a DataStore directly (only the store's
 *    savers persist anything).
 */
class ExitSurveyTest {

    // ------------------------------------------------------------------
    // The shipped defaults (the owner's real support contacts)
    // ------------------------------------------------------------------

    @Test
    fun `the shipped default number is the owners E164 digits and normalises to itself`() {
        assertEquals("916296746606", FeedbackStore.DEFAULT_WHATSAPP_NUMBER)
        // What the owner typed: "+91 62967 46606" must normalise to exactly it.
        assertEquals(FeedbackStore.DEFAULT_WHATSAPP_NUMBER, FeedbackContacts.numberForStorage("+91 62967 46606"))
        // A stored default round-trips (idempotent).
        assertEquals(
            FeedbackStore.DEFAULT_WHATSAPP_NUMBER,
            FeedbackContacts.numberForStorage(FeedbackStore.DEFAULT_WHATSAPP_NUMBER)
        )
    }

    @Test
    fun `the shipped default email is the owners address and passes validation`() {
        assertEquals("chakraborttypabi2772006@gmail.com", FeedbackStore.DEFAULT_CONTACT_EMAIL)
        assertEquals(
            FeedbackStore.DEFAULT_CONTACT_EMAIL,
            FeedbackContacts.emailForStorage(FeedbackStore.DEFAULT_CONTACT_EMAIL)
        )
    }

    // ------------------------------------------------------------------
    // Rating display + the report fragment
    // ------------------------------------------------------------------

    @Test
    fun `stars renders filled then empty stars`() {
        assertEquals("★★★★★", ExitSurvey.stars(5))
        assertEquals("★★★★☆", ExitSurvey.stars(4))
        assertEquals("★☆☆☆☆", ExitSurvey.stars(1))
        assertEquals("☆☆☆☆☆", ExitSurvey.stars(0))
        // Out-of-range inputs clamp instead of crashing the dialog.
        assertEquals("★★★★★", ExitSurvey.stars(9))
        assertEquals("☆☆☆☆☆", ExitSurvey.stars(-3))
    }

    @Test
    fun `ratingLine is produced only for a rating someone actually tapped`() {
        assertEquals("Rating: 4/5", ExitSurvey.ratingLine(4))
        assertNull(ExitSurvey.ratingLine(null))
        assertNull(ExitSurvey.ratingLine(0))
        assertNull(ExitSurvey.ratingLine(6))
        assertNull(ExitSurvey.ratingLine(-1))
        assertFalse(ExitSurvey.hasRating(0))
        assertFalse(ExitSurvey.hasRating(6))
        assertTrue(ExitSurvey.hasRating(3))
    }

    @Test
    fun `a report from the exit survey carries the rating on the info line`() {
        val report = FeedbackDraft.build(input(exitRating = 4))
        assertTrue(report.contains("Project: hello-c · Screen: Feedback · Rating: 4/5"))
    }

    @Test
    fun `a report opened directly carries no rating fragment`() {
        val report = FeedbackDraft.build(input(exitRating = 0))
        assertFalse(report.contains("Rating:"))
        // And an out-of-range value is dropped, not rendered as nonsense.
        assertFalse(FeedbackDraft.build(input(exitRating = 9)).contains("Rating:"))
    }

    private fun input(exitRating: Int?) = FeedbackInput(
        appVersion = "1.3.16 (3439922)",
        androidRelease = "13",
        apiLevel = 33,
        device = "Redmi Note 9",
        abis = "arm64-v8a",
        project = "hello-c",
        screen = "Feedback",
        exitRating = exitRating,
        userText = "nice app",
        maxChars = Int.MAX_VALUE
    )

    // ------------------------------------------------------------------
    // Wiring pins (source scans — CI compiles what was reviewed)
    // ------------------------------------------------------------------

    private fun source(path: String): String = RepoFiles.mainSource(path).readText()

    @Test
    fun `the review target is the public repo over https`() {
        assertEquals("https://github.com/pabi277/CodeC", ExitSurvey.REPO_URL)
    }

    @Test
    fun `back at root shows the prompt and a second back exits - the wiring exists`() {
        val main = source("app/src/main/java/com/codeci/ide/MainActivity.kt")
        assertTrue("the BackHandler must exist", main.contains("BackHandler("))
        assertTrue(
            "back at root decides between the prompt and a direct exit",
            main.contains("if (exitPromptEnabled) exitPromptVisible = true else activity.finish()")
        )
        assertTrue(
            "the prompt is off-able via the store flow",
            main.contains("feedbackStore.exitPromptEnabledFlow.collectAsState")
        )
        assertTrue(
            "the dialog's own back press is the exit (tap again to exit)",
            source("app/src/main/java/com/codeci/ide/ui/support/ExitFeedbackDialog.kt")
                .contains("onDismissRequest = onExit")
        )
        assertTrue(
            "an accidental outside tap must not close the app",
            source("app/src/main/java/com/codeci/ide/ui/support/ExitFeedbackDialog.kt")
                .contains("dismissOnClickOutside = false")
        )
    }

    @Test
    fun `feedback is a separate screen reachable from settings and the exit survey`() {
        val main = source("app/src/main/java/com/codeci/ide/MainActivity.kt")
        assertTrue("Screen.Feedback must be registered in the NavHost", main.contains("Screen.Feedback.route"))
        assertTrue(
            "SHARE EXPERIENCE navigates to the screen with the rating",
            main.contains("Screen.Feedback.createRoute(rating)")
        )
        val settings = source("app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt")
        assertTrue(
            "Settings opens the screen (owner: \"can it be a separate page?\")",
            settings.contains("onNavigateToFeedback")
        )
        val nav = source("app/src/main/java/com/codeci/ide/ui/navigation/Screen.kt")
        assertTrue("the destination exists with a rating arg", nav.contains("feedback?rating={rating}"))
    }

    @Test
    fun `the exit dialog and the feedback screen persist nothing by themselves`() {
        for (path in listOf(
            "app/src/main/java/com/codeci/ide/ui/support/ExitFeedbackDialog.kt",
            "app/src/main/java/com/codeci/ide/ui/screens/FeedbackScreen.kt",
            "app/src/main/java/com/codeci/ide/ui/support/ExitSurvey.kt"
        )) {
            val src = source(path)
            assertTrue("no DataStore writes in $path", !src.contains("dataStore.edit"))
            assertTrue("no preference keys in $path", !src.contains("PreferencesKey"))
        }
    }
}
