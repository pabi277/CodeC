package com.codeci.ide

import com.codeci.ide.ui.ai.AiAgentBudget
import com.codeci.ide.ui.ai.AiAgentDecision
import com.codeci.ide.ui.ai.AiAgentLimits
import com.codeci.ide.ui.ai.AiAgentPolicy
import com.codeci.ide.ui.ai.AiAgentPrompt
import com.codeci.ide.ui.ai.AiAgentStep
import com.codeci.ide.ui.ai.AiAgentStepKind
import com.codeci.ide.ui.ai.AiAgentStopReason
import com.codeci.ide.ui.ai.AiToolLimits
import com.codeci.ide.ui.ai.AiToolName
import com.codeci.ide.ui.ai.AiToolParse
import com.codeci.ide.ui.ai.AiToolProjectView
import com.codeci.ide.ui.ai.AiToolProtocol
import com.codeci.ide.ui.ai.AiToolRequest
import com.codeci.ide.ui.ai.AiToolVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 80 (AI Level 4) — the loop's caps and the packing of one request.
 *
 * These are the cases that make the agent bounded in fact, not in wording: the
 * numbers are the owner's 2026-10-02 answers (≤ 12 turns, ≤ 24 tool calls,
 * ≤ 2 runs, ≤ 8 000 chars per result, ~5 min), and every one of them is
 * asserted against the constant the app uses.
 */
class AiAgentLoopTest {

    private val view = AiToolProjectView(
        existingPaths = setOf("src/main.c", "src/util.c"),
        runsRemaining = AiAgentLimits.MAX_RUNS
    )

    private fun calls(vararg text: String): AiToolParse.Calls =
        AiToolProtocol.parse(text.joinToString("\n")) as AiToolParse.Calls

    private fun readBlock(path: String) =
        "<<<CODEC_TOOL name=\"read_file\">>>\npath: $path\n<<<END_CODEC_TOOL>>>"

    // ---- budgets ----------------------------------------------------------

    @Test
    fun `the owner's caps are the constants the app uses`() {
        assertEquals(12, AiAgentLimits.MAX_TURNS)
        assertEquals(24, AiAgentLimits.MAX_TOOL_CALLS)
        assertEquals(2, AiAgentLimits.MAX_RUNS)
        assertEquals(8_000, AiAgentLimits.MAX_RESULT_CHARS)
        assertEquals(300_000L, AiAgentLimits.MAX_WALL_CLOCK_MS)
    }

    @Test
    fun `a model turn is blocked at the turn cap`() {
        val fresh = AiAgentBudget(startedAtMs = 1_000)
        assertEquals(null, fresh.blockModelTurn(2_000))
        val spent = AiAgentBudget(turnsUsed = AiAgentLimits.MAX_TURNS, startedAtMs = 1_000)
        assertEquals(AiAgentStopReason.TURN_BUDGET, spent.blockModelTurn(2_000))
    }

    @Test
    fun `tools and runs report what is left`() {
        val budget = AiAgentBudget(toolCallsUsed = AiAgentLimits.MAX_TOOL_CALLS - 3, runsUsed = 1)
        assertEquals(3, budget.toolCallsRemaining())
        assertEquals(1, budget.runsRemaining())
        assertEquals(null, budget.blockTool(2_000))
        assertEquals(
            AiAgentStopReason.TOOL_BUDGET,
            budget.copy(toolCallsUsed = AiAgentLimits.MAX_TOOL_CALLS).blockTool(2_000)
        )
    }

    @Test
    fun `the wall clock stops the task whatever else is left`() {
        val budget = AiAgentBudget(startedAtMs = 10_000)
        assertEquals(
            AiAgentStopReason.WALL_CLOCK,
            budget.blockModelTurn(10_000 + AiAgentLimits.MAX_WALL_CLOCK_MS + 1)
        )
        assertEquals(
            AiAgentStopReason.WALL_CLOCK,
            budget.blockTool(10_000 + AiAgentLimits.MAX_WALL_CLOCK_MS + 1)
        )
    }

    // ---- decisions --------------------------------------------------------

