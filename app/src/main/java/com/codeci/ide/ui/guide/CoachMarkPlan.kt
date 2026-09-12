package com.codeci.ide.ui.guide

/**
 * Phase 45.2 — the coach-mark PLAN, rebuilt as **one ordered tour** after the
 * owner's device round (2026-09-12): *"the guided box are not consistent with
 * flow … Not showing the full box guide at one and you didn't add all … remove
 * the next option only the guide will show click the option where showing the
 * guide to the next"*, then the flow spelled out:
 *
 * > ☰ → change the project folder to `demo_flask` → select `app.py` → RUN →
 * > Install (Python) → the Flask web page opens → close it → the "reveal the
 * > tabs" handle → a small tour of Packages and Terminal.
 *
 * Pure, so the rules that decide *does a box appear, and on what* are
 * host-testable (`CoachMarkPlanTest`) instead of being discovered on a phone.
 *
 * Four laws. The first three are the owner's (round 3, after he ran round 2);
 * the fourth is the valve that makes them safe on a phone that cannot produce
 * the control a beat is waiting for:
 *  - **first beat to last, nothing skipped** — *"You add the skip option and
 *    it's not a trough guide mean it got cut. I want a full process 1st to last
 *    without skip anything in this."* So there is NO SKIP button and Back does
 *    not end the tour either: a mid-tour card carries no button at all, every
 *    beat waits for its own control, and the ten boxes arrive in the owner's
 *    order instead of whichever two happened to be on screen.
 *  - **the box is the only forward button** — tapping the highlighted control
 *    performs that control's own action AND advances the tour. Tapping outside
 *    does nothing at all (*"even tap outside will not end that box"*), so a box
 *    can never be dismissed by accident.
 *  - **at the end, a close and a way back** — *"At the end option to close and
 *    view again."* The finish card is the only card with buttons: **CLOSE** (the
 *    tour is over and nothing returns on its own) and **VIEW AGAIN** (all ten
 *    beats from the first). Outside the tour, Settings → About → Reset tips is
 *    the same door.
 *  - **never point at nothing, never brick** — [nextStep] only returns a step
 *    whose anchor the caller reports visible (an anchor publishes its window
 *    rect while it is laid out and withdraws it when it leaves composition), and
 *    while a beat waits nothing is drawn at all, so the app stays usable. A
 *    control that can never appear — no Python, so no Flask preview; the tab bar
 *    never hidden, so no reveal handle — would otherwise hold the tour on that
 *    beat forever, so the HOST may declare a beat *stalled* after
 *    [STALL_GUARD_MS]: a stalled beat is passed for this session and is never
 *    marked seen, so it is still taught on the next pass. Stalling is the only
 *    thing that can move the tour past a beat, and it is a host decision with a
 *    pure signature ([waitingOn]), so this file stays testable.
 *
 * Recorded limit (PART_45_2, deviation 9): a box cannot point into an
 * `AlertDialog`. Compose dialogs live in their own window, so `boundsInWindow()`
 * inside one is dialog-relative and an activity-window scrim would cut its hole
 * in the wrong place. Two of the owner's beats live in dialogs — the project
 * picker ("Open folder") and the Python `Install?` prompt — so those are taught
 * by the copy of the box before them (step 2 names `demo_flask`, step 4 names
 * **Install**) instead of by a hole in the scrim.
 */

/** Which screen teaches a step. Documentation and test vocabulary, not a gate. */
enum class GuideSurface { EDITOR, PREVIEW, PACKAGES, TERMINAL }

/** Anchor ids. One anchor = one box; the id is also the step id. */
object GuideAnchors {
    /** The editor's ☰ (file tree + the project-name switcher). */
    const val EDITOR_DRAWER = "editor_drawer"

    /** The drawer header's project name — tap it to switch projects. */
    const val DRAWER_PROJECT = "drawer_project"

    /** The drawer's row for the demo project's entry file (`app.py`). */
    const val DRAWER_FILE = "drawer_file"

    /** The editor's RUN ▶. */
    const val EDITOR_RUN = "editor_run"

