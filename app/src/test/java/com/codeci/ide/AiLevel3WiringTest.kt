package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 79 (AI Level 3) — wiring and source-scan pins for reviewable multi-file
 * edits, local unified diffs, and the 1-task preimage undo journal
 * (`03_EDIT_REVIEW_AND_UNDO.md`).
 *
 * Executed through [RepoFiles.codeOnly] so comments and string literals cannot
 * satisfy or trip structural pins.
 */
class AiLevel3WiringTest {

    private val aiDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private val projectsDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/projects")
    private fun ai(name: String) = RepoFiles.codeOnly(File(aiDir, name).readText())
    private fun aiRaw(name: String) = File(aiDir, name).readText()
    private fun proj(name: String) = RepoFiles.codeOnly(File(projectsDir, name).readText())
    private val editor = RepoFiles.codeOnly(
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
    )
    private val editorVm = RepoFiles.codeOnly(
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt").readText()
    )

    @Test
    fun `ui-ai remains free of direct file writes and command execution - writes go only through AiEditApplier`() {
        for (f in aiDir.listFiles { x -> x.extension == "kt" }!!) {
            val code = RepoFiles.codeOnly(f.readText())
            for (banned in listOf("writeText(", "saveFile", "runCode", "ProcessBuilder", "Runtime.getRuntime", "updateCode(")) {
                assertFalse("${f.name} must not call $banned directly", code.contains(banned))
            }
        }
        val vm = ai("AiViewModel.kt")
        assertTrue(vm.contains("AiEditApplier.apply("))
        assertTrue(vm.contains("AiEditApplier.undo("))
    }

    @Test
    fun `AiEditProposal is pure Kotlin and computes diffs locally with DiffEngine`() {
        val raw = aiRaw("AiEditProposal.kt")
        val code = ai("AiEditProposal.kt")
        for (banned in listOf("import java.io", "import android.", "import androidx.")) {
            assertFalse("AiEditProposal.kt must stay pure ($banned)", raw.contains(banned))
        }
        assertTrue("Diffs must be computed locally via DiffEngine.compute", code.contains("DiffEngine.compute("))
    }

    @Test
    fun `AiEditProposal validates paths against secret text and excluded-directory rules`() {
        val code = ai("AiEditProposal.kt")
        assertTrue(code.contains("AiProjectFiles.isSecretLike("))
        assertTrue(code.contains("AiProjectFiles.isTextFile("))
        assertTrue(code.contains("AiProjectFiles.isExcludedDirectory("))
    }

    @Test
    fun `AiEditApplier enforces canonical containment symlink checks and native line endings`() {
        val applier = proj("AiEditApplier.kt")
        assertTrue(applier.contains("ProjectPathUtils.resolveInside("))
        assertTrue(applier.contains("file.canonicalPath != file.absolutePath"))
        assertTrue(applier.contains("LineEndings.toNative("))
        assertTrue(applier.contains("LineEndings.detect("))
        assertTrue(applier.contains("GitDiscardEditors.blocks("))
    }

    @Test
    fun `AiEditApplier and AiViewModel never stage or commit to Git automatically`() {
        val applier = proj("AiEditApplier.kt")
        val vm = ai("AiViewModel.kt")
        for (banned in listOf("GitManager", "stageAll", "stageFile", "commit(", "pushCapturing", "ProcessBuilder")) {
            assertFalse("AiEditApplier must not touch Git ($banned)", applier.contains(banned))
            assertFalse("AiViewModel must not touch Git ($banned)", vm.contains(banned))
        }
    }

    @Test
    fun `the 1-task undo journal lives in noBackupFilesDir and is cleaned up on project or key delete`() {
        val applierRaw = File(projectsDir, "AiEditApplier.kt").readText()
        assertTrue(applierRaw.contains("\"ai/undo\""))
        val pm = proj("ProjectManager.kt")
        assertTrue("Deleting a project must delete its AI undo journal", pm.contains("AiEditApplier.clearProjectJournal("))
        val keyStore = ai("AiKeyStore.kt")
        assertTrue("Deleting the API key must clear AI undo journals", keyStore.contains("AiEditApplier.clearAllJournals("))
    }

    @Test
    fun `proposeEdits goes through the preview gate and never calls client stream directly`() {
        val vm = ai("AiViewModel.kt")
        val block = vm.substringAfter("fun proposeEdits(").substringBefore("fun toggleProposalFile(")
        assertTrue("proposeEdits must land on preview(result)", block.contains("preview(result)"))
        assertFalse("proposeEdits must not call client.stream", block.contains("client.stream("))
        assertTrue(block.contains("AiContextBuilder.fromProposeEdits("))
    }

