package com.codeci.ide

import com.codeci.ide.ui.projects.ResumeFacts
import com.codeci.ide.ui.projects.ResumeOffer
import com.codeci.ide.ui.projects.ResumePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumePolicyTest {
    private fun facts(minutes: Long? = 10L, exists: Boolean = true) = ResumeFacts(
        lastProject = "demo",
        lastFile = "src/main.py",
        stillExists = exists,
        minutesSinceLastOpen = minutes,
    )

    @Test fun `brief return continues in place`() {
        assertEquals(ResumeOffer.CONTINUE_IN_PLACE, ResumePolicy.offerFor(facts(0)))
        assertEquals(ResumeOffer.CONTINUE_IN_PLACE, ResumePolicy.offerFor(facts(5)))
    }

    @Test fun `long return gets a visible card`() {
        assertEquals(ResumeOffer.OFFER_CARD, ResumePolicy.offerFor(facts(5 + 1)))
        assertEquals(ResumeOffer.OFFER_CARD, ResumePolicy.offerFor(facts(null)))
    }

    @Test fun `a crash gets a card instead of a silent jump`() {
        assertEquals(
            ResumeOffer.OFFER_CARD,
            ResumePolicy.offerFor(facts(0).copy(crashedLastTime = true)),
        )
    }

    @Test fun `missing project uses the ordinary hub`() {
        assertEquals(ResumeOffer.HUB, ResumePolicy.offerFor(facts(exists = false)))
    }

    @Test fun `higher priority routes win`() {
        assertEquals(ResumeOffer.NONE, ResumePolicy.offerFor(facts().copy(safeMode = true)))
        assertEquals(ResumeOffer.NONE, ResumePolicy.offerFor(facts().copy(welcomePending = true)))
        assertEquals(ResumeOffer.NONE, ResumePolicy.offerFor(facts().copy(setupNeedsWatching = true)))
    }

    @Test fun `path uses the editor project alias`() {
        assertEquals("~proj/demo/src/main.py", ResumePolicy.displayPath("/demo/", "/src/main.py"))
        assertNull(ResumePolicy.displayPath(null, "main.py"))
        assertTrue(ResumePolicy.displayPath("demo", "") == null)
    }
}
