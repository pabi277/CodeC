package com.codeci.ide

import com.codeci.ide.ui.ai.AiAgentLimits
import com.codeci.ide.ui.ai.AiAgentStepKind
import com.codeci.ide.ui.ai.AiAgentUsage
import com.codeci.ide.ui.ai.AiCopy
import com.codeci.ide.ui.ai.AiPhase
import com.codeci.ide.ui.ai.AiProgressInput
import com.codeci.ide.ui.ai.AiProgressPolicy
import com.codeci.ide.ui.ai.AiProgressPolicy.Placement
import com.codeci.ide.ui.ai.AiProgressStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 88.4 / Level 11 — one truthful progress line. Pins the stage precedence,
 * the placement (exactly one line on screen) and the counters' numbers; the
 * stage WORDS are AiCopy's and are not pinned here.
 */
class AiProgressPolicyTest {

    private fun input(
        phase: AiPhase = AiPhase.STREAMING,
        lastKind: AiAgentStepKind? = AiAgentStepKind.REQUEST,
        answerEmpty: Boolean = true,
        runPending: Boolean = false,
        runRunning: Boolean = false,
        retrying: Boolean = false
    ) = AiProgressInput(phase, lastKind, answerEmpty, runPending, runRunning, retrying)

    // ---- stage: the 88.4 precedence table, row by row --------------------------------

    @Test
    fun `row 1 - FAILED beats everything`() {
        assertEquals(AiProgressStage.FAILED, AiProgressPolicy.stage(input(AiPhase.FAILED, AiAgentStepKind.STOPPED, retrying = true, runPending = true)))
    }

    @Test
    fun `rows 2 and 3 - DONE after a STOPPED row is STOPPED, otherwise DONE`() {
        assertEquals(AiProgressStage.STOPPED, AiProgressPolicy.stage(input(AiPhase.DONE, AiAgentStepKind.STOPPED)))
        assertEquals(AiProgressStage.DONE, AiProgressPolicy.stage(input(AiPhase.DONE, AiAgentStepKind.ANSWER, answerEmpty = false)))
    }

    @Test
    fun `row 4 - a rate-limit wait is WAITING_TO_RETRY even mid-run`() {
        assertEquals(AiProgressStage.WAITING_TO_RETRY, AiProgressPolicy.stage(input(retrying = true, runRunning = true, runPending = true)))
    }

    @Test
    fun `rows 5 and 6 - a running command beats a pending decision`() {
        assertEquals(AiProgressStage.RUNNING_COMMAND, AiProgressPolicy.stage(input(runRunning = true, runPending = true)))
        assertEquals(AiProgressStage.WAITING_FOR_RUN_DECISION, AiProgressPolicy.stage(input(runPending = true, lastKind = AiAgentStepKind.TOOL)))
    }

    @Test
    fun `row 7 - after a TOOL or DENIED row the agent is reading files`() {
        assertEquals(AiProgressStage.READING_FILES, AiProgressPolicy.stage(input(lastKind = AiAgentStepKind.TOOL, answerEmpty = false)))
        assertEquals(AiProgressStage.READING_FILES, AiProgressPolicy.stage(input(lastKind = AiAgentStepKind.DENIED)))
    }

    @Test
    fun `rows 8 and 9 - text arriving means replying, an empty answer means waiting`() {
        // Every turn sets answer = "" and appends its REQUEST row (AiViewModel), so
        // REQUEST + empty is "waiting for the model" and REQUEST + text is "replying".
        assertEquals(AiProgressStage.MODEL_REPLYING, AiProgressPolicy.stage(input(answerEmpty = false)))
        assertEquals(AiProgressStage.WAITING_FOR_MODEL, AiProgressPolicy.stage(input()))
        assertEquals(AiProgressStage.WAITING_FOR_MODEL, AiProgressPolicy.stage(input(lastKind = null)))
    }

    // ---- placement: exactly one line ---------------------------------------------

    @Test
    fun `nothing is shown before Send, and a single-shot ask shows no progress line`() {
        for (phase in listOf(AiPhase.IDLE, AiPhase.PREVIEW)) assertEquals(Placement.NONE, AiProgressPolicy.placement(phase, hasAgentSteps = true))
        for (phase in AiPhase.values()) assertEquals(Placement.NONE, AiProgressPolicy.placement(phase, hasAgentSteps = false))
    }

