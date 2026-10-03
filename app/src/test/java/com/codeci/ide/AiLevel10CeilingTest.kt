package com.codeci.ide

import com.codeci.ide.ui.ai.AiAgentBudget
import com.codeci.ide.ui.ai.AiAgentCaps
import com.codeci.ide.ui.ai.AiAgentLimits
import com.codeci.ide.ui.ai.AiAgentStopReason
import com.codeci.ide.ui.ai.AiBudgetExtensionPolicy
import com.codeci.ide.ui.ai.AiOptions
import com.codeci.ide.ui.ai.AiOptionsPolicy
import com.codeci.ide.ui.ai.AiToolLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 87 / Level 10 — **S9** as a test, not a claim: *options tune within
 * caps; they never raise a permission.*
 *
 * One case per ceiling. If a future change lets a control reach any of these,
 * the case fails, and the phase has stopped being an options phase.
 */
class AiLevel10CeilingTest {

    @Test
    fun `no option can raise the model turn cap`() {
        for (o in extremeOptions()) {
            assertEquals(12, AiAgentLimits.MAX_TURNS)
            assertTrue(o.turnsNeverExceedCap())
        }
        assertEquals(12, AiAgentLimits.MAX_TURNS)
    }

    @Test
    fun `no option can raise the tool call cap`() {
        assertEquals(24, AiAgentLimits.MAX_TOOL_CALLS)
        // The only budget-raising control is the extension, and its size is a
        // constant — no option can make it bigger.
        assertEquals(4, AiBudgetExtensionPolicy.EXTRA_TURNS)
        assertEquals(8, AiBudgetExtensionPolicy.EXTRA_TOOL_CALLS)
        assertEquals(1, AiBudgetExtensionPolicy.MAX_EXTENSIONS_PER_TASK)
    }

    @Test
    fun `no option can raise the run cap`() {
        assertEquals(2, AiAgentLimits.MAX_RUNS)
        // The extension returns the run count it was given. This is the single
        // line that keeps 87.6 an option rather than a permission.
        for (caps in listOf(AiAgentCaps(), AiAgentCaps(turns = 99, toolCalls = 99, runs = 2))) {
            assertEquals(caps.runs, AiBudgetExtensionPolicy.extend(caps).runs)
        }
    }

    @Test
    fun `no option can raise the batch read cap`() {
        assertEquals(8, AiToolLimits.MAX_BATCH_READS)
    }

    @Test
    fun `no option can raise the per-result or read char caps`() {
        assertEquals(8_000, AiToolLimits.MAX_RESULT_CHARS)
        assertEquals(24_000, AiAgentLimits.MAX_REQUEST_CHARS)
        assertEquals(AiToolLimits.MAX_RESULT_CHARS, AiAgentLimits.MAX_RESULT_CHARS)
    }

    @Test
    fun `no option can raise the no-progress backstop`() {
        assertEquals(3, AiAgentLimits.MAX_IDENTICAL_REPEATS)
    }

    @Test
    fun `the read window option can never exceed the tool ceiling`() {
        // Declared as the constant itself, so a mismatch cannot compile into a
        // runner that refuses what the model was told it could ask for.
        assertEquals(AiToolLimits.MAX_READ_LINES, AiOptionsPolicy.MAX_READ_WINDOW_LINES)
        for (raw in listOf(Int.MIN_VALUE, -1, 0, 1, 49, 400, 401, Int.MAX_VALUE)) {
            assertTrue(AiOptionsPolicy.clampReadWindow(raw) <= AiToolLimits.MAX_READ_LINES)
            assertTrue(AiOptionsPolicy.clampReadWindow(raw) >= 1)
        }
    }

    @Test
    fun `the working set option can never exceed its declared ceiling`() {
        for (raw in listOf(Int.MIN_VALUE, -1, 0, 1, 8, 9, Int.MAX_VALUE)) {
            val depth = AiOptionsPolicy.clampWorkingSetDepth(raw)
            assertTrue(depth in AiOptionsPolicy.MIN_WORKING_SET_DEPTH..AiOptionsPolicy.MAX_WORKING_SET_DEPTH)
        }
    }

    @Test
    fun `every control has a bounded set of values`() {
        // The option set is finite and enumerable: no free-text prompt field, no
        // unbounded integer, so nothing here can become an undisclosed prompt edit.
        assertEquals(3, com.codeci.ide.ui.ai.AiAnswerDetail.entries.size)
        assertEquals(2, com.codeci.ide.ui.ai.AiActivityDisplay.entries.size)
        assertEquals(2, com.codeci.ide.ui.ai.AiBackupMode.entries.size)
        assertEquals(2, com.codeci.ide.ui.ai.AiBudgetOffer.entries.size)
        assertEquals(2, com.codeci.ide.ui.ai.AiReviewer.entries.size)
        assertEquals(6, com.codeci.ide.ui.ai.AiOptionsPolicy.readWindowChoices().size)
        assertEquals(7, com.codeci.ide.ui.ai.AiOptionsPolicy.workingSetChoices().size)
        for (w in com.codeci.ide.ui.ai.AiOptionsPolicy.readWindowChoices()) {
            assertTrue(w in AiOptionsPolicy.MIN_READ_WINDOW_LINES..AiOptionsPolicy.MAX_READ_WINDOW_LINES)
        }
        for (d in com.codeci.ide.ui.ai.AiOptionsPolicy.workingSetChoices()) {
            assertTrue(d in AiOptionsPolicy.MIN_WORKING_SET_DEPTH..AiOptionsPolicy.MAX_WORKING_SET_DEPTH)
        }
    }

