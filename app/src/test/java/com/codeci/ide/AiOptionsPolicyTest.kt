package com.codeci.ide

import com.codeci.ide.ui.ai.AiActivityDisplay
import com.codeci.ide.ui.ai.AiAgentLimits
import com.codeci.ide.ui.ai.AiAnswerDetail
import com.codeci.ide.ui.ai.AiBackupMode
import com.codeci.ide.ui.ai.AiBudgetOffer
import com.codeci.ide.ui.ai.AiOptions
import com.codeci.ide.ui.ai.AiOptionsPolicy
import com.codeci.ide.ui.ai.AiReviewer
import com.codeci.ide.ui.ai.AiToolLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 87 / Level 10 — the nine bounded controls' pure policy.
 *
 * The clamp cases exist because **S9** is the whole level: an option that can
 * produce a value outside its declared range is a permission change wearing an
 * option's clothes.
 */
class AiOptionsPolicyTest {

    // ---- read window --------------------------------------------------------

    @Test
    fun `read window clamps at and beyond both bounds`() {
        assertEquals(50, AiOptionsPolicy.clampReadWindow(49))
        assertEquals(50, AiOptionsPolicy.clampReadWindow(50))
        assertEquals(150, AiOptionsPolicy.clampReadWindow(150))
        assertEquals(400, AiOptionsPolicy.clampReadWindow(400))
        assertEquals(400, AiOptionsPolicy.clampReadWindow(401))
        assertEquals(400, AiOptionsPolicy.clampReadWindow(9_999))
    }

    @Test
    fun `read window rejects zero and negatives by falling back to the default`() {
        // coerceIn(50, 400) of 0 would be 50, which would silently shrink an
        // untouched install's reads. A non-positive value means "no real choice".
        assertEquals(AiOptionsPolicy.DEFAULT_READ_WINDOW_LINES, AiOptionsPolicy.clampReadWindow(0))
        assertEquals(AiOptionsPolicy.DEFAULT_READ_WINDOW_LINES, AiOptionsPolicy.clampReadWindow(-7))
    }

    @Test
    fun `read window ceiling IS the tool ceiling, never a copy of it`() {
        assertEquals(AiToolLimits.MAX_READ_LINES, AiOptionsPolicy.MAX_READ_WINDOW_LINES)
        assertEquals(400, AiToolLimits.MAX_READ_LINES)
    }

    @Test
    fun `read window default preserves today's behaviour`() {
        // Today `AiTools` defaults an omitted end to MAX_READ_LINES, so shipping a
        // smaller default would change every install on upgrade. Pinned so the
        // Level 10 spec's proposed 150 cannot land silently.
        assertEquals(AiToolLimits.MAX_READ_LINES, AiOptionsPolicy.DEFAULT_READ_WINDOW_LINES)
        assertEquals(400, AiOptionsPolicy.DEFAULT_READ_WINDOW_LINES)
    }

    @Test
    fun `read window decodes garbage to the default`() {
        assertEquals(400, AiOptionsPolicy.readWindow(null))
        assertEquals(400, AiOptionsPolicy.readWindow(""))
        assertEquals(400, AiOptionsPolicy.readWindow("lots"))
        assertEquals(100, AiOptionsPolicy.readWindow("100"))
        assertEquals(400, AiOptionsPolicy.readWindow("999999"))
    }

    // ---- working set --------------------------------------------------------

    @Test
    fun `working set depth clamps at and beyond both bounds`() {
        assertEquals(2, AiOptionsPolicy.clampWorkingSetDepth(1))
        assertEquals(2, AiOptionsPolicy.clampWorkingSetDepth(2))
        assertEquals(4, AiOptionsPolicy.clampWorkingSetDepth(4))
        assertEquals(8, AiOptionsPolicy.clampWorkingSetDepth(8))
        assertEquals(8, AiOptionsPolicy.clampWorkingSetDepth(9))
        assertEquals(8, AiOptionsPolicy.clampWorkingSetDepth(1_000))
    }

    @Test
    fun `working set default is today's KEEP_LAST_RESULTS`() {
        assertEquals(AiAgentLimits.KEEP_LAST_RESULTS, AiOptionsPolicy.DEFAULT_WORKING_SET_DEPTH)
        assertEquals(4, AiOptionsPolicy.DEFAULT_WORKING_SET_DEPTH)
    }

    @Test
    fun `working set decodes garbage to the default`() {
        assertEquals(4, AiOptionsPolicy.workingSetDepth(null))
        assertEquals(4, AiOptionsPolicy.workingSetDepth("nope"))
        assertEquals(6, AiOptionsPolicy.workingSetDepth("6"))
        assertEquals(8, AiOptionsPolicy.workingSetDepth("99"))
    }

    // ---- enums --------------------------------------------------------------

