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
 * Phase 45.2, rebuilt as ONE ORDERED TOUR after the owner's device round
 * (2026-09-12): *"the guided box are not consistent with flow … you didn't add
 * all … remove the next option only the guide will show click the option where
 * showing the guide to the next"*, and then the flow itself:
 *
 * > ☰ → change the project folder to demo_flask → select app.py → run → install
 * > python → the Flask web opens → close → the reveal-the-tabs handle → a small
 * > tour of Packages and Terminal.
 *
 * Every case below is one line of that spec which would otherwise be discovered
 * on a phone:
 *  - the tour is ordered, and the order IS the owner's list;
 *  - a box only ever points at a control the caller says is ON SCREEN;
 *  - a step that [CoachStep.waits] stops the tour until its control appears
 *    (so the tour cannot start on the Terminal tab), and a step that does not
 *    wait is passed over WITHOUT being marked seen (so the tour cannot stall on
 *    a control that only sometimes exists, and the lesson is not spent);
 *  - a blocked screen (exit survey, safe mode, an in-flight Phase 44 download)
 *    suppresses boxes without consuming them;
 *  - SKIP/Back marks the whole tour seen — nothing returns on its own;
 *  - the CSV preference round-trips, keeps tour order and drops garbage.
 */
class CoachMarkPlanTest {

    /** The owner's flow, as anchor ids, in order. Pinned verbatim on purpose. */
    private val tourOrder = listOf(
        GuideAnchors.EDITOR_DRAWER,
        GuideAnchors.DRAWER_PROJECT,
        GuideAnchors.DRAWER_FILE,
        GuideAnchors.EDITOR_RUN,
        GuideAnchors.PREVIEW_CLOSE,
        GuideAnchors.NAV_HANDLE,
        GuideAnchors.NAV_TAB_PACKAGES,
        GuideAnchors.PACKAGES_CARD,
        GuideAnchors.NAV_TAB_TERMINAL,
        GuideAnchors.TERMINAL_CHIP
    )

    private fun chrome(
        vararg anchors: String,
        blocked: Boolean = false,
        drawer: Boolean = false
    ) = ChromeState(
        visibleAnchors = anchors.toSet(),
        blockedByForeground = blocked,
        drawerOpen = drawer
    )

    private val allVisible = chrome(
        GuideAnchors.EDITOR_DRAWER,
        GuideAnchors.DRAWER_PROJECT,
        GuideAnchors.DRAWER_FILE,
        GuideAnchors.EDITOR_RUN,
        GuideAnchors.PREVIEW_CLOSE,
        GuideAnchors.NAV_HANDLE,
        GuideAnchors.NAV_TAB_PACKAGES,
        GuideAnchors.PACKAGES_CARD,
        GuideAnchors.NAV_TAB_TERMINAL,
        GuideAnchors.TERMINAL_CHIP
    )

    // ---- the tour itself ---------------------------------------------------

    @Test
    fun `the tour is the owner's ten beats, in the owner's order`() {
        assertEquals(tourOrder, CoachMarkPlan.steps.map { it.anchorId })
        assertEquals(
            "ids must be unique",
            CoachMarkPlan.steps.size,
            CoachMarkPlan.steps.map { it.id }.distinct().size
        )
        assertEquals(
            "one box per anchor, and every anchor has a box",
            GuideAnchors.all.toSet(),
            CoachMarkPlan.steps.map { it.anchorId }.toSet()
        )
        assertEquals(
            "the step id IS the anchor id (the seen-set is keyed by it)",
            CoachMarkPlan.steps.map { it.id },
            CoachMarkPlan.steps.map { it.anchorId }
        )
    }

    @Test
    fun `each beat is taught on the screen that owns it`() {
        assertEquals(
            listOf(
                GuideSurface.EDITOR,
                GuideSurface.EDITOR,
                GuideSurface.EDITOR,
                GuideSurface.EDITOR,
                GuideSurface.PREVIEW,
                GuideSurface.EDITOR,
                GuideSurface.PACKAGES,
                GuideSurface.PACKAGES,
                GuideSurface.TERMINAL,
                GuideSurface.TERMINAL
            ),
            CoachMarkPlan.steps.map { it.surface }
        )
        assertEquals(GuideSurface.PREVIEW, CoachMarkPlan.step(GuideAnchors.PREVIEW_CLOSE)?.surface)
        assertEquals(GuideSurface.PACKAGES, CoachMarkPlan.step(GuideAnchors.NAV_TAB_PACKAGES)?.surface)
        assertEquals(GuideSurface.PACKAGES, CoachMarkPlan.step(GuideAnchors.PACKAGES_CARD)?.surface)
        assertEquals(GuideSurface.TERMINAL, CoachMarkPlan.step(GuideAnchors.NAV_TAB_TERMINAL)?.surface)
        assertEquals(GuideSurface.TERMINAL, CoachMarkPlan.step(GuideAnchors.TERMINAL_CHIP)?.surface)
    }

