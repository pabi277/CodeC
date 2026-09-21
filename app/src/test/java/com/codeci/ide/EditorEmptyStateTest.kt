package com.codeci.ide

import com.codeci.ide.ui.editor.EditorEmptyAction
import com.codeci.ide.ui.editor.EditorEmptyFacts
import com.codeci.ide.ui.editor.EditorEmptyMessage
import com.codeci.ide.ui.editor.EditorEmptyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.2 — the "nothing open" chrome. The plan's premise ("an empty frame")
 * was half right: the editor keeps one scratch buffer alive, so what is missing
 * is the sentence that says so plus ONE way back into a real file.
 */
class EditorEmptyStateTest {

    @Test
    fun `scratch mode with a resumable file offers to open it`() {
        val chrome = EditorEmptyState.chromeFor(
            EditorEmptyFacts(openTabs = 0, projectOpen = false, lastFileAvailable = true)
        )
        assertEquals(EditorEmptyMessage.SCRATCH_FILE, chrome!!.message)
        assertEquals(EditorEmptyAction.OPEN_LAST_FILE, chrome.action)
    }

    @Test
    fun `scratch mode with nothing to resume points at the hub`() {
        val chrome = EditorEmptyState.chromeFor(EditorEmptyFacts(openTabs = 0, lastFileAvailable = false))
        assertEquals(EditorEmptyMessage.SCRATCH_FILE, chrome!!.message)
        assertEquals(EditorEmptyAction.BROWSE_PROJECTS, chrome.action)
    }

    @Test
    fun `with a tab open the chip says nothing`() {
        assertNull(EditorEmptyState.chromeFor(EditorEmptyFacts(openTabs = 1, lastFileAvailable = true)))
    }

    @Test
    fun `inside a project the drawer is the way around, not a chip`() {
        assertNull(EditorEmptyState.chromeFor(EditorEmptyFacts(openTabs = 0, projectOpen = true)))
    }

    @Test
    fun `there is exactly one action per state`() {
        assertEquals(2, EditorEmptyState.actionCount())
        val chrome = EditorEmptyState.chromeFor(EditorEmptyFacts())
        assertTrue(chrome != null)
    }

    @Test
    fun `the screen renders the chip's sentence and one action from strings`() {
        val editor = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
        ).readText()
        assertTrue(editor.contains("EditorEmptyState.chromeFor("))
        assertTrue(editor.contains("if (emptyChrome != null)"))
        assertTrue(editor.contains("R.string.editor_empty_scratch"))
        assertTrue(editor.contains("R.string.editor_empty_open_last"))
        assertTrue(editor.contains("R.string.editor_empty_browse"))
    }

    @Test
    fun `the chip reuses the one resume source instead of inventing a second`() {
        val editor = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
        ).readText()
        assertTrue(editor.contains("EditorLaunchState.load(context)"))
        val region = editor.substringAfter("EditorEmptyState.chromeFor(").substringBefore("EditorKeysRow(")
        assertTrue(
            "the chip's action must open the remembered file through the ViewModel",
            region.contains("viewModel.openFile(context, target.projectName, target.fileName)"),
        )
        assertTrue(
            "and fall back to the hub when there is nothing to resume",
            region.contains("onOpenProjects()"),
        )
    }

    @Test
    fun `the chip is a state, not a dialog`() {
        val editor = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
        ).readText()
        val region = editor.substringAfter("if (emptyChrome != null)").substringBefore("// Phase 51.2 slot: find_bar")
        assertTrue("the chip must be inline chrome", region.contains("Row("))
        assertTrue("never a nag", !region.contains("AlertDialog") && !region.contains("Dialog("))
    }
}
