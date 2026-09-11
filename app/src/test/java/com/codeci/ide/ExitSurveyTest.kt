package com.codeci.ide

import com.codeci.ide.ui.support.DeveloperContact
import com.codeci.ide.ui.support.ExitSurvey
import com.codeci.ide.ui.support.FeedbackDraft
import com.codeci.ide.ui.support.FeedbackInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 41 follow-up (owner, after device round 1) — the exit survey's
 * pure decisions and the hardcoded developer identity:
 *  - the developer's number/email live in CODE (`DeveloperContact`), are
 *    valid, and are the single source for every channel (round 2, owner:
 *    *"I want to sit as developer not some other guy … remove the boxes
 *    and set it in the code"* — no store key, no input field, nothing
 *    user-configurable);
 *  - a rating renders as stars + an info-line fragment, and a rating the
 *    user did not tap (0, null, out of range) never appears in a report;
 *  - the wiring pins: the exit dialog + Feedback screen exist and are
 *    navigable, the back-at-root path shows the prompt, and none of the
 *    feedback surfaces persist anything by themselves.
 */
class ExitSurveyTest {

    // ------------------------------------------------------------------
    // The hardcoded developer contact (round 2: not a setting, ever)
    // ------------------------------------------------------------------

    @Test
    fun `the hardcoded developer number is valid E164 and normalises to itself`() {
        assertEquals("916296746606", DeveloperContact.WHATSAPP_E164)
        // The number the owner gave: "+91 62967 46606" must normalise to
        // exactly the hardcoded constant — a wrong constant means the CHAT
        // row disappears (whatsappUrl returns null), so this is the pin.
        assertEquals(DeveloperContact.WHATSAPP_E164, FeedbackDraft.normaliseNumber("+91 62967 46606"))
        assertEquals(DeveloperContact.WHATSAPP_E164, FeedbackDraft.normaliseNumber(DeveloperContact.WHATSAPP_E164))
        // The display spelling matches the digits.
        assertEquals("+91 62967 46606", DeveloperContact.WHATSAPP_DISPLAY)
    }

    @Test
    fun `the hardcoded developer email is the owners address`() {
        assertEquals("chakraborttypabi2772006@gmail.com", DeveloperContact.EMAIL)
        assertTrue("must be a plausible address", DeveloperContact.EMAIL.indexOf('@') in 1..DeveloperContact.EMAIL.length - 2)
    }

    @Test
    fun `the developer identity is not user-configurable - no store key, no input field`() {
        // Round 2's law, pinned: the number/email exist ONLY as code
        // constants. A store key for them (the round-1 design) must never
        // come back, and the UI must not offer fields to change them.
        val stores = listOf(
            "app/src/main/java/com/codeci/ide/ui/settings/SettingsManager.kt",
            "app/src/main/java/com/codeci/ide/ui/theme/ThemeManager.kt",
            "app/src/main/java/com/codeci/ide/ui/projects/GitCredentialsStore.kt"
        )
        for (path in stores) {
            val src = RepoFiles.mainSource(path).readText()
            assertFalse("a feedback contact key must not exist ($path)", src.contains("feedback_whatsapp_number"))
            assertFalse("a feedback contact key must not exist ($path)", src.contains("feedback_contact_email"))
        }
        assertTrue("FeedbackStore.kt must stay deleted", !RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/support/FeedbackStore.kt").exists())
        val card = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/support/FeedbackSectionCard.kt").readText()
        assertFalse("no WhatsApp-number input field", card.contains("WhatsApp number for replies"))
        assertFalse("no email input field", card.contains("Contact email for replies"))
        assertFalse("no save button for contacts", card.contains("SAVE"))
        assertTrue("the card reads the hardcoded contact", card.contains("DeveloperContact.WHATSAPP_E164"))
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
            "the prompt is off-able via the SettingsManager flow",
            main.contains("settingsManager.feedbackExitPromptEnabledFlow.collectAsState")
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
    fun `the exit dialog and the feedback surfaces persist nothing by themselves`() {
        for (path in listOf(
            "app/src/main/java/com/codeci/ide/ui/support/ExitFeedbackDialog.kt",
            "app/src/main/java/com/codeci/ide/ui/screens/FeedbackScreen.kt",
            "app/src/main/java/com/codeci/ide/ui/support/ExitSurvey.kt",
            "app/src/main/java/com/codeci/ide/ui/support/DeveloperContact.kt"
        )) {
            val src = source(path)
            assertTrue("no DataStore writes in $path", !src.contains("dataStore.edit"))
            assertTrue("no preference keys in $path", !src.contains("PreferencesKey"))
        }
    }
}