    @Test
    fun `AiViewModel still has exactly two network stream entry points`() {
        // Phase 79: two — send() and test connection. Phase 80 (Level 4) adds a
        // third, agentTurn(), reachable only from the agent loop after the task
        // preview's Send; see AiLevel4WiringTest for the full agent-law pin.
        val vm = ai("AiViewModel.kt")
        assertEquals(3, Regex("client\\.stream\\(").findAll(vm).count())
        assertTrue(vm.contains("if (_state.value.phase != AiPhase.PREVIEW"))
    }

    @Test
    fun `AiChatSheet offers Propose edits per-file checkboxes Apply Reject and Undo`() {
        val sheet = ai("AiChatSheet.kt")
        assertTrue(sheet.contains("AiCopy.PROPOSE_EDITS"))
        assertTrue(sheet.contains("onProposeEdits(question)"))
        assertTrue(sheet.contains("onToggleEditFile(edit.path)"))
        assertTrue(sheet.contains("AiCopy.applyButtonLabel("))
        assertTrue(sheet.contains("AiCopy.REJECT_CHANGES"))
        assertTrue(sheet.contains("AiCopy.UNDO_CHANGES"))
    }

    @Test
    fun `AiChatSheet renders both baseline and post-apply conflict prompts plus the undo scope note`() {
        val sheet = ai("AiChatSheet.kt")
        assertTrue(sheet.contains("state.applyConflictPaths.isNotEmpty()"))
        assertTrue(sheet.contains("AiCopy.applyConflictMessage("))
        assertTrue(sheet.contains("AiCopy.REBUILD_PROPOSAL"))
        assertTrue(sheet.contains("state.undoConflictPaths.isNotEmpty()"))
        assertTrue(sheet.contains("AiCopy.undoConflictMessage("))
        assertTrue(sheet.contains("AiCopy.UNDO_SCOPE_NOTE"))
    }

    @Test
    fun `EditorScreen synchronizes open tabs dirty buffers file tree and Git badges on apply and undo`() {
        // Phase 80 (Level 4) amendment: both project chips now start the agent
        // task (owner 2026-10-02, "both flows through the agent"), so the
        // propose path enters through agentPropose(). The Level 3 method is
        // kept compiled and tested as the documented rollback; its own pin
        // lives in AiLevel4WiringTest.
        assertTrue(editor.contains("aiViewModel.agentPropose("))
        assertTrue(editor.contains("viewModel.dirtyProjectBuffers()"))
        assertTrue(editor.contains("viewModel.syncAfterAiFileChanges("))
        assertTrue(editorVm.contains("fun syncAfterAiFileChanges("))
        assertTrue(editorVm.contains("refreshFileEntries(appCtx)"))
        assertTrue(editorVm.contains("refreshGitMeta(appCtx)"))
    }

    @Test
    fun `EditorUndoManager is per-tab text history and is not misrepresented as task rollback`() {
        val undoMgr = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/editor/EditorUndoManager.kt").readText()
        assertFalse("EditorUndoManager must stay per-tab", undoMgr.contains("AiEditApplier"))
        assertFalse("ui/ai must not touch EditorUndoManager", ai("AiViewModel.kt").contains("EditorUndoManager"))
    }

    @Test
    fun `all AI files stay free of hardcoded hex colours FontFamily Monospace and logging`() {
        for (f in aiDir.listFiles { x -> x.extension == "kt" }!!) {
            val code = RepoFiles.codeOnly(f.readText())
            assertFalse("${f.name}: no Color(0x…)", code.contains("Color(0x"))
            assertFalse("${f.name}: no FontFamily.Monospace", code.contains("FontFamily.Monospace"))
            for (banned in listOf("Log.", "AppLogger", "println(", "printStackTrace")) {
                assertFalse("${f.name} must not log ($banned)", code.contains(banned))
            }
        }
    }

    @Test
    fun `AiEditApplier uses only minSdk-24 APIs`() {
        val applier = proj("AiEditApplier.kt")
        for (banned in listOf("java.nio", "isSymbolicLink", "readNBytes", "readAllBytes", "walkTopDown", "toPath()")) {
            assertFalse("AiEditApplier must not use $banned (API 26+/33+)", applier.contains(banned))
        }
    }

    @Test
    fun `GeminiRequest still sends systemInstruction and userText with store false`() {
        val req = ai("GeminiRequest.kt")
        assertTrue(req.contains("prompt.systemInstruction"))
        assertTrue(req.contains("prompt.userText"))
        assertTrue(aiRaw("GeminiRequest.kt").contains("""append("\"store\":false")"""))
    }

    @Test
    fun `no new dependency permission DataStore key or Settings control arrived with Level 3`() {
        val manifest = RepoFiles.mainSource("app/src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("SYSTEM_ALERT_WINDOW"))
        assertFalse(ai("AiEditProposal.kt").contains("SettingsManager"))
        assertFalse(proj("AiEditApplier.kt").contains("SettingsManager"))
        assertFalse(proj("AiEditApplier.kt").contains("DataStore"))
    }
}
