package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 59 — the hub's two new things are wired, and wired to the policy.
 *
 * The pure decisions live in `ProjectMarks` / `ProjectsHub` (pinned by `ProjectMarkTest` and
 * `ProjectsHubTest`, which run on a host JVM). What a host JVM cannot see is the *screen*:
 *
 *  1. the filter row really is the spec's `All · Recent · Create`, and *Create* really opens the
 *     hub's existing add sheet (a chip that does nothing would be the dead control this app
 *     refuses to draw);
 *  2. the language chips really left the row (the owner's answer of 2026-09-22) while the
 *     language *policy* stayed intact, so the removal is a row change and not a feature removal;
 *  3. the card's leading square really is the name-derived mark — every seat reaches a real
 *     palette colour, and the kind glyph it replaced is really gone;
 *  4. the recency the *Recent* filter ranks by really comes off disk (the ViewModel passes the
 *     folder's own clock), and the ranking really is the side panel's own implementation.
 *
 * The pins that need string literals read the raw text; the mechanism pins read `codeOnly`.
 */
class ProjectMarkWiringTest {

    private val hub = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/projects/ProjectsHub.kt")
        .readText()
    private val hubCode = RepoFiles.codeOnly(hub)

    private val markPolicy = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/projects/ProjectMark.kt"
    ).readText()

    private val iconView = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/components/ProjectIconView.kt"
    ).readText()
    private val iconViewCode = RepoFiles.codeOnly(iconView)

    private val screen = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt"
    ).readText()
    private val screenCode = RepoFiles.codeOnly(screen)

    private val viewModel = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/viewmodels/FileManagerViewModel.kt"
    ).readText()

    private val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()

    private fun stringValue(name: String): String {
        val m = Regex("<string name=\"$name\">([^<]+)</string>").find(strings)
        checkNotNull(m) { "strings.xml has no entry '$name'" }
        return m.groupValues[1]
    }

    // ---- 1. the row is the spec's, and Create is real ------------------------

    @Test
    fun `the filter row is All, Recent and Create - and nothing else`() {
        val chips = Regex("""HubFilterChip\(ProjectHubFilter\.(\w+)""").findAll(screenCode)
            .map { it.groupValues[1] }.toList()
        assertEquals("the row's filters, in order", listOf("ALL", "RECENT"), chips)
        assertTrue(
            "Recent's label is the chip's own string",
            screen.contains("stringResource(R.string.hub_filter_recent)")
        )
        assertEquals("Recent", stringValue("hub_filter_recent"))
        assertEquals("Create", stringValue("hub_filter_create"))
        // The owner's answer of 2026-09-22 (PHASE59_63_UI_PARITY_ROADMAP §3, the chip-row
        // question) made the row the spec's literal three: the language chips are not drawn.
        listOf("hub_filter_git", "hub_filter_c", "hub_filter_python", "hub_filter_web").forEach { name ->
            assertFalse(
                "the language chips left the visible row - '$name' must not be drawn there",
                screen.contains("stringResource(R.string.$name)")
            )
        }
    }

    @Test
    fun `Create opens the sheet the plus button opens`() {
        assertTrue(
            "the chip is an ACTION chip, not a filter value",
            screenCode.contains(
                "HubActionChip(stringResource(R.string.hub_filter_create), Icons.Default.Add, onCreate)"
            )
        )
        assertTrue(
            "and the action is the hub's own add sheet",
            screenCode.contains("onCreate = { showHubSheet = true }")
        )
        assertTrue(
            "the list hands the screen's action down instead of inventing a second one",
            screenCode.contains("onCreate = onCreate")
        )
        assertTrue(
            "the action chip shares the filter chips' shell (same shape, spacing and tap area)",
            screenCode.contains("private fun HubActionChip(") &&
                screenCode.contains("RoundedCornerShape(50)")
        )
    }

    // ---- 2. the language policy survived the row change ----------------------

    @Test
    fun `the language filters are still policy - only their chips are gone`() {
        assertTrue(
            "the enum still carries them (and the row's order is documented in one place)",
            hubCode.contains("enum class ProjectHubFilter { ALL, RECENT, GIT, C, PYTHON, WEB }")
        )
        assertTrue(
            "an entry still claims its chips",
            hubCode.contains("val filters: Set<ProjectHubFilter>")
        )
        assertTrue(
            "and the filter function still honours them",
            hubCode.contains("result = result.filter { it.filters.contains(filter) }")
        )
    }

    // ---- 3. the mark is name-derived, and the kind moved to the subtitle -----

    @Test
    fun `the card's square is the project's own mark`() {
        assertTrue(
            "the view asks the policy, keyed by the name",
            iconViewCode.contains("remember(entry.name) { ProjectMarks.mark(entry.name) }")
        )
        assertTrue("and draws the initials it was given", iconViewCode.contains("text = mark.initials"))
        assertTrue(
            "the kind glyph it replaced is gone (no FileIcon anywhere in the mark any more)",
            !iconViewCode.contains("FileIcon") && !iconViewCode.contains("entry.icon")
        )
        // Every seat must reach a real palette colour: five seats, five distinct constants.
        val seats = Regex("""MarkSeat\.(\w+) -> Color\(CodecPalette\.(TILE_\w+)\)""")
            .findAll(iconViewCode).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertEquals("every seat is mapped", 5, seats.size)
        assertEquals(
            "and each to a different tile colour",
            5, seats.map { it.second }.distinct().size
        )
        assertTrue(
            "white-on-tile contrast is the palette's own measured pairing (Phase 50.1)",
            iconViewCode.contains("color = Color.White")
        )
    }

    @Test
    fun `the kind the mark used to draw is on the card's own line now`() {
        assertTrue(
            "the subtitle leads with the kind",
            hubCode.contains("add(kindLabel(entry.kind))")
        )
        assertTrue(
            "every kind has a word",
            hubCode.contains("fun kindLabel(kind: ProjectHubKind): String")
        )
        assertFalse(
            "and the dead kind→glyph table really is gone",
            hubCode.contains("HubIconToken") || hubCode.contains("iconLabel")
        )
    }

    // ---- 4. recency comes off disk, and Recent is the panel's own list -------

    @Test
    fun `Recent ranks by the folder's own clock, read from disk`() {
        assertTrue(
            "the entry carries the folder's time",
            hubCode.contains("val folderModified: Long = 0L")
        )
        assertTrue(
            "with one definition of what recent means",
            hubCode.contains("val recency: Long get() = if (folderModified > 0L) folderModified else lastModified")
        )
        assertTrue(
            "the scan reports it separately from the newest file",
            hubCode.contains("val folderModified: Long = lastModified") &&
                hubCode.contains("return ScanResult(count, newest, folderModified)")
        )
        assertTrue(
            "and the ViewModel passes it into the entry",
            viewModel.contains("folderModified = scan.folderModified")
        )
    }

    @Test
    fun `the hub's Recent list is the side panel's rule, not a second one`() {
        assertTrue(
            "one implementation: the panel's own builder does the ranking",
            hubCode.contains("RecentProjects.build(")
        )
        assertTrue(
            "and its cap is the panel's own constant",
            hubCode.contains("limit: Int = RecentProjects.MAX_ROWS")
        )
        assertTrue(
            "the Recent filter asks that one list",
            hubCode.contains("val recent = recentEntries(entries).map { it.name }.toSet()")
        )
        assertTrue(
            "and the search still combines with it",
            hubCode.contains("result = result.filter { it.name.lowercase().contains(needle) }") ||
                hubCode.contains("it.name.lowercase().contains(needle)")
        )
        assertTrue(
            "the policy file itself is the only place the mark is decided",
            markPolicy.contains("fun initials(name: String): String") &&
                markPolicy.contains("fun seat(name: String): MarkSeat") &&
                markPolicy.contains("fun mark(name: String): ProjectMark")
        )
    }
}
