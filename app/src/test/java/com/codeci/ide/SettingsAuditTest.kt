package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 38.2 — pins `docs/chat-phase38/SETTINGS_AUDIT.md` to the code:
 * the audit table cannot silently go stale. A control added to
 * SettingsScreen without an audit row fails here; a row deleted from
 * the doc without deleting the control fails here.
 *
 * Counting rule (kept boring and explicit): "controls" are the call
 * sites of the five Settings* composables in SettingsScreen.kt (the
 * definitions at the bottom of the file are excluded); "sections" are
 * the `SettingsSectionHeader` titles in screen order, with
 * `stringResource` titles resolved from the real strings.xml.
 */
class SettingsAuditTest {

    private val settingsScreen: String
        get() = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt"
        ).readText()

    private val auditDoc: String
        get() = RepoFiles.mainSource("docs/chat-phase38/SETTINGS_AUDIT.md").readText()

    private val controlComposables = listOf(
        "SettingsSwitch", "SettingsDropdown", "SettingsSlider", "SettingsItem", "SettingsAction"
    )

    private fun countCalls(source: String, composable: String): Int {
        val all = Regex("\\b$composable\\s*\\(").findAll(source).count()
        val definitions = Regex("fun\\s+$composable\\s*\\(").findAll(source).count()
        return all - definitions
    }

    private fun resolveStringResource(name: String): String {
        val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()
        val m = Regex("<string name=\"$name\">([^<]+)</string>").find(strings)
        checkNotNull(m) { "strings.xml has no entry '$name'" }
        return m.groupValues[1]
    }

    /** Section titles in screen order, literal or resolved from strings.xml. */
    private fun codeSections(): List<String> {
        val re = Regex(
            """SettingsSectionHeader\(\s*(?:"([^"]+)"|stringResource\(com\.codeci\.ide\.R\.string\.(\w+)\))"""
        )
        return re.findAll(settingsScreen).map { m ->
            m.groupValues[1].ifEmpty { resolveStringResource(m.groupValues[2]) }
        }.toList()
    }

    /** The audit doc's control table: rows are `| # | Section | Control | …`. */
    private fun docControlRows(): List<List<String>> =
        auditDoc.lineSequence()
            .filter { it.startsWith("| ") && !it.startsWith("| #") && !it.startsWith("|---") }
            .map { it.trim('|').split('|').map { c -> c.trim() } }
            .filter { cols -> cols.size >= 6 && cols[0].toIntOrNull() != null }
            .toList()

    @Test
    fun `audit doc sections match the screen sections in order`() {
        // The control table alone cannot represent sections with zero
        // Settings* rows (the three card sections), so the doc carries an
        // explicit machine-checked order line:
        //   "Screen order (machine-checked): A | B | C …"
        val manifest = Regex("Screen order \\(machine-checked\\):\\s*(.+)").find(auditDoc)
            ?: error("SETTINGS_AUDIT.md has no 'Screen order (machine-checked):' manifest line")
        val docSections = manifest.groupValues[1].split('|').map { it.trim() }
        assertEquals(
            "SETTINGS_AUDIT.md section list drifted from SettingsScreen.kt",
            codeSections(),
            docSections
        )
        // …and every control row's section must be one of them (in order).
        val rowSections = docControlRows().map { it[1] }
        assertTrue(
            "control rows name sections outside the manifest: " +
                (rowSections - docSections.toSet()),
            rowSections.all { it in docSections }
        )
    }

    @Test
    fun `audit doc row count equals the screen control count`() {
        val codeTotal = controlComposables.sumOf { countCalls(settingsScreen, it) }
        val docTotal = docControlRows().size
        assertEquals(
            "audit table has $docTotal rows but SettingsScreen.kt has $codeTotal controls — " +
                "update docs/chat-phase38/SETTINGS_AUDIT.md in the same commit",
            codeTotal,
            docTotal
        )
    }

    @Test
    fun `audit doc per-section counts equal the code per-section counts`() {
        // Walk the screen top to bottom; each control belongs to the most
        // recent section header above it (index-based, so sections with
        // zero controls still line up).
        val sections = codeSections()
        val controlRe = Regex("""^\s*(${controlComposables.joinToString("|")})\s*\(""")
        val codePerSection = LinkedHashMap<String, Int>()
        var sectionIdx = -1
        for (line in settingsScreen.lineSequence()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("fun ")) continue // composable definitions
            if (trimmed.startsWith("SettingsSectionHeader(")) {
                sectionIdx++
                continue
            }
            if (controlRe.containsMatchIn(line)) {
                val section = sections.getOrNull(sectionIdx) ?: "???"
                codePerSection[section] = (codePerSection[section] ?: 0) + 1
            }
        }
        val docPerSection = docControlRows().groupingBy { it[1] }.eachCount()
        assertEquals(
            "per-section control counts drifted",
            codePerSection,
            docPerSection
        )
    }

    @Test
    fun `the termux bridge card is gone from settings`() {
        assertTrue(
            "the Termux Engine card must not come back (PART_38_2: the fallback stays, the card goes)",
            !settingsScreen.contains("SettingsSectionHeader(\"Termux Engine\"")
        )
        assertTrue(
            "Settings must not shell into Termux (the probe lived in the deleted card)",
            !settingsScreen.contains("TermuxCompiler.")
        )
        assertTrue(
            "the merged compiler section is called exactly 'Compiler'",
            settingsScreen.contains("SettingsSectionHeader(\"Compiler\")")
        )
    }

    @Test
    fun `every audit verdict is keep or an explicit disposition`() {
        // Rows either document a live control ("keep", possibly annotated
        // "keep (dev-only)") or live in the dedicated "Deleted by this
        // audit" table. The control table itself must only contain keeps —
        // anything else means a stale row.
        val nonKeep = docControlRows().filter { !it[6].lowercase().startsWith("keep") }
        assertTrue(
            "control-table rows must all be 'keep' (deletions belong to the Deleted table): $nonKeep",
            nonKeep.isEmpty()
        )
    }
}
