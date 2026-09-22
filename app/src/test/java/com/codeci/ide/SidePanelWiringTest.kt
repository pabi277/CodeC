package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 55 — the side panel is not decoration: this pins the wiring.
 *
 * The pure spec is [com.codeci.ide.SidePanelPlanTest]'s job. This file asks the
 * *source* the questions a phone would ask, in the house style of
 * `DrawerWiringTest`/`GuideWiringTest`/`TokenAdoptionTest`: the panel is really
 * composed by the editor's ☰, the tree really rides in it, the Projects cell
 * really exists (Phase 56's precondition), and — the owner's own row — the
 * bottom bar is still there, five tabs and all.
 */
class SidePanelWiringTest {

    private val editor = RepoFiles.codeOnly(
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
    )
    private val main = RepoFiles.codeOnly(
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/MainActivity.kt").readText()
    )
    private val panel = RepoFiles.codeOnly(
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/components/EditorSidePanel.kt").readText()
    )

    @Test
    fun `the editor's drawer hosts the side panel, and the tree rides in its Files slot`() {
        assertTrue("the panel must be composed in drawerContent", editor.contains("EditorSidePanel("))
        assertTrue("the tree must stay inside the panel", editor.contains("files = {"))
        assertTrue(
            "the project tree itself must still be the drawer component",
            editor.contains("EditorProjectDrawer(")
        )
    }

    @Test
    fun `the five rail slots come from the pure plan, never a hand-written list in the panel`() {
        assertTrue(panel.contains("SidePanelPlan.RAIL.forEach"))
        assertTrue(panel.contains("SidePanelPlan.isWired(slot)"))
        assertTrue(panel.contains("SidePanelPlan.CARD"))
    }

    @Test
    fun `the reserved slot draws no panel`() {
        assertTrue("the reserved slot must fall through", panel.contains("RailPanel.RESERVED -> Unit"))
    }

    @Test
    fun `Projects in the card opens the Projects screen - Phase 56's precondition`() {
        assertTrue(
            "the card's Projects cell must call the hub door",
            editor.contains("NavCell.PROJECTS ->") && editor.contains("onOpenProjectsHub()")
        )
        assertTrue(
            "MainActivity must supply that door as the plain Projects route",
            main.contains("onOpenProjectsHub = {") &&
                main.contains("Screen.FileManager.createRoute()")
        )
        assertTrue("the Packages cell must have a door too", main.contains("onOpenPackages = {"))
    }

    @Test
    fun `the guide's in-drawer beats switch the panel to Files`() {
        assertTrue(
            "the editor must ask the pure plan which beat is due",
            editor.contains("CoachMarkPlan.step(guideBeat)?.inDrawer == true")
        )
        assertTrue(
            "the beat must be published by the guide host",
            RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/guide/CoachMarks.kt")
                .readText().contains("EditorChromeState.setGuideBeat(")
        )
    }

    @Test
    fun `the search slot runs the pure engine, off the main thread`() {
        assertTrue(editor.contains("ProjectSearch.search(root, query, searchOptions)"))
        assertTrue("the walk must not run on the UI thread", editor.contains("Dispatchers.IO"))
    }

    @Test
    fun `the bottom bar survives Phase 55 - five tabs, the handle, hide-while-typing`() {
        // The owner, 2026-09-22: “only removing the project option is ok”. Phase
        // 55 adds a panel; it removes nothing from the bar.
        assertTrue("FlatBottomBar must still exist", main.contains("private fun FlatBottomBar("))
        assertTrue("and must still be composed", main.contains("FlatBottomBar("))
        assertTrue("the reveal handle stays", main.contains("EditorNavRevealHandle("))
        assertTrue("hide-while-typing stays", main.contains("NavBarPolicy.hideNavBar("))
        // The tab list itself: Phase 55 changes it NOT AT ALL (five options).
        assertTrue(
            "the editor tab must not be the first bar option yet — that is Phase 56",
            main.contains("val screens = listOf(")
        )
        assertFalse(
            "Phase 56 has not happened yet: Projects must still be a bottom tab",
            main.contains("val screens = listOf(\n        Screen.Editor,")
        )
    }

    @Test
    fun `the panel carries none of the refused shop chrome`() {
        val lowered = panel.lowercase()
        listOf("upgrade", "credits", "discover", "my labs", "change log", "account").forEach { refused ->
            assertFalse("the panel must not carry \"$refused\"", lowered.contains(refused))
        }
    }
}
