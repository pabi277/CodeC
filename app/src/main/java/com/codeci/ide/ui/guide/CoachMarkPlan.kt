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
 * Three laws, all owner-given:
 *  - **the box is the only forward button** — the card carries no NEXT/GOT IT;
 *    tapping the highlighted control performs that control's own action AND
 *    advances the tour. Tapping outside does nothing at all (the owner: *"even
 *    tap outside will not end that box"*), so a box can never be dismissed by
 *    accident. SKIP (one tap) and Back end the whole tour — the no-nag law
 *    (`ui/support/ExitSurvey.kt`) still requires an exit, and it must never be
 *    a wall: ending the tour marks every step seen, so nothing comes back until
 *    the user asks (Settings → About → Reset tips).
 *  - **never point at nothing** — [nextStep] only returns a step whose anchor
 *    the caller reports visible (an anchor publishes its window rect while it
 *    is laid out and withdraws it when it leaves composition).
 *  - **never stall** — a step that is not on screen is either *waited for*
 *    ([CoachStep.waits]) or *passed over*, and a passed-over step is NOT marked
 *    seen, so it can still teach its lesson later.
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
 * [waits] is the anti-stall rule. `true` = the tour stops here until this
 * control is really on screen (used for controls that are always there on their
 * own screen, so waiting is self-healing: ☰, RUN ▶, the two tabs). `false` =
 * pass over it when it is absent and let a later step teach now (used for
 * controls that only sometimes exist: the drawer's rows, the preview's Back,
 * the reveal handle, the install card). A passed-over step is never marked
 * seen, so it can still show on a later pass.
 */
data class CoachStep(
    val id: String,
    val surface: GuideSurface,
    val anchorId: String,
    val title: String,
    val body: String,
    val waits: Boolean,
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
 */
data class ChromeState(
    val visibleAnchors: Set<String> = emptySet(),
    val blockedByForeground: Boolean = false,
    val drawerOpen: Boolean = false
) {
    companion object {
        fun of(
            visibleAnchors: Set<String>,
            blockedByForeground: Boolean,
            drawerOpen: Boolean = false
        ): ChromeState = ChromeState(visibleAnchors, blockedByForeground, drawerOpen)
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
        // project or file". Always there in the editor, so the tour waits for it
        // and cannot start anywhere else.
        CoachStep(
            id = GuideAnchors.EDITOR_DRAWER,
            surface = GuideSurface.EDITOR,
            anchorId = GuideAnchors.EDITOR_DRAWER,
            title = "Your files",
            body = "Tap \u2630 for the file tree and the project switcher.",
            waits = true
        ),
        // 2 — "change the project folder to demo_flask". The picker itself is an
        // AlertDialog (its own window), so the box names the destination instead
        // of pointing at it.
        CoachStep(
            id = GuideAnchors.DRAWER_PROJECT,
            surface = GuideSurface.EDITOR,
            anchorId = GuideAnchors.DRAWER_PROJECT,
            title = "Change project",
            body = "Tap the project name, then choose demo_flask \u2014 the Flask demo CodeC ships with.",
            waits = false,
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
            waits = false,
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
            waits = true
        ),
        // 5 — "it will open the flusk web -> close".
        CoachStep(
            id = GuideAnchors.PREVIEW_CLOSE,
            surface = GuideSurface.PREVIEW,
            anchorId = GuideAnchors.PREVIEW_CLOSE,
            title = "Your app is running",
            body = "Served by your own phone. Tap Back to close the preview and keep editing.",
            waits = false
        ),
        // 6 — "tap to reveal the keyboard below option" (the owner's original
        // row: "the tap to the open down side of the keyboard").
        CoachStep(
            id = GuideAnchors.NAV_HANDLE,
            surface = GuideSurface.EDITOR,
            anchorId = GuideAnchors.NAV_HANDLE,
            title = "The tabs are here",
            body = "They hide while you type. Tap or swipe up to bring them back.",
            waits = false
        ),
        // 7-8 — "then a small tour of package".
        CoachStep(
            id = GuideAnchors.NAV_TAB_PACKAGES,
            surface = GuideSurface.PACKAGES,
            anchorId = GuideAnchors.NAV_TAB_PACKAGES,
            title = "Packages",
            body = "Tap to add a language or a tool. It downloads once, then works offline.",
            waits = true
        ),
        CoachStep(
            id = GuideAnchors.PACKAGES_CARD,
            surface = GuideSurface.PACKAGES,
            anchorId = GuideAnchors.PACKAGES_CARD,
            title = "One-time download",
            body = "Adding a language downloads once. Keep CodeC open while it finishes.",
            waits = false
        ),
        // 9-10 — "and terminal".
        CoachStep(
            id = GuideAnchors.NAV_TAB_TERMINAL,
            surface = GuideSurface.TERMINAL,
            anchorId = GuideAnchors.NAV_TAB_TERMINAL,
            title = "Terminal",
            body = "A real Linux shell: pkg install, git, cc.",
            waits = true
        ),
        CoachStep(
            id = GuideAnchors.TERMINAL_CHIP,
            surface = GuideSurface.TERMINAL,
            anchorId = GuideAnchors.TERMINAL_CHIP,
            title = "What it is doing",
            body = "Starting, downloading or running. If it says downloading, do not close CodeC.",
            waits = false
        )
    )

    private val byId: Map<String, CoachStep> = steps.associateBy { it.id }

    fun step(id: String): CoachStep? = byId[id]

    /**
     * The step to show, or null. In tour order, the first step that is not yet
     * seen and whose anchor is on screen — except that a step with
     * [CoachStep.waits] stops the tour while its anchor is absent (the box
     * appears the moment the control does), and a step without it is passed over
     * so the tour can never stall on a control that only sometimes exists.
     *
     * A null here marks nothing seen. That is the whole safety argument: a box
     * never points at nothing, and a skipped lesson is never spent.
     *
     * Two "someone else owns the screen" rules sit above the anchor check:
     * [ChromeState.blockedByForeground] (the exit survey, safe mode, an in-flight
     * Phase 44 download, ANY editor dialog) suppresses everything, and
     * [ChromeState.drawerOpen] decides whether a drawer beat or a non-drawer beat
     * may show — the scrim is drawn in the activity window, so a box cut behind a
     * dialog, or on a control the closed drawer still covers, is a hole in
     * nothing. That was the owner's *"not consistent with flow"*.
     */
    fun nextStep(seen: Set<String>, chrome: ChromeState): CoachStep? {
        if (chrome.blockedByForeground) return null
        for (step in steps) {
            if (step.id in seen) continue
            // The drawer's beats need the drawer open; every other beat needs it
            // closed (it would otherwise be cut on a control the drawer covers).
            if (step.inDrawer != chrome.drawerOpen) {
                if (step.waits) return null else continue
            }
            if (chrome.anchorVisible(step.anchorId)) return step
            if (step.waits) return null
        }
        return null
    }

    /** True when the tour has something to teach right now. */
    fun canShow(seen: Set<String>, chrome: ChromeState): Boolean = nextStep(seen, chrome) != null

    /** Lessons still unseen — what "Reset tips" gives back. */
    fun remaining(seen: Set<String>): List<CoachStep> = steps.filter { it.id !in seen }

    fun isComplete(seen: Set<String>): Boolean = steps.all { it.id in seen }

    /**
     * Record a step as seen (the user tapped the control it teaches). Unknown
     * ids are ignored: a preference written by an older build — or a step
     * deleted in a later one — must never grow the set with garbage that then
     * suppresses a real box.
     */
    fun markSeen(seen: Set<String>, stepId: String): Set<String> =
        if (byId.containsKey(stepId)) seen + stepId else seen

    /**
     * SKIP and Back: the whole tour is over, so nothing returns on its own (the
     * no-nag law). Deliberately marks *unseen* steps seen rather than writing a
     * second flag — one key, one meaning, and Settings → Reset tips is the one
     * door back.
     */
    fun markAllSeen(seen: Set<String>): Set<String> = seen + steps.map { it.id }

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
     * The drawer header's anchor: published only while a project is open AND it
     * is not the demo project already (switching to what you are already in is
     * not a lesson). Scratch mode gets no box.
     */
    fun drawerProjectAnchor(projectName: String?, demoProjectName: String): String? =
        if (projectName != null && projectName != demoProjectName) GuideAnchors.DRAWER_PROJECT else null

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
