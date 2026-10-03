package com.codeci.ide

import com.codeci.ide.ui.ai.AiAgentBudget
import com.codeci.ide.ui.ai.AiAgentLimits
import com.codeci.ide.ui.ai.AiAgentStopReason
import com.codeci.ide.ui.ai.AiAgentWorkingSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 85 (AI Level 8, S11) — the loop-local working set. An exact-duplicate read
 * (same path, same range) is served from it at zero execution cost, counted as
 * `toolCallsReused` and never as an execution against the tool cap, and a run of
 * identical no-progress calls stops the loop.
 */
class AiLevel8ReuseTest {

    @Test
    fun `an exact-duplicate read is served from the working set`() {
        val key = AiAgentWorkingSet.readKey("src/main.c", 1, 60)
        var set = AiAgentWorkingSet()
        assertNull(set.cached(key))
        set = set.record(key, "FILE src/main.c — lines 1-60 of 300 [complete]")
        assertEquals("FILE src/main.c — lines 1-60 of 300 [complete]", set.cached(key))
    }

    @Test
    fun `a different range or path is a different working-set entry`() {
        val a = AiAgentWorkingSet.readKey("src/main.c", 1, 60)
        val b = AiAgentWorkingSet.readKey("src/main.c", 61, 120)
        val c = AiAgentWorkingSet.readKey("src/other.c", 1, 60)
        val set = AiAgentWorkingSet().record(a, "A")
        assertEquals("A", set.cached(a))
        assertNull(set.cached(b))
        assertNull(set.cached(c))
    }

    @Test
    fun `the reused counter is separate from executions and refusals`() {
        val budget = AiAgentBudget()
            .withToolCalls(2)
            .withReused(3)
            .withRefused(1)
        assertEquals(2, budget.toolCallsUsed)
        assertEquals(3, budget.toolCallsReused)
        assertEquals(1, budget.toolCallsRefused)
    }

    @Test
    fun `reused reads never gate the tool execution cap`() {
        val budget = AiAgentBudget().withReused(AiAgentLimits.MAX_TOOL_CALLS + 50)
        // The execution counter is untouched, so the loop may still run a tool.
        assertEquals(0, budget.toolCallsUsed)
        assertNull(budget.blockTool(nowMs = 0L))
        assertEquals(AiAgentLimits.MAX_TOOL_CALLS, budget.toolCallsRemaining())
    }

    @Test
    fun `N identical no-progress calls stall the loop`() {
        var set = AiAgentWorkingSet()
        repeat(AiAgentLimits.MAX_IDENTICAL_REPEATS - 1) {
            set = set.note("read_file src/main.c (lines 1-60)")
            assertFalse(set.stalled())
        }
        set = set.note("read_file src/main.c (lines 1-60)")
        assertTrue(set.stalled())
    }

    @Test
    fun `a different call between repeats resets the no-progress count`() {
        var set = AiAgentWorkingSet()
            .note("read_file a.c")
            .note("read_file a.c")
        assertFalse(set.stalled())
        set = set.note("read_file b.c") // progress: a different call
        assertFalse(set.stalled())
        set = set.note("read_file a.c")
        assertFalse("a single repeat after progress must not stall", set.stalled())
    }

    @Test
    fun `no-progress is its own stop reason with a sentence`() {
        val sentence = AiAgentLimits.stopSentence(AiAgentStopReason.NO_PROGRESS)
        assertTrue(sentence.isNotBlank())
        assertTrue(sentence.contains("progress"))
    }
}
