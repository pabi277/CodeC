package com.codeci.ide

import com.codeci.ide.ui.navigation.BackAction
import com.codeci.ide.ui.navigation.BackRouter
import com.codeci.ide.ui.navigation.BackState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 49.1 — the precedence table, pinned (PART_49_1 §Tests). Each case is
 * one precedence pair from the spec: which row wins when two facts are true
 * at once, and what the edge cases answer. A future screen that needs a new
 * row must add it to the enum — which fails here until the table is updated.
 * That is the point.
 */
class BackRouterTest {

    private fun decide(vararg pairs: Pair<String, Any>): BackAction {
        val s = BackState()
        var state = s
        pairs.forEach { (name, value) ->
            state = when (name) {
                "unsaved" -> state.copy(unsavedChanges = value as Boolean)
                "drawer" -> state.copy(editorDrawerOpen = value as Boolean)
                "hub" -> state.copy(hubProjectOpen = value as Boolean)
                "sheet" -> state.copy(sheetOrDialogOpen = value as Boolean)
                "find" -> state.copy(findBarOpen = value as Boolean)
                "output" -> state.copy(outputPanelExpanded = value as Boolean)
                "keyboard" -> state.copy(keyboardVisible = value as Boolean)
                "promptVisible" -> state.copy(exitPromptVisible = value as Boolean)
                "canPop" -> state.copy(canPopRoute = value as Boolean)
                "atRoot" -> state.copy(atRootDestination = value as Boolean)
                "promptEnabled" -> state.copy(exitPromptEnabled = value as Boolean)
                "safeMode" -> state.copy(safeMode = value as Boolean)
                else -> error("unknown field $name")
            }
        }
        return BackRouter.decide(state)
    }

    @Test
    fun `row 1 - unsaved changes outrank an open drawer`() {
        // The old registration-order rule, now a table row: a dirty buffer's
        // back press asks, even over an open drawer.
        assertEquals(
            BackAction.ShowUnsavedDialog,
            decide("unsaved" to true, "drawer" to true)
        )
    }

    @Test
    fun `row 2 - a clean open drawer closes`() {
        assertEquals(BackAction.CloseEditorDrawer, decide("drawer" to true))
    }

    @Test
    fun `row 2 over row 3 - drawer and hub tree at once answer the drawer`() {
        // Impossible in practice (different screens), but the table must
        // still answer: total function, no crash, deterministic.
        assertEquals(
            BackAction.CloseEditorDrawer,
            decide("drawer" to true, "hub" to true)
        )
    }

    @Test
    fun `row 3 - the hub project tree closes, never the app`() {
        assertEquals(BackAction.CloseHubProject, decide("hub" to true))
    }

    @Test
    fun `row 4 - an open sheet owns its own back`() {
        // None = "let the library handle it", not "do nothing".
        assertEquals(BackAction.None, decide("sheet" to true))
    }

    @Test
    fun `row 3 over row 4 - hub tree plus a sheet answers the hub row`() {
        // The spec's order, verbatim. In practice a Material3 sheet is its
        // own window and wins the dispatch before this handler is consulted
        // — which is exactly why the hub+sheet pairing cannot misfire: the
        // row answers, but the sheet's own back handling never lets the
        // handler run while the sheet is up.
        assertEquals(
            BackAction.CloseHubProject,
            decide("hub" to true, "sheet" to true)
        )
    }

    @Test
    fun `row 5 - the find bar closes`() {
        assertEquals(BackAction.CloseFindBar, decide("find" to true))
    }

    @Test
    fun `row 6 - an expanded output panel collapses when the keyboard is down`() {
        assertEquals(
            BackAction.CollapseOutputPanel,
            decide("output" to true, "keyboard" to false)
        )
    }

    @Test
    fun `row 6 guard - with the keyboard up back is the user closing it`() {
        // The platform already does that; the router must not eat it.
        assertEquals(
            BackAction.None,
            decide("output" to true, "keyboard" to true)
        )
    }

    @Test
    fun `row 7 - the prompt up means the next back exits`() {
        assertEquals(BackAction.ExitApp, decide("promptVisible" to true))
    }

    @Test
    fun `row 8 - a non-start tab pops to the start tab`() {
        assertEquals(BackAction.PopRoute, decide("canPop" to true))
    }

    @Test
    fun `row 9 - at the root with the switch on, the prompt shows`() {
        assertEquals(
            BackAction.ShowExitPrompt,
            decide("atRoot" to true, "promptEnabled" to true)
        )
    }

    @Test
    fun `row 9 - at the root with the switch off, back exits directly`() {
        assertEquals(
            BackAction.ExitApp,
            decide("atRoot" to true, "promptEnabled" to false)
        )
    }

    @Test
    fun `row 9 - safe mode never shows the prompt`() {
        // The Phase 42.3 law, carried as a table row instead of a
        // when-branch.
        assertEquals(
            BackAction.ExitApp,
            decide("atRoot" to true, "promptEnabled" to true, "safeMode" to true)
        )
    }

    @Test
    fun `row 10 - nothing applies means the library owns it`() {
        assertEquals(BackAction.None, decide())
    }

    @Test
    fun `screen-local states never reach the navigation rows`() {
        // The defaults the screen-local handlers rely on: rows 7-9 need the
        // root's fields, which a screen never fills.
        assertEquals(BackAction.None, decide("keyboard" to true))
        assertEquals(BackAction.None, decide("output" to true, "keyboard" to true, "sheet" to false))
    }
}
