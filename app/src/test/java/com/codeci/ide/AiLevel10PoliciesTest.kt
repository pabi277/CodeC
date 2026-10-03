package com.codeci.ide

import com.codeci.ide.ui.ai.AiActivityDisplay
import com.codeci.ide.ui.ai.AiActivityPolicy
import com.codeci.ide.ui.ai.AiAgentCaps
import com.codeci.ide.ui.ai.AiAgentStopReason
import com.codeci.ide.ui.ai.AiBackupMode
import com.codeci.ide.ui.ai.AiBackupOffer
import com.codeci.ide.ui.ai.AiBackupProviderPolicy
import com.codeci.ide.ui.ai.AiBudgetExtensionPolicy
import com.codeci.ide.ui.ai.AiBudgetOffer
import com.codeci.ide.ui.ai.AiProviderId
import com.codeci.ide.ui.ai.AiReviewVerdict
import com.codeci.ide.ui.ai.AiReviewer
import com.codeci.ide.ui.ai.AiReviewerPolicy
import com.codeci.ide.ui.ai.AiToolProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 87 / Level 10 — the four S-rule policies (87.5–87.8), pure. */
class AiLevel10PoliciesTest {

    // ---- 87.5 activity display (S8 / S1) ------------------------------------

    @Test
    fun `collapsed and expanded rows describe the same bytes`() {
        // The whole S1 guarantee: the display state changes what is drawn, never
        // what was sent. Same sizes either way.
        val system = "SYSTEM TEXT"
        val user = "USER TEXT THAT IS LONGER"
        val collapsed = AiActivityPolicy.row(
            AiProviderId.GEMINI, "m", system, user, AiActivityDisplay.COLLAPSED
        )
        val expanded = AiActivityPolicy.row(
            AiProviderId.GEMINI, "m", system, user, AiActivityDisplay.EXPANDED
        )
        assertEquals(collapsed.systemChars, expanded.systemChars)
        assertEquals(collapsed.userChars, expanded.userChars)
        assertEquals(collapsed.provider, expanded.provider)
        assertEquals(collapsed.model, expanded.model)
        assertTrue(collapsed.collapsed)
        assertFalse(expanded.collapsed)
        assertEquals(system.length, collapsed.systemChars)
        assertEquals(user.length, collapsed.userChars)
    }

    @Test
    fun `the collapsed summary names the recipient and the sizes, not the content`() {
        val row = AiActivityPolicy.row(
            AiProviderId.NVIDIA, "z-ai/glm-5.3", "secret-ish system", "user", AiActivityDisplay.COLLAPSED
        )
        assertTrue(row.summary.contains("NVIDIA Build"))
        assertTrue(row.summary.contains("z-ai/glm-5.3"))
        assertFalse(row.summary.contains("secret-ish"))
    }

    @Test
    fun `the pre-Send preview is never collapsible`() {
        // D4 needs the exact text visible before Send; collapsing the timeline is
        // fine, collapsing the preview would break the gate.
        assertFalse(AiActivityPolicy.previewIsCollapsible())
    }

    // ---- 87.7 backup provider (S8 / D4) -------------------------------------

    @Test
    fun `OFF never offers, whatever happened`() {
        for (reason in AiAgentStopReason.entries) {
            assertEquals(
                AiBackupOffer.None,
                AiBackupProviderPolicy.offer(
                    AiBackupMode.OFF, AiProviderId.GEMINI, reason,
                    setOf(AiProviderId.GEMINI, AiProviderId.NVIDIA),
                    setOf(AiProviderId.GEMINI, AiProviderId.NVIDIA)
                )
            )
        }
    }

    @Test
    fun `an offer appears only on a provider-attributable stop`() {
        val both = setOf(AiProviderId.GEMINI, AiProviderId.NVIDIA)
        assertEquals(
            AiBackupOffer.Offer(AiProviderId.NVIDIA),
            AiBackupProviderPolicy.offer(
                AiBackupMode.MANUAL, AiProviderId.GEMINI,
                AiAgentStopReason.PROVIDER_FAILURE, both, both
            )
        )
        for (reason in AiAgentStopReason.entries - AiAgentStopReason.PROVIDER_FAILURE) {
            assertEquals(
                "should not offer on $reason",
                AiBackupOffer.None,
                AiBackupProviderPolicy.offer(
                    AiBackupMode.MANUAL, AiProviderId.GEMINI, reason, both, both
                )
            )
        }
        assertEquals(
            AiBackupOffer.None,
            AiBackupProviderPolicy.offer(AiBackupMode.MANUAL, AiProviderId.GEMINI, null, both, both)
        )
    }

    @Test
    fun `the failed provider is never offered back`() {
        val both = setOf(AiProviderId.GEMINI, AiProviderId.NVIDIA)
        assertEquals(
            AiProviderId.GEMINI,
            AiBackupProviderPolicy.candidate(AiProviderId.NVIDIA, both, both)
        )
        assertEquals(null, AiBackupProviderPolicy.candidate(AiProviderId.GEMINI, setOf(AiProviderId.GEMINI), both))
    }

    @Test
    fun `an unconfigured provider is never offered`() {
        val both = setOf(AiProviderId.GEMINI, AiProviderId.NVIDIA)
        assertEquals(
            AiBackupOffer.None,
            AiBackupProviderPolicy.offer(
                AiBackupMode.MANUAL, AiProviderId.GEMINI,
                AiAgentStopReason.PROVIDER_FAILURE,
                setOf(AiProviderId.GEMINI), both
            )
        )
    }

