package com.codeci.ide.ui.guide

/**
 * Phase 45.2 — the coach-mark PLAN: which control gets spotlighted on which
 * screen, in which order, and when it is forbidden. Pure, so the rules that
 * decide "does a mark appear" are host-testable (`CoachMarkPlanTest`) instead of
 * being discovered on a phone.
 *
 * The one rule that makes coach marks safe:
 *
 * > **[nextUnseen] only ever returns a step whose anchor the caller says is
 * > visible**, and a surface whose anchors are all hidden produces no step AND
 * > no "seen" entry — so the mark waits for the moment the control is really on
 * > screen. That kills the worst failure mode (a spotlight on nothing) and it is
 * > what makes the "Show tabs" mark legal at all: the tab bar hides while typing
 * > (Phase 32.1), so its handle is only sometimes there.
 *
 * Caps: [MAX_PER_ARRIVAL] marks per surface per arrival, one mark per anchor for
 * the app's whole life (`coach_marks_seen_csv`), and never while another surface
 * owns the screen ([ChromeState.blockedByForeground]).
 */

/** The three screens that teach something on first arrival. */
enum class GuideSurface { EDITOR, TERMINAL, PACKAGES }

/** Anchor ids. One anchor = one spotlight; the id is also the step id. */
object GuideAnchors {
    /** The editor's ☰ (file tree + the project-name switcher). */
    const val EDITOR_DRAWER = "editor_drawer"

    /** The editor's RUN ▶. */
    const val EDITOR_RUN = "editor_run"

    /** Phase 32.1's "Show tabs" handle — visible only while the bar is hidden. */
    const val NAV_HANDLE = "nav_handle"

    /** The Packages tab's first install card. */
    const val PACKAGES_CARD = "packages_card"

    /** The terminal's status chip (Phase 44.1 made it stage-aware). */
    const val TERMINAL_CHIP = "terminal_chip"

    val all: List<String> = listOf(EDITOR_DRAWER, EDITOR_RUN, NAV_HANDLE, PACKAGES_CARD, TERMINAL_CHIP)
}

/** One spotlight: an anchor, a title, one line of body. */
data class CoachStep(
    val id: String,
    val surface: GuideSurface,
    val anchorId: String,
    val title: String,
    val body: String
)

/**
 * What the caller can see right now. Every flag is an *observation from the
 * Android edge* (an anchor publishes its bounds when laid out and withdraws them
 * when it leaves composition), so the plan never guesses about layout.
 *
 * [blockedByForeground] is the "someone else owns the screen" flag: the exit
 * survey, the safe-mode banner, an in-flight Phase 44 setup bar, safe mode
 * itself. A coach mark under any of them is noise at best.
 */
data class ChromeState(
    val drawerButtonVisible: Boolean = false,
    val runButtonVisible: Boolean = false,
    val navHandleVisible: Boolean = false,
    val installCardVisible: Boolean = false,
    val statusChipVisible: Boolean = false,
    val blockedByForeground: Boolean = false
) {
    companion object {
        /**
         * The Android edge builds the state from the set of anchors currently
         * laid out — one call, so the id→flag mapping lives here (host-tested)
         * instead of in a composable.
         */
        fun of(visibleAnchors: Set<String>, blockedByForeground: Boolean): ChromeState = ChromeState(
            drawerButtonVisible = GuideAnchors.EDITOR_DRAWER in visibleAnchors,
            runButtonVisible = GuideAnchors.EDITOR_RUN in visibleAnchors,
            navHandleVisible = GuideAnchors.NAV_HANDLE in visibleAnchors,
            installCardVisible = GuideAnchors.PACKAGES_CARD in visibleAnchors,
            statusChipVisible = GuideAnchors.TERMINAL_CHIP in visibleAnchors,
            blockedByForeground = blockedByForeground
        )
    }

    fun anchorVisible(anchorId: String): Boolean = when (anchorId) {
        GuideAnchors.EDITOR_DRAWER -> drawerButtonVisible
        GuideAnchors.EDITOR_RUN -> runButtonVisible
        GuideAnchors.NAV_HANDLE -> navHandleVisible
        GuideAnchors.PACKAGES_CARD -> installCardVisible
        GuideAnchors.TERMINAL_CHIP -> statusChipVisible
        else -> false
    }
}

object CoachMarkPlan {

    /** Two marks per arrival. A third is a tour, and tours get skipped. */
    const val MAX_PER_ARRIVAL = 2

    const val MAX_TITLE_CHARS = 24
    const val MAX_BODY_CHARS = 90

