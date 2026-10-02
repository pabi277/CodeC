package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 78 (AI Level 2) — the whole-project context laws, asked of the source
 * (house style of `AiHelperWiringTest` / `AiSurfaceWiringTest`).
 *
 * A pure policy nobody calls is decoration, and a safety rule that lives only
 * in a comment is not a rule. These pins exist so the next phase cannot quietly:
 *  - reuse the editor's search filter, which admits `.env` and `.npmrc`;
 *  - add a second road to the network;
 *  - persist an index (D6);
 *  - call an API newer than minSdk 24.
 */
class AiLevel2WiringTest {

    private val aiDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private fun ai(name: String) = RepoFiles.codeOnly(File(aiDir, name).readText())
    private fun aiRaw(name: String) = File(aiDir, name).readText()
    private val editor = RepoFiles.codeOnly(
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
    )
    private val newFiles = listOf("AiProjectFiles.kt", "AiProjectReader.kt")

    // ---- 1. the filter is the AI's own, never the search's ----------------

    @Test
    fun `the AI never borrows the editor search filter that admits dot-env and npmrc`() {
        for (f in aiDir.listFiles { x -> x.extension == "kt" }!!) {
            val code = RepoFiles.codeOnly(f.readText())
            assertFalse(
                "${f.name} must not use ProjectSearch.isSearchable — it admits .env/.env.*/.npmrc",
                code.contains("isSearchable")
            )
            assertFalse(
                "${f.name} must not reach for the search engine",
                code.contains("ProjectSearch.")
            )
        }
        // and the reason is recorded where the filter lives
        assertTrue(
            "AiProjectFiles must name the hazard it exists to avoid",
            aiRaw("AiProjectFiles.kt").contains(".env") && aiRaw("AiProjectFiles.kt").contains(".npmrc")
        )
    }

    @Test
    fun `the secret check is the first branch of the exclusion decision`() {
        val policy = ai("AiProjectFiles.kt")
        val body = policy.substringAfter("fun exclusionFor(").substringBefore("fun isTextFile(")
        val secretAt = body.indexOf("Exclusion.SECRET")
        val textAt = body.indexOf("Exclusion.NOT_TEXT")
        val binaryAt = body.indexOf("Exclusion.BINARY")
        assertTrue("SECRET must be decided first", secretAt in 0 until textAt)
        assertTrue("SECRET must precede BINARY too", secretAt in 0 until binaryAt)
    }

    @Test
    fun `there is no opt-in for a credential-shaped file`() {
        val policy = ai("AiProjectFiles.kt")
        for (banned in listOf("includeSecret", "allowSecret", "optIn", "forceInclude", "ignoreExclusion")) {
            assertFalse("no secret opt-in may exist ($banned)", policy.contains(banned))
        }
    }

    // ---- 2. the policy is really wired -----------------------------------

    @Test
    fun `the reader asks the policy, and the view model asks the reader`() {
        assertTrue(ai("AiProjectReader.kt").contains("AiProjectFiles.isSecretLike("))
        assertTrue(ai("AiProjectReader.kt").contains("AiProjectFiles.isTextFile("))
        assertTrue(ai("AiProjectReader.kt").contains("AiProjectFiles.shortlist("))
        assertTrue(ai("AiViewModel.kt").contains("AiProjectReader.scan("))
        assertTrue(ai("AiViewModel.kt").contains("AiProjectFiles.plan("))
        assertTrue(ai("AiViewModel.kt").contains("AiContextBuilder.fromProject("))
    }

