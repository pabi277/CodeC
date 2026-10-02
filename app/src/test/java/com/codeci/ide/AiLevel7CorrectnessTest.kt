package com.codeci.ide

import com.codeci.ide.ui.ai.AiAgentBudget
import com.codeci.ide.ui.ai.AiAgentLimits
import com.codeci.ide.ui.ai.AiAgentPrompt
import com.codeci.ide.ui.ai.AiAgentStep
import com.codeci.ide.ui.ai.AiAgentStepKind
import com.codeci.ide.ui.ai.AiAgentStopReason
import com.codeci.ide.ui.ai.AiCopy
import com.codeci.ide.ui.ai.AiToolLimits
import com.codeci.ide.ui.ai.AiToolName
import com.codeci.ide.ui.ai.AiToolParse
import com.codeci.ide.ui.ai.AiToolProtocol
import com.codeci.ide.ui.ai.AiToolRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 84 (AI Level 7) — the six agent-correctness fixes as pure, host-testable
 * policy. Every case here is one of the acceptance checks in
 * `07_AGENT_CORRECTNESS.md`, and every one of them **failed before this phase**.
 *
 * The wiring that proves the app really uses these lives in
 * [AiLevel7WiringTest]; the measured before/after lives in the Phase 83 baseline
 * (`AiLevel6BaselineTest`), re-run here against the fixed behavior.
 */
class AiLevel7CorrectnessTest {

    // ---- fix 1 (S1): the model reads full fidelity, not the preview ---------

    @Test
    fun `renderStep packs the full model result and never the clipped preview`() {
        val full = "FILE a.c — lines 1-400 of 900\n" + "x".repeat(AiToolLimits.MAX_RESULT_CHARS)
        val step = AiAgentStep(
            kind = AiAgentStepKind.TOOL,
            title = "read_file a.c (lines 1-400)",
            detail = full.take(AiAgentLimits.MAX_STEP_DETAIL_CHARS),
            modelResult = full
        )
        val rendered = AiAgentPrompt.renderStep(step)
        assertTrue("the request must carry the full body", rendered.contains("x".repeat(2_000)))
        assertTrue(rendered.length > AiAgentLimits.MAX_STEP_DETAIL_CHARS)
        // The preview clip is a different, shorter string.
        assertTrue(step.detail.length <= AiAgentLimits.MAX_STEP_DETAIL_CHARS)
    }

    @Test
    fun `a step with no model result packs an empty body not the preview`() {
        // Guards the S1 pin: renderStep must not silently fall back to `detail`.
        val step = AiAgentStep(AiAgentStepKind.TOOL, "read_file a.c", detail = "preview only")
        val rendered = AiAgentPrompt.renderStep(step)
        assertFalse(rendered.contains("preview only"))
    }

    // ---- fix 5 (S2): the cut marker survives every clip boundary -----------

    @Test
    fun `the timeline clip keeps the runner cut marker when the result had one`() {
        val full = "FILE a.c\n" + "y".repeat(AiAgentLimits.MAX_STEP_DETAIL_CHARS) + "\n" + AiToolRunner.CUT_NOTE
        val clipped = AiAgentLimits.timelineDetail(full, AiToolRunner.CUT_NOTE)
        assertTrue(clipped.length <= AiAgentLimits.MAX_STEP_DETAIL_CHARS)
        assertTrue("the honest cut marker must survive the clip", clipped.contains(AiToolRunner.CUT_NOTE))
    }

    @Test
    fun `the timeline clip never invents a marker the result did not have`() {
        val full = "z".repeat(AiAgentLimits.MAX_STEP_DETAIL_CHARS + 500)
        val clipped = AiAgentLimits.timelineDetail(full, AiToolRunner.CUT_NOTE)
        assertTrue(clipped.length <= AiAgentLimits.MAX_STEP_DETAIL_CHARS)
        assertFalse(clipped.contains(AiToolRunner.CUT_NOTE))
    }

    @Test
    fun `a short result is returned unchanged by the timeline clip`() {
        val full = "FILE a.c — lines 1-3 of 3\n1: a\n2: b\n3: c"
        assertEquals(full, AiAgentLimits.timelineDetail(full, AiToolRunner.CUT_NOTE))
    }

    // ---- fix 2: the tool budget gates every resume path --------------------

    @Test
    fun `an exhausted tool budget blocks the resume path even with turns left`() {
        val budget = AiAgentBudget(turnsUsed = 2, toolCallsUsed = AiAgentLimits.MAX_TOOL_CALLS, startedAtMs = 1)
        assertEquals(AiAgentStopReason.TOOL_BUDGET, budget.blockResume(2))
        // The old resume path only checked the turn budget, which was still open.
        assertEquals(null, budget.blockModelTurn(2))
    }

    @Test
    fun `the turn budget is reported before the tool budget on the resume path`() {
        val budget = AiAgentBudget(
            turnsUsed = AiAgentLimits.MAX_TURNS,
            toolCallsUsed = AiAgentLimits.MAX_TOOL_CALLS,
            startedAtMs = 1
        )
        assertEquals(AiAgentStopReason.TURN_BUDGET, budget.blockResume(2))
    }