    @Test
    fun `a provider without its own accepted terms is never offered`() {
        // Consent is per provider and is never replayed across providers (S8).
        assertEquals(
            AiBackupOffer.None,
            AiBackupProviderPolicy.offer(
                AiBackupMode.MANUAL, AiProviderId.GEMINI,
                AiAgentStopReason.PROVIDER_FAILURE,
                setOf(AiProviderId.GEMINI, AiProviderId.NVIDIA),
                setOf(AiProviderId.GEMINI)
            )
        )
    }

    // ---- 87.6 budget extension (S9 / S12) -----------------------------------

    @Test
    fun `an extension is offered only at a turn or tool budget stop`() {
        for (reason in listOf(AiAgentStopReason.TURN_BUDGET, AiAgentStopReason.TOOL_BUDGET)) {
            assertTrue(AiBudgetExtensionPolicy.offerAt(AiBudgetOffer.OFFER, reason, 0))
        }
        for (reason in AiAgentStopReason.entries -
            setOf(AiAgentStopReason.TURN_BUDGET, AiAgentStopReason.TOOL_BUDGET)
        ) {
            assertFalse("should not offer on $reason", AiBudgetExtensionPolicy.offerAt(AiBudgetOffer.OFFER, reason, 0))
        }
        assertFalse(AiBudgetExtensionPolicy.offerAt(AiBudgetOffer.OFFER, null, 0))
    }

    @Test
    fun `NO_OFFER never offers`() {
        for (reason in AiAgentStopReason.entries) {
            assertFalse(AiBudgetExtensionPolicy.offerAt(AiBudgetOffer.NO_OFFER, reason, 0))
        }
    }

    @Test
    fun `at most one extension per task`() {
        assertTrue(AiBudgetExtensionPolicy.offerAt(AiBudgetOffer.OFFER, AiAgentStopReason.TURN_BUDGET, 0))
        assertFalse(AiBudgetExtensionPolicy.offerAt(AiBudgetOffer.OFFER, AiAgentStopReason.TURN_BUDGET, 1))
        assertFalse(AiBudgetExtensionPolicy.offerAt(AiBudgetOffer.OFFER, AiAgentStopReason.TURN_BUDGET, 5))
    }

    @Test
    fun `an extension raises read-only budget and returns the run count unchanged`() {
        val caps = AiAgentCaps()
        val extended = AiBudgetExtensionPolicy.extend(caps)
        assertEquals(caps.turns + AiBudgetExtensionPolicy.EXTRA_TURNS, extended.turns)
        assertEquals(caps.toolCalls + AiBudgetExtensionPolicy.EXTRA_TOOL_CALLS, extended.toolCalls)
        // S9: the run count is a side-effect cap and an owner cap. Untouched.
        assertEquals(caps.runs, extended.runs)
        assertEquals(2, extended.runs)
    }

    @Test
    fun `a second extension still cannot raise the run count`() {
        val twice = AiBudgetExtensionPolicy.extend(AiBudgetExtensionPolicy.extend(AiAgentCaps()))
        assertEquals(2, twice.runs)
    }

    // ---- 87.8 read-only reviewer (S3 / S7) ----------------------------------

    @Test
    fun `a review needs the option on, a user tap, and an idle surface`() {
        assertTrue(AiReviewerPolicy.mayTrigger(AiReviewer.ON, true, true))
        assertFalse(AiReviewerPolicy.mayTrigger(AiReviewer.OFF, true, true))
        assertFalse(AiReviewerPolicy.mayTrigger(AiReviewer.ON, false, true))
        assertFalse(AiReviewerPolicy.mayTrigger(AiReviewer.ON, true, false))
    }

    @Test
    fun `the reviewer instruction names neither tool protocol`() {
        val text = AiReviewerPolicy.instruction()
        assertFalse(text.contains(AiToolProtocol.OPEN))
        assertFalse(text.contains("read_file"))
        assertFalse(text.contains("request_run"))
        assertTrue(text.contains("no tools"))
    }

    @Test
    fun `tool markup in a review is displayed as text, never parsed into a call`() {
        val markup = "Here is a fix: <<<CODEC_TOOL name=\"read_file\">>>\npath: a.kt\n<<<END_CODEC_TOOL>>>"
        val verdict = AiReviewerPolicy.parse(markup)
        assertTrue(verdict is AiReviewVerdict.MarkupShownAsText)
        assertEquals(markup, (verdict as AiReviewVerdict.MarkupShownAsText).text)
    }

    @Test
    fun `edit markup in a review is displayed as text too`() {
        val verdict = AiReviewerPolicy.parse("try <<<CODEC_EDIT path=\"a.kt\" op=\"modify\">>>")
        assertTrue(verdict is AiReviewVerdict.MarkupShownAsText)
    }

    @Test
    fun `plain prose in a review is plain text`() {
        val verdict = AiReviewerPolicy.parse("The loop is fine; the null check is missing.")
        assertTrue(verdict is AiReviewVerdict.Text)
        assertEquals("The loop is fine; the null check is missing.", (verdict as AiReviewVerdict.Text).text)
    }
}
