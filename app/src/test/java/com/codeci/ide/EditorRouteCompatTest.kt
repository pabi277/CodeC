package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 46.2 — the route-compatibility pin (PART_46_2 §2): `single=1` is
 * OPT-IN. Every pre-existing route builder keeps producing a PROJECT route
 * (EditorLaunchState, "Open with CodeC", templates, the bottom-bar Editor
 * tab, renames), and exactly ONE call site in the app passes `single = true`
 * — the hub's file tap.
 */
class EditorRouteCompatTest {

    private fun source(relative: String): String =
        RepoFiles.mainSource(relative).readText()

    private fun mainSources(): List<File> = RepoFiles.mainKotlinSources()

    @Test
    fun `createRoute defaults to single = false`() {
        val screen = source("app/src/main/java/com/codeci/ide/ui/navigation/Screen.kt")
        assertTrue(
            "Screen.Editor.createRoute must default single to false",
            screen.contains("single: Boolean = false")
        )
    }

    @Test
    fun `exactly one call site passes single = true - the hub's file tap`() {
        val callSites = mainSources().joinToString("\n")
        val hits = Regex("""single\s*=\s*true""").findAll(callSites).count()
        assertEquals(
            "only the Projects hub's peek navigation may pass single = true",
            1,
            hits
        )
        val main = source("app/src/main/java/com/codeci/ide/MainActivity.kt")
        assertTrue(
            "the one single=true site is the hub's onProjectFilePeek",
            main.contains("onProjectFilePeek") && main.contains("single = true")
        )
    }

    @Test
    fun `the editor reads the flag and dispatches the peek through the VM`() {
        val editor = source("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt")
        assertTrue(
            "EditorScreen must take the singleFile flag with a safe (PROJECT) default",
            editor.contains("singleFile: Boolean = false")
        )
        assertTrue(
            "the route effect must dispatch openSingleProjectFile for peeks",
            editor.contains("viewModel.openSingleProjectFile(context, projectName, fileName)")
        )
    }

    @Test
    fun `launch-state writers are untouched by the flag`() {
        // EditorLaunchState.save exists ONLY in the editor's PROJECT-mode
        // writers (openProjectFile's two paths + rememberLaunchPoint) and the
        // welcome starter in MainActivity — a single-file peek never appears
        // next to a save call.
        val vm = source("app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt")
        val saves = Regex("""EditorLaunchState\.save\([^)]*\)""").findAll(vm).count()
        assertEquals(3, saves)
        for (m in Regex("""EditorLaunchState\.save\(([^)]*)\)""").findAll(vm)) {
            assertTrue(
                "no EditorLaunchState.save may mention singleFile",
                !m.groupValues[1].contains("singleFile")
            )
        }
    }
}