    /** The Web Preview's Back/close button (the Flask page the demo serves). */
    const val PREVIEW_CLOSE = "preview_close"

    /** Phase 32.1's "Show tabs" handle — visible only while the bar is hidden. */
    const val NAV_HANDLE = "nav_handle"

    /** The bottom bar's Packages tab. */
    const val NAV_TAB_PACKAGES = "nav_tab_packages"

    /** The Packages tab's first install card. */
    const val PACKAGES_CARD = "packages_card"

    /** The bottom bar's Terminal tab. */
    const val NAV_TAB_TERMINAL = "nav_tab_terminal"

    /** The terminal's status chip (Phase 44.1 made it stage-aware). */
    const val TERMINAL_CHIP = "terminal_chip"

    val all: List<String> = listOf(
        EDITOR_DRAWER,
        DRAWER_PROJECT,
        DRAWER_FILE,
        EDITOR_RUN,
        PREVIEW_CLOSE,
        NAV_HANDLE,
        NAV_TAB_PACKAGES,
        PACKAGES_CARD,
        NAV_TAB_TERMINAL,
        TERMINAL_CHIP
    )
}

/**
 * One box in the tour: an anchor, a title, one line of body.
 *
 * [inDrawer] marks the two beats that live INSIDE the editor's ☰ drawer. It
 * exists because a closed drawer can still have laid-out rows (M3 keeps the
 * drawer content composed and off-screen), so "the anchor published a rect" is
 * not by itself proof the user can see it: a drawer beat shows only while the
 * drawer is open, and every other beat only while it is closed — a box on a
 * control the drawer is covering is a hole cut in nothing.
 *
 * There is no per-beat "wait or pass over" flag any more (round 2 had one, and
 * it is what let the tour skip the owner's beats): **every beat waits**. The
 * tour stops on the first beat that is neither taught nor stalled and shows it
 * the moment its control is laid out, so the order the owner dictated is the
 * order the boxes arrive in. The one escape is the host's stall guard, which
 * passes a beat for the session WITHOUT spending it.
 */
data class CoachStep(
    val id: String,
    val surface: GuideSurface,
    val anchorId: String,
    val title: String,
    val body: String,
    val inDrawer: Boolean = false
)

/**
 * What the caller can see right now: the set of anchors currently laid out (the
 * Android edge publishes a rect on `onGloballyPositioned` and withdraws it on
 * dispose) plus the one "someone else owns the screen" flag.
 *
 * [blockedByForeground] covers the exit survey, safe mode and a Phase 44 setup
 * stage that is actually moving (DOWNLOADING/VERIFYING/EXTRACTING — not
 * CHECKING, which is a startup transient that would otherwise mean no box ever
 * shows on a fresh phone). A box under any of them is noise at best.
 *
 * [route] is the navigation destination the user is on. It decides ONE thing:
 * whether the stall guard may time a beat out (see [CoachMarkPlan.waitingOn] and
 * [CoachMarkPlan.anchorsPossibleOn]). It is never a gate on showing a box —
 * round 2 filtered beats by surface and that is part of why the tour arrived
 * with holes in it.
 */
data class ChromeState(
    val visibleAnchors: Set<String> = emptySet(),
    val blockedByForeground: Boolean = false,
    val drawerOpen: Boolean = false,
    val route: String? = null
) {
    companion object {
        fun of(
            visibleAnchors: Set<String>,
            blockedByForeground: Boolean,
            drawerOpen: Boolean = false,
            route: String? = null
        ): ChromeState = ChromeState(visibleAnchors, blockedByForeground, drawerOpen, route)
    }

    fun anchorVisible(anchorId: String): Boolean = anchorId in visibleAnchors
}

object CoachMarkPlan {

    const val MAX_TITLE_CHARS = 24
    const val MAX_BODY_CHARS = 110

