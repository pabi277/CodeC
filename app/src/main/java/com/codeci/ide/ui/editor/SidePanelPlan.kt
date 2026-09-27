package com.codeci.ide.ui.editor

/**
 * Phase 55 — the side panel's pure spec, and the laws the shots pinned.
 *
 * Spec: `docs/chat-phase55/PART_55_1_PANEL.md` + the reference card
 * `docs/chat-phase54/PART_54_1_SHOTS.md` (written from the seven
 * `docs/spck-ui/Screenshot_20260922_*.jpg`, read 2026-09-22).
 *
 * **Why this file is pure.** Every decision below is a *decision about the
 * reference*, not about pixels: which five rail slots exist and in what order,
 * which of them are wired, which six cells the Navigation card holds, how a
 * recent row's age is worded, and when the badge is allowed to say “External”.
 * All of it is host-tested ([com.codeci.ide.SidePanelPlanTest]), so a later
 * phase cannot quietly re-order the rail or copy the shot's shop row.
 *
 * **The bottom bar is NOT part of this file.** The owner's reversal
 * (2026-09-22: *“I don't think removing the full down ber is a good choice i
 * think only removing the project option is ok.”*) means `FlatBottomBar`, the
 * reveal handle and Phase 32's hide-while-typing all stay. This panel is an
 * addition; the only thing that ever leaves the bar is the Projects option, in
 * Phase 56, once [NavCell.PROJECTS] in this card can open that screen.
 */
object SidePanelPlan {

    /**
     * How much of the width the panel takes. The shots measure ≈85 %
     * (`Screenshot_20260922_124049`: the panel's edge at ≈ x 611 of 720 viewed
     * px of a 1080×2340 screen), which leaves a live strip of the editor —
     * including the green play — on the right. That strip is the point of the
     * number: the panel is not a full-screen drawer.
     */
    const val PANEL_WIDTH_FRACTION = 0.85f

    /**
     * A left-edge drag this long (dp) opens the panel — the same gesture the
     * retired `ModalNavigationDrawer` gave us, kept because a phone user's
     * first instinct on a drawer is the edge. Phase 25.2's law is untouched:
     * the screen only enables the gesture when no file is open.
     */
    const val OPEN_GESTURE_DP = 32f

    /**
     * The rail, **in the shot's order**: outline triangle, folder,
     * search-with-`<>`, branch, person. Order is law; the fifth slot is the
     * owner's (*“Reseserve it i have plan for ai i can use that”*, 2026-09-22) —
     * it is drawn as a reserved slot and opens nothing, so no screen is
     * implied and no shop is faked.
     */
    val RAIL: List<RailPanel> = listOf(
        RailPanel.NAVIGATION,
        RailPanel.FILES,
        RailPanel.SEARCH,
        RailPanel.REPOSITORY,
        RailPanel.RESERVED
    )

    /** The slots that open a panel today. [RailPanel.RESERVED] is not one. */
    val WIRED: List<RailPanel> = RAIL.filter { it != RailPanel.RESERVED }

    val DEFAULT_PANEL: RailPanel = RailPanel.NAVIGATION

    /**
     * The Navigation card: **one card, 3 columns × 2 rows** — the shape the
     * phone shows, and the drawing got wrong.
     *
     * Top row is the shot's own (`Projects · Editor · Settings`). Bottom row is
     * the owner's answer of 2026-09-22 (option *“Terminal · Packages · Guide”*),
     * replacing the shot's `Discover · My Labs · Change Log`, which CodeC must
     * not copy. [Selecting][SELECTED_CELL] is the editor, exactly as the shot
     * shows a raised, highlit **Editor** cell.
     */
    val CARD: List<List<NavCell>> = listOf(
        listOf(NavCell.PROJECTS, NavCell.EDITOR, NavCell.SETTINGS),
        listOf(NavCell.TERMINAL, NavCell.PACKAGES, NavCell.GUIDE)
    )

    /** The shot's selected cell (raised tile, white label). */
    val SELECTED_CELL: NavCell = NavCell.EDITOR

    /** The grid's column count — `CARD` must stay rectangular (tested). */
    const val CARD_COLUMNS = 3

    /**
     * Copy we will not ship, whatever the shots show: the shop row and SPCK's
     * own discovery surfaces (roadmap “Do not add”). Kept as data so the test
     * can fail loudly if a label like these ever enters [CARD].
     */
    val REFUSED_LABELS: List<String> = listOf(
        "Discover", "My Labs", "Change Log", "Upgrade", "Account", "Credits"
    )

    fun panelFor(id: String): RailPanel? = RAIL.firstOrNull { it.id == id }

    /** True when a rail tap does something today (the reserved slot does not). */
    fun isWired(panel: RailPanel): Boolean = WIRED.contains(panel)

    /** A left-edge drag of at least [OPEN_GESTURE_DP] opens the panel. */
    fun shouldOpenPanel(draggedDp: Float): Boolean = draggedDp >= OPEN_GESTURE_DP