    @Test
    fun `only the always-there controls make the tour wait for them`() {
        // Waiting is what keeps the tour in order; passing-over is what keeps it
        // from stalling. A control that only sometimes exists must never be a
        // waiting step, or the tour would stop forever the moment it is absent.
        assertEquals(
            listOf(
                GuideAnchors.EDITOR_DRAWER,
                GuideAnchors.EDITOR_RUN,
                GuideAnchors.NAV_TAB_PACKAGES,
                GuideAnchors.NAV_TAB_TERMINAL
            ),
            CoachMarkPlan.steps.filter { it.waits }.map { it.anchorId }
        )
        for (step in CoachMarkPlan.steps.filterNot { it.waits }) {
            assertTrue(
                "${step.anchorId} only sometimes exists, so it must not wait",
                step.anchorId in listOf(
                    GuideAnchors.DRAWER_PROJECT,
                    GuideAnchors.DRAWER_FILE,
                    GuideAnchors.PREVIEW_CLOSE,
                    GuideAnchors.NAV_HANDLE,
                    GuideAnchors.PACKAGES_CARD,
                    GuideAnchors.TERMINAL_CHIP
                )
            )
        }
    }

    @Test
    fun `every box fits the card`() {
        for (step in CoachMarkPlan.steps) {
            assertTrue(
                "${step.id} title is ${step.title.length} chars (max ${CoachMarkPlan.MAX_TITLE_CHARS})",
                step.title.length <= CoachMarkPlan.MAX_TITLE_CHARS
            )
            assertTrue(
                "${step.id} body is ${step.body.length} chars (max ${CoachMarkPlan.MAX_BODY_CHARS})",
                step.body.length <= CoachMarkPlan.MAX_BODY_CHARS
            )
            assertTrue("${step.id} body must be one line", !step.body.contains('\n'))
            assertTrue("${step.id} must say something", step.body.isNotBlank())
        }
    }

    // ---- the visibility law ------------------------------------------------

    @Test
    fun `the tour cannot start anywhere but the editor's drawer button`() {
        // Step 1 waits, so on a screen without ☰ (a fresh install diverted to the
        // Terminal by Phase 44.1) nothing at all is shown — no out-of-order box.
        assertNull(
            "RUN ▶ alone must not start the tour",
            CoachMarkPlan.nextStep(emptySet(), chrome(GuideAnchors.EDITOR_RUN))
        )
        assertNull(
            "the terminal chip alone must not start the tour",
            CoachMarkPlan.nextStep(emptySet(), chrome(GuideAnchors.TERMINAL_CHIP))
        )
        assertEquals(
            GuideAnchors.EDITOR_DRAWER,
            CoachMarkPlan.nextStep(emptySet(), chrome(GuideAnchors.EDITOR_DRAWER))?.id
        )
        assertTrue(CoachMarkPlan.canShow(emptySet(), chrome(GuideAnchors.EDITOR_DRAWER)))
        assertFalse(CoachMarkPlan.canShow(emptySet(), chrome()))
    }

    @Test
    fun `a box is only returned when its anchor is on screen`() {
        val seen = setOf(GuideAnchors.EDITOR_DRAWER)
        // The drawer is closed again: steps 2 and 3 do not wait, so the tour
        // passes them and teaches RUN ▶ now — and 2/3 stay unseen for later.
        assertEquals(
            GuideAnchors.EDITOR_RUN,
            CoachMarkPlan.nextStep(seen, chrome(GuideAnchors.EDITOR_RUN))?.id
        )
        // The drawer is open on demo_flask: the project box is not published
        // (already there), so the app.py row is next — the owner's beat 3.
        assertEquals(
            GuideAnchors.DRAWER_FILE,
            CoachMarkPlan.nextStep(
                seen,
                chrome(GuideAnchors.DRAWER_FILE, GuideAnchors.EDITOR_RUN, drawer = true)
            )?.id
        )
        // The drawer is open on some other project: beat 2 comes before beat 3.
        assertEquals(
            GuideAnchors.DRAWER_PROJECT,
            CoachMarkPlan.nextStep(
                seen,
                chrome(
                    GuideAnchors.DRAWER_PROJECT,
                    GuideAnchors.DRAWER_FILE,
                    GuideAnchors.EDITOR_RUN,
                    drawer = true
                )
            )?.id
        )
    }