    /**
     * The tour, in the order the owner dictated. Copy names only real labels
     * (`Install` is `R.string.install_prompt_confirm`, `Packages`/`Terminal` are
     * the tab titles in `ui/navigation/Screen.kt`, `app.py`/`demo_flask` are the
     * bundled demo's real names — `GuideWiringTest` pins each one to its file).
     */
    val steps: List<CoachStep> = listOf(
        // 1 — the owner's row: "user don't know where should they change the
        // project or file". Always there in the editor, so the tour cannot start
        // anywhere else — and every later beat waits behind this one.
        CoachStep(
            id = GuideAnchors.EDITOR_DRAWER,
            surface = GuideSurface.EDITOR,
            anchorId = GuideAnchors.EDITOR_DRAWER,
            title = "Your files",
            body = "Tap \u2630 for the file tree and the project switcher.",
        ),
        // 2 — "change the project folder to demo_flask". The picker itself is an
        // AlertDialog (its own window), so the box names the destination instead
        // of pointing at it. Its anchor is the drawer header in EVERY state
        // (another project, the demo already, scratch mode), because the header
        // always opens the picker: a beat that only exists in one project state
        // is a beat a "1st to last" tour would have to skip.
        CoachStep(
            id = GuideAnchors.DRAWER_PROJECT,
            surface = GuideSurface.EDITOR,
            anchorId = GuideAnchors.DRAWER_PROJECT,
            title = "Change project",
            body = "Tap the project name, then choose demo_flask \u2014 the Flask demo CodeC ships with.",
            inDrawer = true
        ),
        // 3 — "selected app.py". Only ever published for the demo project's own
        // entry file, so the box cannot land on some other row.
        CoachStep(
            id = GuideAnchors.DRAWER_FILE,
            surface = GuideSurface.EDITOR,
            anchorId = GuideAnchors.DRAWER_FILE,
            title = "Open app.py",
            body = "The demo's entry file. Tap it and it opens in the editor.",
            inDrawer = true
        ),
        // 4 — "run -> install -> python". The Install? prompt is a dialog, so
        // the box teaches the tap that leads to it and names the real button.
        CoachStep(
            id = GuideAnchors.EDITOR_RUN,
            surface = GuideSurface.EDITOR,
            anchorId = GuideAnchors.EDITOR_RUN,
            title = "Run it",
            body = "Runs the open file. If Python is missing, tap Install \u2014 one download, one time.",
        ),
        // 5 — "it will open the flusk web -> close".
        CoachStep(
            id = GuideAnchors.PREVIEW_CLOSE,
            surface = GuideSurface.PREVIEW,
            anchorId = GuideAnchors.PREVIEW_CLOSE,
            title = "Your app is running",
            body = "Served by your own phone. Tap Back to close the preview and keep editing.",
        ),
        // 6 — "tap to reveal the keyboard below option" (the owner's original
        // row: "the tap to the open down side of the keyboard").
        CoachStep(
            id = GuideAnchors.NAV_HANDLE,
            surface = GuideSurface.EDITOR,
            anchorId = GuideAnchors.NAV_HANDLE,
            title = "The tabs are here",
            // Round 4: the copy says TAP, not "swipe up", because while a box is
            // up the overlay owns the gesture (it performs the control's click
            // itself) and a swipe that leaves the hole is not a tap. The swipe
            // still works the moment the tour is over (NavBarPolicy).
            body = "Five tabs, one tap away. They hide while you type \u2014 tap this handle to bring them back.",
        ),
        // 7-8 — "then a small tour of package".
        CoachStep(
            id = GuideAnchors.NAV_TAB_PACKAGES,
            surface = GuideSurface.PACKAGES,
            anchorId = GuideAnchors.NAV_TAB_PACKAGES,
            title = "Packages",
            body = "Tap to add a language or a tool. It downloads once, then works offline.",
        ),
        CoachStep(
            id = GuideAnchors.PACKAGES_CARD,
            surface = GuideSurface.PACKAGES,
            anchorId = GuideAnchors.PACKAGES_CARD,
            title = "One-time download",
            body = "Adding a language downloads once. Keep CodeC open while it finishes.",
        ),
        // 9-10 — "and terminal".
        CoachStep(
            id = GuideAnchors.NAV_TAB_TERMINAL,
            surface = GuideSurface.TERMINAL,
            anchorId = GuideAnchors.NAV_TAB_TERMINAL,
            title = "Terminal",
            body = "A real Linux shell: pkg install, git, cc.",
        ),
        CoachStep(
            id = GuideAnchors.TERMINAL_CHIP,
            surface = GuideSurface.TERMINAL,
            anchorId = GuideAnchors.TERMINAL_CHIP,
            title = "What it is doing",
            body = "Starting, downloading or running. If it says downloading, do not close CodeC.",
        )
    )

