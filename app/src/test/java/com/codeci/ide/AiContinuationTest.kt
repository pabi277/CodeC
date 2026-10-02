package com.codeci.ide

import com.codeci.ide.ui.ai.AiContinuation
import com.codeci.ide.ui.ai.AiContinuationRequest
import com.codeci.ide.ui.ai.AiLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 81 — the pure half of "Continue after cut off":
 * `ui/ai/AiContinuation.kt` only describes the follow-up; it never sends.
 *
 * Owner row (2026-10-02): *"Add a continue open on Gemini so i can continue
 * after cut off"*.
 */
class AiContinuationTest {

    // ---- the tail that travels back -----------------------------------------

    @Test
    fun `a short answer is carried whole and without a fragment marker`() {
        val answer = "First line.\nSecond line."
        assertEquals(answer, AiContinuation.tailOf(answer))
    }

    @Test
    fun `a long answer keeps only its last lines and says it is a fragment`() {
        val answer = (1..400).joinToString("\n") { "line $it" }
        val tail = AiContinuation.tailOf(answer, maxChars = 120)
        assertTrue("the tail must be marked as cut", tail.startsWith("…"))
        assertTrue("the tail must end where the answer ended", tail.endsWith("line 400"))
        assertTrue("the tail must fit the budget", tail.length <= 121)
        assertFalse("the tail must not carry the start", tail.contains("line 1\n"))
    }

    @Test
    fun `the tail starts at a line break when one is close`() {
        val answer = "x".repeat(500) + "\n" + "y".repeat(100)
        val tail = AiContinuation.tailOf(answer, maxChars = 150)
        // The newline sits 150 chars before the end, inside the snap window, so
        // the tail begins at the second line rather than mid-'x'.
        assertEquals("…" + "y".repeat(100), tail)
    }

    @Test
    fun `an empty answer never produces a fake tail`() {
        assertEquals("", AiContinuation.tailOf(""))
        assertEquals("", AiContinuation.tailOf("   \n\n "))
    }

    // ---- the budget ---------------------------------------------------------

    @Test
    fun `a continuation may add at most one reply's worth and never more than the total`() {
        assertEquals(AiLimits.MAX_REPLY_CHARS, AiContinuation.requestBudget(0))
        val almostFull = AiContinuation.MAX_TOTAL_CHARS - 1_000
        assertEquals(1_000, AiContinuation.requestBudget(almostFull))
        assertTrue(AiContinuation.requestBudget(AiContinuation.MAX_TOTAL_CHARS) >= 1)
    }

    @Test
    fun `the remaining budget never goes negative`() {
        assertEquals(0, AiContinuation.remainingChars(AiContinuation.MAX_TOTAL_CHARS))
        assertEquals(0, AiContinuation.remainingChars(AiContinuation.MAX_TOTAL_CHARS + 5_000))
        assertEquals(500, AiContinuation.remainingChars(AiContinuation.MAX_TOTAL_CHARS - 500))
    }

    @Test
    fun `continue is offered until the tap limit is reached`() {
        assertTrue(AiContinuation.canContinue(0, 1_000))
        assertTrue(AiContinuation.canContinue(AiContinuation.MAX_CONTINUATIONS - 1, 1_000))
        assertFalse(AiContinuation.canContinue(AiContinuation.MAX_CONTINUATIONS, 1_000))
    }

    @Test
    fun `continue is not offered when the total length budget is spent`() {
        assertTrue(AiContinuation.canContinue(1, AiContinuation.MAX_TOTAL_CHARS - AiContinuation.MIN_REMAINING_CHARS))
        assertFalse(AiContinuation.canContinue(1, AiContinuation.MAX_TOTAL_CHARS - AiContinuation.MIN_REMAINING_CHARS + 1))
        assertFalse(AiContinuation.canContinue(1, AiContinuation.MAX_TOTAL_CHARS + 100))
    }

    // ---- the words the model reads ------------------------------------------

    @Test
    fun `the block tells the model to resume without repeating or restarting`() {
        val block = AiContinuation.block(AiContinuationRequest(index = 1, tail = "the last words"))
        assertTrue(block.contains("Continue it from exactly where it stopped"))
        assertTrue(block.contains("Do not repeat"))
        assertTrue(block.contains("do not start the answer again"))
    }

    @Test
    fun `the block carries the tail exactly as it will be sent`() {
        val tail = "…and then the function returns."
        val block = AiContinuation.block(AiContinuationRequest(index = 3, tail = tail))
        assertTrue(block.contains(tail))
    }

    // ---- the sentence a missing button owes the user ------------------------

    @Test
    fun `there is no limit note while a continuation is still possible`() {
        assertNull(AiContinuation.limitNote(0, 1_000))
        assertNull(AiContinuation.limitNote(AiContinuation.MAX_CONTINUATIONS - 1, 1_000))
    }

    @Test
    fun `the limit note names the reason it can no longer continue`() {
        val taps = AiContinuation.limitNote(AiContinuation.MAX_CONTINUATIONS, 1_000)
        assertTrue(taps!!.contains("continued ${AiContinuation.MAX_CONTINUATIONS} times"))
        val length = AiContinuation.limitNote(1, AiContinuation.MAX_TOTAL_CHARS)
        assertTrue(length!!.contains("length limit"))
        assertTrue(length.contains(AiContinuation.MAX_TOTAL_CHARS.toString()))
    }
}
