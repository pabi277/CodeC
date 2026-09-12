package com.codeci.ide

import com.codeci.ide.ui.guide.ChromeState
import com.codeci.ide.ui.guide.CoachMarkPlan
import com.codeci.ide.ui.guide.GuideAnchors
import com.codeci.ide.ui.guide.GuideSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 45.2 — the coach-mark plan. Every case below is one line of the spec
 * that would otherwise be discovered on a phone:
 *  - a mark only ever points at a control the caller says is ON SCREEN, and a
 *    surface with nothing visible marks nothing seen (so it can show later);
 *  - two marks per arrival, one per anchor for the app's life;
 *  - a blocked screen (exit survey, safe mode, an in-flight Phase 44 setup bar)
 *    suppresses marks without consuming them;
 *  - the CSV preference round-trips and drops garbage.
 */
class CoachMarkPlanTest {

    private val allVisible = ChromeState(
        drawerButtonVisible = true,
        runButtonVisible = true,
        navHandleVisible = true,
        installCardVisible = true,
        statusChipVisible = true
    )

    // ---- the plan itself ---------------------------------------------------

    @Test
    fun `five marks, one per anchor, and the editor's three are ordered`() {
        assertEquals(5, CoachMarkPlan.steps.size)
        assertEquals(
            "ids must be unique",
            CoachMarkPlan.steps.size,
            CoachMarkPlan.steps.map { it.id }.distinct().size
        )
        assertEquals(
            "one mark per anchor",
            CoachMarkPlan.steps.size,
            CoachMarkPlan.steps.map { it.anchorId }.distinct().size
        )
        assertEquals(
            "every anchor has a mark and no mark points at an unknown anchor",
            GuideAnchors.all.toSet(),
            CoachMarkPlan.steps.map { it.anchorId }.toSet()
        )
        assertEquals(
            listOf(GuideAnchors.EDITOR_DRAWER, GuideAnchors.EDITOR_RUN, GuideAnchors.NAV_HANDLE),
            CoachMarkPlan.steps.filter { it.surface == GuideSurface.EDITOR }.map { it.anchorId }
        )
        assertEquals(1, CoachMarkPlan.steps.count { it.surface == GuideSurface.PACKAGES })
        assertEquals(1, CoachMarkPlan.steps.count { it.surface == GuideSurface.TERMINAL })
    }

    @Test
    fun `the cap is two marks per arrival - a third would be a tour`() {
        assertEquals(2, CoachMarkPlan.MAX_PER_ARRIVAL)
        // The host's counter is what enforces it: with two already shown, an
        // unseen visible step is still not returned.
        val seen = setOf(GuideAnchors.EDITOR_DRAWER, GuideAnchors.EDITOR_RUN)
        assertNull(
            CoachMarkPlan.stepForArrival(GuideSurface.EDITOR, seen, allVisible, shownThisArrival = 2)
        )
        assertEquals(
            GuideAnchors.NAV_HANDLE,
            CoachMarkPlan.stepForArrival(GuideSurface.EDITOR, seen, allVisible, shownThisArrival = 1)?.id
        )
        assertNull(CoachMarkPlan.stepForArrival(null, seen, allVisible, shownThisArrival = 0))
    }

    @Test
    fun `every mark fits the card`() {
        for (step in CoachMarkPlan.steps) {
            assertTrue("${step.id}: title blank", step.title.isNotBlank())
            assertTrue("${step.id}: body blank", step.body.isNotBlank())
            assertTrue(
                "${step.id}: title ${step.title.length} > ${CoachMarkPlan.MAX_TITLE_CHARS}",
                step.title.length <= CoachMarkPlan.MAX_TITLE_CHARS
            )
            assertTrue(
                "${step.id}: body ${step.body.length} > ${CoachMarkPlan.MAX_BODY_CHARS}",
                step.body.length <= CoachMarkPlan.MAX_BODY_CHARS
            )
            // The copy names real controls only (the same vocabulary pin the
            // slides use — a coach mark pointing at a renamed control is worse
            // than no mark at all).
            assertEquals(
                "${step.id}: names something the product does not have",
                emptyList<String>(),
                com.codeci.ide.ui.guide.GuideVocabulary.unprovenTerms(
                    listOf(
                        com.codeci.ide.ui.guide.GuideSlide(
                            id = step.id,
                            title = step.title,
                            body = step.body,
                            actionLabel = ""
                        )
                    )
                )
            )
        }
    }