    @Test
    fun `every enum decodes a valid name, an unknown name and null`() {
        assertEquals(AiAnswerDetail.THOROUGH, AiOptionsPolicy.answerDetail("THOROUGH"))
        assertEquals(AiAnswerDetail.NORMAL, AiOptionsPolicy.answerDetail("verbose"))
        assertEquals(AiAnswerDetail.NORMAL, AiOptionsPolicy.answerDetail(null))

        assertEquals(AiActivityDisplay.EXPANDED, AiOptionsPolicy.activity("EXPANDED"))
        assertEquals(AiActivityDisplay.COLLAPSED, AiOptionsPolicy.activity("wide"))
        assertEquals(AiActivityDisplay.COLLAPSED, AiOptionsPolicy.activity(null))

        assertEquals(AiBackupMode.MANUAL, AiOptionsPolicy.backup("MANUAL"))
        assertEquals(AiBackupMode.OFF, AiOptionsPolicy.backup("AUTO"))
        assertEquals(AiBackupMode.OFF, AiOptionsPolicy.backup(null))

        assertEquals(AiBudgetOffer.NO_OFFER, AiOptionsPolicy.budgetOffer("NO_OFFER"))
        assertEquals(AiBudgetOffer.OFFER, AiOptionsPolicy.budgetOffer("maybe"))
        assertEquals(AiBudgetOffer.OFFER, AiOptionsPolicy.budgetOffer(null))

        assertEquals(AiReviewer.ON, AiOptionsPolicy.reviewer("ON"))
        assertEquals(AiReviewer.OFF, AiOptionsPolicy.reviewer("yes"))
        assertEquals(AiReviewer.OFF, AiOptionsPolicy.reviewer(null))
    }

    @Test
    fun `task memory is on unless explicitly false, matching the existing convention`() {
        assertTrue(AiOptionsPolicy.booleanOn(null))
        assertTrue(AiOptionsPolicy.booleanOn(""))
        assertTrue(AiOptionsPolicy.booleanOn("true"))
        assertFalse(AiOptionsPolicy.booleanOn("false"))
    }

    // ---- answer detail ------------------------------------------------------

    @Test
    fun `NORMAL reproduces the sentence that used to be hardcoded, verbatim`() {
        // This is the whole point of the default: an untouched install must send
        // byte-identical instructions after the upgrade.
        assertEquals(
            "Keep answers short: they are read on a phone.",
            AiOptionsPolicy.detailSentence(AiAnswerDetail.NORMAL)
        )
    }

    @Test
    fun `the three detail levels are three different sentences`() {
        val sentences = AiAnswerDetail.entries.map { AiOptionsPolicy.detailSentence(it) }.toSet()
        assertEquals(3, sentences.size)
        assertTrue(AiOptionsPolicy.detailSentence(AiAnswerDetail.THOROUGH).contains("thoroughly"))
        assertTrue(AiOptionsPolicy.detailSentence(AiAnswerDetail.BRIEF).contains("short"))
    }

    // ---- whole-set decode ---------------------------------------------------

    @Test
    fun `decode of all-absent properties is the default set`() {
        assertEquals(
            AiOptions(),
            AiOptionsPolicy.decode(null, null, null, null, null, null, null, null)
        )
    }

    @Test
    fun `decode round-trips a full set unchanged`() {
        val set = AiOptions(
            readWindowLines = 100,
            workingSetDepth = 6,
            taskMemory = false,
            answerDetail = AiAnswerDetail.THOROUGH,
            activity = AiActivityDisplay.EXPANDED,
            backup = AiBackupMode.MANUAL,
            budgetOffer = AiBudgetOffer.NO_OFFER,
            reviewer = AiReviewer.ON
        )
        assertEquals(
            set,
            AiOptionsPolicy.decode(
                readWindow = "100",
                workingSet = "6",
                taskMemory = "false",
                answerDetail = "THOROUGH",
                activity = "EXPANDED",
                backup = "MANUAL",
                budgetOffer = "NO_OFFER",
                reviewer = "ON"
            )
        )
    }

    @Test
    fun `decode of a hostile set lands entirely inside the ranges`() {
        val decoded = AiOptionsPolicy.decode(
            readWindow = "999999",
            workingSet = "-3",
            taskMemory = "false",
            answerDetail = "DROP TABLE",
            activity = null,
            backup = "AUTOMATIC",
            budgetOffer = "!!!",
            reviewer = "ON"
        )
        assertTrue(decoded.readWindowLines in 50..AiOptionsPolicy.MAX_READ_WINDOW_LINES)
        assertTrue(decoded.workingSetDepth in 2..8)
        assertEquals(AiAnswerDetail.NORMAL, decoded.answerDetail)
        assertEquals(AiActivityDisplay.COLLAPSED, decoded.activity)
        assertEquals(AiBackupMode.OFF, decoded.backup)
        assertEquals(AiBudgetOffer.OFFER, decoded.budgetOffer)
        assertEquals(AiReviewer.ON, decoded.reviewer)
    }

    @Test
    fun `the defaults are the safe values`() {
        val d = AiOptionsPolicy.DEFAULT
        assertEquals(400, d.readWindowLines)
        assertEquals(4, d.workingSetDepth)
        assertTrue(d.taskMemory)
        assertEquals(AiAnswerDetail.NORMAL, d.answerDetail)
        assertEquals(AiActivityDisplay.COLLAPSED, d.activity)
        assertEquals(AiBackupMode.OFF, d.backup)
        assertEquals(AiBudgetOffer.OFFER, d.budgetOffer)
        assertEquals(AiReviewer.OFF, d.reviewer)
    }
}
