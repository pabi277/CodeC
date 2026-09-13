package com.codeci.ide

import com.codeci.ide.ui.navigation.BackAction
import com.codeci.ide.ui.navigation.BackRouter
import com.codeci.ide.ui.navigation.BackState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 49.2 — the exit prompt's four states, decided from the SAME inputs
 * `BackRouter` uses, so the policy and the wiring cannot drift (PART_49_2
 * §Tests). The device-dependent behaviour this replaces: the old condition
 * asked `popBackStack()` — so a tab-tapped stack (cause A) and a per-install
 * start destination (cause B) hid the prompt on most phones. Now the ROUTE
 * decides.
 */
class ExitPromptPolicyTest {

    private val tabPatterns = listOf(
        "file_manager?openSheet={openSheet}",
        "editor?projectName={projectName}&fileName={fileName}&single={single}",
        "terminal?cmd={cmd}&nonce={nonce}",
        "modules",
        "settings"
    )

    /** The exact construction MainActivity's root handler performs. */
    private fun decideAt(route: String?, canPopRoute: Boolean): BackAction =
        BackRouter.decide(
            BackState(
                canPopRoute = canPopRoute,
                atRootDestination = BackRouter.isRoot(route, tabPatterns),
                exitPromptEnabled = true,
                exitPromptVisible = false,
                safeMode = false
            )
        )

    @Test
    fun `fresh install - back at the root shows the prompt`() {
        // Start destination = file_manager; the stack holds one entry
        // (canPopRoute false — which is now IRRELEVANT to the decision, but
        // stated the way the device really reports it).
        assertEquals(
            BackAction.ShowExitPrompt,
            decideAt("file_manager", canPopRoute = false)
        )
    }

    @Test
    fun `upgrade - back at the editor root shows the prompt too`() {
        // Start destination = editor (EditorLaunchState present). Cause B:
        // same build, different first back — until the route decided.
        assertEquals(
            BackAction.ShowExitPrompt,
            decideAt("editor?projectName=demo&fileName=main.c", canPopRoute = false)
        )
    }

    @Test
    fun `tab tap - back on a non-start tab pops, the SECOND back prompts`() {
        // Cause A: tapping a tab pushes a second entry
        // (popUpTo(start) { saveState }), so the first back lands on the
        // start tab — the platform's own bottom-nav behaviour, now a tested
        // row instead of an accident.
        assertEquals(
            BackAction.PopRoute,
            decideAt("terminal?nonce=1", canPopRoute = true)
        )
        assertEquals(
            BackAction.ShowExitPrompt,
            decideAt("file_manager", canPopRoute = false)
        )
    }

    @Test
    fun `a tapped tab back AT the start destination still prompts`() {
        // The subtle case the old popBackStack() condition got wrong: after
        // tab taps the start destination can sit UNDER another entry, but a
        // user back at the start route IS at the root. With
        // canPopRoute=false the prompt shows regardless of stack history.
        assertEquals(
            BackAction.ShowExitPrompt,
            decideAt("file_manager?openSheet=1", canPopRoute = false)
        )
    }

    @Test
    fun `the switch off - back at the root exits directly`() {
        val action = BackRouter.decide(
            BackState(
                atRootDestination = true,
                exitPromptEnabled = false
            )
        )
        assertEquals(BackAction.ExitApp, action)
    }

    @Test
    fun `safe mode - back at the root exits directly, prompt never`() {
        val action = BackRouter.decide(
            BackState(
                atRootDestination = true,
                exitPromptEnabled = true,
                safeMode = true
            )
        )
        assertEquals(BackAction.ExitApp, action)
    }

    @Test
    fun `the second press belongs to the dialog - the handler stands down`() {
        // While the prompt is up the root handler is disabled
        // (`enabled = !exitPromptVisible && …`) and the dialog's
        // onDismissRequest IS the exit. The router still answers the state
        // honestly if asked (row 7), and the wiring keeps exactly ONE of the
        // two paths live — pinned mechanically by BackHandlerWiringTest.
        assertEquals(
            BackAction.ExitApp,
            BackRouter.decide(BackState(exitPromptVisible = true))
        )
    }
}
