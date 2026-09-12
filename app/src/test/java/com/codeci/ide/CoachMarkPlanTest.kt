package com.codeci.ide

import com.codeci.ide.ui.guide.ChromeState
import com.codeci.ide.ui.guide.CoachMarkPlan
import com.codeci.ide.ui.guide.GuideAnchors
import com.codeci.ide.ui.guide.GuideRect
import com.codeci.ide.ui.guide.GuideSurface
import com.codeci.ide.ui.guide.GuideTapPolicy
import com.codeci.ide.ui.guide.GuideTapTarget
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
 *  - EVERY beat waits for its own control (round 3: *"I want a full process 1st
 *    to last without skip anything in this"*), so no later beat ever jumps the
 *    queue — which is also why the tour cannot start on the Terminal tab;
 *  - the one thing that can move the tour past a beat is the host's stall guard
 *    ([CoachMarkPlan.waitingOn] + [CoachMarkPlan.STALL_GUARD_MS]), and a stalled
 *    beat is passed WITHOUT being marked seen, so it still teaches on the next
 *    pass;
 *  - a blocked screen (exit survey, safe mode, an in-flight Phase 44 download,
 *    any dialog) suppresses boxes without consuming them, and does NOT arm the
 *    stall guard — a five-minute Python install must not cost the beat after it;
 *  - nothing ends the tour early: there is no `markAllSeen`, and **VIEW AGAIN**
 *    is an empty seen set, so all ten beats come back;
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

    /** The routes the tour walks, as the NavHost knows them. */
    private val editorRoute = "editor?projectName={projectName}&fileName={fileName}"
    private val previewRoute = "preview?projectName={projectName}&fileName={fileName}&url={url}"
    private val modulesRoute = "modules"
    private val terminalRoute = "terminal?cmd={cmd}&nonce={nonce}"

    private fun chrome(
        vararg anchors: String,
        blocked: Boolean = false,
        drawer: Boolean = false,
        route: String? = editorRoute
    ) = ChromeState(
        visibleAnchors = anchors.toSet(),
        blockedByForeground = blocked,
        drawerOpen = drawer,
        route = route
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
    fun `every beat waits, so the tour is first to last with nothing skipped`() {
        // Round 2 gave each beat a "wait or pass over" flag, and passing over is
        // what punched holes in the owner's flow (*"it's not a trough guide mean
        // it got cut"*): the reveal-tabs beat went unseen because the Packages tab
        // happened to be laid out. Round 3: the tour stops on the first beat that
        // is neither taught nor stalled, whatever kind of control it is.
        for (index in CoachMarkPlan.steps.indices) {
            val taught = CoachMarkPlan.steps.take(index).map { it.id }.toSet()
            val later = CoachMarkPlan.steps.drop(index + 1).map { it.anchorId }
            assertNull(
                "${CoachMarkPlan.steps[index].anchorId} must hold the tour until it is on screen",
                CoachMarkPlan.nextStep(taught, chrome(*later.toTypedArray()))
            )
        }
        // The concrete case the owner hit: beat 1 taught, the drawer shut, RUN ▶
        // (beat 4) on screen — and still no box, because beats 2 and 3 come first.
        assertNull(
            "a later beat must not jump the queue",
            CoachMarkPlan.nextStep(
                setOf(GuideAnchors.EDITOR_DRAWER),
                chrome(GuideAnchors.EDITOR_RUN)
            )
        )
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
        val drawerOpen = chrome(
            GuideAnchors.DRAWER_PROJECT,
            GuideAnchors.DRAWER_FILE,
            GuideAnchors.EDITOR_RUN,
            drawer = true
        )
        // The drawer is shut: beats 2 and 3 are not on screen, so nothing is —
        // RUN ▶ is laid out and still waits its turn.
        assertNull(CoachMarkPlan.nextStep(seen, chrome(GuideAnchors.EDITOR_RUN)))
        // The drawer opens: beat 2 (the header, published in every project state)
        // comes before beat 3, because order is the tour.
        assertEquals(GuideAnchors.DRAWER_PROJECT, CoachMarkPlan.nextStep(seen, drawerOpen)?.id)
        assertEquals(
            GuideAnchors.DRAWER_FILE,
            CoachMarkPlan.nextStep(seen + GuideAnchors.DRAWER_PROJECT, drawerOpen)?.id
        )
        // Both drawer beats taught and the drawer closed again: RUN ▶ at last.
        assertEquals(
            GuideAnchors.EDITOR_RUN,
            CoachMarkPlan.nextStep(
                seen + GuideAnchors.DRAWER_PROJECT + GuideAnchors.DRAWER_FILE,
                chrome(GuideAnchors.EDITOR_RUN)
            )?.id
        )
    }

    @Test
    fun `a stalled beat is passed without being spent, so it still teaches later`() {
        val seen = setOf(GuideAnchors.EDITOR_DRAWER)
        val shut = chrome(GuideAnchors.EDITOR_RUN)
        // The drawer never opens. Nothing shows, and the plan itself will never
        // move on: passing a beat is the HOST's decision, after a timed wait.
        assertNull(CoachMarkPlan.nextStep(seen, shut))
        val stalled = setOf(GuideAnchors.DRAWER_PROJECT, GuideAnchors.DRAWER_FILE)
        assertEquals(
            GuideAnchors.EDITOR_RUN,
            CoachMarkPlan.nextStep(seen, shut, stalled)?.id
        )
        // Passed, not taught: neither beat is seen, so `remaining` still lists
        // them and the next pass (next launch, or VIEW AGAIN) still walks them.
        assertFalse(GuideAnchors.DRAWER_PROJECT in seen)
        assertFalse(GuideAnchors.DRAWER_FILE in seen)
        assertEquals(
            listOf(GuideAnchors.DRAWER_PROJECT, GuideAnchors.DRAWER_FILE),
            CoachMarkPlan.remaining(seen).filter { it.inDrawer }.map { it.id }
        )
        // Later the user opens the drawer with nothing stalled: beat 2 is there.
        assertEquals(
            GuideAnchors.DRAWER_PROJECT,
            CoachMarkPlan.nextStep(seen, chrome(GuideAnchors.DRAWER_PROJECT, drawer = true))?.id
        )
        // A stalled beat leaves the tour unfinished in the sense that matters:
        // it is a lesson still owed.
        assertFalse(CoachMarkPlan.isFinished(seen, stalled))
    }

    @Test
    fun `a drawer beat needs the drawer open, and a covered beat needs it shut`() {
        val seen = setOf(GuideAnchors.EDITOR_DRAWER)
        // M3 keeps the closed drawer's rows laid out, so a row can publish a rect
        // nobody can see. With the drawer shut the tour waits on beat 2: no hole
        // behind the panel, and no jumping ahead to RUN ▶.
        assertNull(
            CoachMarkPlan.nextStep(
                seen,
                chrome(GuideAnchors.DRAWER_PROJECT, GuideAnchors.DRAWER_FILE, GuideAnchors.EDITOR_RUN)
            )
        )
        // Drawer open: the header teaches, then the row — and RUN ▶, which the
        // drawer is covering, waits instead of getting a hole cut behind it.
        assertEquals(
            GuideAnchors.DRAWER_PROJECT,
            CoachMarkPlan.nextStep(
                seen,
                chrome(GuideAnchors.DRAWER_PROJECT, GuideAnchors.DRAWER_FILE, GuideAnchors.EDITOR_RUN, drawer = true)
            )?.id
        )
        assertEquals(
            GuideAnchors.DRAWER_FILE,
            CoachMarkPlan.nextStep(
                seen + GuideAnchors.DRAWER_PROJECT,
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
    fun `a beat holds the tour until its control appears, and names itself while it waits`() {
        val seen = tourOrder.take(5).toSet() // through the preview's Back
        // The tab bar is hidden by the keyboard and the reveal handle is not laid
        // out yet, so beat 6 holds the tour. Round 2 walked past it to Packages,
        // which is exactly how a beat went missing on the owner's phone.
        assertNull(CoachMarkPlan.nextStep(seen, chrome(GuideAnchors.PACKAGES_CARD)))
        assertEquals(
            "waitingOn names the beat, so the host knows what it is timing",
            GuideAnchors.NAV_HANDLE,
            CoachMarkPlan.waitingOn(seen, chrome(GuideAnchors.PACKAGES_CARD))?.id
        )
        // The handle appears: it is taught, then the tabs.
        assertNull(
            "a beat that is on screen is not a wait",
            CoachMarkPlan.waitingOn(seen, chrome(GuideAnchors.NAV_HANDLE))
        )
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
    fun `nothing ends the tour early, and VIEW AGAIN restarts all ten beats`() {
        // Round 2 ended the tour with SKIP and Back by marking every unseen beat
        // seen. Round 3 (*"it's not a trough guide mean it got cut"*) removes both
        // exits, so the plan has no such function any more: the only way a beat is
        // marked seen is tapping the control it teaches. A half-walked tour is
        // simply a tour that is not over.
        val half = tourOrder.take(4).toSet()
        assertFalse(CoachMarkPlan.isComplete(half))
        assertFalse(CoachMarkPlan.isFinished(half))
        assertEquals(tourOrder.size - 4, CoachMarkPlan.remaining(half).size)
        // VIEW AGAIN is an empty seen set — no second flag, so the one key keeps
        // one meaning, and Settings → About → Reset tips does the same thing from
        // the other end.
        assertEquals(emptySet<String>(), CoachMarkPlan.replay())
        assertEquals(tourOrder.size, CoachMarkPlan.remaining(CoachMarkPlan.replay()).size)
        assertEquals(
            GuideAnchors.EDITOR_DRAWER,
            CoachMarkPlan.nextStep(CoachMarkPlan.replay(), chrome(GuideAnchors.EDITOR_DRAWER))?.id
        )
        // All ten taught: finished AND complete, and it never comes back on its
        // own (the finish card's CLOSE is in-memory, the seen set is the record).
        val all = tourOrder.toSet()
        assertTrue(CoachMarkPlan.isFinished(all))
        assertTrue(CoachMarkPlan.isComplete(all))
        assertNull(CoachMarkPlan.nextStep(all, allVisible))
        // Nine taught and one stalled is FINISHED — the finish card is earned —
        // but not complete, because that beat is still owed.
        val owed = setOf(GuideAnchors.PREVIEW_CLOSE)
        assertTrue(CoachMarkPlan.isFinished(all - GuideAnchors.PREVIEW_CLOSE, owed))
        assertFalse(CoachMarkPlan.isComplete(all - GuideAnchors.PREVIEW_CLOSE))
        assertNull(CoachMarkPlan.nextStep(all - GuideAnchors.PREVIEW_CLOSE, allVisible, owed))
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
    fun `the guard never times out a beat the user has to travel to`() {
        // The cascade this prevents: RUN ▶ is tapped, Python installs for two
        // minutes, and the tour is sitting on beat 5 — the Flask preview's Back,
        // which does not exist on the editor route. Timing that out would stall
        // beat 5, then beat 6, then every beat behind it, and the tour would
        // "finish" before the owner's flow ever reached the web page. A beat
        // that is merely early draws nothing and waits.
        val fourSeen = tourOrder.take(4).toSet()
        assertNull(
            "the preview's Back is not possible on the editor route, so it is not hopeless",
            CoachMarkPlan.waitingOn(fourSeen, chrome(GuideAnchors.EDITOR_RUN, route = editorRoute))
        )
        assertNull(CoachMarkPlan.nextStep(fourSeen, chrome(GuideAnchors.EDITOR_RUN, route = editorRoute)))
        // Arrive at the preview and the beat is taught at once.
        assertEquals(
            GuideAnchors.PREVIEW_CLOSE,
            CoachMarkPlan.nextStep(
                fourSeen,
                chrome(GuideAnchors.PREVIEW_CLOSE, route = previewRoute)
            )?.id
        )
        // The same law for the beats behind a screen the user has not reached:
        // the Packages card is only possible on the Packages tab, so waiting for
        // the user to walk there is not a stall.
        val sevenSeen = tourOrder.take(7).toSet()
        assertNull(
            CoachMarkPlan.waitingOn(
                sevenSeen,
                chrome(GuideAnchors.NAV_TAB_TERMINAL, route = editorRoute)
            )
        )
        // ...but on the Packages tab itself, a card that is not laid out (say a
        // collapsed first section) IS hopeless, and the tour moves on.
        assertEquals(
            GuideAnchors.PACKAGES_CARD,
            CoachMarkPlan.waitingOn(
                sevenSeen,
                chrome(GuideAnchors.NAV_TAB_TERMINAL, route = modulesRoute)
            )?.id
        )
    }

    @Test
    fun `the route map says where each beat can exist at all`() {
        // The whole input to the stall guard, pinned route by route: the bar is
        // on every screen (Phase 32.1 hides it only inside the editor, and beat 6
        // doubles as the bar), everything else belongs to one screen.
        val bar = setOf(
            GuideAnchors.NAV_HANDLE,
            GuideAnchors.NAV_TAB_PACKAGES,
            GuideAnchors.NAV_TAB_TERMINAL
        )
        assertEquals(bar, CoachMarkPlan.anchorsPossibleOn(previewRoute) - GuideAnchors.PREVIEW_CLOSE)
        assertEquals(bar, CoachMarkPlan.anchorsPossibleOn(modulesRoute) - GuideAnchors.PACKAGES_CARD)
        assertEquals(bar, CoachMarkPlan.anchorsPossibleOn(terminalRoute) - GuideAnchors.TERMINAL_CHIP)
        assertEquals(bar, CoachMarkPlan.anchorsPossibleOn("file_manager"))
        assertEquals(bar, CoachMarkPlan.anchorsPossibleOn("settings"))
        assertEquals(bar, CoachMarkPlan.anchorsPossibleOn(null))
        assertEquals(
            bar + setOf(
                GuideAnchors.EDITOR_DRAWER,
                GuideAnchors.DRAWER_PROJECT,
                GuideAnchors.DRAWER_FILE,
                GuideAnchors.EDITOR_RUN
            ),
            CoachMarkPlan.anchorsPossibleOn(editorRoute)
        )
        // And every beat of the tour is possible somewhere, or the guard could
        // never bound it and "every beat waits" would be a dead end.
        for (step in CoachMarkPlan.steps) {
            assertTrue(
                "${step.anchorId} is possible on no route at all",
                listOf(editorRoute, previewRoute, modulesRoute, terminalRoute, "file_manager")
                    .any { step.anchorId in CoachMarkPlan.anchorsPossibleOn(it) }
            )
        }
    }

    @Test
    fun `the plan says when the beat it waits for is behind the drawer`() {
        // The editor's project picker closes the drawer before it opens, and
        // beat 3 (`app.py`) is a drawer beat. Without this the owner's "change
        // the project folder to demo_flask → selected app.py" would go silent
        // after the pick and wait for the user to find ☰ again — the tour
        // feeling cut, which is the whole complaint round 3 exists to fix.
        assertTrue(
            "a cold tour waits on beat 1, which is not in the drawer",
            !CoachMarkPlan.nextBeatIsInDrawer(emptySet())
        )
        assertTrue(
            "after ☰ is taught, the tour waits inside the drawer",
            CoachMarkPlan.nextBeatIsInDrawer(setOf(GuideAnchors.EDITOR_DRAWER))
        )
        assertTrue(
            "and it still does after the header tap, which is when the picker closes the drawer",
            CoachMarkPlan.nextBeatIsInDrawer(
                setOf(GuideAnchors.EDITOR_DRAWER, GuideAnchors.DRAWER_PROJECT)
            )
        )
        assertFalse(
            "once app.py is open the drawer beats are behind us",
            CoachMarkPlan.nextBeatIsInDrawer(
                setOf(
                    GuideAnchors.EDITOR_DRAWER,
                    GuideAnchors.DRAWER_PROJECT,
                    GuideAnchors.DRAWER_FILE
                )
            )
        )
        assertFalse(
            "a finished tour reopens nothing",
            CoachMarkPlan.nextBeatIsInDrawer(tourOrder.toSet())
        )
        assertTrue(
            "a stalled drawer beat is not a beat the tour waits for",
            !CoachMarkPlan.nextBeatIsInDrawer(
                setOf(GuideAnchors.EDITOR_DRAWER),
                setOf(GuideAnchors.DRAWER_PROJECT, GuideAnchors.DRAWER_FILE)
            )
        )
    }

    @Test
    fun `the stall guard is armed only by a control that is not laid out`() {
        // A moving Phase 44 download, a dialog, the exit survey: the tour is
        // blocked, and the guard must NOT time that — a five-minute Python
        // install would otherwise cost the beat that follows it (beat 5, the
        // Flask preview the install exists to produce).
        val blocked = chrome(GuideAnchors.EDITOR_RUN, blocked = true)
        assertNull(CoachMarkPlan.nextStep(emptySet(), blocked))
        assertNull(CoachMarkPlan.waitingOn(emptySet(), blocked))
        // The drawer being in the other state is the same kind of wait: nothing
        // is drawn, nothing is trapped, and the user's own next tap ends it.
        val seen = setOf(GuideAnchors.EDITOR_DRAWER)
        assertNull(CoachMarkPlan.waitingOn(seen, chrome(GuideAnchors.EDITOR_RUN)))
        // A control that COULD be on this route and is simply not laid out is the
        // one thing worth timing: the beat that could otherwise hold the tour
        // forever while the user looks at the screen it belongs to.
        val fiveSeen = tourOrder.take(5).toSet()
        assertEquals(
            GuideAnchors.NAV_HANDLE,
            CoachMarkPlan.waitingOn(fiveSeen, chrome(GuideAnchors.NAV_TAB_PACKAGES))?.id
        )
        // Once the host has stalled that beat, the guard names the next missing
        // control instead — one beat at a time, never the whole tour at once.
        assertEquals(
            GuideAnchors.NAV_TAB_PACKAGES,
            CoachMarkPlan.waitingOn(
                fiveSeen,
                chrome(GuideAnchors.TERMINAL_CHIP),
                setOf(GuideAnchors.NAV_HANDLE)
            )?.id
        )
        assertTrue(
            "the guard is a real bound, not a hope",
            CoachMarkPlan.STALL_GUARD_MS in 5_000L..60_000L
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

    // ---- round 4: ONE tap does both halves --------------------------------

    @Test
    fun `a tap performs the most specific anchored control under the finger`() {
        // Beat 6 spotlights the whole bottom bar, so its hole CONTAINS the tabs:
        // the tap has to resolve to the control the finger is really on, or the
        // tour either does nothing or does the wrong thing.
        val bar = GuideRect(0f, 900f, 1080f, 1050f)
        val tabs = listOf(
            GuideTapTarget(GuideAnchors.NAV_TAB_PACKAGES, GuideRect(648f, 900f, 864f, 1050f)),
            GuideTapTarget(GuideAnchors.NAV_TAB_TERMINAL, GuideRect(864f, 900f, 1080f, 1050f))
        )
        assertEquals(
            GuideAnchors.NAV_TAB_PACKAGES,
            GuideTapPolicy.targetFor(700f, 950f, bar, tabs)?.id
        )
        assertEquals(
            GuideAnchors.NAV_TAB_TERMINAL,
            GuideTapPolicy.targetFor(900f, 950f, bar, tabs)?.id
        )
        // A tap on the part of the bar the tour does not anchor (Projects,
        // Editor, Settings) resolves to NOTHING: the overlay leaves that gesture
        // alone and the real tab performs it.
        assertNull(GuideTapPolicy.targetFor(100f, 950f, bar, tabs))
        // Nested anchors: the innermost is what the user aimed at (a package card
        // contains its own INSTALL button).
        val card = GuideTapTarget(GuideAnchors.PACKAGES_CARD, GuideRect(50f, 400f, 1000f, 700f))
        val button = GuideTapTarget("install_button", GuideRect(700f, 600f, 950f, 680f))
        assertEquals(
            "install_button",
            GuideTapPolicy.targetFor(800f, 640f, card.rect, listOf(card, button))?.id
        )
        assertEquals(
            GuideAnchors.PACKAGES_CARD,
            GuideTapPolicy.targetFor(120f, 450f, card.rect, listOf(card, button))?.id
        )
        // Equal areas fall back to TOUR ORDER, so the answer never depends on the
        // order a map happened to be iterated in.
        val same = GuideRect(0f, 0f, 100f, 100f)
        assertEquals(
            GuideAnchors.NAV_TAB_PACKAGES,
            GuideTapPolicy.targetFor(
                50f,
                50f,
                same,
                listOf(
                    GuideTapTarget(GuideAnchors.NAV_TAB_TERMINAL, same),
                    GuideTapTarget(GuideAnchors.NAV_TAB_PACKAGES, same)
                )
            )?.id
        )
    }

    @Test
    fun `nothing outside the hole is performed, and a label performs nothing`() {
        val hole = GuideRect(0f, 0f, 100f, 100f)
        val run = GuideTapTarget(GuideAnchors.EDITOR_RUN, GuideRect(150f, 150f, 250f, 250f))
        // The finger is inside that control but OUTSIDE the hole: the overlay
        // never acts on a control it is not teaching.
        assertNull(GuideTapPolicy.targetFor(200f, 200f, hole, listOf(run)))
        // Beat 10's status chip is a label with no click of its own: null, so the
        // overlay leaves the gesture to Compose and only advances the tour.
        assertNull(GuideTapPolicy.targetFor(50f, 50f, hole, emptyList()))
    }

    @Test
    fun `a drag that leaves the hole is not a tap, so it spends no beat`() {
        val handle = GuideRect(0f, 900f, 1080f, 1000f)
        // A press and a lift in the same place is a tap.
        assertTrue(GuideTapPolicy.isTap(500f, 950f, 504f, 952f, handle, 20f))
        // A swipe up to reveal the bar travels and lifts OUTSIDE the hole: not a
        // tap. Answering it with the control's action — or with an advance and no
        // action — is exactly the owner's "the message went away and nothing
        // happened", so the box stays and the beat is not spent.
        assertFalse(GuideTapPolicy.isTap(500f, 950f, 500f, 700f, handle, 20f))
        // A long travel that ends INSIDE the hole is still a tap on the control.
        assertTrue(GuideTapPolicy.isTap(100f, 100f, 300f, 300f, GuideRect(0f, 0f, 1000f, 1000f), 20f))
        // A drift past the slop, lifted inside the hole: a tap.
        assertTrue(GuideTapPolicy.isTap(500f, 950f, 512f, 960f, handle, 8f))
        // A drift inside the slop: a tap wherever it lifts.
        assertTrue(GuideTapPolicy.isTap(500f, 950f, 506f, 956f, handle, 8f))
    }

    @Test
    fun `the tour's copy teaches the gesture the tour actually accepts`() {
        // Round 4: while a box is up the overlay owns the gesture, so a beat that
        // told the user to SWIPE would be a beat that never moves. The handle's
        // swipe still works the moment the tour is over (NavBarPolicy).
        val beat6 = CoachMarkPlan.step(GuideAnchors.NAV_HANDLE)!!
        assertTrue(beat6.body.contains("tap"))
        assertFalse(
            "a box must not teach a gesture the overlay swallows: ${beat6.body}",
            beat6.body.contains("swipe")
        )
        for (step in CoachMarkPlan.steps) {
            assertTrue("title too long: ${step.title}", step.title.length <= CoachMarkPlan.MAX_TITLE_CHARS)
            assertTrue("body too long: ${step.body}", step.body.length <= CoachMarkPlan.MAX_BODY_CHARS)
        }
    }
}
