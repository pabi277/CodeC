package com.codeci.ide

import org.junit.Assert.*
import org.junit.Test

class GitDiscardWiringTest {
    private fun source(path: String) = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/$path").readText()
    private val sheet = source("screens/GitControlView.kt")
    private val vm = source("viewmodels/GitControlViewModel.kt")
    private val editor = source("viewmodels/EditorViewModel.kt")

    @Test fun `row only requests confirmation and only confirm invokes discard`() {
        assertTrue(sheet.contains("{ pendingDiscard = change }"))
        assertTrue(sheet.contains("onDiscard = if (GitDiscardPolicy.canDiscard(change))"))
        val dialog = sheet.substringAfter("pendingDiscard?.let { change ->").substringBefore("if (state.discardBusy)")
        assertTrue(dialog.contains("R.string.git_discard_confirm, change.path"))
        assertEquals(1, Regex("viewModel\\.discardUnstaged\\(").findAll(sheet).count())
        assertTrue(dialog.substringAfter("confirmButton =").contains("viewModel.discardUnstaged(context, projectRoot, change)"))
        assertFalse(dialog.substringAfter("dismissButton =").contains("discardUnstaged("))
        assertTrue(sheet.contains("dismissOnBackPress = false, dismissOnClickOutside = false"))
    }
    @Test fun `engine operation is enclosed by editor reconciliation even on failure`() {
        val operation = vm.substringAfter("fun discardUnstaged(").substringBefore("/** Opens the inline diff")
        assertTrue(operation.contains("GitDiscardEditors.begin(projectRoot, change.path)"))
        assertTrue(operation.contains("withContext(NonCancellable)"))
        assertTrue(operation.contains("finally {\n                        GitDiscardEditors.finish(ticket)"))
        assertTrue(operation.contains("git.discardUnstaged(projectRoot, change.path)"))
        assertTrue(operation.contains("refresh(context, projectRoot, finalMessage = message)"))
    }
    @Test fun `every editor registers and its project write choke point is guarded`() {
        assertTrue(editor.contains("GitDiscardEditors.register(this)"))
        assertTrue(editor.contains("GitDiscardEditors.unregister(this)"))
        val write = editor.substringAfter("private fun writeProjectFile(").substringBefore("fun saveFile(")
        assertTrue(write.indexOf("GitDiscardEditors.blocks(info.root, safe)") < write.indexOf("file.writeText("))
        assertTrue(write.contains("GitDiscardEditors.blocks(info.root, safe)) return false"))
        val reload = editor.substringAfter("override fun reloadDiscardedFile(").substringBefore("fun reloadActiveTab(")
        assertTrue(reload.contains("check(canDiscardFile(root, path))"))
        assertTrue(reload.contains("undoManagers.remove(path)"))
        assertFalse(reload.contains("undoManagers.clear()"))
        assertFalse(reload.contains("saveAllTabs("))
    }
    @Test fun `existing git affordances still delegate to the one engine`() {
        for (call in listOf("viewModel.toggleStage(", "viewModel.openDiff(", "viewModel.commitAndPush(",
            "viewModel.pull(", "viewModel.push(", "viewModel.markResolved(")) assertTrue(call, sheet.contains(call))
        for (call in listOf("git.stageAll(", "git.commit(", "git.pull(", "git.pushCapturing(")) assertTrue(call, vm.contains(call))
        assertTrue(vm.contains("val staged = change.isStaged"))
        assertTrue(sheet.contains("val staged = change.isStaged"))
    }
}