    /** Order matters: within a surface the marks arrive in this sequence. */
    val steps: List<CoachStep> = listOf(
        // The owner's exact words: "user don't know where should they change the
        // project or file".
        CoachStep(
            id = GuideAnchors.EDITOR_DRAWER,
            surface = GuideSurface.EDITOR,
            anchorId = GuideAnchors.EDITOR_DRAWER,
            title = "Your files",
            body = "Tap here for the file tree. The project name at the top switches projects."
        ),
        CoachStep(
            id = GuideAnchors.EDITOR_RUN,
            surface = GuideSurface.EDITOR,
            anchorId = GuideAnchors.EDITOR_RUN,
            title = "Run your code",
            body = "Compiles and runs. The result opens at the bottom."
        ),
        // The owner's exact words: "the tap to the open down side of the
        // keyboard". Only ever shown when the handle is really on screen.
        CoachStep(
            id = GuideAnchors.NAV_HANDLE,
            surface = GuideSurface.EDITOR,
            anchorId = GuideAnchors.NAV_HANDLE,
            title = "The tabs are here",
            body = "They hide while you type. Tap or swipe up to bring them back."
        ),
        CoachStep(
            id = GuideAnchors.PACKAGES_CARD,
            surface = GuideSurface.PACKAGES,
            anchorId = GuideAnchors.PACKAGES_CARD,
            title = "One-time download",
            body = "Adding a language downloads once. Keep CodeC open while it finishes."
        ),
        CoachStep(
            id = GuideAnchors.TERMINAL_CHIP,
            surface = GuideSurface.TERMINAL,
            anchorId = GuideAnchors.TERMINAL_CHIP,
            title = "What it is doing",
            body = "Starting, downloading or running. If it says downloading, do not close CodeC."
        )
    )

    private val byId: Map<String, CoachStep> = steps.associateBy { it.id }

    fun step(id: String): CoachStep? = byId[id]

    /**
     * The steps a surface could show right now: its own steps whose anchor the
     * caller reports visible, in plan order. [ChromeState.blockedByForeground] is
     * NOT applied here — this is the visibility half of the rule, and the caller
     * (and [nextUnseen]) applies the "someone else owns the screen" half.
     */
    fun stepsFor(surface: GuideSurface, chrome: ChromeState): List<CoachStep> =
        steps.filter { it.surface == surface && chrome.anchorVisible(it.anchorId) }

    /**
     * The step to show, or null: nothing when another surface owns the screen,
     * nothing when every anchor of this surface is hidden, and nothing once the
     * user has seen them all. A null here never marks anything seen.
     */
    fun nextUnseen(surface: GuideSurface, seen: Set<String>, chrome: ChromeState): CoachStep? {
        if (chrome.blockedByForeground) return null
        return stepsFor(surface, chrome).firstOrNull { it.id !in seen }
    }

    /** True when this surface has something to teach right now. */
    fun canShow(surface: GuideSurface, chrome: ChromeState): Boolean =
        nextUnseen(surface, emptySet(), chrome) != null

    /**
     * Record a step as seen. Unknown ids are ignored: a preference written by an
     * older build (or a step deleted in a later one) must never grow the set with
     * garbage that then suppresses a real mark.
     */
    fun markSeen(seen: Set<String>, stepId: String): Set<String> =
        if (byId.containsKey(stepId)) seen + stepId else seen

    /** `coach_marks_seen_csv` → ids. Blank/garbage tokens drop out. */
    fun parseSeen(csv: String?): Set<String> = csv
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { byId.containsKey(it) }
        ?.toSet()
        ?: emptySet()

    /** ids → `coach_marks_seen_csv`, in plan order so the value is stable. */
    fun serializeSeen(seen: Set<String>): String =
        steps.map { it.id }.filter { it in seen }.joinToString(",")

    /** Which surface a navigation route is, or null when it teaches nothing. */
    fun surfaceForRoute(route: String?): GuideSurface? {
        val r = route?.substringBefore('?')?.trim().orEmpty()
        return when {
            r.isEmpty() -> null
            r == "editor" || r.startsWith("editor") -> GuideSurface.EDITOR
            r == "terminal" || r.startsWith("terminal") -> GuideSurface.TERMINAL
            r == "modules" -> GuideSurface.PACKAGES
            else -> null
        }
    }

    /**
     * What the host may show on this arrival: the unseen, visible step, or null
     * once this arrival has spent its [MAX_PER_ARRIVAL] marks. `shownThisArrival`
     * is the host's counter, reset when the destination changes.
     */
    fun stepForArrival(
        surface: GuideSurface?,
        seen: Set<String>,
        chrome: ChromeState,
        shownThisArrival: Int
    ): CoachStep? {
        if (surface == null) return null
        if (shownThisArrival >= MAX_PER_ARRIVAL) return null
        return nextUnseen(surface, seen, chrome)
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
 * The tooltip never leaves the screen and never covers its own hole. Pure so the
 * three cases (anchor at the top, in the middle, at the bottom) plus a landscape
 * box are host-tested (`TooltipPlacementTest`) rather than eyeballed on one
 * phone.
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
