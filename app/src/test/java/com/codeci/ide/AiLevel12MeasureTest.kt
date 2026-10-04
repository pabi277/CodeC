package com.codeci.ide

import com.codeci.ide.ui.ai.AiMeasureLimits
import com.codeci.ide.ui.ai.AiMeasurePolicy
import com.codeci.ide.ui.ai.AiMeasurements
import com.codeci.ide.ui.ai.AiTokenUsage
import com.codeci.ide.ui.ai.GeminiResponse
import com.codeci.ide.ui.ai.NvidiaResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 89 (AI Level 12, part 89.2) — the numbers-only readout.
 *
 * The owner chose this readout on 2026-10-04 ("a small in-memory numbers-only
 * readout") for the Level 12 evaluation, with one hard constraint: **D6**. These
 * cases pin what makes the readout honest rather than decorative:
 *
 *  - unknown is never rendered as zero (`0` is a claim; `—`/`not reported` is not);
 *  - a hostile provider report (S3) cannot render a negative, absurd or crashing
 *    number — the door sanitises, the arithmetic is saturating;
 *  - latency stamps are idempotent (first token wins) and a partial token sum is
 *    labelled "partial" instead of being presented as the whole.
 *
 * The readout is display state: this class never touches a `File`, a DataStore or
 * a log, and `AiLevel12WiringTest` pins that the app does not either.
 */
class AiLevel12MeasureTest {

    private val started = 1_000_000L

    // ---- unknown is not zero -------------------------------------------------

    @Test
    fun `a task with nothing measured renders nothing at all`() {
        assertNull(AiMeasurePolicy.render(AiMeasurements()))
    }

    @Test
    fun `an unreported provider says so once the task is finished and never claims zero`() {
        val m = AiMeasurements(firstTokenMs = 1_400, totalMs = 22_600)
            .let { AiMeasurePolicy.withUsage(it, null) }
        val line = AiMeasurePolicy.render(m)!!
        assertTrue("a missing usage report must be named", line.contains("tokens not reported"))
        assertFalse("no zero may stand in for an unknown", line.contains("0 in"))
        assertTrue(line.startsWith("first 1.4 s"))
        assertTrue(line.contains("total 22.6 s"))
    }

    @Test
    fun `a half-reported usage keeps the missing side as a dash and is not zeroed`() {
        val m = AiMeasurePolicy.withUsage(AiMeasurements(totalMs = 4_000), AiTokenUsage(prompt = 1_812, output = null))
        val line = AiMeasurePolicy.render(m)!!
        assertTrue("known side is rendered", line.contains("tokens 1 812 in / — out"))
        assertFalse("the missing side must not become 0", line.contains("0 out"))
    }

    @Test
    fun `a finished task with no measurements on either side is empty not zero`() {
        val m = AiMeasurePolicy.withUsage(AiMeasurements(), AiTokenUsage())
        assertNull("an empty report measures nothing", AiMeasurePolicy.render(m))
        assertFalse("and it did not claim completeness", m.tokensComplete)
    }

    // ---- S3: provider values are untrusted -----------------------------------

    @Test
    fun `a negative or absurd reported count becomes unknown instead of a number`() {
        assertNull(AiMeasurePolicy.reportedTokens(-1))
        assertNull(AiMeasurePolicy.reportedTokens(AiMeasureLimits.MAX_REPORTED_TOKENS + 1))
        assertEquals(0, AiMeasurePolicy.reportedTokens(0))
        assertEquals(1_234, AiMeasurePolicy.reportedTokens(1_234))
    }

    @Test
    fun `a hostile usage object renders as not reported rather than a negative line`() {
        val m = AiMeasurePolicy.withUsage(AiMeasurements(totalMs = 500), AiTokenUsage(prompt = Int.MIN_VALUE, output = -5))
        val line = AiMeasurePolicy.render(m)!!
        assertTrue(line.contains("tokens not reported"))
        assertFalse(line.contains("-"))
        assertFalse(m.tokensComplete)
    }

    @Test
    fun `a reported count beyond the bound cannot make the sum overflow`() {
        var m = AiMeasurements()
        repeat(4) { m = AiMeasurePolicy.withUsage(m, AiTokenUsage(prompt = AiMeasureLimits.MAX_REPORTED_TOKENS, output = AiMeasureLimits.MAX_REPORTED_TOKENS)) }
        assertEquals(AiMeasureLimits.MAX_REPORTED_TOKENS, m.promptTokens)
        assertTrue(m.promptTokens!! >= 0)
    }

    @Test
    fun `latency and memory accept only believable values`() {
        assertNull(AiMeasurePolicy.reportedMs(-1))
        assertNull(AiMeasurePolicy.reportedMs(AiMeasureLimits.MAX_LATENCY_MS + 1))
        assertNull(AiMeasurePolicy.reportedMemory(0))
        assertNull(AiMeasurePolicy.reportedMemory(-1024))
        assertNull(AiMeasurePolicy.reportedMemory(AiMeasureLimits.MAX_MEMORY_BYTES + 1))
        assertEquals(64L * 1024L * 1024L, AiMeasurePolicy.reportedMemory(64L * 1024L * 1024L))
    }

    // ---- where the numbers come from (the providers' own reports) -------------

    @Test
    fun `the Gemini report is read when present and stays null when absent`() {
        val withUsage = GeminiResponse.parse(
            """{"candidates":[{"content":{"parts":[{"text":"hi"}]},"finishReason":"STOP"}],
               "usageMetadata":{"promptTokenCount":1812,"candidatesTokenCount":640}}""".trimIndent()
        )!!
        assertEquals("hi", withUsage.text)
        assertEquals(AiTokenUsage(prompt = 1_812, output = 640), withUsage.usage)

        val without = GeminiResponse.parse(
            """{"candidates":[{"content":{"parts":[{"text":"hi"}]}}]}"""
        )!!
        assertNull("no metadata means not reported, not zero", without.usage)
    }

    @Test
    fun `the NVIDIA usage-only event keeps its no-text contract and its counts`() {
        // The Level 5 contract: a usage-only event never becomes answer text.
        val counted = NvidiaResponse.parse(
            """{"choices":[],"usage":{"prompt_tokens":1200,"completion_tokens":300}}"""
        )!!
        assertEquals("", counted.text)
        assertEquals(AiTokenUsage(prompt = 1_200, output = 300), counted.usage)

        // A `total_tokens`-only report names no side: the chunk still exists
        // (that contract predates this phase), its text is still empty, and the
        // pair stays unknown rather than being invented from a total.
        val totalOnly = NvidiaResponse.parse("""{"choices":[],"usage":{"total_tokens":4}}""")!!
        assertEquals("", totalOnly.text)
        assertNull("an unnameable pair is not reported", totalOnly.usage)

        // No usage object at all: not a chunk, exactly as before this phase.
        assertNull(NvidiaResponse.parse("""{"choices":[]}"""))
    }

    // ---- the task's life -----------------------------------------------------

    @Test
    fun `the first token is stamped once and later chunks change nothing`() {
        val first = AiMeasurePolicy.firstToken(AiMeasurements(), started + 1_400, started)
        assertEquals(1_400L, first.firstTokenMs)
        val later = AiMeasurePolicy.firstToken(first, started + 9_000, started)
        assertEquals("the first stamp wins", 1_400L, later.firstTokenMs)
    }

    @Test
    fun `no usable start instant records no latency rather than a bogus offset`() {
        assertEquals(null, AiMeasurePolicy.firstToken(AiMeasurements(), 5_000, 0).firstTokenMs)
        assertEquals(null, AiMeasurePolicy.finish(AiMeasurements(), 5_000, 0).totalMs)
    }

    @Test
    fun `a partial sum is labelled partial and a complete one is not`() {
        var m = AiMeasurePolicy.withUsage(AiMeasurements(totalMs = 900), AiTokenUsage(prompt = 100, output = 50))
        var line = AiMeasurePolicy.render(m)!!
        assertTrue(line.contains("tokens 100 in / 50 out"))
        assertFalse("a complete report is not labelled partial", line.contains("(partial)"))

        m = AiMeasurePolicy.withUsage(m, null)
        line = AiMeasurePolicy.render(m)!!
        assertTrue("one silent request makes the sum a floor", line.contains("(partial)"))
        assertEquals("and the known numbers are kept", true, line.contains("100 in / 50 out"))
    }

    @Test
    fun `finishing stamps the total and one memory sample`() {
        val m = AiMeasurePolicy.finish(
            AiMeasurements(firstTokenMs = 1_400),
            nowMs = started + 22_600,
            startedAtMs = started,
            memoryBytes = 61_400_000L
        )
        assertEquals(22_600L, m.totalMs)
        assertEquals(61_400_000L, m.memoryBytes)
        val line = AiMeasurePolicy.render(m)!!
        assertTrue(line.contains("total 22.6 s"))
        assertTrue(line.contains("memory at finish 58.5 MB"))
    }

    // ---- the line itself -----------------------------------------------------

    @Test
    fun `the line is numbers and labels only, separated like the progress line`() {
        val m = AiMeasurements(firstTokenMs = 820, totalMs = 125_000, promptTokens = 128_405, outputTokens = 640, memoryBytes = 1_024L * 1_024L * 61)
        val line = AiMeasurePolicy.render(m)!!
        assertEquals(
            "first 820 ms · total 2 m 5 s · tokens 128 405 in / 640 out · memory at finish 61.0 MB",
            line
        )
    }

    @Test
    fun `durations and byte counts round to a stable precision`() {
        assertEquals("999 ms", AiMeasurePolicy.duration(999))
        assertEquals("1.0 s", AiMeasurePolicy.duration(1_000))
        assertEquals("1.4 s", AiMeasurePolicy.duration(1_449))
        assertEquals("59.9 s", AiMeasurePolicy.duration(59_949))
        assertEquals("1 m 0 s", AiMeasurePolicy.duration(60_000))
        // The boundary is decided on the rounded tenths, so neither side lies:
        // 59.949 s stays sub-minute, 59.95 s has already rounded to a minute.
        assertEquals("1 m 0 s", AiMeasurePolicy.duration(59_950))
        assertEquals("1 m 0 s", AiMeasurePolicy.duration(59_999))
        assertEquals("980 B", AiMeasurePolicy.bytes(980))
        assertEquals("1 KB", AiMeasurePolicy.bytes(1_024))
        assertEquals("1.0 MB", AiMeasurePolicy.bytes(1_048_576))
    }

    @Test
    fun `a token count is grouped the way the documents group sizes`() {
        assertEquals("0", AiMeasurePolicy.thousands(0))
        assertEquals("640", AiMeasurePolicy.thousands(640))
        assertEquals("1 812", AiMeasurePolicy.thousands(1_812))
        assertEquals("128 405", AiMeasurePolicy.thousands(128_405))
    }
}
