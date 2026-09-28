package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 69.4 — the wiring pins for the owner's second report (2026-09-28,
 * verbatim): *"If i run a file but it is not in 1st of the editor and back from
 * preview it again opens the 1st file on the editor not the file i opened"*.
 *
 * `EditorRouteOpenTest` proves the rule; this file proves the editor actually
 * asks it: the route's file is opened behind the guard, the guard is the only
 * thing that changed about the effect, and the session marker lives in the
 * ViewModel (so it survives a rotation and dies with the tabs).
 */
class EditorRouteWiringTest {

    private val screen = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
    ).readText()

    private val viewModel = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt"
    ).readText()

    private fun code(source: String): String = RepoFiles.codeOnly(source)

    @Test
    fun `the route's file is opened behind the once-per-session guard`() {
        val body = code(screen)
        val route = body.indexOf("val editorRouteKey = EditorRouteOpen.key(projectName, fileName, singleFile)")
        assertTrue("the route key is gone", route > 0)
        val effect = body.indexOf("LaunchedEffect(editorRouteKey) {")
        assertTrue("the open effect must key on the route, not on three separate args", effect > route)
        val window = body.substring(effect, effect + 400)
        assertTrue(
            "the effect must ask the pure rule before it opens anything",
            window.contains("EditorRouteOpen.shouldOpen(editorRouteKey, viewModel.openedRoute())")
        )
        assertTrue(
            "and it must record the route it opened",
            window.contains("viewModel.markRouteOpened(editorRouteKey)")
        )
        // The guard really wraps the opens, not just sits above them.
        val opens = listOf(
            "viewModel.openSingleProjectFile(context, projectName, fileName)",
            "viewModel.openFile(context, projectName, fileName)"
        )
        for (call in opens) {
            val at = body.indexOf(call)
            assertTrue("$call is gone", at > 0)
            assertTrue("every open must sit after the guard", at > effect)
        }
    }

    @Test
    fun `the route guard is asked exactly once, and only in the editor`() {
        assertEquals(
            "one guard, one place",
            1,
            Regex(Regex.escape("EditorRouteOpen.shouldOpen(")).findAll(code(screen)).count()
        )
    }

    @Test
    fun `the session marker lives in the ViewModel, not in the screen`() {
        // It must outlive a rotation (a `remember` in the screen would not) and
        // die with the tabs (a saved-state flag would not).
        val vm = code(viewModel)
        assertTrue(vm.contains("private var openedRouteKey: String? = null"))
        assertTrue(vm.contains("fun openedRoute(): String? = openedRouteKey"))
        assertTrue(vm.contains("fun markRouteOpened(routeKey: String)"))
        assertEquals(
            "exactly one writer of the marker",
            1,
            Regex(Regex.escape("openedRouteKey = routeKey")).findAll(vm).count()
        )
    }
}