    private val byId: Map<String, CoachStep> = steps.associateBy { it.id }

    fun step(id: String): CoachStep? = byId[id]

    /**
     * The step to show, or null: in tour order, the FIRST beat that is neither
     * taught nor stalled — shown when its anchor is on screen (and, for a drawer
     * beat, when the drawer is open), waited for otherwise. Nothing is passed
     * over just because some later control happens to be laid out; that was
     * round 2's *"it got cut"*.
     *
     * A null here marks nothing seen and draws nothing, so the app stays usable
     * while a beat waits. That is the whole safety argument: a box never points
     * at nothing, and a beat that has not been taught is never spent.
     *
     * [stalled] is the host's anti-brick valve (see [waitingOn] and
     * [STALL_GUARD_MS]): beats the host stopped waiting for are passed for this
     * session only. The plan itself never stalls a beat and never marks one
     * seen.
     *
     * Two "someone else owns the screen" rules sit above the anchor check:
     * [ChromeState.blockedByForeground] (the exit survey, safe mode, an in-flight
     * Phase 44 download, ANY editor dialog) suppresses everything, and
     * [ChromeState.drawerOpen] decides whether a drawer beat or a non-drawer beat
     * may show — the scrim is drawn in the activity window, so a box cut behind a
     * dialog, or on a control the closed drawer still covers, is a hole in
     * nothing. That was the owner's *"not consistent with flow"*.
     */
    fun nextStep(
        seen: Set<String>,
        chrome: ChromeState,
        stalled: Set<String> = emptySet()
    ): CoachStep? {
        if (chrome.blockedByForeground) return null
        for (step in steps) {
            if (step.id in seen || step.id in stalled) continue
            // The drawer's beats need the drawer open; every other beat needs it
            // closed (it would otherwise be cut on a control the drawer covers).
            if (step.inDrawer != chrome.drawerOpen) return null
            if (chrome.anchorVisible(step.anchorId)) return step
            return null
        }
        return null
    }

    /**
     * The beat the tour is stopped on because ITS CONTROL IS NOT LAID OUT, or
     * null. This is what the host times: after [STALL_GUARD_MS] it may add the
     * beat to `stalled`, and the tour moves on without spending the lesson.
     *
     * Deliberately null for the three waits that need no guard:
     *  - while another surface owns the screen ([ChromeState.blockedByForeground])
     *    — a Python download takes minutes and must not cost the beat after it;
     *  - while the drawer is simply in the other state ([CoachStep.inDrawer]) —
     *    the user's own next tap ends it;
     *  - while the beat's control CANNOT exist on this route
     *    ([anchorsPossibleOn]) — the user has to travel (or wait for a run to
     *    open the preview), and timing that out is what would cascade: the Flask
     *    preview would stall, then every beat behind it, and the tour would
     *    "finish" on a screen the owner's flow has not reached yet.
     *
     * None of the three draws anything, so none of them can trap the user.
     */
    fun waitingOn(
        seen: Set<String>,
        chrome: ChromeState,
        stalled: Set<String> = emptySet()
    ): CoachStep? {
        if (chrome.blockedByForeground) return null
        for (step in steps) {
            if (step.id in seen || step.id in stalled) continue
            if (step.inDrawer != chrome.drawerOpen) return null
            if (chrome.anchorVisible(step.anchorId)) return null
            return if (step.anchorId in anchorsPossibleOn(chrome.route)) step else null
        }
        return null
    }

