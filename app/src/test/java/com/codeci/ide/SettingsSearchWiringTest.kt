package com.codeci.ide

import com.codeci.ide.ui.settings.SettingsCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 62 — the Settings search and the collapsible headers are wired, and the
 * policy is what decides.
 *
 * The pure rules live in `SettingsSearch` / `SettingsDisclosure` / `SettingsCatalog`
 * (pinned by `SettingsSearchPolicyTest`); what a host JVM cannot see is the *screen*.
 * These pins read `SettingsScreen.kt` and hold the four things that make the feature
 * real rather than decorative:
 *
 *  1. the box exists, and its value is the only thing that drives the view state;
 *  2. every control row asks the policy before it draws (`settingsRowVisible`), so a
 *     row cannot slip past the filter;
 *  3. every section header sits inside its own `SettingsSection` wrapper, so a filtered
 *     screen never leaves a preview block floating with no header above it;
 *  4. the header folds only what has rows to fold, says how many it holds, and the empty
 *     state says so when nothing matches.
 *
 * The screen's own kdoc and comments quote these names, so the mechanism pins read
 * `RepoFiles.codeOnly`; the pins that need string literals read the raw text.
 */
class SettingsSearchWiringTest {

    private val raw = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt"
    ).readText()

    private val code = RepoFiles.codeOnly(raw)

    private val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()

    private fun stringValue(name: String): String {
        val m = Regex("<string name=\"$name\">([^<]+)</string>").find(strings)
        checkNotNull(m) { "strings.xml has no entry '$name'" }
        return m.groupValues[1]
    }

    /** Headers as the audit test resolves them: a literal, or a string resource's value. */
    private fun headersIn(text: String): List<String> {
        val re = Regex(
            """SettingsSectionHeader\(\s*(?:"([^"]+)"|stringResource\(com\.codeci\.ide\.R\.string\.(\w+)\))"""
        )
        return re.findAll(text).map { m ->
            m.groupValues[1].ifEmpty { stringValue(m.groupValues[2]) }
        }.toList()
    }

    private val sectionTitles = listOf(
        "Editor Settings", "CodeC Keys", "Compiler", "Terminal", "Terminal Extra-Keys & Shortcuts",
        "Package Repository & Trust", "GitHub Account", "Appearance", "Storage", "About",
        "Feedback & Support", "Developer Options",
    )

    // ---- 1. the box ---------------------------------------------------------

    @Test
    fun `the screen has a search box above its sections`() {
        assertTrue(
            "the box's value is view state, restored on rotation",
            raw.contains("var settingsQuery by rememberSaveable { mutableStateOf(\"\") }")
        )
        assertTrue(
            "and it is the only thing the field is wired to",
            code.contains("SettingsSearchField(query = settingsQuery, onQueryChange = { settingsQuery = it })")
        )
        assertTrue("the field is drawn under the app bar", code.contains("SettingsSearchField("))
        assertTrue(
            "clearing it is offered only when there is something to clear",
            code.contains("if (query.isNotEmpty())")
        )
        assertTrue(
            "and clearing it writes the empty query back (a literal, so this one reads raw)",
            raw.contains("IconButton(onClick = { onQueryChange(\"\") })")
        )
        assertTrue(
            "the ✕ names itself for a screen reader",
            code.contains("com.codeci.ide.R.string.settings_search_clear")
        )
        assertEquals("Search settings", stringValue("settings_search_hint"))
        assertEquals("Clear search", stringValue("settings_search_clear"))
    }

    // ---- 2. one decision per row, asked by the five helpers ------------------

    @Test
    fun `all five row helpers ask the policy before they draw`() {
        val helpers = listOf(
            "SettingsSwitch", "SettingsSlider", "SettingsDropdown", "SettingsItem", "SettingsAction",
        )
        for (fn in helpers) {
            assertTrue(
                "$fn must ask before it draws",
                Regex("fun $fn\\([\\s\\S]*?\\) \\{\\n\\s*if \\(!settingsRowVisible\\(title\\)\\) return")
                    .containsMatchIn(code),
            )
        }
        assertTrue(
            "and the guard asks the policy, with the state the screen provided",
            code.contains("SettingsSearch.rowVisible(view.query, title, view.folded)")
        )
        assertEquals(
            "five guards and their one definition - no row may skip the question",
            6, Regex("settingsRowVisible\\(").findAll(code).count()
        )
    }

    // ---- 3. sections hide as units ------------------------------------------

    @Test
    fun `every header sits inside its own section wrapper`() {
        val opens = Regex("""SettingsSection\("([^"]+)"\) \{""").findAll(raw)
            .map { it.groupValues[1] to it.range.first }.toList()
        assertEquals("twelve sections", 12, opens.size)
        assertEquals("the screen's sections, in order", sectionTitles, opens.map { it.first })
        assertEquals(
            "and the catalog knows every section the screen draws",
            SettingsCatalog.allSectionTitles, sectionTitles
        )

        // Slice the file by wrapper: each slice must hold exactly its own header, so a
        // section (or its bespoke preview block) cannot escape the filter.
        val slices = opens.indices.map { i ->
            raw.substring(opens[i].second, if (i + 1 < opens.size) opens[i + 1].second else raw.length)
        }
        slices.forEachIndexed { i, slice ->
            assertEquals(
                "section '${opens[i].first}' must wrap exactly its own header",
                listOf(opens[i].first), headersIn(slice)
            )
        }
        assertTrue(
            "the wrapper is the policy's own question",
            code.contains("if (!SettingsSearch.sectionVisible(LocalSettingsView.current.query, title)) return")
        )
    }

    // ---- 4. folding ---------------------------------------------------------

    @Test
    fun `a header folds its section, counts it, and only when there is something to fold`() {
        assertTrue(
            "only a section that holds rows is foldable",
            code.contains("val foldable = SettingsCatalog.isControlSection(title)")
        )
        assertTrue(
            "so a form-only section is not given a tap that hides nothing",
            code.contains(".then(if (foldable) Modifier.clickable { view.onToggleSection(title) } else Modifier)")
        )
        assertTrue(
            "the fold state is remembered across a rotation and re-parsed from one string",
            raw.contains("var foldedSectionsCsv by rememberSaveable { mutableStateOf(\"\") }") &&
                code.contains("SettingsDisclosure.parse(foldedSectionsCsv)") &&
                code.contains("SettingsDisclosure.serialize(") &&
                code.contains("SettingsDisclosure.toggle(foldedSections, section)")
        )
        assertTrue(
            "the chevron is the app's own fold idiom (the project drawer's)",
            code.contains("if (folded) Icons.Default.KeyboardArrowRight else Icons.Default.ExpandMore")
        )
        assertTrue(
            "a folded header still says how much it is holding",
            code.contains("SettingsSearch.sectionMatchCount(view.query, title).toString()")
        )
        assertTrue(
            "and while the user is filtering it says how many rows answered",
            code.contains("if (folded || SettingsSearch.isActive(view.query))")
        )
        assertEquals("Expand section", stringValue("settings_section_expand"))
        assertEquals("Collapse section", stringValue("settings_section_collapse"))
    }

    // ---- 5. the honest empty state ------------------------------------------

    @Test
    fun `nothing matched is said, not shown as blank space`() {
        assertTrue(
            "the message is the policy's empty result, not a guess",
            code.contains("if (SettingsSearch.isEmptyResult(settingsQuery)) {")
        )
        assertTrue(code.contains("SettingsNoMatch(settingsQuery)"))
        assertTrue(
            "and it reads the query back to the user",
            code.contains("com.codeci.ide.R.string.settings_no_match, query")
        )
        assertTrue(
            "the empty state is the app's own: a lens and a line, centred",
            code.contains("private fun SettingsNoMatch(") &&
                code.contains("CodecTokens.space(Space.XXL)") &&
                code.contains("com.codeci.ide.R.string.settings_no_match")
        )
        assertTrue(stringValue("settings_no_match").startsWith("No settings match"))
    }

    // ---- 6. the state the policy is asked about -----------------------------

    @Test
    fun `the screen hands the policy its own query and folds, and nothing else`() {
        assertTrue(
            "one value, built from the query and the fold set",
            code.contains("SettingsViewState(") &&
                code.contains("query = settingsQuery,") &&
                code.contains("folded = foldedSections,")
        )
        assertTrue(
            "provided for the one screen that reads it",
            code.contains("CompositionLocalProvider(LocalSettingsView provides settingsView) {")
        )
        assertEquals(
            "and provided exactly once",
            1, Regex("LocalSettingsView provides").findAll(code).count()
        )
        assertTrue(
            "and the catalog is the policy's own, never a copy",
            code.contains("SettingsSearch.rowVisible(") || code.contains("SettingsSearch.sectionVisible(")
        )
    }
}
