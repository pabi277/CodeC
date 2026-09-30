package com.codeci.ide

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 76 — the AI helper's safety laws, asked of the source (house style of
 * `SidePanelWiringTest`): read-only (D1), key storage (D3), preview before
 * send (D4), projects only (D5), nothing saved (D6), no logging of secrets.
 */
class AiHelperWiringTest {

    private val aiDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    /** Code with comments AND string literals blanked — for call-site pins. */
    private fun src(name: String) = RepoFiles.codeOnly(File(aiDir, name).readText())
    /** The file as written — only for pinning literal constants. */
    private fun raw(name: String) = File(aiDir, name).readText()
    private val all: String by lazy {
        aiDir.listFiles { f -> f.extension == "kt" }!!.sortedBy { it.name }
            .joinToString("\n") { RepoFiles.codeOnly(it.readText()) }
    }
    private val editor = RepoFiles.codeOnly(
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
    )

    @Test
    fun `nothing in the helper logs`() {
        for (banned in listOf("Log.", "AppLogger", "println(", "printStackTrace")) {
            assertFalse("ui/ai must not use $banned", all.contains(banned))
        }
    }

    @Test
    fun `the helper is read-only - it never writes project files or runs code`() {
        for (banned in listOf("writeText(", "saveFile", "runCode", "ProcessBuilder", "Runtime.getRuntime", "updateCode(")) {
            assertFalse("ui/ai must not call $banned", all.contains(banned))
        }
    }

    @Test
    fun `the key lives in Keystore-encrypted storage outside backups, with no plaintext store`() {
        val store = src("AiKeyStore.kt")
        assertTrue(store.contains("noBackupFilesDir"))
        assertTrue(raw("AiKeyStore.kt").contains("\"AndroidKeyStore\""))
        assertTrue(raw("AiKeyStore.kt").contains("\"AES/GCM/NoPadding\""))
        for (banned in listOf("SharedPreferences", "dataStore", "filesDir,", "getExternal")) {
            assertFalse("AiKeyStore must not use $banned", store.contains(banned))
        }
    }

    @Test
    fun `the key never reaches UI state`() {
        val vm = src("AiViewModel.kt")
        val stateBlock = vm.substringAfter("data class AiUiState(").substringBefore(")\n")
        assertFalse(stateBlock.contains("apiKey"))
        assertFalse(stateBlock.contains("key:"))
    }

    @Test
    fun `a request is sent only from Send on a preview`() {
        val vm = src("AiViewModel.kt")
        assertTrue(vm.contains("if (_state.value.phase != AiPhase.PREVIEW"))
        // Exactly two network entry points: send() and the content-free test.
        assertTrue(Regex("client\\.stream\\(").findAll(vm).count() == 2)
        assertTrue(vm.contains("GeminiRequest.testBody()"))
        val panel = src("AiPanel.kt")
        assertTrue(panel.contains("Button(onClick = onSend)"))
    }

    @Test
    fun `the answer is never persisted`() {
        val vm = src("AiViewModel.kt")
        for (banned in listOf("rememberSaveable", "SavedStateHandle", "writeText", "Properties")) {
            assertFalse("AiViewModel must not use $banned", vm.contains(banned))
        }
    }

    @Test
    fun `the editor gates the helper on its open mode and resets it per project`() {
        assertTrue(editor.contains("AiGate.availability(openMode, currentProject, aiState.keySaved)"))
        assertTrue(editor.contains("aiViewModel.onProjectChanged("))
    }

    @Test
    fun `the panel uses theme colours only`() {
        assertFalse(src("AiPanel.kt").contains("Color(0x"))
    }
}