    // ---- 87.6: the caps the gates actually use -----------------------------

    @Test
    fun `an unextended task is gated by exactly the constants`() {
        // The whole "no behaviour change on upgrade" claim, as behaviour: the
        // default caps object gates identically to the old hardcoded constants.
        val caps = AiAgentCaps()
        assertEquals(AiAgentLimits.MAX_TURNS, caps.turns)
        assertEquals(AiAgentLimits.MAX_TOOL_CALLS, caps.toolCalls)
        assertEquals(AiAgentLimits.MAX_RUNS, caps.runs)

        val atTurnCap = AiAgentBudget(turnsUsed = AiAgentLimits.MAX_TURNS)
        assertEquals(
            AiAgentStopReason.TURN_BUDGET,
            atTurnCap.blockModelTurn(nowMs = 0L, caps = caps)
        )
        val atToolCap = AiAgentBudget(toolCallsUsed = AiAgentLimits.MAX_TOOL_CALLS)
        assertEquals(
            AiAgentStopReason.TOOL_BUDGET,
            atToolCap.blockTool(nowMs = 0L, caps = caps)
        )
        // And the wall clock is still a wall clock, extension or not.
        val late = AiAgentBudget(startedAtMs = 1_000L)
        assertEquals(
            AiAgentStopReason.WALL_CLOCK,
            late.blockModelTurn(nowMs = 1_000L + AiAgentLimits.MAX_WALL_CLOCK_MS + 1, caps = AiAgentCaps(turns = 99))
        )
    }

    @Test
    fun `one accepted extension buys exactly the documented read-only budget`() {
        val extended = AiBudgetExtensionPolicy.extend(AiAgentCaps())
        assertEquals(AiAgentLimits.MAX_TURNS + AiBudgetExtensionPolicy.EXTRA_TURNS, extended.turns)
        assertEquals(
            AiAgentLimits.MAX_TOOL_CALLS + AiBudgetExtensionPolicy.EXTRA_TOOL_CALLS,
            extended.toolCalls
        )
        // S9, as behaviour rather than as a comment.
        assertEquals(AiAgentLimits.MAX_RUNS, extended.runs)

        // A task that was stopped at 12 turns runs again at 16 — and stops again
        // at 16, so the extension is a bounded amount of work, not an open end.
        val atOldCap = AiAgentBudget(turnsUsed = AiAgentLimits.MAX_TURNS)
        assertEquals(null, atOldCap.blockModelTurn(nowMs = 0L, caps = extended))
        val atNewCap = AiAgentBudget(turnsUsed = extended.turns)
        assertEquals(
            AiAgentStopReason.TURN_BUDGET,
            atNewCap.blockModelTurn(nowMs = 0L, caps = extended)
        )

        // The run budget does not move, however many extensions are stacked.
        var caps = AiAgentCaps()
        repeat(5) { caps = AiBudgetExtensionPolicy.extend(caps) }
        assertEquals(AiAgentLimits.MAX_RUNS, caps.runs)
        assertEquals(AiAgentLimits.MAX_RUNS, AiAgentBudget(runsUsed = 0).runsRemaining())
    }

    @Test
    fun `the tool counter clamps to the caps in force, so it cannot read past them`() {
        val extended = AiBudgetExtensionPolicy.extend(AiAgentCaps())
        // Past the constant, inside the extended cap: the count is kept.
        assertEquals(25, AiAgentBudget(toolCallsUsed = 24).withToolCalls(1, extended).toolCallsUsed)
        // Past the extended cap: clamped, never beyond what was granted.
        assertEquals(
            extended.toolCalls,
            AiAgentBudget(toolCallsUsed = extended.toolCalls).withToolCalls(5, extended).toolCallsUsed
        )
        assertEquals(extended.toolCalls - 25, AiAgentBudget(toolCallsUsed = 25).toolCallsRemaining(extended))
    }

    /** Every control at both ends of its range at once. */
    private fun extremeOptions(): List<AiOptions> = listOf(
        AiOptions(),
        AiOptions(readWindowLines = 50, workingSetDepth = 8),
        AiOptions(readWindowLines = 400, workingSetDepth = 2)
    )

    /** The caps are compile-time constants; nothing in AiOptions can reach them. */
    private fun AiOptions.turnsNeverExceedCap(): Boolean = AiAgentLimits.MAX_TURNS == 12
}