    /**
     * The anchors that CAN be laid out on a route — the stall guard's whole
     * input. A beat whose control is possible here and still missing is the one
     * hopeless case worth timing out (the Packages card behind a collapsed
     * section, the demo's `app.py` row when the user picked some other project);
     * a beat whose control belongs to a screen the user is not on is not
     * hopeless, it is simply early, and the tour waits for them to arrive.
     *
     * The bottom bar is on every route (Phase 32.1 hides it only inside the
     * editor, and beat 6 doubles as the bar itself), so its three anchors are
     * possible everywhere.
     */
    fun anchorsPossibleOn(route: String?): Set<String> {
        val r = route?.substringBefore('?')?.trim().orEmpty()
        val bar = setOf(
            GuideAnchors.NAV_HANDLE,
            GuideAnchors.NAV_TAB_PACKAGES,
            GuideAnchors.NAV_TAB_TERMINAL
        )
        return when {
            r.startsWith("preview") -> bar + GuideAnchors.PREVIEW_CLOSE
            r.startsWith("editor") -> bar + setOf(
                GuideAnchors.EDITOR_DRAWER,
                GuideAnchors.DRAWER_PROJECT,
                GuideAnchors.DRAWER_FILE,
                GuideAnchors.EDITOR_RUN
            )
            r.startsWith("modules") -> bar + GuideAnchors.PACKAGES_CARD
            r.startsWith("terminal") -> bar + GuideAnchors.TERMINAL_CHIP
            else -> bar
        }
    }

    /**
     * True when the beat the tour is waiting for lives INSIDE the ☰ drawer.
     *
     * One honest use: the editor's project picker closes the drawer before it
     * opens (it always has), and beat 3 — the demo's `app.py` row — is a drawer
     * beat, so the owner's *"change the project folder to demo_flask → selected
     * app.py"* would otherwise go silent and wait for the user to guess which
     * button brings the files back. While this is true the editor reopens the
     * drawer after a project is chosen, and the walk stays one walk. When the tour
     * is over (or has not started) this is false and nothing changes for anybody.
     */
    fun nextBeatIsInDrawer(seen: Set<String>, stalled: Set<String> = emptySet()): Boolean {
        for (step in steps) {
            if (step.id in seen || step.id in stalled) continue
            return step.inDrawer
        }
        return false
    }

    /** True when the tour has something to teach right now. */
    fun canShow(
        seen: Set<String>,
        chrome: ChromeState,
        stalled: Set<String> = emptySet()
    ): Boolean = nextStep(seen, chrome, stalled) != null

    /** Lessons still unseen — what "Reset tips" gives back. */
    fun remaining(seen: Set<String>): List<CoachStep> = steps.filter { it.id !in seen }

    fun isComplete(seen: Set<String>): Boolean = steps.all { it.id in seen }

    /**
     * True when THIS SESSION's tour has run to its end: every beat is either
     * taught or stalled. That is when the finish card — the only card with
     * buttons, the owner's *"At the end option to close and view again"* — is
     * earned. Finished is not complete: a stalled beat stays unseen, so a
     * finished tour can still have a lesson left for the next pass.
     */
    fun isFinished(seen: Set<String>, stalled: Set<String> = emptySet()): Boolean =
        steps.all { it.id in seen || it.id in stalled }

    /**
     * Record a step as seen (the user tapped the control it teaches). Unknown
     * ids are ignored: a preference written by an older build — or a step
     * deleted in a later one — must never grow the set with garbage that then
     * suppresses a real box.
     */
    fun markSeen(seen: Set<String>, stepId: String): Set<String> =
        if (byId.containsKey(stepId)) seen + stepId else seen

    /**
     * **VIEW AGAIN** on the finish card: the whole tour from beat 1. Clearing the
     * seen set IS the replay — no second flag, so the one key keeps one meaning,
     * and Settings → About → Reset tips does the same thing from the other end.
     *
     * There is deliberately no `markAllSeen` any more. Round 2 ended the tour
     * with SKIP and Back by marking every unseen beat seen; the owner's round 3
     * (*"it's not a trough guide mean it got cut"*) removed both exits, so
     * nothing in the product can now spend a beat the user never saw. The only
     * way a beat is marked seen is by tapping the control it teaches.
     */
    fun replay(): Set<String> = emptySet()

