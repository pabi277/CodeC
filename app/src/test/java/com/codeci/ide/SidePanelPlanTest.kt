package com.codeci.ide

import com.codeci.ide.ui.editor.NavCell
import com.codeci.ide.ui.editor.RailPanel
import com.codeci.ide.ui.editor.RecentProjects
import com.codeci.ide.ui.editor.SidePanelPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 55 — the side panel's decisions, pinned on the host JVM.
 *
 * These are not style checks: every case below is a sentence out of the
 * reference card (`docs/chat-phase54/PART_54_1_SHOTS.md`) or an owner row of
 * 2026-09-22, turned into something that fails loudly if a later phase drifts.
 */
class SidePanelPlanTest {

    // ---- the rail ----------------------------------------------------------

    @Test
    fun `the rail is the shot's five slots in the shot's order`() {
        assertEquals(
            listOf("navigation", "files", "search", "repository", "reserved"),
            SidePanelPlan.RAIL.map { it.id }
        )
        // The order is the reading order of Screenshot_20260922_124049/122203/124052/124055/124058:
        // triangle · folder · search-with-<> · branch · person.
        assertEquals(RailPanel.NAVIGATION, SidePanelPlan.RAIL.first())
        assertEquals(RailPanel.REPOSITORY, SidePanelPlan.RAIL[3])
    }

    @Test
    fun `four rail slots are wired and the fifth is the owner's reserved AI slot`() {
        assertEquals(4, SidePanelPlan.WIRED.size)
        assertFalse(SidePanelPlan.isWired(RailPanel.RESERVED))
        // “Reseserve it i have plan for ai i can use that” (owner, 2026-09-22) —
        // a reserved slot that opened a shop or a fake account would be a lie.
        assertTrue(SidePanelPlan.WIRED.all { it != RailPanel.RESERVED })
        assertEquals(RailPanel.NAVIGATION, SidePanelPlan.DEFAULT_PANEL)
    }

    @Test
    fun `the panel is not full screen - a strip of the editor stays live`() {
        assertTrue(SidePanelPlan.PANEL_WIDTH_FRACTION > 0.75f)
        assertTrue(SidePanelPlan.PANEL_WIDTH_FRACTION < 1f)
    }

    // ---- the navigation card ----------------------------------------------

    @Test
    fun `the navigation card is one card of three columns by two rows`() {
        assertEquals(2, SidePanelPlan.CARD.size)
        assertEquals(SidePanelPlan.CARD_COLUMNS, SidePanelPlan.CARD[0].size)
        assertEquals(SidePanelPlan.CARD_COLUMNS, SidePanelPlan.CARD[1].size)
        assertEquals(6, SidePanelPlan.CARD.flatten().size)
    }

    @Test
    fun `the top row is the shot's and the bottom row is the owner's answer`() {
        assertEquals(
            listOf(NavCell.PROJECTS, NavCell.EDITOR, NavCell.SETTINGS),
            SidePanelPlan.CARD[0]
        )
        // Owner picked “Terminal · Packages · Guide” for the bottom row; the
        // shot's Discover / My Labs / Change Log are refused outright.
        assertEquals(
            listOf(NavCell.TERMINAL, NavCell.PACKAGES, NavCell.GUIDE),
            SidePanelPlan.CARD[1]
        )
    }

    @Test
    fun `no refused label can appear on the card`() {
        val labels = SidePanelPlan.cardLabels()
        SidePanelPlan.REFUSED_LABELS.forEach { refused ->
            assertFalse("the card must not carry \"$refused\"", labels.any { it.equals(refused, true) })
        }
    }

    @Test
    fun `Projects is a cell - it is the door Phase 56 removes the tab for`() {
        assertEquals(NavCell.PROJECTS, SidePanelPlan.cellFor("projects"))
        assertTrue(SidePanelPlan.cardLabels().contains("Projects"))
    }

    @Test
    fun `the selected cell is Editor, as the shot's raised tile`() {
        assertEquals(NavCell.EDITOR, SidePanelPlan.SELECTED_CELL)
    }

