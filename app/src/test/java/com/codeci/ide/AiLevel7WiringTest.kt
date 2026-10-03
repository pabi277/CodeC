package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 84 (AI Level 7) — source pins for the six correctness fixes. A pure
 * policy is decoration unless the app really calls it, so each fix is pinned to
 * the production wiring through [RepoFiles.codeOnly] (comments and string
 * literals blanked, so a sentence can neither satisfy nor trip a pin).
 */
class AiLevel7WiringTest {

    private val aiDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private fun aiRaw(name: String): String = File(aiDir, name).readText()
    private fun ai(name: String): String = RepoFiles.codeOnly(aiRaw(name))

    // ---- fix 1 / fix 5 (S1, S2): two stores, marker preserved --------------

    @Test
    fun `the request packer reads modelResult and never the timeline detail`() {
        val loop = ai("AiAgentLoop.kt")
        val renderStep = loop.substringAfter("fun renderStep(").substringBefore("\n    }")
        assertTrue("renderStep must pack the full-fidelity result", renderStep.contains("step.modelResult"))
        assertFalse("renderStep must never pack the clipped preview", renderStep.contains("step.detail"))
    }

    @Test
    fun `the view model stores the full result beside a clipped preview`() {
        val vm = ai("AiViewModel.kt")
        assertTrue("the tool step must carry the full result to the model", vm.contains("modelResult = outcome.text"))
        assertTrue(
            "the timeline preview must be clipped by the marker-preserving helper",
            vm.contains("AiAgentLimits.timelineDetail(outcome.text, AiToolRunner.CUT_NOTE)")
        )
    }

    @Test
    fun `the timeline clip helper preserves the cut marker`() {
        val loop = ai("AiAgentLoop.kt")
        assertTrue(loop.contains("fun timelineDetail(full: String, cutMarker: String)"))
        assertTrue("it must only re-add a marker the result already had", loop.contains("full.contains(cutMarker)"))
    }

    // ---- fix 2: the tool budget gates every resume path --------------------

    @Test
    fun `resumeAgentOrStop gates on the combined turn and tool budget`() {
        val vm = ai("AiViewModel.kt")
        val resume = vm.substringAfter("private fun resumeAgentOrStop(").substringBefore("private fun")
        assertTrue("the resume path must consult blockResume", resume.contains("blockResume("))
        assertFalse("it must no longer gate on the turn budget alone", resume.contains("blockModelTurn("))
        val loop = ai("AiAgentLoop.kt")
        assertTrue(loop.contains("fun blockResume(nowMs: Long)"))
    }

    // ---- fix 3: split, clamped counters ------------------------------------

    @Test
    fun `refusals are counted on a separate clamped counter`() {
        val loop = ai("AiAgentLoop.kt")
        assertTrue(loop.contains("fun withRefused(n: Int)"))
        assertTrue("executions are clamped to the cap", loop.contains("coerceIn(0, AiAgentLimits.MAX_TOOL_CALLS)"))
        val vm = ai("AiViewModel.kt")
        assertTrue("denied blocks increment the refused counter", vm.contains("withRefused(denied.size)"))
        assertTrue("a malformed block increments the refused counter", vm.contains("withRefused(1)"))
    }

    @Test
    fun `the usage line takes refused and reused separately`() {
        // The copy lives in string literals, so read the raw source for the signature.
        val copy = aiRaw("AiCopy.kt")
        assertTrue(copy.contains("refused: Int = 0"))
        assertTrue(copy.contains("reused: Int = 0"))
        val sheet = ai("AiChatSheet.kt")
        assertTrue("the sheet must pass the split counters", sheet.contains("usage.refused"))
    }

    // ---- fix 4 (S12): reserved masked synthesis turn -----------------------

    @Test
    fun `a budget stop is routed through one reserved synthesis turn`() {
        val vm = ai("AiViewModel.kt")
        assertTrue("stopAgent must offer a synthesis turn", vm.contains("finalSynthesis = true"))
        assertTrue("the synthesis turn runs at most once", vm.contains("synthesisDone"))
        assertTrue("USER_STOP must not trigger a delayed call", vm.contains("AiAgentStopReason.USER_STOP"))
        assertTrue("the terminal stop must set a prose answer", vm.contains("proseOnly("))
    }

    @Test
    fun `the synthesis turn reuses the agent turn so no fourth stream site appears`() {
        val vm = ai("AiViewModel.kt")
        assertEquals(
            "exactly three stream sites: helper send, agentTurn, test connection",
            3, Regex("client[.]stream[(]").findAll(vm).count()
        )
        assertTrue("the synthesis path calls agentTurn, not a new stream", vm.contains("agentTurn(session, finalSynthesis = true"))
        val loop = ai("AiAgentLoop.kt")
        assertTrue(loop.contains("fun finalSynthesis("))
        assertTrue(loop.contains("FINAL_SYNTHESIS_INSTRUCTION"))
    }

    // ---- fix 6: the parser keeps the good calls ----------------------------

    @Test
    fun `every malformed branch carries the calls parsed so far`() {
        val tools = ai("AiTools.kt")
        // All four malformed returns append the accumulated calls; `calls.toList()`
        // appears nowhere else in the file.
        assertEquals("all four malformed returns must keep the parsed calls", 4, Regex("""calls\.toList\(\)""").findAll(tools).count())
        assertTrue(tools.contains("val calls: List<AiToolRequest> = emptyList()"))
    }

    // ---- standing security boundaries re-asserted --------------------------

    @Test
    fun `no ai file executes a command or reaches a terminal or installer`() {
        // S6/S7: ui/ai runs nothing. (Project-write freedom is pinned by
        // AiLevel4WiringTest on the tool runner; AiKeyStore legitimately writes
        // its own encrypted blob under no_backup/ai/, which is not a project write.)
        for (f in aiDir.listFiles { x -> x.extension == "kt" }!!) {
            val code = RepoFiles.codeOnly(f.readText())
            for (banned in listOf(
                "ProcessBuilder", "Runtime.getRuntime", "sendCommand(",
                "TerminalViewModel", "confirmInstall", "installCommand(", "pkg install"
            )) {
                assertFalse("${f.name} must run no command ($banned)", code.contains(banned))
            }
        }
    }

    @Test
    fun `the pure loop and tool files stay Android-free`() {
        for (name in listOf("AiAgentLoop.kt", "AiTools.kt")) {
            val raw = aiRaw(name)
            for (banned in listOf("import java.io", "import android.", "import androidx.")) {
                assertFalse("$name must stay pure ($banned)", raw.contains(banned))
            }
        }
    }
}
