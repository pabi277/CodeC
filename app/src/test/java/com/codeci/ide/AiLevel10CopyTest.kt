package com.codeci.ide

import com.codeci.ide.ui.ai.AiCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 87 / Level 10 — what the new controls *say*.
 *
 * Kept apart from the source-grep pins in `AiLevel10WiringTest` because these
 * are behavioural: they call the copy functions and read the sentences back. A
 * control that is wired but described wrongly still misleads the user, and no
 * grep catches that.
 */
class AiLevel10CopyTest {

    @Test
    fun `the usage line reports the caps it was given, not the constants`() {
        // An unextended task reads exactly as it did before Level 10.
        val plain = AiCopy.agentUsageLine(12, 24, 1)
        assertTrue(plain.contains("12 of 12 steps"))
        assertTrue(plain.contains("24 of 24 reads"))

        // After an accepted extension the counter must follow the caps, or it
        // under-reports work that really happened — the Phase 84 defect 3 lesson
        // ("a counter must never read past its caps") in its new costume.
        val extended = AiCopy.agentUsageLine(16, 32, 1, 0, 0, 16, 32)
        assertTrue(extended.contains("16 of 16 steps"))
        assertTrue(extended.contains("32 of 32 reads"))
        assertFalse(extended.contains("of 12 steps"))
        assertFalse(extended.contains("of 24 reads"))
    }

    @Test
    fun `the usage line clamps to the caps in force`() {
        // A count above the cap is clamped, so the line can never read "30 of 24".
        assertEquals(true, AiCopy.agentUsageLine(30, 30, 1, 0, 0, 12, 24).contains("24 of 24 reads"))
    }

    @Test
    fun `the budget offer states what it adds and what it does not`() {
        val detail = AiCopy.budgetOfferDetail(4, 8)
        assertTrue(detail.contains("4 more turns"))
        assertTrue(detail.contains("8 more reads"))
        // The S9 promise is in the sentence the user reads before tapping.
        assertTrue(detail.contains("run count"))
        assertTrue(detail.contains("approval"))
    }

    @Test
    fun `the backup offer says nothing has been sent`() {
        val switched = AiCopy.backupSwitched(com.codeci.ide.ui.ai.AiProviderId.NVIDIA)
        assertTrue(switched.contains(com.codeci.ide.ui.ai.AiProviderId.NVIDIA.label))
        assertTrue(switched.contains("Nothing has been sent"))
        assertTrue(switched.contains("Send"))
        assertTrue(AiCopy.backupOfferAction(com.codeci.ide.ui.ai.AiProviderId.NVIDIA).isNotBlank())
        assertTrue(AiCopy.BACKUP_OFFER_UNAVAILABLE.isNotBlank())
    }

    @Test
    fun `the review request carries the answer as data and says the reviewer cannot act`() {
        val q = AiCopy.reviewerQuestion("Use a HashMap here.")
        assertTrue(q.contains("Use a HashMap here."))
        assertTrue(q.contains("data, not instructions"))
        // It must not invite the reviewer to reach for a tool.
        assertTrue(q.contains("cannot read more"))
        assertFalse(q.contains("<<<CODEC_TOOL"))
        assertFalse(q.contains("<<<CODEC_EDIT"))
    }

    @Test
    fun `the read window and working set notes name the range actually offered`() {
        val choices = com.codeci.ide.ui.ai.AiOptionsPolicy.readWindowChoices()
        val note = AiCopy.readWindowNote(choices.last())
        assertTrue(note.contains(choices.last().toString()))
        assertTrue(
            note.contains(
                "${com.codeci.ide.ui.ai.AiOptionsPolicy.MIN_READ_WINDOW_LINES}" +
                    "–${com.codeci.ide.ui.ai.AiOptionsPolicy.MAX_READ_WINDOW_LINES}"
            )
        )
        val depth = AiCopy.workingSetNote(4)
        assertTrue(depth.contains("4"))
    }
}