    // ---- the visibility law ------------------------------------------------

    @Test
    fun `a step is only returned when its anchor is on screen`() {
        val nothingVisible = ChromeState()
        for (surface in GuideSurface.entries) {
            assertNull(CoachMarkPlan.nextUnseen(surface, emptySet(), nothingVisible))
            assertEquals(emptyList<Any>(), CoachMarkPlan.stepsFor(surface, nothingVisible))
            assertFalse(CoachMarkPlan.canShow(surface, nothingVisible))
        }
        // Only RUN is laid out: the plan skips the ☰ mark instead of pointing
        // at a button that is not there.
        val runOnly = ChromeState(runButtonVisible = true)
        assertEquals(
            GuideAnchors.EDITOR_RUN,
            CoachMarkPlan.nextUnseen(GuideSurface.EDITOR, emptySet(), runOnly)?.id
        )
        assertEquals(1, CoachMarkPlan.stepsFor(GuideSurface.EDITOR, runOnly).size)
    }

    @Test
    fun `an all-hidden surface marks nothing seen so it can show later`() {
        // The failure mode this kills: a surface consumed on arrival while its
        // anchors were off screen, so the user never gets the mark at all.
        var seen = emptySet<String>()
        val step = CoachMarkPlan.nextUnseen(GuideSurface.EDITOR, seen, ChromeState())
        assertNull(step)
        if (step != null) seen = CoachMarkPlan.markSeen(seen, step.id)
        assertEquals(emptySet<String>(), seen)
        // …and the same surface teaches on a later arrival, once visible.
        assertEquals(
            GuideAnchors.EDITOR_DRAWER,
            CoachMarkPlan.nextUnseen(
                GuideSurface.EDITOR,
                seen,
                ChromeState(drawerButtonVisible = true)
            )?.id
        )
    }

    @Test
    fun `the show-tabs mark waits for the handle the keyboard reveals`() {
        // Exit condition 2 of 45.2: the handle exists only while the bar is
        // hidden in the editor (Phase 32.1), so its mark must not fire before.
        val othersSeen = setOf(GuideAnchors.EDITOR_DRAWER, GuideAnchors.EDITOR_RUN)
        assertNull(
            CoachMarkPlan.nextUnseen(
                GuideSurface.EDITOR,
                othersSeen,
                ChromeState(drawerButtonVisible = true, runButtonVisible = true, navHandleVisible = false)
            )
        )
        assertEquals(
            GuideAnchors.NAV_HANDLE,
            CoachMarkPlan.nextUnseen(
                GuideSurface.EDITOR,
                othersSeen,
                ChromeState(drawerButtonVisible = true, runButtonVisible = true, navHandleVisible = true)
            )?.id
        )
    }

    @Test
    fun `a blocked screen suppresses marks without consuming them`() {
        val blocked = allVisible.copy(blockedByForeground = true)
        for (surface in GuideSurface.entries) {
            assertNull(CoachMarkPlan.nextUnseen(surface, emptySet(), blocked))
            assertFalse(CoachMarkPlan.canShow(surface, blocked))
            assertNull(CoachMarkPlan.stepForArrival(surface, emptySet(), blocked, shownThisArrival = 0))
        }
        // Visibility is still reported (the two halves of the rule stay
        // separate, so the host can tell "nothing to show" from "not now").
        assertTrue(CoachMarkPlan.stepsFor(GuideSurface.EDITOR, blocked).isNotEmpty())
    }

    @Test
    fun `the seen set suppresses a surface permanently`() {
        val seen = setOf(GuideAnchors.EDITOR_DRAWER, GuideAnchors.EDITOR_RUN, GuideAnchors.NAV_HANDLE)
        assertNull(CoachMarkPlan.nextUnseen(GuideSurface.EDITOR, seen, allVisible))
        // canShow is the "is there anything to teach right now" question and
        // deliberately ignores `seen` — the host asks nextUnseen for the step.
        assertTrue(CoachMarkPlan.canShow(GuideSurface.EDITOR, allVisible))
        // …while an unseen surface still teaches.
        assertEquals(
            GuideAnchors.TERMINAL_CHIP,
            CoachMarkPlan.nextUnseen(GuideSurface.TERMINAL, seen, allVisible)?.id
        )
    }

