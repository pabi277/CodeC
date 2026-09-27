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
        assertTrue(screen.contains("onSearch = { fileSearchOpen = !fileSearchOpen"))
        assertTrue(screen.contains("onLocate = { fileSearchQuery = \"\"; viewModel.revealActiveFile() }"))
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
        assertTrue(screen.contains("if (ok) { pendingCreate = null; entryName = \"\"; fileSearchQuery = \"\" }"))
        assertTrue(screen.contains("if (viewModel.renameFileEntry(context, target, renameValue)) pendingRenameEntry = null"))
        assertTrue(screen.contains("if (viewModel.deleteFileEntry(context, target)) pendingDelete = null"))
        assertTrue(screen.contains("supportingText = { operationError?.let { Text(it) } }"))
    }

    @Test fun `hamburger selects Files and new file starts blank`() {
        val open = screen.substringAfter("val toggleDrawer: () -> Unit").substringBefore("val closeDrawer:")
        assertTrue(open.contains("sidePanel = RailPanel.FILES"))
        assertTrue(open.contains("drawerProjectsExpanded = false"))
        val create = screen.substringAfter("onNewFile = { parent ->").substringBefore("onNewFolder =")
        assertTrue(create.contains("entryName = \"\""))
        assertFalse(create.contains("main.c"))
    }
    @Test fun `single-file download and share use system destinations and read-only attachments`() {
        assertTrue(screen.contains("ActivityResultContracts.CreateDocument"))
        assertTrue(screen.contains("Intent.EXTRA_STREAM"))
        assertTrue(screen.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION"))
        assertFalse(screen.contains("Intent.EXTRA_TEXT"))
        assertTrue(screen.contains("SingleFileTransfer.snapshot(source, context.cacheDir)"))
        assertTrue(screen.contains("pendingDownloadPath = prepared.path"))
        assertTrue(screen.contains("SingleFileTransfer.copy(snapshot, it)"))
    }
}
