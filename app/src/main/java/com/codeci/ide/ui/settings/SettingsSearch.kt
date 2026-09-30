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

/** A searchable control description inside a bespoke form section; not a rendered fake row. */
data class SettingsFormSearchEntry(
    val section: String,
    val label: String,
    val keywords: List<String> = emptyList(),
) {
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

    /** Fixed labels and aliases for controls inside bespoke form-only groups. */
    val formEntries: List<SettingsFormSearchEntry> = listOf(
        SettingsFormSearchEntry(
            section = "Terminal Extra-Keys & Shortcuts",
            label = "Custom Extra-Key Shortcuts",
            keywords = listOf("Extra Keys", "terminal key bar", "shortcut buttons", "macros", "comma separated", "add example", "save shortcuts"),
        ),
        SettingsFormSearchEntry(
            section = "Package Repository & Trust",
            label = "Repository Trust Status",
            keywords = listOf("signed channel", "keyring", "OpenPGP", "gpgv", "fail closed", "signing subkey", "repository signature", "check repository", "InRelease", "online", "offline", "userland"),
        ),
        SettingsFormSearchEntry(
            section = "GitHub Account",
            label = "GitHub Personal Access Token",
            keywords = listOf("token", "credentials", "source control", "push", "repository contents", "username", "commit name", "commit email", "author name", "author email", "disconnect", "save", "create token"),
        ),
    )

    /**
     * Every section SettingsScreen draws, in the screen's own order - the nine with standard
     * control rows plus the three bespoke form groups, which have fixed search descriptors in
     * [formEntries]. `SettingsSearchWiringTest` pins this list against the screen's headers.
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

    /** True when [section] contains indexed control rows (form-only groups have none). */
    fun isControlSection(section: String): Boolean = sections.contains(section)

    /** Every visible Settings group can fold, including bespoke form-only groups. */
    fun isFoldableSection(section: String): Boolean = allSectionTitles.contains(section)
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
 *     names it or one of its rows. The three bespoke form groups answer to fixed field labels and
 *     aliases; runtime form values, credentials, and repository data are never indexed.
 *  3. **Folding is view state, never applied over a search.** With an empty box the fold rule
 *     runs and folded sections show only their header and chevron (plus a count where rows are
 *     indexed). The moment the user types, every matching row renders - a folded section must
 *     not swallow the result the search just found - and the fold comes back when the box clears.
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

    /** Matching standard rows plus matching form groups; form results are not fake row entries. */
    fun matchCount(query: String): Int =
        if (!isActive(query)) SettingsCatalog.entries.size
        else SettingsCatalog.entries.count { matchesRow(query, it.label) } +
            SettingsCatalog.formEntries.count { matchesForm(query, it) }

    private fun matchesForm(query: String, entry: SettingsFormSearchEntry): Boolean {
        val wanted = normalize(query)
        if (wanted.isEmpty()) return true
        return wanted.all { token ->
            (entry.searchable + entry.section).any { matchesText(token, it) }
        }
    }

    /**
     * Nothing matched: the one case the screen must say something about.
     *
     * "Nothing" means nothing AT ALL - not a row, and not a section title either. A query that
     * names a section the catalog has no rows for ("github") still puts that section on screen,
     * and showing "No settings match" next to it would be the screen contradicting itself.
     */
    fun isEmptyResult(query: String): Boolean =
        isActive(query) && matchCount(query) == 0 &&
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
        return SettingsCatalog.entries.any { it.section == section && matchesRow(query, it.label) } ||
            SettingsCatalog.formEntries.any { it.section == section && matchesForm(query, it) }
    }

    /**
     * How many of a section's rows a query matches - the "Appearance 4" count on a folded
     * header, so folding never hides how much is inside.
     */
    fun sectionMatchCount(query: String, section: String): Int =
        SettingsCatalog.entries.count { it.section == section && matchesRow(query, it.label) } +
            SettingsCatalog.formEntries.count { it.section == section && matchesForm(query, it) }
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

    /** Every Settings group begins collapsed on a fresh screen visit. */
    fun initialCollapsedCsv(): String = serialize(SettingsCatalog.allSectionTitles.toSet())

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
        !SettingsCatalog.isFoldableSection(section) || !collapsed.contains(section)
}
