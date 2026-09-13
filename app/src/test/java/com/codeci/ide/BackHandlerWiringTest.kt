package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 49.1 — the one-table pin (PART_49_1 §Tests): every `BackHandler(`
 * in the app is either MainActivity's ROOT handler or routes through
 * `BackRouter.decide(`. A future screen cannot add an ad-hoc handler that
 * contradicts the precedence table — it fails here first.
 */
class BackHandlerWiringTest {

    private fun sources(): List<File> = RepoFiles.mainKotlinSources()

    private fun source(relative: String): String =
        RepoFiles.mainSource(relative).readText()

    @Test
    fun `every handler file routes through the router`() {
        val handlers = sources().filter { it.readText().contains("BackHandler(") }
        val offenders = handlers.filter { it.name != "MainActivity.kt" }
            .filter { !it.readText().contains("BackRouter.decide(") }
        assertTrue(
            "ad-hoc back handlers outside the router: ${offenders.map { it.name }}",
            offenders.isEmpty()
        )
        // The full list is exactly the four surfaces 49 wired.
        assertEquals(
            listOf(
                "FileManagerScreen.kt", "GuideScreen.kt",
                "MainActivity.kt", "EditorScreen.kt"
            ).sorted(),
            handlers.map { it.name }.sorted()
        )
    }

    @Test
    fun `MainActivity has exactly one handler - the root`() {
        val main = source("app/src/main/java/com/codeci/ide/MainActivity.kt")
        assertEquals(
            1,
            Regex("\\bBackHandler\\s*\\(").findAll(main).count()
        )
        // 49.2 — the prompt-up state keeps the root handler OFF (the
        // dialog's own back is the second press; one exit path only).
        assertTrue(main.contains("BackHandler(enabled = !exitPromptVisible &&"))
        assertTrue(main.contains("BackRouter.isRoot("))
        assertTrue(main.contains("screens.map { it.route }"))
        // The three actions the root may perform.
        assertTrue(main.contains("BackAction.PopRoute -> navController.popBackStack()"))
        assertTrue(main.contains("BackAction.ShowExitPrompt -> exitPromptVisible = true"))
        assertTrue(main.contains("BackAction.ExitApp -> activity.finish()"))
    }

    @Test
    fun `EditorScreen has one router-driven handler - the interim drawer handler is gone`() {
        val editor = source("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt")
        assertEquals(
            1,
            Regex("\\bBackHandler\\s*\\(").findAll(editor).count()
        )
        assertFalse(
            "47.1's interim handler must be folded into the router, not kept beside it",
            editor.contains("BackHandler(enabled = drawerState.isOpen)")
        )
        assertTrue(editor.contains("BackRouter.decide("))
        assertTrue(editor.contains("BackAction.CloseEditorDrawer -> closeDrawer(DrawerCloseReason.BACK)"))
        assertTrue(editor.contains("BackAction.ShowUnsavedDialog -> showUnsavedDialog = true"))
        assertTrue(editor.contains("BackAction.CloseFindBar -> viewModel.hideFind()"))
        assertTrue(editor.contains("BackAction.CollapseOutputPanel -> viewModel.toggleOutput()"))
        // H2 — the drawer row keys on targetValue, in the state AND in the
        // one close callback.
        assertTrue(editor.contains("editorDrawerOpen = drawerState.targetValue == DrawerValue.Open"))
        assertTrue(
            editor.contains("DrawerPolicy.shouldClose(reason, drawerState.targetValue == DrawerValue.Open)")
        )
    }

    @Test
    fun `FileManagerScreen closes the tree and never the app`() {
        val hub = source("app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt")
        assertEquals(
            1,
            Regex("\\bBackHandler\\s*\\(").findAll(hub).count()
        )
        assertTrue(hub.contains("hubProjectOpen = activeProject != null"))
        assertTrue(hub.contains("BackAction.CloseHubProject ->"))
        assertTrue(hub.contains("viewModel.closeProject()"))
    }

    @Test
    fun `GuideScreen leaves one level through the router`() {
        val guide = source("app/src/main/java/com/codeci/ide/ui/guide/GuideScreen.kt")
        assertTrue(guide.contains("BackRouter.decide(BackState(canPopRoute = true))"))
        assertTrue(guide.contains("BackAction.PopRoute -> onFinished()"))
    }

    @Test
    fun `the tour overlay still has no back handler of its own`() {
        // The 45.2 round-2 law (owner: back must not end the tour) — the
        // coach-mark row the 49 spec sketched was deliberately not built
        // (see PART_49_1's implementation record); GuideWiringTest pins the
        // overlay side, this pins that no close-the-mark row came back.
        val overlay = source("app/src/main/java/com/codeci/ide/ui/guide/CoachMarks.kt")
        assertFalse(overlay.contains("BackHandler("))
    }

    @Test
    fun `the exit prompt has a second door in Settings`() {
        // 49.2 — the compensation for cause C (a home swipe sends no back
        // event): the same dialog, reachable on demand.
        val settings = source("app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt")
        assertTrue(settings.contains("onShowExitPrompt: () -> Unit = {}"))
        assertTrue(settings.contains("Tell us before you go"))
        assertTrue(settings.contains("onClick = { onShowExitPrompt() }"))
        val main = source("app/src/main/java/com/codeci/ide/MainActivity.kt")
        assertTrue(main.contains("onShowExitPrompt = { exitPromptVisible = true }"))
    }
}
