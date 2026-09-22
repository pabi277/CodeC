package com.codeci.ide.ui.settings

/**
 * Phase 62.1 - the Settings screen, findable.
 *
 * The owner's spec (section 4): a Settings search bar "allowing users to quickly find
 * specific configurations". The screen is a 1,600-line list of 66 control rows in
 * 9 sections; before this file, the only way to find one was to scroll.
 *
 * **The catalog below is generated from the screen, not invented.** Every entry is one
 * `Settings*` call's own `title`, in screen order, with the section it sits under - the same
 * 66 rows `docs/chat-phase38/SETTINGS_AUDIT.md` already pins as the audit table, and
 * `SettingsSearchWiringTest` re-checks both directions (the catalog must hold exactly the rows
 * the screen renders, and every label must exist in the screen or in `strings.xml`). That is
 * why the labels read like the screen's own words - including row 11, a completion switch
 * whose label starts with a quote mark and a glyph.
 *
 * Pure Kotlin on purpose: the matching rule is the interesting part and it is host-testable,
 * while the Compose wiring is one `CompositionLocal` plus a folded-header row.
 */

/**
 * One control row: the section it sits under, the label it renders, and any words a user
 * might type that the label does not contain.
 */
data class SettingsEntry(
    val section: String,
    val label: String,
    val keywords: List<String> = emptyList(),
) {
    /** Everything a query may match for this row. */
    val searchable: List<String> get() = listOf(label) + keywords
}

object SettingsCatalog {

    /** Every `Settings*` row, in screen order - the audit's 66, in the screen's own wording. */
    val entries: List<SettingsEntry> = listOf(
        SettingsEntry(section = "Editor Settings", label = "Font Size"),
        SettingsEntry(section = "Editor Settings", label = "Font Family"),
        SettingsEntry(section = "Editor Settings", label = "Tab Size"),
        SettingsEntry(section = "Editor Settings", label = "Line Numbers"),
        SettingsEntry(section = "Editor Settings", label = "Auto Indent"),
        SettingsEntry(section = "Editor Settings", label = "Word Wrap"),
        SettingsEntry(section = "Editor Settings", label = "Autocompletion"),
        SettingsEntry(section = "Editor Settings", label = "How suggestions appear"),
        SettingsEntry(section = "Editor Settings", label = "Inline ghost text"),
        SettingsEntry(section = "Editor Settings", label = "Suggestion chips in the keys row"),
        SettingsEntry(section = "Editor Settings", label = "\"⌄ more\" opens the full completion panel"),
        SettingsEntry(section = "Editor Settings", label = "Suggestion delay"),
        SettingsEntry(section = "CodeC Keys", label = "Dedicated in-app code keyboard"),
        SettingsEntry(section = "CodeC Keys", label = "CodeC Keys"),
        SettingsEntry(section = "CodeC Keys", label = "Keep the code keyboard open while editing"),
        SettingsEntry(section = "CodeC Keys", label = "Haptic tick per key"),
        SettingsEntry(section = "CodeC Keys", label = "Key row height"),
        SettingsEntry(section = "Compiler", label = "C Standard"),
        SettingsEntry(section = "Compiler", label = "Warning Level"),
        SettingsEntry(section = "Compiler", label = "Optimization Level"),
        SettingsEntry(section = "Compiler", label = "Engine"),
        SettingsEntry(section = "Compiler", label = "Built-in TCC status"),
        SettingsEntry(section = "Terminal", label = "Terminal Font Size"),
        SettingsEntry(section = "Terminal", label = "Terminal Font Family"),
        SettingsEntry(section = "Terminal", label = "Terminal Theme"),
        SettingsEntry(section = "Terminal", label = "Terminal"),
        SettingsEntry(section = "Appearance", label = "Editor Theme"),
        SettingsEntry(section = "Appearance", label = "Accent Color"),
        SettingsEntry(section = "Appearance", label = "Match my wallpaper"),
        SettingsEntry(section = "Appearance", label = "Haptics"),
        SettingsEntry(section = "Storage", label = "Terminal Storage Access (~/storage)"),
        SettingsEntry(section = "Storage", label = "Projects Location"),
        SettingsEntry(section = "Storage", label = "Temporary files"),
        SettingsEntry(section = "Storage", label = "Clear temporary files"),
        SettingsEntry(section = "Storage", label = "Clear Cache"),
        SettingsEntry(section = "About", label = "Show the welcome screen again"),
        SettingsEntry(section = "About", label = "Help & guide"),
        SettingsEntry(section = "About", label = "Reset tips"),
        SettingsEntry(section = "About", label = "Your CodeC progress"),
        SettingsEntry(section = "About", label = "App Version"),
        SettingsEntry(section = "About", label = "GitHub"),
        SettingsEntry(section = "About", label = "Build date"),
        SettingsEntry(section = "About", label = "Authors"),
        SettingsEntry(section = "About", label = "Open-source licenses"),
        SettingsEntry(section = "About", label = "Privacy & permissions — all-files access (optional)"),
        SettingsEntry(section = "About", label = "Legacy storage read/write (≤ Android 12L)"),
        SettingsEntry(section = "About", label = "Camera (optional)"),
        SettingsEntry(section = "About", label = "Internet"),
        SettingsEntry(section = "About", label = "Network & Wi-Fi state"),
        SettingsEntry(section = "About", label = "Run notification + foreground service"),
        SettingsEntry(section = "About", label = "Install packages"),
        SettingsEntry(section = "About", label = "Wake lock"),
        SettingsEntry(section = "About", label = "Vibration"),
        SettingsEntry(section = "About", label = "Termux bridge (optional)"),
        SettingsEntry(section = "About", label = "The full table"),
        SettingsEntry(section = "About", label = "Check for updates"),
        SettingsEntry(section = "Feedback & Support", label = "Send feedback, rate, or report a bug"),
        SettingsEntry(section = "Feedback & Support", label = "Tell us before you go"),
        SettingsEntry(section = "Feedback & Support", label = "Report the last crash (log attached, nothing sent by itself)"),
        SettingsEntry(section = "Developer Options", label = "Show File Paths"),
        SettingsEntry(section = "Developer Options", label = "Export App Logs"),
        SettingsEntry(section = "Developer Options", label = "View App Logs"),
        SettingsEntry(section = "Developer Options", label = "Clear ALL Data"),
        SettingsEntry(section = "Developer Options", label = "Test Compiler Service"),
        SettingsEntry(section = "Developer Options", label = "Simulate Module Download"),
        SettingsEntry(section = "Developer Options", label = "Force Crash"),
    )