    @Test
    fun `a fresh budget does not block the resume path`() {
        assertEquals(null, AiAgentBudget(startedAtMs = 1).blockResume(2))
    }

    // ---- fix 3: counters are split and clamped, never one mixed value ------

    @Test
    fun `the execution counter is clamped to the cap and refusals stay separate`() {
        val budget = AiAgentBudget().withToolCalls(49).withRefused(25)
        assertEquals(AiAgentLimits.MAX_TOOL_CALLS, budget.toolCallsUsed)
        assertEquals(25, budget.toolCallsRefused)
        assertEquals(0, budget.toolCallsReused)
    }

    @Test
    fun `the usage line renders executions and refusals separately and never over the cap`() {
        val line = AiCopy.agentUsageLine(turns = 9, toolCalls = 24, runs = 0, refused = 25, reused = 0)
        assertEquals("9 of 12 steps · 24 of 24 reads · 25 refused · 0 of 2 runs", line)
        assertFalse("the mixed '49 of 24 reads' must never render", line.contains("49 of 24"))
        // A clean task shows no refused/reused noise.
        assertEquals(
            "1 of 12 steps · 2 of 24 reads · 0 of 2 runs",
            AiCopy.agentUsageLine(turns = 1, toolCalls = 2, runs = 0)
        )
    }

    @Test
    fun `the usage line coerces an over-cap execution count passed by a caller`() {
        val line = AiCopy.agentUsageLine(turns = 3, toolCalls = 99, runs = 1)
        assertTrue(line.contains("${AiAgentLimits.MAX_TOOL_CALLS} of ${AiAgentLimits.MAX_TOOL_CALLS} reads"))
        assertFalse(line.contains("99 of"))
    }

    // ---- fix 4 (S12): a reserved masked synthesis turn yields prose --------

    @Test
    fun `the final synthesis request masks tools without removing their definitions`() {
        val packed = AiAgentPrompt.finalSynthesis("Why does it crash?", "PROJECT MAP", emptyList())
        assertTrue(packed.text.contains(AiAgentPrompt.FINAL_SYNTHESIS_INSTRUCTION))
        assertTrue(packed.text.contains("Tool use is disabled for this final turn"))
        assertTrue(packed.text.contains("Do not emit"))
        // The task and map are still present, so the model can summarize them.
        assertTrue(packed.text.contains("Task: Why does it crash?"))
        assertTrue(packed.text.contains("PROJECT MAP"))
    }

    @Test
    fun `proseOnly strips complete tool blocks and stops at an unclosed one`() {
        val withBlock = "Here is the answer.\n" +
            "<<<CODEC_TOOL name=\"read_file\">>>\npath: a.c\n<<<END_CODEC_TOOL>>>\n" +
            "And a closing note."
        val prose = AiToolProtocol.proseOnly(withBlock)
        assertTrue(prose.contains("Here is the answer."))
        assertTrue(prose.contains("And a closing note."))
        assertFalse(prose.contains("CODEC_TOOL"))

        val unclosed = "Partial answer.\n<<<CODEC_TOOL name=\"read_file\">>>\npath: a.c"
        assertEquals("Partial answer.", AiToolProtocol.proseOnly(unclosed))
    }

    @Test
    fun `proseOnly of a pure tool answer is blank so the stop sentence can replace it`() {
        val onlyBlock = "<<<CODEC_TOOL name=\"read_file\">>>\npath: a.c\n<<<END_CODEC_TOOL>>>"
        assertEquals("", AiToolProtocol.proseOnly(onlyBlock))
    }

    // ---- fix 6: the parser keeps the good calls beside a broken block ------

    @Test
    fun `parse returns the successfully parsed calls alongside the format error`() {
        val answer = listOf(
            "<<<CODEC_TOOL name=\"read_file\">>>\npath: a.c\n<<<END_CODEC_TOOL>>>",
            "<<<CODEC_TOOL name=\"read_file\">>>\npath: b.c\n<<<END_CODEC_TOOL>>>",
            "<<<CODEC_TOOL name=\"read_file\">>>\nthis line has no colon\n<<<END_CODEC_TOOL>>>"
        ).joinToString("\n")
        val parsed = AiToolProtocol.parse(answer) as AiToolParse.Malformed
        assertEquals(2, parsed.calls.size)
        assertTrue(parsed.calls.all { it.name == AiToolName.READ_FILE })
        assertEquals(listOf("a.c", "b.c"), parsed.calls.map { it.args["path"] })
        assertTrue(parsed.reason.contains("no name"))
    }

    @Test
    fun `a wholly malformed answer still carries no calls`() {
        val parsed = AiToolProtocol.parse("<<<CODEC_TOOL name=\"read_file\">>>\npath: a.c") as AiToolParse.Malformed
        assertTrue(parsed.calls.isEmpty())
    }
}
