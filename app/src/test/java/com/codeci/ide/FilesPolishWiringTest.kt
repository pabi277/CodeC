package com.codeci.ide

import org.junit.Assert.*
import org.junit.Test

/** Source checks supplement (not replace) Android rendering and buffer tests. */
class FilesPolishWiringTest {
    private fun source(path: String) = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/$path").readText()
    private val drawer = source("components/EditorProjectDrawer.kt")
    private val screen = source("screens/EditorScreen.kt")
    private val panel = source("components/EditorSidePanel.kt")

    @Test fun `tree has depth guides outline selection and independent row menu targets`() {
        assertTrue(drawer.contains("entry.depth.coerceIn"))
        assertTrue(drawer.contains("drawLine(guide"))
        assertTrue(drawer.contains("Modifier.border(1.dp"))
        assertTrue(drawer.contains("Actions for \${entry.name}"))
        assertTrue(drawer.contains("Modifier.size(48.dp)"))
        assertTrue(drawer.contains("stateDescription"))
    }
    @Test fun `switcher stays in panel and is bounded while toolbar routes real actions`() {
        assertTrue(drawer.contains("heightIn(max = 192.dp)"))
        assertTrue(drawer.contains("Text(\"Switch project\")"))
        assertTrue(screen.contains("onSearch = { sidePanel = RailPanel.SEARCH }"))
        assertTrue(screen.contains("onLocate = { viewModel.revealActiveFile() }"))
        assertTrue(drawer.contains("Close files panel"))
    }
    @Test fun `search binds displayed and opened hits to the full request key`() {
        assertTrue(screen.contains("hits = if (searchResultKey == searchKey) searchHits else emptyList()"))
        assertTrue(screen.contains("viewModel.openSearchHit(context, project, hit)"))
        assertTrue(screen.contains("throw cancelled"))
        assertTrue(panel.contains("state.searching"))
        assertTrue(panel.contains("state.message"))
        assertTrue(panel.contains("state.capped"))
        assertTrue(panel.contains("saved files"))
        assertTrue(panel.contains("contentDescription = description"))
    }
    @Test fun `drawer operation errors remain inside dialogs`() {
        assertTrue(screen.contains("if (ok) { pendingCreate = null; entryName = \"\" }"))
        assertTrue(screen.contains("if (viewModel.renameFileEntry(context, target, renameValue)) pendingRenameEntry = null"))
        assertTrue(screen.contains("if (viewModel.deleteFileEntry(context, target)) pendingDelete = null"))
        assertTrue(screen.contains("supportingText = { operationError?.let { Text(it) } }"))
    }
}