    /** `coach_marks_seen_csv` → ids. Blank/garbage tokens drop out. */
    fun parseSeen(csv: String?): Set<String> = csv
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { byId.containsKey(it) }
        ?.toSet()
        ?: emptySet()

    /** ids → `coach_marks_seen_csv`, in tour order so the value is stable. */
    fun serializeSeen(seen: Set<String>): String =
        steps.map { it.id }.filter { it in seen }.joinToString(",")

    /**
     * The bottom-bar tab a route teaches, or null for a tab the tour does not
     * use. Pure so the route→anchor mapping is host-tested, and so the bar only
     * publishes the two rects the tour can actually use.
     */
    fun tabAnchorFor(route: String?): String? {
        val r = route?.substringBefore('?')?.trim().orEmpty()
        return when {
            r == "modules" || r.startsWith("modules") -> GuideAnchors.NAV_TAB_PACKAGES
            r == "terminal" || r.startsWith("terminal") -> GuideAnchors.NAV_TAB_TERMINAL
            else -> null
        }
    }

    /**
     * How long the host lets a beat wait for a control that has not appeared
     * before passing it for this session (see [waitingOn]). Long enough that the
     * tour the owner described never hits it — his walk-through is a few seconds
     * per beat, and a Python download does not count, because a moving setup
     * stage blocks the tour without arming the guard — and short enough that a
     * phone which genuinely cannot produce the control (no Python, so no Flask
     * preview; the tab bar never hidden, so no reveal handle) is not held on one
     * beat forever with no exit on the card.
     */
    const val STALL_GUARD_MS = 20_000L

    /**
     * The drawer row the tour spotlights: the demo project's entry file, and
     * only it — a box on some other row would teach the wrong tap. The demo's
     * name and entry file are passed in (the caller reads them from
     * `DemoProjects`) so this file stays free of file-system types.
     */
    fun drawerFileAnchor(
        projectName: String?,
        relativePath: String,
        isDirectory: Boolean,
        demoProjectName: String,
        demoEntryFile: String
    ): String? = if (
        !isDirectory &&
        projectName == demoProjectName &&
        relativePath == demoEntryFile
    ) {
        GuideAnchors.DRAWER_FILE
    } else {
        null
    }
}

/** A rectangle in window pixels — the pure twin of Compose's `Rect`. */
data class GuideRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val area: Float get() = width * height

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom
}

/** A size in window pixels — the pure twin of Compose's `Size`. */
data class GuideSize(val width: Float, val height: Float)

/** Where the tooltip card goes: below or above the hole, and its top-left. */
data class GuidePlacement(val below: Boolean, val left: Float, val top: Float)

/**
 * The card never leaves the screen and never covers its own hole. Pure so the
 * three cases (anchor at the top, in the middle, at the bottom) plus a landscape
 * box are host-tested (`TooltipPlacementTest`) rather than eyeballed on one
 * phone. The owner's *"Not showing the full box guide at one"* is the reason the
 * height passed in is now MEASURED (`CoachMarks.kt`) instead of estimated.
 */
object TooltipPlacement {

    fun place(
        anchor: GuideRect,
        screen: GuideSize,
        tooltip: GuideSize,
        gap: Float,
        margin: Float
    ): GuidePlacement {
        val fitsBelow = anchor.bottom + gap + tooltip.height <= screen.height
        val fitsAbove = anchor.top - gap - tooltip.height >= 0f
        val top = when {
            fitsBelow -> anchor.bottom + gap
            fitsAbove -> anchor.top - gap - tooltip.height
            // Neither fits (a short screen, a tall card): clamp inside, keeping
            // the margin, and let the card sit over the scrim's bottom.
            else -> (screen.height - tooltip.height - margin).coerceAtLeast(margin)
        }
        val maxLeft = (screen.width - tooltip.width - margin).coerceAtLeast(margin)
        val left = (anchor.centerX - tooltip.width / 2f).coerceIn(margin, maxLeft)
        return GuidePlacement(below = fitsBelow, left = left, top = top)
    }
}

