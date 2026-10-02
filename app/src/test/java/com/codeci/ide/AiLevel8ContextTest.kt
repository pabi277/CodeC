package com.codeci.ide

import com.codeci.ide.ui.ai.AiAgentLimits
import com.codeci.ide.ui.ai.AiAgentPrompt
import com.codeci.ide.ui.ai.AiAgentStep
import com.codeci.ide.ui.ai.AiAgentStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 85 (AI Level 8, item 4) — restorable eviction. A tool result that does not
 * fit the request is no longer reduced to a bare count: each dropped result leaves a
 * pointer (`path — lines a-b — status`) so the model can re-read exactly that range
 * on demand. Every evicted result stays re-acquirable by path AND range.
 */
class AiLevel8ContextTest {

    private fun readStep(i: Int) = AiAgentStep(
        kind = AiAgentStepKind.TOOL,
        title = "read_file src/file$i.c (lines ${i * 100}-${i * 100 + 59})",
        modelResult = "FILE src/file$i.c — lines ${i * 100}-${i * 100 + 59} of 300 [complete]\n" + "x".repeat(40)
    )

    @Test
    fun `a dropped read is named by path range and status so it can be re-read`() {
        val steps = (1..6).map { readStep(it) }
        val packed = AiAgentPrompt.pack("q", "MAP", steps)
        // 6 results, KEEP_LAST_RESULTS ride along, the 2 oldest are dropped.
        assertEquals(6 - AiAgentLimits.KEEP_LAST_RESULTS, packed.droppedResults)
        assertTrue(packed.text.contains("re-read any on demand"))
        assertTrue(packed.text.contains("src/file1.c — lines 100-159 — complete"))
        assertTrue(packed.text.contains("src/file2.c — lines 200-259 — complete"))
    }

    @Test
    fun `every evicted read stays re-acquirable by path and range`() {
        val steps = (1..6).map { readStep(it) }
        val packed = AiAgentPrompt.pack("q", "MAP", steps)
        for (i in 1..(6 - AiAgentLimits.KEEP_LAST_RESULTS)) {
            assertTrue(
                "dropped read $i must be re-acquirable by path and range",
                packed.text.contains("src/file$i.c — lines ${i * 100}-${i * 100 + 59}")
            )
        }
    }

    @Test
    fun `the eviction pointer carries a partial or refused status`() {
        val partial = AiAgentStep(
            kind = AiAgentStepKind.TOOL,
            title = "read_file src/big.c (lines 1-400)",
            modelResult = "FILE src/big.c — lines 1-145 of 2000 [partial: result cut at 8000 chars]\n" + "y".repeat(40)
        )
        val refused = AiAgentStep(
            kind = AiAgentStepKind.TOOL,
            title = "read_file .env",
            modelResult = "FILE .env — [refused: credential-shaped]",
            ok = false
        )
        // Both are the oldest, so both are dropped and must show their true status.
        val steps = listOf(refused, partial) + (1..5).map { readStep(it) }
        val packed = AiAgentPrompt.pack("q", "MAP", steps)
        assertTrue(packed.text.contains("src/big.c — lines 1-145 — partial"))
        assertTrue(packed.text.contains(".env — [refused: credential-shaped]") || packed.text.contains("read_file .env"))
    }

    @Test
    fun `the eviction pointer list is capped and reports the remainder`() {
        val steps = (1..60).map { readStep(it) }
        val packed = AiAgentPrompt.pack("q", "MAP", steps)
        assertTrue(packed.droppedResults > 10)
        assertTrue(packed.text.contains("more; re-issue the call to re-read"))
        assertTrue(packed.chars <= AiAgentLimits.MAX_REQUEST_CHARS)
    }
}