    @Test
    fun `markSeen is monotonic and ignores unknown ids`() {
        val once = CoachMarkPlan.markSeen(emptySet(), GuideAnchors.EDITOR_RUN)
        assertEquals(setOf(GuideAnchors.EDITOR_RUN), once)
        assertEquals("marking twice changes nothing", once, CoachMarkPlan.markSeen(once, GuideAnchors.EDITOR_RUN))
        assertEquals(
            "an id from another build must not enter the set",
            once,
            CoachMarkPlan.markSeen(once, "editor_teleport")
        )
        assertEquals(once, CoachMarkPlan.markSeen(once, ""))
    }

    // ---- persistence -------------------------------------------------------

    @Test
    fun `the seen csv round-trips, sorts by plan order and drops garbage`() {
        val seen = setOf(GuideAnchors.NAV_HANDLE, GuideAnchors.EDITOR_DRAWER)
        val csv = CoachMarkPlan.serializeSeen(seen)
        assertEquals("${GuideAnchors.EDITOR_DRAWER},${GuideAnchors.NAV_HANDLE}", csv)
        assertEquals(seen, CoachMarkPlan.parseSeen(csv))
        assertEquals(emptySet<String>(), CoachMarkPlan.parseSeen(null))
        assertEquals(emptySet<String>(), CoachMarkPlan.parseSeen(""))
        assertEquals(
            setOf(GuideAnchors.EDITOR_DRAWER, GuideAnchors.NAV_HANDLE),
            CoachMarkPlan.parseSeen(" ${GuideAnchors.EDITOR_DRAWER} ,, editor_teleport , ${GuideAnchors.NAV_HANDLE} ")
        )
        assertEquals("", CoachMarkPlan.serializeSeen(emptySet()))
        // Reset tips = an empty CSV = every mark returns, and nothing else.
        assertEquals(emptySet<String>(), CoachMarkPlan.parseSeen(CoachMarkPlan.serializeSeen(emptySet())))
    }

    @Test
    fun `chrome state is built from the set of laid-out anchors`() {
        val chrome = ChromeState.of(
            visibleAnchors = setOf(GuideAnchors.PACKAGES_CARD),
            blockedByForeground = false
        )
        assertTrue(chrome.installCardVisible)
        assertFalse(chrome.drawerButtonVisible)
        assertFalse(chrome.runButtonVisible)
        assertFalse(chrome.navHandleVisible)
        assertFalse(chrome.statusChipVisible)
        assertFalse(chrome.blockedByForeground)
        assertTrue(chrome.anchorVisible(GuideAnchors.PACKAGES_CARD))
        assertFalse("an unknown anchor is never visible", chrome.anchorVisible("editor_teleport"))
        assertTrue(ChromeState.of(emptySet(), blockedByForeground = true).blockedByForeground)
    }

    // ---- routes ------------------------------------------------------------

    @Test
    fun `routes map to the surfaces that teach, and nothing else`() {
        assertEquals(GuideSurface.EDITOR, CoachMarkPlan.surfaceForRoute("editor"))
        assertEquals(
            GuideSurface.EDITOR,
            CoachMarkPlan.surfaceForRoute("editor?projectName=Demo&fileName=main.c")
        )
        assertEquals(GuideSurface.TERMINAL, CoachMarkPlan.surfaceForRoute("terminal?nonce=17"))
        assertEquals(GuideSurface.PACKAGES, CoachMarkPlan.surfaceForRoute("modules"))
        assertNull(CoachMarkPlan.surfaceForRoute("file_manager"))
        assertNull(CoachMarkPlan.surfaceForRoute("settings"))
        assertNull(CoachMarkPlan.surfaceForRoute("preview?projectName=Demo"))
        assertNull(CoachMarkPlan.surfaceForRoute("logs"))
        assertNull(CoachMarkPlan.surfaceForRoute("feedback?rating=5"))
        assertNull(CoachMarkPlan.surfaceForRoute(null))
        assertNull(CoachMarkPlan.surfaceForRoute(""))
    }
}