    @Test
    fun `an unknown cell id resolves to nothing rather than a wrong room`() {
        assertNull(SidePanelPlan.cellFor("discover"))
        assertNull(SidePanelPlan.cellFor(""))
        assertNull(SidePanelPlan.panelFor("account"))
        assertEquals(RailPanel.SEARCH, SidePanelPlan.panelFor("search"))
    }

    @Test
    fun `the edge gesture opens the panel only past the threshold`() {
        assertFalse(SidePanelPlan.shouldOpenPanel(0f))
        assertFalse(SidePanelPlan.shouldOpenPanel(SidePanelPlan.OPEN_GESTURE_DP - 0.5f))
        assertTrue(SidePanelPlan.shouldOpenPanel(SidePanelPlan.OPEN_GESTURE_DP))
        assertTrue(SidePanelPlan.shouldOpenPanel(80f))
    }

    // ---- recent -------------------------------------------------------------

    @Test
    fun `recent rows are newest first and capped`() {
        val now = 1_000_000_000L
        // Entry 0 was opened just now; 1..11 walk back a minute each (one is dropped by the cap).
        val entries = (0..11).map {
            RecentProjects.Entry(id = "p$it", name = "Project $it", lastOpenedMillis = now - it * 60_000L)
        }
        val rows = RecentProjects.build(entries, now, privateRoot = null, externalRoot = null)
        assertEquals(RecentProjects.MAX_ROWS, rows.size)
        assertEquals("Project 0", rows.first().name)
        assertEquals("just now", rows.first().ageLabel)
        assertEquals("1 minute ago", rows[1].ageLabel)
        assertEquals("7 minutes ago", rows[7].ageLabel)
        // The cap is the newest eight, never the oldest eight.
        assertTrue(rows.none { it.name == "Project 11" })
    }

    @Test
    fun `an entry with no known time sorts last, never first`() {
        val now = 1_000_000_000L
        val rows = RecentProjects.build(
            listOf(
                RecentProjects.Entry("unknown", "Unknown", 0L),
                RecentProjects.Entry("known", "Known", now - 60_000L)
            ),
            now,
            privateRoot = null,
            externalRoot = null
        )
        assertEquals("Known", rows.first().name)
    }

    @Test
    fun `the external badge is data-driven, not decoration`() {
        val priv = "/data/user/0/com.codeci.ide/files/CodeC/projects"
        val ext = "/storage/emulated/0/Android/data/com.codeci.ide/files/CodeC/projects"
        assertTrue(RecentProjects.isExternal("$ext/1st semester", priv, ext))
        assertFalse(RecentProjects.isExternal("$priv/1st semester", priv, ext))
        // Unknown is never external — the shot's always-on badge is refused.
        assertFalse(RecentProjects.isExternal(null, priv, ext))
        assertFalse(RecentProjects.isExternal("$priv/x", priv, null))
        assertFalse(RecentProjects.isExternal("", priv, ext))
        assertFalse(RecentProjects.isExternal("/sdcard/Download/x", priv, ext))
    }

    @Test
    fun `age labels read like the shot's`() {
        assertEquals("just now", RecentProjects.ageLabel(0L))
        assertEquals("1 minute ago", RecentProjects.ageLabel(60_000L))
        assertEquals("18 minutes ago", RecentProjects.ageLabel(18 * 60_000L))
        assertEquals("1 hour ago", RecentProjects.ageLabel(60 * 60_000L))
        assertEquals("yesterday", RecentProjects.ageLabel(26 * 60 * 60_000L))
        assertEquals("1 week ago", RecentProjects.ageLabel(8L * 24 * 60 * 60_000L))
        assertEquals("2 weeks ago", RecentProjects.ageLabel(17L * 24 * 60 * 60_000L))
        assertEquals("1 month ago", RecentProjects.ageLabel(35L * 24 * 60 * 60_000L))
    }
}