    /**
     * Every section SettingsScreen draws, in the screen's own order - the nine that hold control
     * rows plus the three whose content the catalog cannot index (GitHub Account, Package
     * Repository & Trust, Terminal Extra-Keys & Shortcuts). `SettingsSearchWiringTest` pins this
     * list against the screen's own headers, so a new section cannot appear without it.
     */
    val allSectionTitles: List<String> = listOf(
        "Editor Settings",
        "CodeC Keys",
        "Compiler",
        "Terminal",
        "Terminal Extra-Keys & Shortcuts",
        "Package Repository & Trust",
        "GitHub Account",
        "Appearance",
        "Storage",
        "About",
        "Feedback & Support",
        "Developer Options",
    )

    /** The sections that hold control rows, in screen order. */
    val sections: List<String> = entries.map { it.section }.distinct()

    /** The row whose label is exactly [title], or null for a row the catalog has not met. */
    fun entryFor(title: String): SettingsEntry? = entries.firstOrNull { it.label == title }

    /** True when [section] is a section of control rows (the form-only sections are not). */
    fun isControlSection(section: String): Boolean = sections.contains(section)
}

/**
 * Phase 62.1/62.2 - what a query shows, and what a folded section hides.
 *
 * Three laws, all of them from the owner's spec text:
 *
 *  1. **A query narrows; it never guesses.** Matching is case-insensitive and punctuation-blind
 *     (`normalize`), and every token the user typed must appear somewhere in the row - its
 *     label, its keywords, or the section it lives in. So "theme" finds *Editor Theme* and
 *     *Terminal Theme*, "appearance" finds everything under Appearance (its rows match through
 *     their section), and "more" finds the completion-panel row even though its label opens
 *     with a quote mark.
 *  2. **While the box has something in it, the query decides.** A section shows iff the query
 *     names it or one of its rows, and a row shows iff the query matches it. That includes the
 *     three sections the catalog has no rows for (GitHub Account, Package Repository & Trust,
 *     Terminal Extra-Keys & Shortcuts): their content cannot be indexed, so "github" shows the
 *     GitHub section and clearing the box brings all three back - which is also what makes the
 *     empty state ("nothing matches") true when it appears.
 *  3. **Folding is remembered, never applied over a search.** With an empty box the fold rule
 *     runs and folded sections show only their header, count and chevron. The moment the user
 *     types, every row that matches renders - a folded section must not swallow the result the
 *     search just found - and the fold comes back when the box is cleared.
 *
 * [rowVisible] is the single question the screen asks per row; [sectionVisible] the one it asks
 * per header.
 */
object SettingsSearch {