/**
 * One anchored control the tour may act on: its id and where it is. The Android
 * edge builds these from the anchor registry (`GuideAnchorRegistry.tapTargets`),
 * so the DECISION — which control a tap performs — stays here and is host-tested
 * (`CoachMarkPlanTest`) instead of being discovered on a phone.
 */
data class GuideTapTarget(val id: String, val rect: GuideRect)

/**
 * Phase 45 round 4 — the tap that does BOTH halves at once.
 *
 * The owner, after running round 3: *"Now the steps feel like an overlay on the
 * botton so 1st click disappear the massage and i have to click 2nd time to
 * really work but if someone don't click 2nd time it just cut off the flow of
 * tutorial."*
 *
 * Round 3 left the tap inside the hole UNCONSUMED and asked Compose to deliver
 * the rest of the gesture to the real control. That depends on the control still
 * being the same node when the finger lifts — and the tour's own advance is what
 * recomposes the host (`coachSeen` → `tourWaitsInDrawer`, the next beat's box),
 * so the first tap could spend the lesson and lose the click. The overlay now
 * performs the anchored control's OWN click (published beside its rect) and
 * swallows the gesture, so one tap is one action plus one beat, in that order,
 * with no double-fire.
 *
 * Two decisions, both pure:
 *  - [targetFor]: which control a tap performs — the MOST SPECIFIC actionable
 *    box under the finger, so beat 6's bar-wide hole resolves a tap on the
 *    Packages tab to the Packages tab (and a tap on a tab the tour does not use
 *    resolves to nothing, which leaves that tap to the real control);
 *  - [isTap]: whether the gesture meant "tap the highlighted control" at all. A
 *    finger that travels and lifts outside the hole was a scroll or a swipe, and
 *    answering it with the control's action — or with an advance and no action —
 *    is the exact "the message went away and nothing happened" the owner
 *    reported. Such a gesture does nothing at all: the box stays.
 */
object GuideTapPolicy {

    /**
     * The control a tap at (`tapX`, `tapY`) performs, or null when no anchored
     * control with a click of its own is under the finger.
     *
     * Smallest box wins: anchors nest (the bottom bar contains its tabs, a
     * package card contains its INSTALL button), and the innermost one is what
     * the user aimed at. Equal areas fall back to tour order, so the answer
     * never depends on the order a map happened to be iterated in.
     */
    fun targetFor(
        tapX: Float,
        tapY: Float,
        hole: GuideRect,
        targets: List<GuideTapTarget>
    ): GuideTapTarget? {
        if (!hole.contains(tapX, tapY)) return null
        return targets
            .filter { it.rect.contains(tapX, tapY) }
            .minWithOrNull(compareBy({ it.rect.area }, { beatOrder(it.id) }))
    }

    /**
     * Was this gesture a tap on the highlighted control?
     *
     * True when the finger barely moved (a press-and-lift anywhere in the hole)
     * or when it lifted inside the hole; false for a drag that travelled and
     * ended outside, which is a scroll or a swipe and must not spend a beat.
     * Movement is measured as the larger of dx/dy — no square root on the
     * pointer path, and a conservative test (a diagonal move counts as the
     * longer of its two sides).
     */
    fun isTap(
        downX: Float,
        downY: Float,
        upX: Float,
        upY: Float,
        hole: GuideRect,
        touchSlop: Float
    ): Boolean {
        val dx = if (upX > downX) upX - downX else downX - upX
        val dy = if (upY > downY) upY - downY else downY - upY
        val moved = if (dx > dy) dx else dy
        if (moved <= touchSlop) return true
        return hole.contains(upX, upY)
    }

    /** Earlier lesson wins a tie; an id outside the tour sorts last. */
    private fun beatOrder(id: String): Int {
        val index = CoachMarkPlan.steps.indexOfFirst { it.id == id }
        return if (index < 0) Int.MAX_VALUE else index
    }
}