    /** The cell a label/id maps to, or null (an unknown id is never navigated). */
    fun cellFor(id: String): NavCell? = CARD.flatten().firstOrNull { it.id == id }

    /** Every cell label in reading order — the tour and the tests read this. */
    fun cardLabels(): List<String> = CARD.flatten().map { it.label }
}

/**
 * One rail slot. [id] is stable (tests, anchors); [label] is the section
 * heading the panel prints under the rail.
 */
enum class RailPanel(val id: String, val label: String) {
    NAVIGATION("navigation", "Navigation"),
    FILES("files", "Files"),
    SEARCH("search", "Search"),
    REPOSITORY("repository", "Repository"),

    /**
     * The fifth slot: reserved for the owner's planned AI surface
     * (*“Reseserve it i have plan for ai i can use that”*). It is drawn — the
     * rail keeps the shot's five positions — and it is **not** tappable.
     */
    RESERVED("reserved", "Reserved")
}

/**
 * A cell of the Navigation card. [routeHint] is only a hint of the room behind
 * it; the wiring lives in `EditorScreen` (one place), and Phase 56's rule that
 * Projects must open from here is pinned by the wiring test.
 */
enum class NavCell(val id: String, val label: String) {
    PROJECTS("projects", "Projects"),
    EDITOR("editor", "Editor"),
    SETTINGS("settings", "Settings"),
    TERMINAL("terminal", "Terminal"),
    PACKAGES("packages", "Packages"),
    GUIDE("guide", "Guide")
}

/**
 * The **Recent** list under the card: name, a relative age, and — only when it
 * is really true — an `External` badge.
 *
 * The shot puts `External` on every row; the roadmap's rule is stricter
 * (*“External only when the project really came from outside”*), so the badge
 * is data-driven: [RecentProjects.isExternal] answers it from the project's real
 * root (a project living under the app's **external** files dir came in through
 * import/share/clone; one under the private projects root did not).
 */
object RecentProjects {

    /** How many rows the panel shows — the shot's list is long; ours is capped. */
    const val MAX_ROWS = 8

    data class Entry(
        val id: String,
        val name: String,
        /** Wall-clock millis of the last open; 0 = unknown. */
        val lastOpenedMillis: Long,
        /** The project's real root path, when it is known. */
        val rootPath: String? = null
    )

    data class Row(
        val id: String,
        val name: String,
        val ageLabel: String,
        /** True only when [Row] came from an entry whose root is really outside. */
        val external: Boolean
    )

    /**
     * Newest first, capped at [limit]. An entry whose age is unknown (0) sorts
     * last — never first, because “first” is what the shot's highlight means.
     */
    fun build(
        entries: List<Entry>,
        nowMillis: Long,
        privateRoot: String?,
        externalRoot: String?,
        limit: Int = MAX_ROWS
    ): List<Row> = entries
        .sortedWith(compareByDescending<Entry> { it.lastOpenedMillis }.thenBy { it.name.lowercase() })
        .take(limit.coerceAtLeast(0))
        .map { entry ->
            Row(
                id = entry.id,
                name = entry.name,
                ageLabel = ageLabel(nowMillis - entry.lastOpenedMillis),
                external = isExternal(entry.rootPath, privateRoot, externalRoot)
            )
        }

    /**
     * The badge's one rule: the project's root is under the **external** files
     * dir (the `ProjectTransfer` import/clone location) and *not* under the
     * private projects root. Anything else — including “we do not know” — is not
     * external. A badge that is always on is decoration, and the roadmap
     * forbids copying the shot's always-on badge.
     */
    fun isExternal(rootPath: String?, privateRoot: String?, externalRoot: String?): Boolean {
        val root = rootPath?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return false
        val ext = externalRoot?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return false
        if (!root.startsWith("$ext/") && root != ext) return false
        val priv = privateRoot?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        return !(priv != null && (root == priv || root.startsWith("$priv/")))
    }

    private const val MINUTE = 60_000L
    private const val HOUR = 60L * MINUTE
    private const val DAY = 24L * HOUR

    /**
     * The shot's own vocabulary (`18 minutes ago`, `1 week ago`, `2 weeks ago`,
     * `1 month ago`). Singular/plural and “just now” are deliberate: the row is
     * a sentence a user reads, not a timestamp.
     */
    fun ageLabel(ageMillis: Long): String {
        if (ageMillis <= 0L) return "just now"
        val minutes = ageMillis / MINUTE
        val hours = ageMillis / HOUR
        val days = ageMillis / DAY
        return when {
            minutes < 1L -> "just now"
            minutes < 60L -> if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
            hours < 24L -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            days == 1L -> "yesterday"
            days < 7L -> "$days days ago"
            days < 14L -> "1 week ago"
            days < 30L -> "${days / 7} weeks ago"
            days < 60L -> "1 month ago"
            days < 365L -> "${days / 30} months ago"
            days < 730L -> "1 year ago"
            else -> "${days / 365} years ago"
        }
    }
}
