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
        for (call in listOf("viewModel.openDiff(", "viewModel.commitAndPush(",
            "viewModel.pull(", "viewModel.push(", "viewModel.markResolved(")) assertTrue(call, sheet.contains(call))
        for (call in listOf("git.stageAll(", "git.commit(", "git.pull(", "git.pushCapturing(")) assertTrue(call, vm.contains(call))
    }

    // Phase 73.1 — the per-file +/− stage toggle changed the git index but
    // never changed what COMMIT & PUSH committed (it always `stageAll`s
    // first), so it was removed from the ordinary change row. Mark Resolved
    // (a conflict row) is the one remaining use of that trailing control.
    @Test fun `the ordinary change row has no stage toggle, only Mark Resolved does`() {
        assertFalse(sheet.contains("viewModel.toggleStage("))
        assertFalse(vm.contains("fun toggleStage("))
        val row = sheet.substringAfter("private fun GitChangeRow(").substringBefore("@Composable\nprivate fun GitDiffDialog(")
        // The trailing control renders only for a conflict row.
        assertTrue(row.contains("if (markResolvedMode && onToggleStage != null)"))
        assertFalse(row.contains("SpckIcons.PlusMinus"))
        // The "others" (non-conflict) call site passes no onToggleStage.
        // Phase 73.5 — the list is search-filtered (`visibleOthers`), but it
        // is still the same call site with the same row contract.
        val othersCall = sheet.substringAfter("itemsIndexed(visibleOthers, key = { _, change -> change.path }) { index, change ->")
            .substringBefore("// Mockup: a hairline between every change row.")
        assertFalse(othersCall.contains("onToggleStage"))
        // The conflicts call site still wires Mark Resolved.
        val conflictsCall = sheet.substringAfter("conflicts.forEachIndexed { index, change ->")
            .substringBefore("if (index < conflicts.lastIndex)")
        assertTrue(conflictsCall.contains("onToggleStage = {"))
        assertTrue(conflictsCall.contains("viewModel.markResolved(context, projectRoot, change)"))
        assertTrue(conflictsCall.contains("markResolvedMode = true"))
    }
}