    @Test
    fun `the bar carries the line while streaming and the card after`() {
        assertEquals(Placement.BOTTOM_BAR, AiProgressPolicy.placement(AiPhase.STREAMING, hasAgentSteps = true))
        assertEquals(Placement.CARD, AiProgressPolicy.placement(AiPhase.DONE, hasAgentSteps = true))
        assertEquals(Placement.CARD, AiProgressPolicy.placement(AiPhase.FAILED, hasAgentSteps = true))
        // One placement per state, so the card and the bar can never both draw it.
        for (phase in AiPhase.values()) {
            val p = AiProgressPolicy.placement(phase, hasAgentSteps = true)
            assertTrue(p == Placement.NONE || p == Placement.BOTTOM_BAR || p == Placement.CARD)
        }
    }

    // ---- the line ------------------------------------------------------------------

    @Test
    fun `the line is the stage words, then the turn and read counters`() {
        val usage = AiAgentUsage(turns = 2, toolCalls = 8, runs = 0)
        val line = AiProgressPolicy.line(AiProgressStage.READING_FILES, usage)
        assertTrue(line.startsWith(AiCopy.agentProgressStage(AiProgressStage.READING_FILES)))
        // "steps" means model turns: 2 turns that batched 8 reads say 2 steps, never 8.
        assertTrue(line, line.contains("2 of ${AiAgentLimits.MAX_TURNS} steps"))
        assertTrue(line, line.contains("8 of ${AiAgentLimits.MAX_TOOL_CALLS} reads"))
        assertFalse(line, line.contains("8 of ${AiAgentLimits.MAX_TURNS} steps"))
    }

    @Test
    fun `counters clamp to the task's own caps, including after an accepted extension`() {
        val extended = AiAgentUsage(turns = 15, toolCalls = 40, runs = 3, turnCap = 14, readCap = 30)
        val line = AiProgressPolicy.line(AiProgressStage.WAITING_FOR_MODEL, extended)
        assertTrue(line, line.contains("14 of 14 steps"))
        assertTrue(line, line.contains("30 of 30 reads"))
        assertTrue(line, line.contains("${AiAgentLimits.MAX_RUNS} of ${AiAgentLimits.MAX_RUNS} runs"))
        val normal = AiProgressPolicy.line(AiProgressStage.DONE, AiAgentUsage(turns = 3, toolCalls = 30, runs = 0))
        assertTrue(normal, normal.contains("${AiAgentLimits.MAX_TOOL_CALLS} of ${AiAgentLimits.MAX_TOOL_CALLS} reads"))
    }

    @Test
    fun `refused and reused appear only when non-zero, runs only once requested or used`() {
        val clean = AiProgressPolicy.line(AiProgressStage.DONE, AiAgentUsage(turns = 1, toolCalls = 1, runs = 0))
        assertFalse(clean, clean.contains("refused"))
        assertFalse(clean, clean.contains("reused"))
        assertFalse(clean, clean.contains("runs"))
        val busy = AiProgressPolicy.line(AiProgressStage.DONE, AiAgentUsage(turns = 4, toolCalls = 5, runs = 1, refused = 2, reused = 3))
        assertTrue(busy, busy.contains("2 refused"))
        assertTrue(busy, busy.contains("3 reused"))
        assertTrue(busy, busy.contains("1 of ${AiAgentLimits.MAX_RUNS} runs"))
        val asking = AiProgressPolicy.line(AiProgressStage.WAITING_FOR_RUN_DECISION, AiAgentUsage(turns = 2, toolCalls = 1, runs = 0))
        assertTrue(asking, asking.contains("0 of ${AiAgentLimits.MAX_RUNS} runs"))
    }

    @Test
    fun `without usage the line is the stage words alone`() {
        for (stage in AiProgressStage.values()) {
            assertEquals(AiCopy.agentProgressStage(stage), AiProgressPolicy.line(stage, null))
            assertTrue(AiCopy.agentProgressStage(stage).isNotBlank())
        }
    }

    @Test
    fun `the retry countdown replaces the stage words, so it needs no line of its own`() {
        val usage = AiAgentUsage(turns = 3, toolCalls = 2, runs = 0)
        val countdown = "COUNTDOWN-TEXT"
        val line = AiProgressPolicy.line(AiProgressStage.WAITING_TO_RETRY, usage, countdown)
        assertTrue(line.startsWith(countdown))
        assertFalse(line.contains(AiCopy.agentProgressStage(AiProgressStage.WAITING_TO_RETRY)))
        assertTrue(line.contains("3 of ${AiAgentLimits.MAX_TURNS} steps"))
        // A countdown never leaks into another stage.
        assertFalse(AiProgressPolicy.line(AiProgressStage.DONE, usage, countdown).contains(countdown))
    }

    @Test
    fun `the old usage line is unchanged for its other callers`() {
        assertEquals(
            "3 of 12 steps · 1 of 24 reads · 0 of 2 runs",
            AiCopy.agentUsageLine(turns = 3, toolCalls = 1, runs = 0)
        )
    }
}