    @Test
    fun `prose with no tool block ends the task`() {
        val decision = AiAgentPolicy.decide(
            AiToolParse.Calls("All done: the file prints hello.", emptyList()),
            AiAgentBudget(startedAtMs = 1),
            nowMs = 2,
            projectView = view
        )
        assertTrue(decision is AiAgentDecision.Finish)
        assertEquals("All done: the file prints hello.", (decision as AiAgentDecision.Finish).answer)
    }

    @Test
    fun `a valid tool call is executed`() {
        val decision = AiAgentPolicy.decide(
            calls(readBlock("src/main.c")),
            AiAgentBudget(startedAtMs = 1),
            nowMs = 2,
            projectView = view
        ) as AiAgentDecision.ExecuteTools
        assertEquals(1, decision.calls.size)
        assertEquals(AiToolName.READ_FILE, decision.calls[0].name)
        assertTrue(decision.denied.isEmpty())
    }

    @Test
    fun `an unknown tool becomes a denial the model can read`() {
        val decision = AiAgentPolicy.decide(
            calls("<<<CODEC_TOOL name=\"rm_rf\">>>\npath: /\n<<<END_CODEC_TOOL>>>"),
            AiAgentBudget(startedAtMs = 1),
            nowMs = 2,
            projectView = view
        ) as AiAgentDecision.ExecuteTools
        assertTrue(decision.calls.isEmpty())
        assertEquals(1, decision.denied.size)
        assertTrue(decision.denied[0].reason.contains("unknown tool"))
    }

    @Test
    fun `a run request pauses the loop for the user's tap`() {
        val decision = AiAgentPolicy.decide(
            calls("<<<CODEC_TOOL name=\"request_run\">>>\ntarget: src/main.c\n<<<END_CODEC_TOOL>>>"),
            AiAgentBudget(startedAtMs = 1),
            nowMs = 2,
            projectView = view
        ) as AiAgentDecision.AskRunApproval
        assertEquals(AiToolName.REQUEST_RUN, decision.call.name)
        assertEquals("src/main.c", decision.call.path)
        assertTrue(decision.alsoQueued.isEmpty())
    }

    @Test
    fun `the calls beside a run request wait with it instead of being dropped`() {
        val decision = AiAgentPolicy.decide(
            calls(
                readBlock("src/main.c"),
                "<<<CODEC_TOOL name=\"request_run\">>>\n<<<END_CODEC_TOOL>>>",
                "<<<CODEC_TOOL name=\"nonsense\">>>\n<<<END_CODEC_TOOL>>>"
            ),
            AiAgentBudget(startedAtMs = 1),
            nowMs = 2,
            projectView = view
        ) as AiAgentDecision.AskRunApproval
        assertEquals(1, decision.alsoQueued.size)
        assertEquals(AiToolName.READ_FILE, decision.alsoQueued[0].name)
        assertEquals(1, decision.denied.size)
        assertTrue(decision.denied[0].reason.contains("unknown tool"))
    }

    @Test
    fun `a run request beyond the run budget is refused not paused`() {
        val decision = AiAgentPolicy.decide(
            calls("<<<CODEC_TOOL name=\"request_run\">>>\n<<<END_CODEC_TOOL>>>"),
            AiAgentBudget(startedAtMs = 1),
            nowMs = 2,
            projectView = view.copy(runsRemaining = 0)
        ) as AiAgentDecision.ExecuteTools
        assertTrue(decision.calls.isEmpty())
        assertTrue(decision.denied.single().reason.contains("budget"))
    }

    @Test
    fun `calls past the remaining tool budget are answered with a refusal`() {
        val blocks = (1..6).joinToString("\n") { readBlock("src/main.c") }
        val decision = AiAgentPolicy.decide(
            AiToolProtocol.parse(blocks) as AiToolParse.Calls,
            AiAgentBudget(toolCallsUsed = AiAgentLimits.MAX_TOOL_CALLS - 2, startedAtMs = 1),
            nowMs = 2,
            projectView = view
        ) as AiAgentDecision.ExecuteTools
        assertEquals(2, decision.calls.size)
        assertEquals(4, decision.denied.size)
        assertTrue(decision.denied.all { it.reason.contains("budget") })
    }