    @Test
    fun `the project source goes through the same preview gate as the other two`() {
        val vm = ai("AiViewModel.kt")
        // askProject ends in preview(...), and only send() touches the client.
        val ask = vm.substringAfter("fun askProject(").substringBefore("fun cancelGather(")
        assertTrue("askProject must land on preview()", ask.contains("preview(result)"))
        assertFalse("askProject must not reach the network", ask.contains("client.stream("))
        // Phase 80 (Level 4): a third site, agentTurn(), is reachable only after
        // the task preview's Send; AiLevel4WiringTest pins that it stays private
        // and behind the PREVIEW gate, and still no source calls it directly.
        assertEquals("three network entry points, all behind PREVIEW", 3, Regex("client\\.stream\\(").findAll(vm).count())
        assertTrue(vm.contains("if (_state.value.phase != AiPhase.PREVIEW"))
    }

    @Test
    fun `the editor hands the live buffer over, so a dirty file is never described from disk`() {
        // Phase 80 (Level 4) amendment: the chip now starts the agent task
        // (agentAsk), which takes the same live buffer into the same preview
        // gate. The Level 2 method stays compiled as the documented rollback.
        assertTrue(editor.contains("aiViewModel.agentAsk("))
        assertTrue(editor.contains("openText = buffer.text"))
        assertTrue(editor.contains("openDirty = viewModel.isDirty.value"))
        assertEquals(1, Regex("onAskProject = aiAskProject").findAll(editor).count())
        assertEquals(1, Regex("aiViewModel\\.agentAsk\\(").findAll(editor).count())
    }

    @Test
    fun `the sheet offers the third source and disables it while a walk runs`() {
        val sheet = ai("AiChatSheet.kt")
        assertTrue(sheet.contains("AiCopy.ASK_PROJECT"))
        assertTrue(sheet.contains("enabled = !state.gathering"))
        assertTrue(sheet.contains("onAskProject(question)"))
        // the preview names the files, which is the whole point of a Level 2 preview
        assertTrue(sheet.contains("AiCopy.PROJECT_PREVIEW_TITLE"))
        assertTrue(sheet.contains("AiPromptText.projectFileLines("))
        assertTrue(sheet.contains("AiPromptText.projectLeftOutLine("))
    }

    @Test
    fun `the send arrow follows the question, it is not hardcoded to the selection`() {
        // Phase 78 device round 1: the arrow was wired straight to
        // onExplainSelection, so a typed question with nothing selected hit the
        // blank-selection refusal and answered "Select some code in the editor
        // first" — a dead end wearing a send icon. Pinned so it cannot go back.
        val sheet = ai("AiChatSheet.kt")
        assertTrue(
            "the arrow must branch on whether code is selected",
            sheet.contains("if (hasSelection) onExplainSelection(question) else onAskProject(question)")
        )
        assertFalse(
            "the arrow must no longer be a bare onExplainSelection call",
            sheet.contains("IconButton(onClick = { onDismissNotice(); onExplainSelection(question) })")
        )
        // the hint shown when nothing is selected must offer the project route too
        val copy = File(aiDir, "AiCopy.kt").readText()
        assertTrue(
            "the no-selection hint must mention asking about the project",
            copy.contains("ask about the whole")
        )
    }

    // ---- 3. read-only, nothing saved, nothing logged (D1/D6) --------------

    @Test
    fun `the walk reads and does nothing else`() {
        for (f in newFiles) {
            val code = ai(f)
            for (banned in listOf(
                "writeText", "writeBytes", "mkdirs", "delete(", "deleteRecursively",
                "ProcessBuilder", "Runtime.getRuntime", "renameTo", "setWritable"
            )) {
                assertFalse("$f must not write or run anything ($banned)", code.contains(banned))
            }
        }
    }

    @Test
    fun `no index, cache or preference is persisted by the new code`() {
        for (f in newFiles) {
            val code = ai(f)
            for (banned in listOf("SharedPreferences", "dataStore", "DataStore", "Properties", "AiKeyStore")) {
                assertFalse("$f must not persist anything ($banned)", code.contains(banned))
            }
        }
        // the AI properties file gained no key in this phase
        val store = aiRaw("AiKeyStore.kt")
        for (banned in listOf("project_index", "ai_index", "exclusions", "last_project")) {
            assertFalse("AiKeyStore must not gain a Level 2 key ($banned)", store.contains(banned))
        }
    }

