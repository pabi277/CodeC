package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 86 / Level 9 source pins for D4, D6's narrow amendment, S3–S6 and S11. */
class AiLevel9WiringTest {
    private val aiDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private val projectsDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/projects")
    private fun ai(name: String) = RepoFiles.codeOnly(File(aiDir, name).readText())
    private fun project(name: String) = RepoFiles.codeOnly(File(projectsDir, name).readText())

    @Test
    fun `Send remains the only network road and the agent keeps exactly three stream sites`() {
        val vm = ai("AiViewModel.kt")
        assertEquals(3, Regex("client[.]stream[(]").findAll(vm).count())
        assertTrue(vm.contains("if (_state.value.phase != AiPhase.PREVIEW"))
        assertTrue(vm.contains("agentTurn(session, refreshMemory = false)"))
        assertTrue(vm.contains("sentUserText = packed.text"))
        assertTrue(vm.contains("TASK_MEMORY_CHANGED"))
    }

    @Test
    fun `the complete recited plan is in the initial preview and every later disclosed request`() {
        val context = ai("AiContext.kt")
        val loop = ai("AiAgentLoop.kt")
        val sheet = ai("AiChatSheet.kt")
        val vm = ai("AiViewModel.kt")
        assertTrue(context.contains("agentMemory"))
        assertTrue(context.contains("memory = agentMemory"))
        assertTrue(loop.contains("renderPlanRecitation()"))
        assertTrue(loop.contains("planTail"))
        assertTrue(vm.contains("sentUserText = packed.text"))
        assertTrue(sheet.contains("SentText(instruction"))
        assertTrue(ai("AiTaskMemory.kt").contains("fun renderPlanRecitation()"))
        assertTrue(File(aiDir, "AiTaskMemory.kt").readText().contains("repeat this plan verbatim"))
    }

    @Test
    fun `file cache is re-versioned against admitted paths and live dirty buffers before reuse`() {
        val memory = ai("AiTaskMemory.kt")
        val vm = ai("AiViewModel.kt")
        val runner = ai("AiToolRunner.kt")
        val editor = RepoFiles.codeOnly(
            RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
        )
        assertTrue(memory.contains("contentVersion"))
        assertTrue(memory.contains("canonicalPath"))
        assertTrue(memory.contains("snapshot(root"))
        assertTrue(memory.contains("dirtyBuffers"))
        assertTrue(memory.contains("readFilesKey"))
        assertTrue(vm.contains("prepareRead("))
        assertTrue(vm.contains("updateAgentDirtyBuffers("))
        assertTrue(
            "every request retries until its dirty-buffer snapshot is stable",
            vm.contains("if (session.dirtyBuffers == dirtySnapshot)")
        )
        assertTrue(
            "continuous edits suppress file-backed memory rather than sending a stale cache",
            vm.contains("session.memoryStore.reconcile(before, session.root, emptyList(), emptyMap())")
        )
        assertTrue(editor.contains("dirtyBuffers = viewModel.dirtyProjectBuffers()"))
        assertTrue(editor.contains("updateAgentDirtyBuffers(viewModel.dirtyProjectBuffers())"))
        assertTrue(runner.contains("cachedFiles"))
        assertTrue(File(aiDir, "AiToolRunner.kt").readText().contains("[cached memory]"))
    }

    @Test
    fun `only bounded task memory reaches no-backup ai task storage and deletion hooks clear it`() {
        val vm = ai("AiViewModel.kt")
        val store = project("AiTaskMemoryStore.kt")
        val keyStore = ai("AiKeyStore.kt")
        val manager = project("ProjectManager.kt")
        assertTrue(vm.contains("noBackupFilesDir"))
        assertTrue(File(projectsDir, "AiTaskMemoryStore.kt").readText().contains("task-memory.bin"))
        assertTrue(store.contains("MAX_STORE_BYTES"))
        assertTrue(store.contains("sanitizedForStore"))
        assertTrue("loaded snapshots are revalidated against the live project", store.contains("decoded.reconcile(projectRoot, admittedPaths, dirtyBuffers)"))
        val saveBody = store.substringAfter("fun save(").substringBefore("companion object")
        assertFalse("the durable writer never targets the project tree", saveBody.contains("projectRoot"))
        assertTrue(saveBody.contains("projectDirectory(noBackupRoot"))
        assertTrue(keyStore.contains("AiTaskMemoryStore.clearAll"))
        assertTrue(manager.contains("AiTaskMemoryStore.clearProject"))
        assertTrue(store.contains("persistentEnabled"))
        assertTrue(store.contains("if (!persistentEnabled)"))
    }

    @Test
    fun `filtering runs at snapshot model-note and storage boundaries before any write`() {
        val memory = ai("AiTaskMemory.kt")
        val store = project("AiTaskMemoryStore.kt")
        assertTrue(memory.contains("containsSecretMaterial"))
        assertTrue(memory.contains("safeRelativePath"))
        assertTrue(memory.contains("safeCanonicalPath"))
        assertTrue(memory.contains("sanitizeMemoryText"))
        assertTrue(store.contains("memory.sanitizedForStore(admittedPaths)"))
        assertTrue(store.contains("FileOutputStream(tmp)"))
        assertTrue(store.contains("noBackupRoot"))
    }

    @Test
    fun `task memory core stays host-only and does not widen ui-ai into project writes or execution`() {
        val raw = File(aiDir, "AiTaskMemory.kt").readText()
        assertFalse(raw.contains("import android."))
        assertFalse(raw.contains("java.nio.file"))
        val allAi = aiDir.listFiles { file -> file.extension == "kt" }!!
            .joinToString("\n") { RepoFiles.codeOnly(it.readText()) }
        for (banned in listOf("writeText(", "FileOutputStream", "ProcessBuilder", "Runtime.getRuntime", "sendCommand(")) {
            assertFalse("ui/ai must not write project files or execute commands ($banned)", allAi.contains(banned))
        }
    }
}
