package com.codeci.ide

import com.codeci.ide.ui.ai.AiAgentLimits
import com.codeci.ide.ui.ai.AiPromptText
import com.codeci.ide.ui.ai.AiToolLimits
import com.codeci.ide.ui.ai.AiToolName
import com.codeci.ide.ui.ai.AiToolProtocol
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 80 (AI Level 4) — wiring and source-scan pins for the bounded agent:
 * read-only tools, one preview per task, a run only through CodeC's own RUN
 * action, and the D6 limit that permits only Level 9's separate bounded task memory.
 *
 * Executed through [RepoFiles.codeOnly], so a comment or a string literal can
 * neither satisfy nor trip a structural pin (the Phase 45 lesson: *pin the
 * button, never the word*).
 */
class AiLevel4WiringTest {

    private val aiDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private val projectsDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/projects")

    private fun aiRaw(name: String): String = File(aiDir, name).readText()
    private fun ai(name: String): String = RepoFiles.codeOnly(aiRaw(name))
    private fun proj(name: String): String = RepoFiles.codeOnly(File(projectsDir, name).readText())
    private fun screen(): String = RepoFiles.codeOnly(
        File(RepoFiles.root(), "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
    )

    // ---- D1: the tool surface is read-only --------------------------------

    @Test
    fun `the tool runner opens files for reading and nothing else`() {
        val runner = ai("AiToolRunner.kt")
        for (banned in listOf(
            "writeText(", "FileOutputStream", "OutputStreamWriter", "appendText(",
            "createNewFile(", "mkdir", "delete(", "deleteRecursively", "renameTo(", "setWritable"
        )) {
            assertFalse("AiToolRunner.kt must stay read-only ($banned)", runner.contains(banned))
        }
        assertTrue("tools must refuse credential-shaped paths", runner.contains("AiProjectFiles.isSecretLike("))
        assertTrue("tools must refuse symlinks and escapes", runner.contains("isSymlink("))
        assertTrue("tools must enforce the walk's admitted paths", runner.contains("paths: List<String>"))
    }

    @Test
    fun `no AI file executes commands or reaches a terminal`() {
        for (f in aiDir.listFiles { x -> x.extension == "kt" }!!) {
            val code = RepoFiles.codeOnly(f.readText())
            for (banned in listOf(
                "ProcessBuilder", "Runtime.getRuntime", "sendCommand(", "TerminalViewModel",
                "confirmInstall", "installCommand(", "pkg install"
            )) {
                assertFalse("${f.name} must not run commands ($banned)", code.contains(banned))
            }
        }
    }

    @Test
    fun `request_run is an approval request and never a call into a runner`() {
        val tools = ai("AiTools.kt")
        assertTrue(tools.contains("REQUEST_RUN"))
        assertTrue(tools.contains("runsRemaining"))
        val runner = ai("AiToolRunner.kt")
        assertTrue(
            "the runner must answer a run request without executing anything",
            runner.contains("AiToolName.REQUEST_RUN -> Outcome(false,")
        )
        for (banned in listOf("runFile(", "runActiveFile(", "ExecutionRunner")) {
            assertFalse("ui/ai must never start a run itself ($banned)", runner.contains(banned))
        }
    }

    // ---- D4 as amended: one preview, then read-only steps ------------------

    @Test
    fun `the agent's turns exist only behind the task preview's Send`() {
        val vm = ai("AiViewModel.kt")
        assertEquals("three stream sites: send, agentTurn, test connection", 3, Regex("client[.]stream[(]").findAll(vm).count())
        assertTrue("the loop must be private", vm.contains("private fun agentTurn("))
        assertTrue(
            "the agent branch must sit inside send() behind the preview gate",
            vm.substringAfter("fun send()").substringBefore("fun stop()")
                .contains("if (prompt.agent)")
        )
        assertTrue(vm.contains("if (_state.value.phase != AiPhase.PREVIEW"))
        // Every model turn is announced in the timeline, so the user sees what
        // was sent for each one (D4, amended 2026-10-02).
        assertTrue(vm.contains("AiAgentStepKind.ANSWER"))
    }

    @Test
    fun `the agent surface's own sentences exist and name the taps`() {
        // The copy lives in string literals, so this half reads the raw source
        // (AiCopy cannot be compiled without the Android-dependent projects
        // files); the promise itself is asserted on the instruction VALUES above.
        val copy = aiRaw("AiCopy.kt")
        assertTrue(copy.contains("nothing runs until you tap Run"))
        assertTrue(copy.contains("AGENT_RUN_RUNNING"))
        assertTrue(copy.contains("AGENT_RUN_SKIP"))
        assertEquals(AiToolLimits.MAX_RESULT_CHARS, AiAgentLimits.MAX_RESULT_CHARS)
    }

    @Test
    fun `the caps are the owner's numbers and the app enforces them`() {
        val loop = ai("AiAgentLoop.kt")
        assertTrue(loop.contains("const val MAX_TURNS = 12"))
        assertTrue(loop.contains("const val MAX_TOOL_CALLS = 24"))
        assertTrue(loop.contains("const val MAX_RUNS = 2"))
        assertTrue(loop.contains("const val MAX_RESULT_CHARS = AiToolLimits.MAX_RESULT_CHARS"))
        assertTrue(loop.contains("const val MAX_WALL_CLOCK_MS = 300_000L"))
        // Phase 87 (Level 10, 87.6): both gates take the task's caps now. The
        // second parameter defaults to the plain constants, so an unextended
        // task is gated by exactly the numbers asserted above — pinning the
        // defaulted signature keeps that guarantee in the pin.
        assertTrue(
            "the wall clock must be checked before a turn",
            loop.contains("blockModelTurn(nowMs: Long, caps: AiAgentCaps = AiAgentCaps())")
        )
        assertTrue(
            "the wall clock must be checked before a tool",
            loop.contains("blockTool(nowMs: Long, caps: AiAgentCaps = AiAgentCaps())")
        )
        assertTrue("the policy decides, the view model executes", loop.contains("object AiAgentPolicy"))
    }

    @Test
    fun `the tools fail closed on anything they do not understand`() {
        val tools = ai("AiTools.kt")
        assertTrue("an unknown name must become a denial", tools.contains("?: return AiToolVerdict.Denied("))
        assertTrue("an unexpected argument must be refused", tools.contains("request.args.keys - allowedKeys"))
        assertTrue("the protocol is parsed locally", tools.contains("object AiToolProtocol"))
        val raw = aiRaw("AiTools.kt")
        assertFalse("native function calling was not adopted", RepoFiles.codeOnly(raw).contains("functionCall"))
        // The wire format is CodeC's own and is taught in the sent text.
        assertTrue(AiToolProtocol.INSTRUCTIONS.contains("<<<CODEC_TOOL name="))
        for (name in AiToolName.entries) {
            assertTrue("every tool must be named in the instructions", AiToolProtocol.INSTRUCTIONS.contains(name.wire))
        }
    }

    // ---- the run loop: CodeC's own RUN, then a disclosed digest ------------

    @Test
    fun `the approved run goes through the editor's own RUN action`() {
        val screenCode = screen()
        val approve = screenCode.substringAfter("val aiApproveRun: () -> Unit =").substringBefore("val aiSkipRun")
        assertTrue(
            "the AI run must be the editor's own runFile entry point",
            approve.contains("viewModel.runFile(context, target)")
        )
        assertFalse("no duplicate runner may appear", approve.contains("ExecutionRunner("))
        assertFalse("no terminal may be used", approve.contains("sendCommand"))
        assertFalse("no package install may be started", approve.contains("confirmInstall"))
        assertTrue("a run that cannot start must be reported", approve.contains("onAgentRunNotStarted("))
    }

    @Test
    fun `only a digest of the run goes back to the model`() {
        val digest = ai("AiRunDigest.kt")
        assertTrue(digest.contains("buildExitCode"))
        assertTrue(digest.contains("runExitCode"))
        assertTrue(digest.contains("MAX_DIGEST_CHARS"))
        assertTrue("the digest must count what it left out", aiRaw("AiRunDigest.kt").contains("lines left out"))
        val screenCode = screen()
        assertTrue(
            "the editor must hand the digest to the loop",
            screenCode.contains("aiViewModel.onAgentRunFinished(")
        )
    }

    // ---- the map: full knowledge, still bounded ---------------------------

    @Test
    fun `the map is bounded and drops what the walk refused`() {
        val map = ai("AiRepoMap.kt")
        assertTrue(map.contains("const val MAX_MAP_CHARS"))
        assertTrue(map.contains("AiProjectFiles.isSecretLike("))
        assertTrue(map.contains("AiProjectFiles.isExcludedDirectory("))
        assertTrue("the map must reserve room for its own elision", map.contains("ELISION_RESERVE"))
        assertTrue("and it must report the elision", map.contains("val elided = pathLines.size < fileCount"))
        for (banned in listOf("import java.io", "import android.", "import androidx.")) {
            assertFalse("AiRepoMap.kt must stay pure ($banned)", aiRaw("AiRepoMap.kt").contains(banned))
        }
    }

    @Test
    fun `the pure tool and loop files stay Android-free`() {
        for (name in listOf("AiRepoMap.kt", "AiTools.kt", "AiAgentLoop.kt", "AiRunDigest.kt")) {
            val raw = aiRaw(name)
            for (banned in listOf("import java.io", "import android.", "import androidx.")) {
                assertFalse("$name must stay pure ($banned)", raw.contains(banned))
            }
        }
        assertTrue("the executor may use java.io, but not Android", aiRaw("AiToolRunner.kt").contains("import java.io.File"))
    }

    @Test
    fun `an agent edit still needs the Level 3 diff review`() {
        val context = ai("AiContext.kt")
        assertTrue(context.contains("AGENT_EDIT_SYSTEM_INSTRUCTION"))
        // The promises the user reads are asserted on the VALUES (which is what
        // actually ships), not on a source scan that blanks string literals.
        assertTrue(
            "the ask instruction must promise that nothing is changed",
            AiPromptText.AGENT_ASK_SYSTEM_INSTRUCTION.contains("You cannot change files, run programs, install packages")
        )
        assertTrue(
            "the edit instruction must promise the local diff and the user's tap",
            AiPromptText.AGENT_EDIT_SYSTEM_INSTRUCTION.contains("CodeC computes a local diff and asks the user before applying")
        )
        assertTrue(
            "both instructions must teach the tool protocol",
            AiPromptText.AGENT_ASK_SYSTEM_INSTRUCTION.contains("<<<CODEC_TOOL name=") &&
                AiPromptText.AGENT_EDIT_SYSTEM_INSTRUCTION.contains("<<<CODEC_TOOL name=")
        )
        val vm = ai("AiViewModel.kt")
        val finish = vm.substringAfter("private fun finishAgent(").substringBefore("private fun stopAgent(")
        assertTrue("an agent edit is parsed by the Level 3 parser", finish.contains("AiEditProposalParser.parse("))
        assertTrue("and it must not apply itself", !finish.contains("AiEditApplier"))
    }

    // ---- D6: chat state stays ephemeral; task memory is the narrow amendment ----

    @Test
    fun `the timeline stays in memory and bounded task memory adds no settings key`() {
        val vm = ai("AiViewModel.kt")
        for (banned in listOf("rememberSaveable", "SavedStateHandle", "Properties", "SharedPreferences")) {
            assertFalse("AiViewModel must not persist chat or timeline state ($banned)", vm.contains(banned))
        }
        assertTrue("only the separate Level 9 task-memory store is wired", vm.contains("AiTaskMemoryStore"))
        // The keys are string literals, so this half reads the RAW source: the
        // settings store must not become a transcript or task-memory database.
        val store = aiRaw("AiKeyStore.kt")
        for (key in listOf("bubble_pos", "bubble_show", "sheet_with_output", "model")) {
            assertTrue("the AI settings keys stay the Phase 76-79 set ($key)", store.contains(key))
        }
        assertFalse("no agent chat or task-memory key may be added to properties", store.contains("agent_"))
        assertTrue("task memory is cleared on key deletion", store.contains("AiTaskMemoryStore.clearAll"))
    }

    // ---- the surface: the timeline, the approval card, the Stop -----------

    @Test
    fun `the sheet shows the timeline the approval card and keeps Stop available`() {
        val sheet = ai("AiChatSheet.kt")
        assertTrue(sheet.contains("AgentActivityCard("))
        assertTrue(sheet.contains("AgentRunCard("))
        assertTrue(sheet.contains("AiCopy.AGENT_RUN_APPROVE"))
        assertTrue(sheet.contains("AiCopy.AGENT_RUN_SKIP"))
        assertTrue("the Run button must not race a live run", sheet.contains("enabled = !runBusy"))
        assertTrue("Stop stays on the streaming bar", sheet.contains("AiCopy.STOP"))
        assertTrue("the preview names the tool allowlist", sheet.contains("AiCopy.agentPreviewNote()"))
    }
}
