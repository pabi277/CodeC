package com.codeci.ide

import com.codeci.ide.ui.settings.SettingsCatalog
import com.codeci.ide.ui.settings.SettingsDisclosure
import com.codeci.ide.ui.settings.SettingsSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 62.1/62.2 — the Settings search's two laws, and the catalog that
 * makes them true.
 *
 * A search bar over a hand-written list would rot the day a row moves; the
 * catalog is generated from the screen's own `Settings*` calls, and this
 * test re-derives those rows from the source text and compares — so a row
 * added to SettingsScreen without a catalog entry fails here, and a query
 * can never offer a label the screen does not render.
 *
 * The audit-table check is the second half of the same idea: the 66 rows
 * `docs/chat-phase38/SETTINGS_AUDIT.md` pins as *one row, one effect* are
 * exactly the rows the user can now search, counted per section.
 */
class SettingsSearchPolicyTest {

    private val screen: String
        get() = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt"
        ).readText()

    private val strings: String
        get() = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()

    private val auditDoc: String
        get() = RepoFiles.mainSource("docs/chat-phase38/SETTINGS_AUDIT.md").readText()

    private val controlComposables =
        listOf("SettingsSwitch", "SettingsDropdown", "SettingsSlider", "SettingsItem", "SettingsAction")

    /** The screen's control-row call sites — the audit test's own counting rule. */
    private fun screenControlRows(): Int = controlComposables.sumOf { composable ->
        Regex("\\b$composable\\s*\\(").findAll(screen).count() -
            Regex("fun\\s+$composable\\s*\\(").findAll(screen).count()
    }

    /** Section titles the screen renders, in order (literal or resolved from strings.xml). */
    private fun screenSections(): List<String> {
        val re = Regex(
            """SettingsSectionHeader\(\s*(?:"([^"]+)"|stringResource\(com\.codeci\.ide\.R\.string\.(\w+)\))"""
        )
        return re.findAll(screen).map { m ->
            m.groupValues[1].ifEmpty { stringValue(m.groupValues[2]) }
        }.toList()
    }

    private fun stringValue(name: String): String {
        val m = Regex("<string name=\"$name\">([^<]+)</string>").find(strings)
        checkNotNull(m) { "strings.xml has no entry '$name'" }
        return m.groupValues[1]
    }

    /**
     * Where the screen's own row for [label] starts: its `title = …` argument. Anchored
     * on the argument, not the bare word — a label may also be a section's title (there
     * is a “CodeC Keys” row inside the CodeC Keys section), and a header is not a row.
     */
    private fun renderedAt(label: String): Int {
        // A label's own quote marks are escaped where the screen writes it as a literal.
        val literal = screen.indexOf("title = \"" + label.replace("\"", "\\\"") + "\"")
        if (literal >= 0) return literal
        val name = Regex("<string name=\"(\\w+)\">" + Regex.escape(label) + "</string>")
            .find(strings)?.groupValues?.get(1)
            ?: return -1
        return Regex("title = stringResource\\((?:com\\.codeci\\.ide\\.R\\.string\\.|R\\.string\\.)$name\\)")
            .find(screen)?.range?.first ?: -1
    }

    /** The audit doc's numbered control rows, as (section, label) pairs. */
    private fun auditRows(): List<Pair<String, String>> =
        auditDoc.lineSequence()
            .filter { it.startsWith("| ") }
            .map { it.trim('|').split('|').map { c -> c.trim() } }
            .filter { it.size >= 4 && it[0].matches(Regex("\\d+")) }
            .map { it[1] to it[2] }
            .toList()

    // ---- the catalog is the screen ------------------------------------------

    @Test
    fun `the catalog holds every control row the screen renders`() {
        assertEquals(
            "the screen's Settings* call sites and the catalog must be the same 66 rows",
            screenControlRows(), SettingsCatalog.entries.size
        )
    }

    @Test
    fun `every catalog label is really rendered, in screen order`() {
        var previous = -1
        SettingsCatalog.entries.forEach { entry ->
            val at = renderedAt(entry.label)
            assertTrue(
                "SettingsScreen renders no row titled '${entry.label}'",
                at >= 0
            )
            assertTrue(
                "'${entry.label}' is catalogued out of the screen's own order",
                at > previous
            )
            previous = at
        }
    }

    @Test
    fun `the catalog's sections are the screen's own headers, in order`() {
        val withRows = screenSections().filter { SettingsCatalog.isControlSection(it) }
        assertEquals(
            "sections that hold rows, in the order SettingsScreen draws them",
            withRows, SettingsCatalog.sections
        )
    }

    @Test
    fun `the searchable rows are the audit table's rows, counted per section`() {
        val audit = auditRows().groupingBy { it.first }.eachCount()
        assertEquals("the audit doc still numbers 66 rows", 66, auditRows().size)
        audit.forEach { (section, count) ->
            assertEquals(
                "section '$section': the audit's rows and the searchable rows must be one set",
                count,
                SettingsCatalog.entries.count { it.section == section }
            )
        }
        assertEquals(
            "the audit doc names no section the catalog has not met",
            emptySet<String>(),
            audit.keys - SettingsCatalog.sections.toSet()
        )
    }

    // ---- law 1: a query narrows, and never guesses --------------------------

    @Test
    fun `a query matches a row's own words`() {
        assertTrue(SettingsSearch.matchesRow("font size", "Font Size"))
        assertTrue("case is not something the user retypes", SettingsSearch.matchesRow("FONT", "Font Size"))
        assertTrue("a partial word is enough", SettingsSearch.matchesRow("compil", "Compiler"))
    }

    @Test
    fun `a query matches the section a row lives in`() {
        val appearance = SettingsCatalog.entries
            .filter { it.section == "Appearance" }
            .map { it.label }
            .toSet()
        assertEquals(setOf("Editor Theme", "Accent Color", "Match my wallpaper", "Haptics"), appearance)
        appearance.forEach {
            assertTrue("'$it' is filed under Appearance", SettingsSearch.matchesRow("appearance", it))
        }
        assertFalse(
            "an Appearance query must not drag in Editor Settings rows",
            SettingsSearch.matchesRow("appearance", "Font Size")
        )
    }

    @Test
    fun `punctuation and quote marks are not something to type`() {
        val quoted = "\"⌄ more\" opens the full completion panel"
        assertTrue(SettingsSearch.matchesRow("more", quoted))
        assertTrue("the glyph itself is not a query", SettingsSearch.matchesRow("completion panel", quoted))
        assertTrue(SettingsSearch.matchesRow("~/storage", "Terminal Storage Access (~/storage)"))
        assertTrue(
            "an em dash is not a word",
            SettingsSearch.matchesRow("permissions", "Privacy & permissions — all-files access (optional)")
        )
    }

    @Test
    fun `every token must land somewhere in the row`() {
        assertTrue(SettingsSearch.matchesRow("clear cache", "Clear Cache"))
        assertFalse(
            "the second word must match too, in the label or its section",
            SettingsSearch.matchesRow("cache font", "Clear Cache")
        )
        assertTrue(
            "and it may land in a different haystack than the first",
            SettingsSearch.matchesRow("cache storage", "Clear Cache")
        )
    }

    @Test
    fun `a blank query is not a filter`() {
        assertFalse(SettingsSearch.isActive("   "))
        assertTrue(SettingsSearch.matchesRow("   ", "Font Size"))
        assertEquals(
            "an idle search box counts everything",
            SettingsCatalog.entries.size, SettingsSearch.matchCount("")
        )
        assertFalse(SettingsSearch.isEmptyResult(""))
    }

    @Test
    fun `a query that names a section is never "nothing matched"`() {
        assertEquals(
            "the catalog has no rows for Package Repository & Trust, so its count is zero...",
            0, SettingsSearch.matchCount("repository")
        )
        assertFalse(
            "...but the section itself is on screen, so the empty state must not claim otherwise",
            SettingsSearch.isEmptyResult("repository")
        )
        assertTrue(SettingsSearch.sectionVisible("repository", "Package Repository & Trust"))
        assertTrue(
            "every section the screen draws is known to the catalog",
            SettingsCatalog.allSectionTitles.containsAll(SettingsCatalog.sections)
        )
        assertEquals(
            "and the row-bearing ones are exactly the catalog's own",
            SettingsCatalog.sections,
            SettingsCatalog.allSectionTitles.filter { SettingsCatalog.isControlSection(it) }
        )
    }

    @Test
    fun `a query nothing matches is the empty state, and it is honest`() {
        val nonsense = "zzzqqq"
        assertEquals(0, SettingsSearch.matchCount(nonsense))
        assertTrue(SettingsSearch.isEmptyResult(nonsense))
        assertFalse("a query that finds something is not empty", SettingsSearch.isEmptyResult("theme"))
        assertTrue(SettingsSearch.matchCount("theme") >= 2)
    }

    @Test
    fun `while the box has something in it, the query decides what is on screen`() {
        listOf("GitHub Account", "Package Repository & Trust", "Terminal Extra-Keys & Shortcuts")
            .forEach { section ->
                assertFalse("$section has no indexed rows", SettingsCatalog.isControlSection(section))
                assertTrue(
                    "a form-only section still answers to its own name",
                    SettingsSearch.sectionVisible("github", "GitHub Account")
                )
                assertFalse(
                    "and is gone while the user searches for something else",
                    SettingsSearch.sectionVisible("font", section)
                )
            }
        assertTrue(
            "clearing the box brings every section back",
            SettingsSearch.sectionVisible("", "Terminal Extra-Keys & Shortcuts")
        )
    }

    // ---- law 2: filtering and folding agree with the catalog ----------------

    @Test
    fun `a section stays while the user filters only if it has something to show`() {
        assertTrue(SettingsSearch.sectionVisible("font", "Editor Settings"))
        assertFalse(
            "no Editor Settings row matches 'cache' — the bare header would promise nothing",
            SettingsSearch.sectionVisible("cache", "Editor Settings")
        )
        assertTrue("but Storage does", SettingsSearch.sectionVisible("cache", "Storage"))
        assertTrue(
            "a section query keeps its own header",
            SettingsSearch.sectionVisible("appearance", "Appearance")
        )
        assertEquals(4, SettingsSearch.sectionMatchCount("appearance", "Appearance"))
    }

    @Test
    fun `naming a section shows that whole section`() {
        val compiler = SettingsCatalog.entries.filter { it.section == "Compiler" }.map { it.label }
        assertEquals(5, compiler.size)
        compiler.forEach {
            assertTrue("'$it' comes with the Compiler section", SettingsSearch.matchesRow("compiler", it))
        }
    }

    @Test
    fun `a folded section never swallows the row the search just found`() {
        val folded = setOf("Appearance")
        assertEquals(
            "the folder keeps what it hid",
            setOf("Editor Theme", "Accent Color", "Match my wallpaper", "Haptics"),
            SettingsCatalog.entries.filter { it.section == "Appearance" }.map { it.label }.toSet()
        )
        assertFalse(
            "with an empty box, folded means folded",
            SettingsSearch.rowVisible("", "Editor Theme", folded)
        )
        assertTrue(
            "the moment the user types, the match renders",
            SettingsSearch.rowVisible("theme", "Editor Theme", folded)
        )
        assertFalse(
            "and folding still hides rows the query does not want",
            SettingsSearch.rowVisible("theme", "Accent Color", folded)
        )
        assertTrue(
            "clearing the box hands the screen back to the fold",
            !SettingsSearch.rowVisible("", "Editor Theme", folded)
        )
    }

    @Test
    fun `a row the catalog has not met is never hidden by a fold`() {
        assertTrue(
            "an uncatalogued row renders rather than disappearing silently",
            SettingsSearch.rowVisible("", "A row this catalog has not met", setOf("Appearance"))
        )
        assertFalse(
            "it is still nobody's search result",
            SettingsSearch.rowVisible("font", "A row this catalog has not met", emptySet())
        )
    }

    @Test
    fun `folding a section keeps its state across a rotation without inventing a setting`() {
        var collapsed = emptySet<String>()
        collapsed = SettingsDisclosure.toggle(collapsed, "Appearance")
        assertEquals(setOf("Appearance"), collapsed)
        assertFalse(SettingsDisclosure.expanded("Appearance", collapsed))
        assertTrue("its neighbours are untouched", SettingsDisclosure.expanded("Storage", collapsed))
        assertEquals(
            "the state survives a round trip through saved instance state",
            collapsed, SettingsDisclosure.parse(SettingsDisclosure.serialize(collapsed))
        )
        collapsed = SettingsDisclosure.toggle(collapsed, "Appearance")
        assertTrue(SettingsDisclosure.expanded("Appearance", collapsed))
    }

    @Test
    fun `only sections that hold rows can fold`() {
        listOf("GitHub Account", "Package Repository & Trust", "Terminal Extra-Keys & Shortcuts")
            .forEach { section ->
                assertTrue(
                    "$section has no rows of its own, so folding it would hide nothing but a form",
                    SettingsDisclosure.expanded(section, setOf(section))
                )
            }
        assertFalse(
            "a row-bearing section really folds",
            SettingsDisclosure.expanded("Appearance", setOf("Appearance"))
        )
    }

    @Test
    fun `no two rows share a label, so a query can never be ambiguous`() {
        val labels = SettingsCatalog.entries.map { it.label }
        assertEquals(
            "duplicate labels would make entryFor (and so the section a row reports) a coin toss",
            labels.size, labels.distinct().size
        )
        SettingsCatalog.entries.forEach { entry ->
            assertTrue("every row says its own words", entry.label.isNotBlank())
            assertTrue("every row is filed somewhere", entry.section.isNotBlank())
            assertTrue(
                "keywords are extra words, never a replacement for the label",
                entry.searchable.first() == entry.label
            )
        }
    }
}
