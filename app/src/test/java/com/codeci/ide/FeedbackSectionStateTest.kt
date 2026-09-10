package com.codeci.ide

import com.codeci.ide.ui.support.FeedbackAction
import com.codeci.ide.ui.support.FeedbackDraft
import com.codeci.ide.ui.support.FeedbackInput
import com.codeci.ide.ui.support.FeedbackSectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 41.2 — the Feedback & Support section's pure decisions: which rows
 * exist, which action is primary, what is prefilled, and (the part that is
 * a privacy behaviour, not a comment) that the attachment choices are a
 * FRESH choice per report. The Compose layout itself is compile + lint in
 * CI and the owner's device round.
 */
class FeedbackSectionStateTest {

    // Exit 41.2.1 — with no WhatsApp number, COPY is the primary action.
    @Test
    fun `with no whatsapp number the primary action is COPY`() {
        val s = FeedbackSectionState(userText = "it crashed")
        assertFalse(s.chatAvailable)
        assertFalse(s.emailAvailable)
        assertEquals(FeedbackAction.COPY, s.primaryAction)
    }

    @Test
    fun `a valid number plus text makes CHAT the primary action`() {
        val s = FeedbackSectionState(userText = "it crashed", whatsappNumberE164 = "919876543210")
        assertTrue(s.chatAvailable)
        assertTrue(s.chatReady)
        assertEquals(FeedbackAction.CHAT, s.primaryAction)
    }

    @Test
    fun `CHAT needs something to say - blank text keeps COPY primary`() {
        val s = FeedbackSectionState(userText = "   ", whatsappNumberE164 = "919876543210")
        assertTrue(s.chatAvailable)
        assertFalse(s.chatReady)
        assertEquals(FeedbackAction.COPY, s.primaryAction)
    }

    @Test
    fun `a stored-but-invalid number hides the chat row, not breaks it`() {
        val s = FeedbackSectionState(userText = "hi", whatsappNumberE164 = "12345")
        assertFalse(s.chatAvailable)
        assertEquals(FeedbackAction.COPY, s.primaryAction)
    }

    @Test
    fun `the email channel appears only when an address is stored`() {
        assertFalse(FeedbackSectionState(contactEmail = null).emailAvailable)
        assertFalse(FeedbackSectionState(contactEmail = "  ").emailAvailable)
        assertTrue(FeedbackSectionState(contactEmail = "owner@example.com").emailAvailable)
    }

    // Exit 41.2.4 / README exit 3 — the checkboxes are the truth in the draft.
    @Test
    fun `checkboxes off - the draft contains neither log nor crash section headers`() {
        val crash = "==== 2026-09-10 01:22:11 thread=main ====\njava.lang.RuntimeException: forced"
        val draft = FeedbackDraft.build(
            base(state = FeedbackSectionState(userText = "hi")).copy(
                includeLog = false,
                logTail = listOf("E/A: 1"),
                includeCrash = false,
                crashRecord = crash
            )
        )
        assertFalse(draft.contains("--- log"))
        assertFalse(draft.contains("--- crash"))
    }

    @Test
    fun `a crash present but unticked still stays out of the draft`() {
        val crash = "==== 2026-09-10 01:22:11 thread=main ====\njava.lang.RuntimeException: forced"
        val state = FeedbackSectionState(
            userText = "hi",
            crashRecordPresent = true,
            includeCrash = false // explicitly unticked by the user
        )
        val draft = FeedbackDraft.build(base(state).copy(crashRecord = crash))
        assertFalse(draft.contains("--- crash"))
    }

    @Test
    fun `ticked boxes put their sections in the draft`() {
        val crash = "==== 2026-09-10 01:22:11 thread=main ====\njava.lang.RuntimeException: forced"
        val state = FeedbackSectionState(
            userText = "hi",
            includeLog = true,
            crashRecordPresent = true,
            includeCrash = true
        )
        val draft = FeedbackDraft.build(base(state).copy(logTail = listOf("E/A: 1"), crashRecord = crash))
        assertTrue(draft.contains("--- log (last 1 lines, redacted) ---"))
        assertTrue(draft.contains("--- crash ---"))
    }

    // Exit 41.2.5 — after a crash, the crash row is prefilled in.
    @Test
    fun `a fresh state prefills the crash box exactly when a crash is present`() {
        assertTrue(FeedbackSectionState(crashRecordPresent = true).includeCrash)
        assertFalse(FeedbackSectionState(crashRecordPresent = false).includeCrash)
    }

    /** The section's choices mapped onto the draft input, the way the card does it. */
    private fun base(state: FeedbackSectionState): FeedbackInput = FeedbackInput(
        appVersion = "1.3.16 (3439922)",
        androidRelease = "13",
        apiLevel = 33,
        device = "Redmi Note 9",
        abis = "arm64-v8a",
        project = "hello-c",
        screen = "Settings",
        userText = state.userText,
        includeLog = state.includeLog,
        logTail = emptyList(),
        includeCrash = state.includeCrash,
        crashRecord = null
    )
}