    /** Lower-cased word tokens: punctuation, quote marks and glyphs are not something to type. */
    fun normalize(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    /** Is the user filtering? A blank (or whitespace-only) query is not a filter. */
    fun isActive(query: String): Boolean = normalize(query).isNotEmpty()

    /** Does [text] satisfy every token of [query]? A blank query always matches. */
    fun matchesText(query: String, text: String): Boolean {
        val wanted = normalize(query)
        if (wanted.isEmpty()) return true
        val have = normalize(text)
        return wanted.all { token -> have.any { word -> word.contains(token) } }
    }

    /**
     * The screen's rule for one row: the query may match the row's label, its keywords, or the
     * name of the section it lives in.
     */
    fun matchesRow(query: String, title: String): Boolean {
        val wanted = normalize(query)
        if (wanted.isEmpty()) return true
        val entry = SettingsCatalog.entryFor(title)
        val haystacks = buildList {
            add(title)
            entry?.let { e ->
                addAll(e.keywords)
                add(e.section)
            }
        }
        return wanted.all { token -> haystacks.any { matchesText(token, it) } }
    }

    /** How many rows the catalog says a query matches (the empty state's only input). */
    fun matchCount(query: String): Int =
        if (!isActive(query)) SettingsCatalog.entries.size
        else SettingsCatalog.entries.count { matchesRow(query, it.label) }

    /**
     * Nothing matched: the one case the screen must say something about.
     *
     * "Nothing" means nothing AT ALL - not a row, and not a section title either. A query that
     * names a section the catalog has no rows for ("github") still puts that section on screen,
     * and showing "No settings match" next to it would be the screen contradicting itself.
     */
    fun isEmptyResult(query: String): Boolean =
        isActive(query) &&
            matchCount(query) == 0 &&
            SettingsCatalog.allSectionTitles.none { matchesText(query, it) }

    /**
     * The screen's one question per ROW: does the current view want it?
     *
     * No when the query does not match it; yes while the user is filtering (a search result is
     * never folded away); otherwise it depends on whether its section is folded.
     */
    fun rowVisible(query: String, title: String, collapsed: Set<String>): Boolean {
        if (!matchesRow(query, title)) return false
        if (isActive(query)) return true
        val section = SettingsCatalog.entryFor(title)?.section ?: return true
        return SettingsDisclosure.expanded(section, collapsed)
    }

    /**
     * The same question for a SECTION header: may it stay on screen?
     *
     * No while the user filters and the query names neither the section nor any of its rows -
     * the bare header would promise content that is not there. Otherwise yes: every section is
     * on screen when the box is empty, which is the screen the user had before this phase.
     */
    fun sectionVisible(query: String, section: String): Boolean {
        if (!isActive(query)) return true
        if (matchesText(query, section)) return true
        return SettingsCatalog.entries.any { it.section == section && matchesRow(query, it.label) }
    }

    /**
     * How many of a section's rows a query matches - the "Appearance 4" count on a folded
     * header, so folding never hides how much is inside.
     */
    fun sectionMatchCount(query: String, section: String): Int =
        SettingsCatalog.entries.count { it.section == section && matchesRow(query, it.label) }
}

/**
 * Phase 62 - what the Settings screen is currently showing, as one value.
 *
 * Plain data, no Compose: the screen provides it once and every row and header reads it, so the
 * policy above stays host-testable and the screen keeps one source of truth for what the user
 * sees right now.
 */
data class SettingsViewState(
    val query: String = "",
    val folded: Set<String> = emptySet(),
    val onToggleSection: (String) -> Unit = {},
)

/**
 * Phase 62.2 - the collapsible headers, as data.
 *
 * The state is a separated string of collapsed section names, so it survives a rotation
 * without pulling a preference system into view state: which sections a user has folded away
 * is not a setting, it is where they left the screen.
 */
object SettingsDisclosure {

    /** Unit separator: a character no section title contains. */
    const val SEPARATOR = "\u001F"

    fun parse(csv: String): Set<String> =
        if (csv.isBlank()) emptySet()
        else csv.split(SEPARATOR).filter { it.isNotBlank() }.toSet()

    fun serialize(collapsed: Set<String>): String =
        collapsed.sorted().joinToString(SEPARATOR)

    /** Fold, or unfold, one section. */
    fun toggle(collapsed: Set<String>, section: String): Set<String> =
        if (collapsed.contains(section)) collapsed - section else collapsed + section

    /**
     * Are this section's rows on screen? Whether the user is filtering is not this function's
     * business - that is [SettingsSearch.rowVisible]'s, and the answer there is "a search
     * result is never folded away".
     */
    fun expanded(section: String, collapsed: Set<String>): Boolean =
        !SettingsCatalog.isControlSection(section) || !collapsed.contains(section)
}