    @Test
    fun `a passed-over step is never marked seen, so it can still teach later`() {
        var seen = setOf(GuideAnchors.EDITOR_DRAWER)
        // RUN ▶ taught while the drawer was shut.
        val shown = CoachMarkPlan.nextStep(seen, chrome(GuideAnchors.EDITOR_RUN))
        assertEquals(GuideAnchors.EDITOR_RUN, shown?.id)
        seen = CoachMarkPlan.markSeen(seen, shown!!.id)
        assertFalse("the skipped drawer beats must not be spent", GuideAnchors.DRAWER_FILE in seen)
        assertFalse(GuideAnchors.DRAWER_PROJECT in seen)
        // Later the user opens the drawer on demo_flask: beat 3 is still there.
        assertEquals(
            GuideAnchors.DRAWER_FILE,
            CoachMarkPlan.nextStep(seen, chrome(GuideAnchors.DRAWER_FILE, drawer = true))?.id
        )
    }

    @Test
    fun `a drawer beat needs the drawer open, and a covered beat needs it shut`() {
        val seen = setOf(GuideAnchors.EDITOR_DRAWER)
        // M3 keeps the closed drawer's rows laid out, so the row can publish a
        // rect nobody can see: with the drawer shut, RUN ▶ teaches instead.
        assertEquals(
            GuideAnchors.EDITOR_RUN,
            CoachMarkPlan.nextStep(seen, chrome(GuideAnchors.DRAWER_FILE, GuideAnchors.EDITOR_RUN))?.id
        )
        // Drawer open: the row is the lesson, and RUN ▶ — which the drawer is
        // covering — waits instead of getting a hole cut behind the panel.
        assertEquals(
            GuideAnchors.DRAWER_FILE,
            CoachMarkPlan.nextStep(
                seen,
                chrome(GuideAnchors.DRAWER_FILE, GuideAnchors.EDITOR_RUN, drawer = true)
            )?.id
        )
        val drawerBeatsSeen = seen + GuideAnchors.DRAWER_PROJECT + GuideAnchors.DRAWER_FILE
        assertNull(
            "a waiting beat covered by the open drawer must not be cut",
            CoachMarkPlan.nextStep(drawerBeatsSeen, chrome(GuideAnchors.EDITOR_RUN, drawer = true))
        )
        assertEquals(
            GuideAnchors.EDITOR_RUN,
            CoachMarkPlan.nextStep(drawerBeatsSeen, chrome(GuideAnchors.EDITOR_RUN))?.id
        )
        assertEquals(
            "exactly the two beats inside the ☰ drawer are drawer beats",
            listOf(GuideAnchors.DRAWER_PROJECT, GuideAnchors.DRAWER_FILE),
            CoachMarkPlan.steps.filter { it.inDrawer }.map { it.anchorId }
        )
    }

    @Test
    fun `a waiting step holds the tour until its control appears`() {
        val seen = tourOrder.take(5).toSet() // through the preview's Back
        // The tab bar is hidden by the keyboard and the reveal handle is not laid
        // out yet: beat 6 does not wait, so beat 7 (which does) holds the tour —
        // no box on a bar that is not there.
        assertNull(CoachMarkPlan.nextStep(seen, chrome(GuideAnchors.PACKAGES_CARD)))
        // The handle appears: it is taught, then the tabs.
        assertEquals(
            GuideAnchors.NAV_HANDLE,
            CoachMarkPlan.nextStep(seen, chrome(GuideAnchors.NAV_HANDLE))?.id
        )
        assertEquals(
            GuideAnchors.NAV_TAB_PACKAGES,
            CoachMarkPlan.nextStep(
                seen + GuideAnchors.NAV_HANDLE,
                chrome(GuideAnchors.NAV_TAB_PACKAGES, GuideAnchors.NAV_TAB_TERMINAL)
            )?.id
        )
    }

