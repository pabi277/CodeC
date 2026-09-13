package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 47.1 — the wiring pins (PART_47_1 §Tests, the SettingsAuditTest
 * shape): the retired "Open folder" picker cannot come back, the drawer has
 * its ✕ and its in-drawer PROJECTS list, and Back closes the drawer.
 */
class DrawerWiringTest {

    private fun source(relative: String): String =
        RepoFiles.mainSource(relative).readText()

    private val editor: String
        get() = source("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt")

    private val drawer: String
        get() = source("app/src/main/java/com/codeci/ide/ui/components/EditorProjectDrawer.kt")

    @Test
    fun `no dialog anywhere is titled Open folder - the string is gone from the app`() {
        val all = RepoFiles.mainKotlinSources().joinToString("\n") { it.readText() }
        val stringsXml = source("app/src/main/res/values/strings.xml")
        assertFalse(
            "the Open-folder dialog title must not exist in code",
            all.contains("\"Open folder\"")
        )
        assertFalse(
            "the Open-folder dialog title must not exist in resources",
            stringsXml.contains(">Open Folder<") || stringsXml.contains(">Open folder<")
        )
        assertFalse(
            "the retired picker identifier must not exist",
            all.contains("showContextPicker")
        )
    }

    @Test
    fun `the drawer header carries the close button and it only closes`() {
        assertTrue(
            "EditorProjectDrawer must take an onClose callback",
            drawer.contains("onClose: () -> Unit")
        )
        assertTrue(
            "the ✕ must be a real icon in the header",
            drawer.contains("Icons.Default.Close")
        )
        assertTrue(
            "EditorScreen must wire onClose to the policy's CLOSE_BUTTON",
            editor.contains("onClose = { closeDrawer(DrawerCloseReason.CLOSE_BUTTON) }")
        )
    }

    @Test
    fun `Back closes the drawer inside the editor`() {
        // Phase 49.1 — 47.1's interim one-line handler is FOLDED INTO the
        // BackRouter (one table for every back press in the app); the pin
        // moves with it: the editor's handler still closes the drawer and
        // still routes through the one close callback with BACK, but the
        // DECISION now comes from BackRouter.decide (pinned mechanically by
        // BackHandlerWiringTest — every handler in the app is either the
        // root or router-driven).
        assertTrue(
            "the editor's handler must close the drawer through the router",
            editor.contains("BackAction.CloseEditorDrawer -> closeDrawer(DrawerCloseReason.BACK)")
        )
        assertTrue(
            "the drawer row must key on targetValue (a back press inside the open animation still closes)",
            editor.contains("editorDrawerOpen = drawerState.targetValue == DrawerValue.Open")
        )
        assertTrue(
            "the one close callback must ask the policy with the same targetValue semantics",
            editor.contains("DrawerPolicy.shouldClose(reason, drawerState.targetValue == DrawerValue.Open)")
        )
    }

    @Test
    fun `the PROJECTS section exists with its callbacks`() {
        assertTrue(
            "the drawer takes the built rows",
            drawer.contains("projects: List<DrawerProjectList.Row>")
        )
        assertTrue(
            "the header tap expands the list (the retired dialog's entry point)",
            editor.contains("onSwitchProject = { drawerProjectsExpanded = !drawerProjectsExpanded }")
        )
        assertTrue(
            "a pick routes through switchContext - one code path, two entry points",
            editor.contains("viewModel.switchContext(context, contextName)")
        )
        assertTrue(
            "the + New project row hands off to the Projects tab",
            drawer.contains("R.string.editor_drawer_new_project") &&
                editor.contains("onOpenProjects()")
        )
    }

    @Test
    fun `the hub route carries the optional openSheet arg and the tab tap stays plain`() {
        val screen = source("app/src/main/java/com/codeci/ide/ui/navigation/Screen.kt")
        val main = source("app/src/main/java/com/codeci/ide/MainActivity.kt")
        assertTrue(
            "Screen.FileManager exposes createRoute(openAddSheet)",
            screen.contains("file_manager?openSheet={openSheet}") &&
                screen.contains("fun createRoute(openAddSheet: Boolean = false)")
        )
        assertTrue(
            "MainActivity reads the flag into openAddSheet",
            main.contains("openAddSheet = it.arguments?.getString(\"openSheet\") == \"1\"")
        )
        assertTrue(
            "the bottom bar never navigates the literal pattern",
            main.contains("is Screen.FileManager -> Screen.FileManager.createRoute()")
        )
    }

    @Test
    fun `the New-project hand-off is an instruction - it restores nothing`() {
        // 47.1 device round: restoreState = true let the navigation come back
        // with a saved sub-stack whose top was an EDITOR (the owner saw the
        // drawer close and the editor "just open"), or a plain hub entry with
        // no openSheet arg. The row must land a FRESH Projects instance.
        val main = source("app/src/main/java/com/codeci/ide/MainActivity.kt")
        val at = main.indexOf("onOpenProjects = {")
        assertTrue("the onOpenProjects wiring is gone", at >= 0)
        val window = main.substring(at, main.indexOf("},", at) + 2)
        assertTrue(
            "the + New project navigation must be restoreState = false",
            window.contains("createRoute(openAddSheet = true)") &&
                window.contains("restoreState = false")
        )
        assertFalse(
            "the broken restoreState = true must be gone from this row",
            window.contains("restoreState = true")
        )
    }
}
