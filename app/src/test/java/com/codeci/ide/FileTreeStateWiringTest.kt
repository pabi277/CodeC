package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 69.4 — the wiring pins for the owner's first report (2026-09-28,
 * verbatim): *"When i import a zip or repository and open in editor it will in
 * collapse state and remember what open by the use when leaving and again open
 * the editor and the project in the same position no all expend or collapse"*.
 *
 * The arithmetic is `FileTreeCollapseTest`'s; this file pins the half a unit
 * test cannot see — that the tree's shape is really stored per project, that
 * every way the user changes it is remembered (and only those), that a project
 * listed for the first time starts closed, and that a deleted project does not
 * leave its entry behind forever.
 */
class FileTreeStateWiringTest {

    private val viewModel = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt"
    ).readText()

    private val hub = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/viewmodels/FileManagerViewModel.kt"
    ).readText()

    private val storage = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/editor/FileTreeMemory.kt"
    ).readText()

    private fun code(source: String): String = RepoFiles.codeOnly(source)

    @Test
    fun `the tree's shape is stored per project, not in the session`() {
        val vm = code(viewModel)
        assertTrue(
            "the project's remembered shape must be read when its tree is listed",
            vm.contains("FileTreeMemory.load(context, project)")
        )
        assertTrue(
            "the first open's shape must be written, so the next open restores it",
            vm.contains("FileTreeMemory.save(context, project, shape)")
        )
        assertTrue(
            "storage must be keyed by project (one tree per project)",
            code(storage).contains("PREFIX + projectName")
        )
        assertTrue(
            "a load that has never been stored must stay distinguishable from 'all expanded'",
            code(storage).contains("fun load(context: Context, projectName: String): Set<String>?")
        )
    }

    @Test
    fun `every way the user changes the shape is remembered`() {
        // The chevron, Collapse all / Expand all, the reveal of the active
        // file, and a rename's path remap. Five call sites, one writer — and
        // the resets that belong to a PROJECT SWITCH are deliberately NOT
        // among them (the tree we are leaving keeps its own shape).
        assertEquals(
            "every user-driven shape change must be written",
            5,
            Regex("^\\s+rememberTreeState\\(\\)$", RegexOption.MULTILINE)
                .findAll(code(viewModel)).count()
        )
        val body = code(viewModel)
        for (mutator in listOf(
            "fun toggleDirectory(path: String) {",
            "fun collapseAllDirectories() {",
            "fun expandAllDirectories() {"
        )) {
            val at = body.indexOf(mutator)
            assertTrue("$mutator is gone", at > 0)
            val end = body.indexOf("\n    }", at)
            assertTrue(
                "$mutator must remember what the user just did",
                body.substring(at, end).contains("rememberTreeState()")
            )
        }
    }

    @Test
    fun `a project listed for the first time starts with every folder closed`() {
        // Owner: *"import a zip or repository and open in editor it will in
        // collapse state"* — before 69.4 a fresh project opened expanded.
        val vm = code(viewModel)
        assertTrue(
            "the first-open shape must come from the pure policy",
            vm.contains("FileTreeCollapse.initialTree(dirs, null)")
        )
        assertTrue(
            "folders that no longer exist must drop out of what is remembered",
            vm.contains("FileTreeCollapse.prune(")
        )
    }

    @Test
    fun `the first-open default never overwrites what the user already did`() {
        // A new nested file reveals its parents the moment it is created, and
        // in the same VM the drawer may not have listed anything yet. That
        // reveal is the user's own change and outranks "everything closed" —
        // without this the Files tree re-collapsed the folder a file had just
        // been created in (CI round 2, run 36384352636).
        val vm = code(viewModel)
        assertTrue(
            "a first open with nothing touched is the only 'close everything' case",
            vm.contains("remembered == null && !treeStateTouched -> FileTreeCollapse.initialTree(dirs, null)")
        )
        assertTrue(
            "a shape the user already chose must be kept, not re-initialised",
            vm.contains("remembered == null -> FileTreeCollapse.prune(_collapsedDirs.value, dirs)")
        )
        assertTrue(
            "any user-driven change must count as a touch, even before the first listing",
            vm.contains("treeStateTouched = true")
        )
        // …and a PROJECT SWITCH must clear it, or the project being entered
        // would inherit "everything expanded" from the one being left.
        assertTrue(
            "leaving a project must clear the touch flag",
            vm.contains("treeStateTouched = false")
        )
        assertEquals(
            "the three switches that drop the tree all clear the flag",
            3,
            Regex("^\\s+resetTreeShapeForNewContext\\(\\)$", RegexOption.MULTILINE)
                .findAll(vm).count()
        )
    }

    @Test
    fun `a deleted project takes its tree shape with it`() {
        assertTrue(
            "the hub's delete must forget the project's remembered tree",
            code(hub).contains("FileTreeMemory.forget(context, name)")
        )
        assertTrue(
            "and the store must have a way to forget",
            code(storage).contains("fun forget(context: Context, projectName: String)")
        )
    }
}