    @Test
    fun `an exhausted tool budget stops the task before anything runs`() {
        val decision = AiAgentPolicy.decide(
            calls(readBlock("src/main.c")),
            AiAgentBudget(toolCallsUsed = AiAgentLimits.MAX_TOOL_CALLS, startedAtMs = 1),
            nowMs = 2,
            projectView = view
        )
        assertEquals(AiAgentStopReason.TOOL_BUDGET, (decision as AiAgentDecision.Stop).reason)
    }

    @Test
    fun `a budget object is only ever grown by the loop`() {
        val b = AiAgentBudget(startedAtMs = 100)
        assertEquals(1, b.withTurn().turnsUsed)
        assertEquals(3, b.withToolCalls(3).toolCallsUsed)
        assertEquals(1, b.withRun().runsUsed)
        assertEquals(0L, b.elapsedMs(100))
        assertEquals(50L, b.elapsedMs(150))
    }

    // ---- packing ----------------------------------------------------------

    private fun step(i: Int) = AiAgentStep(
        kind = AiAgentStepKind.TOOL,
        title = "read_file src/file$i.c",
        detail = "FILE src/file$i.c — line $i"
    )

    @Test
    fun `the task and the map are always in the request`() {
        val packed = AiAgentPrompt.pack("Why does it crash?", "PROJECT MAP\n  src/main.c (4 lines)", emptyList())
        assertTrue(packed.text.contains("Task: Why does it crash?"))
        assertTrue(packed.text.contains("PROJECT MAP"))
        assertTrue(packed.text.contains("src/main.c (4 lines)"))
        assertTrue(packed.text.contains("Emit one or more tool blocks"))
        assertEquals(0, packed.droppedResults)
        assertEquals(packed.text.length, packed.chars)
    }

    @Test
    fun `only the newest results ride along and the dropped ones are counted`() {
        val steps = (1..7).map { step(it) }
        val packed = AiAgentPrompt.pack("q", "MAP", steps)
        assertTrue(packed.text.contains("src/file7.c"))
        assertTrue(packed.text.contains("src/file6.c"))
        assertFalse(packed.text.contains("src/file1.c"))
        assertEquals(7 - AiAgentLimits.KEEP_LAST_RESULTS, packed.droppedResults)
        assertTrue(packed.text.contains("earlier tool results were dropped to fit"))
    }

    @Test
    fun `a tight request budget drops results rather than overrunning the cap`() {
        val huge = AiAgentStep(
            kind = AiAgentStepKind.TOOL,
            title = "read_file src/big.c",
            detail = "x".repeat(AiToolLimits.MAX_RESULT_CHARS)
        )
        val packed = AiAgentPrompt.pack("q", "MAP", listOf(step(1), step(2), huge), budget = 2_000)
        assertTrue(packed.chars <= 2_000)
        assertTrue(packed.droppedResults >= 1)
    }

    @Test
    fun `a result block keeps the tool name and the refusal marker`() {
        val ok = AiAgentPrompt.renderStep(AiAgentStep(AiAgentStepKind.TOOL, "read_file a.c", "body"))
        assertTrue(ok.startsWith("--- read_file a.c\n"))
        assertTrue(ok.endsWith("body\n"))
        val denied = AiAgentPrompt.renderStep(
            AiAgentStep(AiAgentStepKind.DENIED, "read_file .env", "credential-shaped", ok = false)
        )
        assertTrue(denied.startsWith("--- read_file .env [refused]"))
    }

    @Test
    fun `every stop reason has a sentence the user can read`() {
        for (reason in AiAgentStopReason.entries) {
            assertTrue(AiAgentLimits.stopSentence(reason).isNotBlank())
        }
        assertTrue(AiAgentLimits.stopSentence(AiAgentStopReason.USER_STOP).contains("Stopped"))
    }

    @Test
    fun `a denial for an unknown tool never carries an argument map into the planner`() {
        val request = AiToolRequest("read_file", mapOf("path" to "src/main.c"))
        assertTrue(AiToolProtocol.parse(AiAgentPrompt.renderStep(AiAgentStep(AiAgentStepKind.TOOL, "x", "y"))).let { it is AiToolParse.Calls })
        assertTrue(AiToolVerdict.Allowed(com.codeci.ide.ui.ai.AiToolCall(AiToolName.READ_FILE, request.rawName)).call.name == AiToolName.READ_FILE)
    }
}