    @Test
    fun `nothing in the new files logs`() {
        for (f in newFiles) {
            val code = ai(f)
            for (banned in listOf("Log.", "AppLogger", "println(", "printStackTrace")) {
                assertFalse("$f must not log ($banned)", code.contains(banned))
            }
        }
    }

    // ---- 4. minSdk 24 -----------------------------------------------------

    @Test
    fun `the walk uses only APIs that exist on minSdk 24`() {
        val reader = ai("AiProjectReader.kt")
        for (banned in listOf("java.nio", "isSymbolicLink", "readNBytes", "readAllBytes", "walkTopDown", "toPath()")) {
            assertFalse("AiProjectReader must not use $banned (API 26+/33+)", reader.contains(banned))
        }
        // the symlink test is the canonical-path comparison the search already uses
        assertTrue(reader.contains("file.canonicalPath != file.absolutePath"))
    }

    // ---- 5. the prompt is the preview -------------------------------------

    @Test
    fun `a project prompt carries its file list, and the body is built from it`() {
        val ctx = ai("AiContext.kt")
        assertTrue(ctx.contains("PROJECT"))
        assertTrue(ctx.contains("data class AiProjectSummary("))
        assertTrue(ctx.contains("fun projectBody("))
        assertTrue(ctx.contains("fun projectFileLines("))
        // the request body is still the same two strings the preview renders
        val req = ai("GeminiRequest.kt")
        assertTrue(req.contains("prompt.systemInstruction"))
        assertTrue(req.contains("prompt.userText"))
        // pinned as written: the source line is append("\"store\":false")
        assertTrue(
            "every body must still carry store:false",
            File(aiDir, "GeminiRequest.kt").readText().contains("""append("\"store\":false")""")
        )
    }

    @Test
    fun `the third source is refused, never silently narrowed, when nothing fits`() {
        val ctx = ai("AiContext.kt")
        assertTrue(ctx.contains("NO_PROJECT_FILES"))
        assertTrue(ctx.contains("PROJECT_TOO_LARGE"))
        val copy = ai("AiCopy.kt")
        assertTrue(copy.contains("NO_PROJECT_FILES"))
        assertTrue(copy.contains("PROJECT_TOO_LARGE"))
    }

    @Test
    fun `the budget is packed against the one existing ceiling, not a new one`() {
        val policy = ai("AiProjectFiles.kt")
        assertTrue(policy.contains("AiLimits.MAX_CONTEXT_CHARS"))
        assertFalse("no second context ceiling may be invented", policy.contains("MAX_TOTAL_CHARS"))
        // and the value Level 1 shipped with is untouched
        val limits = ai("AiPolicy.kt")
        assertTrue(limits.contains("MAX_CONTEXT_CHARS = 12_000"))
    }

    @Test
    fun `no new dependency, permission or Settings control arrived with Level 2`() {
        // The overlay permission is the one a "floating AI over other apps"
        // feature would have needed; the Phase 77 README rejected it and it
        // must stay rejected. (The app's existing storage permissions are
        // pre-existing and are not this phase's business.)
        val manifest = RepoFiles.mainSource("app/src/main/AndroidManifest.xml").readText()
        assertFalse("no SYSTEM_ALERT_WINDOW", manifest.contains("SYSTEM_ALERT_WINDOW"))
        for (f in newFiles) {
            val code = ai(f)
            assertFalse("$f must not add a Settings control", code.contains("SettingsManager"))
        }
        // no new artifact in the app module's dependency block
        val gradle = RepoFiles.mainSource("app/build.gradle.kts").readText()
        for (banned in listOf("embedding", "vectorstore", "onnx", "tensorflow", "llama", "openai", "langchain")) {
            assertFalse("no AI dependency may be added ($banned)", gradle.lowercase().contains(banned))
        }
    }
}
