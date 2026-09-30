package com.codeci.ide

import com.codeci.ide.ui.navigation.BackAction
import com.codeci.ide.ui.navigation.BackRouter
import com.codeci.ide.ui.navigation.BackState
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
        // The full list is the five surfaces that now use the shared policy.
        assertEquals(
            listOf(
                "FileManagerScreen.kt",
                "MainActivity.kt", "EditorScreen.kt", "FirstRunIntroScreen.kt",
                "WebPreviewScreen.kt"
            ).sorted(),
            handlers.map { it.name }.sorted()
        )
    }

    @Test
    fun `WebPreviewScreen routes system and toolbar Back through WebView history first`() {
        val preview = source("app/src/main/java/com/codeci/ide/ui/screens/WebPreviewScreen.kt")
        assertEquals(
            1,
            Regex("\\bBackHandler\\s*\\(").findAll(preview).count()
        )
        assertTrue(preview.contains("BackRouter.decide("))
        assertTrue(preview.contains("webViewCanGoBack = webView?.canGoBack() == true"))
        assertTrue(preview.contains("BackAction.GoBackInWebView -> webView?.goBack()"))
        assertTrue(preview.contains("BackAction.PopRoute -> onNavigateBack()"))
        assertTrue(preview.contains("performPreviewBack(fromToolbar = true)"))
        assertTrue(preview.contains("performPreviewBack(fromToolbar = false)"))
        assertTrue(preview.contains("BackHandler(enabled = !previewTransientSurfaceOpen && imeDp <= 0f)"))
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
        // Phase 77.2 — the AI sheet is a router row, not a second handler.
        assertTrue(editor.contains("aiSheetOpen = aiSheetOpen"))
        assertTrue(editor.contains("BackAction.CollapseAiSheet -> aiViewModel.sheetEvent(AiSheetEvent.BACK)"))
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

    /**
     * Phase 66.1 follow-up — owner, 2026-09-27: *"when i go to projects and
     * press back it's showing options of close the app but i want previous
     * editor page than if i press back it will be back"*.
     *
     * Projects left the bottom bar in Phase 56, but its two doors from the
     * editor still navigated tab-style (`popUpTo(start) { saveState }`), which
     * POPS the editor whenever the hub is the start destination (the resume
     * card: any launch after more than five minutes away). With nothing under
     * the hub, Back reached the root exit prompt. The doors now push the
     * hub over the editor like the side panel's Settings cell, so row 11
     * (`canPopRoute -> PopRoute`) returns to the editor and the prompt is the
     * NEXT Back. The router itself did not change.
     */
    @Test
    fun `Projects opened from the editor is a page over it - Back returns to the editor`() {
        val main = RepoFiles.codeOnly(source("app/src/main/java/com/codeci/ide/MainActivity.kt"))
        for (door in listOf("onOpenProjectsHub = {", "onOpenProjects = {")) {
            val at = main.indexOf(door)
            assertTrue("the $door door is gone", at >= 0)
            val window = main.substring(at, main.indexOf("},", at) + 2)
            assertTrue("$door must navigate to the hub", window.contains("Screen.FileManager.createRoute("))
            assertFalse(
                "$door must not pop the editor from under the hub (tab-style popUpTo)",
                window.contains("popUpTo(")
            )
            assertTrue("$door stays single-top", window.contains("launchSingleTop = true"))
        }
        // The precedent it now matches: the Settings door pushes without popUpTo.
        val settingsDoor = main.substring(main.indexOf("onOpenSettings = {"))
            .substringBefore("},")
        assertFalse(settingsDoor.contains("popUpTo("))
        // Packages IS still a tab and keeps the tab idiom — the change is Projects only.
        val packagesDoor = main.substring(main.indexOf("onOpenPackages = {")).substringBefore("},")
        assertTrue(packagesDoor.contains("popUpTo(navController.graph.findStartDestination().id) { saveState = true }"))
        // And the row that now answers: something below the hub pops to it,
        // before the root row can prompt.
        assertEquals(
            BackAction.PopRoute,
            BackRouter.decide(BackState(canPopRoute = true, atRootDestination = true))
        )
        assertEquals(
            BackAction.ShowExitPrompt,
            BackRouter.decide(BackState(canPopRoute = false, atRootDestination = true))
        )
    }
}
