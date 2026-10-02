package com.codeci.ide

import com.codeci.ide.ui.ai.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiAnswerBudgetTest {
    @Test fun `owner's exact caps are global and Continue total remains separate`() {
        assertEquals(32_768, AiLimits.MAX_OUTPUT_TOKENS)
        assertEquals(48_000, AiLimits.MAX_REPLY_CHARS)
        assertEquals(64_000, AiContinuation.MAX_TOTAL_CHARS)
        assertEquals(8, AiContinuation.MAX_CONTINUATIONS)
        assertTrue(AiLimits.MAX_REPLY_CHARS < AiContinuation.MAX_TOTAL_CHARS)
    }
    @Test fun `full first reply leaves exactly sixteen thousand characters`() {
        assertEquals(16_000, AiContinuation.requestBudget(48_000))
        assertEquals(16_000, AiContinuation.remainingChars(48_000))
        assertTrue(AiContinuation.canContinue(0, 48_000))
        assertFalse(AiContinuation.canContinue(1, 64_000))
    }
    @Test fun `accumulator grows to raised cap and never one character beyond it`() {
        val acc = AiAnswerAccumulator()
        assertTrue(acc.accept(GeminiChunk(text = "x".repeat(48_000))))
        assertFalse(acc.accept(GeminiChunk(text = "z")))
        assertEquals(AiOutcome.Answer("x".repeat(48_000), true), acc.outcome())
    }
    @Test fun `continuation accumulator respects remaining budget not the larger default`() {
        val base = "x".repeat(48_000)
        val acc = AiAnswerAccumulator(AiContinuation.requestBudget(base.length))
        acc.accept(GeminiChunk(text = "y".repeat(20_000)))
        val answer = acc.outcome() as AiOutcome.Answer
        assertEquals(64_000, base.length + answer.text.length)
        assertTrue(answer.cutShort)
    }
    @Test fun `selection error agent edit and connection tests all ask the larger model cap`() {
        val p = (AiContextBuilder.fromSelection("int x=0;", 0, 8, "main.c", "C", false, "why") as AiContextResult.Ready).prompt
        for (source in AiSource.entries) {
            val request = p.copy(source = source)
            assertTrue(GeminiRequest.body(request).contains("\"maxOutputTokens\":32768"))
            assertTrue(GeminiRequest.body(request.copy(agent = true)).contains("\"maxOutputTokens\":32768"))
        }
        assertTrue(GeminiRequest.testBody().contains("\"maxOutputTokens\":32768"))
        assertTrue(NvidiaRequest.testBody(AiProviders.NVIDIA_DEFAULT).contains("\"max_tokens\":32768"))
    }
    @Test fun `input and tool permission budgets have not been raised with output`() {
        assertEquals(12_000, AiLimits.MAX_CONTEXT_CHARS)
        assertEquals(24_000, AiAgentLimits.MAX_REQUEST_CHARS)
        assertEquals(12, AiAgentLimits.MAX_TURNS)
        assertEquals(24, AiAgentLimits.MAX_TOOL_CALLS)
        assertEquals(2, AiAgentLimits.MAX_RUNS)
        assertEquals(300_000L, AiAgentLimits.MAX_WALL_CLOCK_MS)
        assertEquals(8_000, AiToolLimits.MAX_RESULT_CHARS)
    }
}