    @Test
    fun `the small tour of packages and terminal walks tab, card, tab, chip`() {
        var seen = tourOrder.take(6).toSet()
        val bar = chrome(GuideAnchors.NAV_TAB_PACKAGES, GuideAnchors.NAV_TAB_TERMINAL)
        assertEquals(GuideAnchors.NAV_TAB_PACKAGES, CoachMarkPlan.nextStep(seen, bar)?.id)
        seen += GuideAnchors.NAV_TAB_PACKAGES
        // On the Packages tab the card is laid out; the Terminal tab is still
        // visible in the bar, but order wins.
        val packages = chrome(GuideAnchors.NAV_TAB_TERMINAL, GuideAnchors.PACKAGES_CARD)
        assertEquals(GuideAnchors.PACKAGES_CARD, CoachMarkPlan.nextStep(seen, packages)?.id)
        seen += GuideAnchors.PACKAGES_CARD
        assertEquals(GuideAnchors.NAV_TAB_TERMINAL, CoachMarkPlan.nextStep(seen, packages)?.id)
        seen += GuideAnchors.NAV_TAB_TERMINAL
        assertEquals(
            GuideAnchors.TERMINAL_CHIP,
            CoachMarkPlan.nextStep(seen, chrome(GuideAnchors.TERMINAL_CHIP))?.id
        )
        seen += GuideAnchors.TERMINAL_CHIP
        assertTrue(CoachMarkPlan.isComplete(seen))
        assertNull("a finished tour never comes back", CoachMarkPlan.nextStep(seen, allVisible))
        assertEquals(emptyList<Any>(), CoachMarkPlan.remaining(seen))
    }

    @Test
    fun `a blocked screen suppresses the tour without consuming it`() {
        val blocked = chrome(GuideAnchors.EDITOR_DRAWER, GuideAnchors.EDITOR_RUN, blocked = true)
        assertNull(CoachMarkPlan.nextStep(emptySet(), blocked))
        assertFalse(CoachMarkPlan.canShow(emptySet(), blocked))
        // Nothing was spent: the same screen unblocked teaches beat 1.
        assertEquals(
            GuideAnchors.EDITOR_DRAWER,
            CoachMarkPlan.nextStep(
                emptySet(),
                chrome(GuideAnchors.EDITOR_DRAWER, GuideAnchors.EDITOR_RUN)
            )?.id
        )
    }

    // ---- the seen set ------------------------------------------------------

    @Test
    fun `skip and back end the whole tour, so nothing returns on its own`() {
        val afterSkip = CoachMarkPlan.markAllSeen(setOf(GuideAnchors.EDITOR_DRAWER))
        assertTrue(CoachMarkPlan.isComplete(afterSkip))
        assertNull(CoachMarkPlan.nextStep(afterSkip, allVisible))
        assertEquals(tourOrder.size, afterSkip.size)
        // Skipping from a cold start is just as final (one tap, no nag).
        assertTrue(CoachMarkPlan.isComplete(CoachMarkPlan.markAllSeen(emptySet())))
    }

    @Test
    fun `markSeen is monotonic and ignores unknown ids`() {
        val seen = CoachMarkPlan.markSeen(emptySet(), GuideAnchors.EDITOR_DRAWER)
        assertEquals(setOf(GuideAnchors.EDITOR_DRAWER), seen)
        assertEquals(seen, CoachMarkPlan.markSeen(seen, GuideAnchors.EDITOR_DRAWER))
        assertEquals(
            "an id no step owns must not enter the set",
            seen,
            CoachMarkPlan.markSeen(seen, "a_step_that_never_existed")
        )
    }

    @Test
    fun `the seen csv round-trips, keeps tour order and drops garbage`() {
        val seen = setOf(
            GuideAnchors.TERMINAL_CHIP,
            GuideAnchors.EDITOR_DRAWER,
            GuideAnchors.EDITOR_RUN
        )
        val csv = CoachMarkPlan.serializeSeen(seen)
        assertEquals(
            "serialised in tour order, not set order",
            "${GuideAnchors.EDITOR_DRAWER},${GuideAnchors.EDITOR_RUN},${GuideAnchors.TERMINAL_CHIP}",
            csv
        )
        assertEquals(seen, CoachMarkPlan.parseSeen(csv))
        assertEquals(emptySet<String>(), CoachMarkPlan.parseSeen(null))
        assertEquals(emptySet<String>(), CoachMarkPlan.parseSeen(""))
        assertEquals(emptySet<String>(), CoachMarkPlan.parseSeen(" , ,"))
        assertEquals(
            "a build that shipped fewer steps must not poison the set",
            setOf(GuideAnchors.EDITOR_DRAWER),
            CoachMarkPlan.parseSeen("editor_drawer,nav_bar,packages_card_typo,")
        )
    }

    @Test
    fun `chrome state is built from the set of laid-out anchors`() {
        val state = ChromeState.of(
            visibleAnchors = setOf(GuideAnchors.EDITOR_RUN, GuideAnchors.NAV_HANDLE),
            blockedByForeground = false
        )
        assertTrue(state.anchorVisible(GuideAnchors.EDITOR_RUN))
        assertTrue(state.anchorVisible(GuideAnchors.NAV_HANDLE))
        assertFalse(state.anchorVisible(GuideAnchors.EDITOR_DRAWER))
        assertFalse(state.anchorVisible("not_an_anchor"))
        assertFalse(ChromeState().anchorVisible(GuideAnchors.EDITOR_RUN))
        assertTrue(ChromeState.of(emptySet(), true).blockedByForeground)
        assertFalse("the drawer is shut unless the editor says otherwise", state.drawerOpen)
        assertTrue(ChromeState.of(emptySet(), false, drawerOpen = true).drawerOpen)
    }

    // ---- the pure wiring helpers the Android edge calls --------------------

    @Test
    fun `routes map to the two tabs the tour teaches, and nothing else`() {
        assertEquals(GuideAnchors.NAV_TAB_PACKAGES, CoachMarkPlan.tabAnchorFor("modules"))
        assertEquals(GuideAnchors.NAV_TAB_TERMINAL, CoachMarkPlan.tabAnchorFor("terminal"))
        assertEquals(
            GuideAnchors.NAV_TAB_TERMINAL,
            CoachMarkPlan.tabAnchorFor("terminal?sessionId=abc")
        )
        assertNull(CoachMarkPlan.tabAnchorFor("editor?fileName=app.py&projectName=demo_flask"))
        assertNull(CoachMarkPlan.tabAnchorFor("filemanager"))
        assertNull(CoachMarkPlan.tabAnchorFor("settings"))
        assertNull(CoachMarkPlan.tabAnchorFor(""))
        assertNull(CoachMarkPlan.tabAnchorFor(null))
    }

    @Test
    fun `the project box is offered only where switching teaches something`() {
        assertEquals(
            GuideAnchors.DRAWER_PROJECT,
            CoachMarkPlan.drawerProjectAnchor("C Starter", "demo_flask")
        )
        assertNull(
            "already in the demo: nothing to switch to",
            CoachMarkPlan.drawerProjectAnchor("demo_flask", "demo_flask")
        )
        assertNull(
            "scratch mode has no project name to tap",
            CoachMarkPlan.drawerProjectAnchor(null, "demo_flask")
        )
    }

    @Test
    fun `the file box is the demo's entry file and nothing else`() {
        assertEquals(
            GuideAnchors.DRAWER_FILE,
            CoachMarkPlan.drawerFileAnchor(
                projectName = "demo_flask",
                relativePath = "app.py",
                isDirectory = false,
                demoProjectName = "demo_flask",
                demoEntryFile = "app.py"
            )
        )
        assertNull(
            CoachMarkPlan.drawerFileAnchor("demo_flask", "index.html", false, "demo_flask", "app.py")
        )
        assertNull(
            CoachMarkPlan.drawerFileAnchor("C Starter", "app.py", false, "demo_flask", "app.py")
        )
        assertNull(
            CoachMarkPlan.drawerFileAnchor(null, "app.py", false, "demo_flask", "app.py")
        )
        assertNull(
            "a directory called app.py is not the file",
            CoachMarkPlan.drawerFileAnchor("demo_flask", "app.py", true, "demo_flask", "app.py")
        )
        assertNull(
            CoachMarkPlan.drawerFileAnchor("demo_flask", "src/app.py", false, "demo_flask", "app.py")
        )
    }
}
