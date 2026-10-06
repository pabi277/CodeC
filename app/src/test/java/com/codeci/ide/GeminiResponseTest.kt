package com.codeci.ide

import com.codeci.ide.ui.ai.AiAnswerAccumulator
import com.codeci.ide.ui.ai.AiOutcome
import com.codeci.ide.ui.ai.AiTokenUsage
import com.codeci.ide.ui.ai.GeminiResponse
import com.codeci.ide.ui.ai.SseLineSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 76 — decoding `streamGenerateContent?alt=sse` events. Robolectric
 * because `org.json` is an Android framework class (same as `ReleaseFetchTest`).
 * Shapes per https://ai.google.dev/api/generate-content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiResponseTest {

    @Test
    fun `text parts are joined and thought parts are skipped`() {
        val c = GeminiResponse.parse(
            """{"candidates":[{"content":{"role":"model","parts":[
               {"text":"thinking…","thought":true},{"text":"Hello "},{"text":"there"}]}}]}"""
        )
        assertNotNull(c)
        assertEquals("Hello there", c!!.text)
        assertNull(c.finishReason)
    }

    @Test
    fun `finish and block reasons are read`() {
        assertEquals("MAX_TOKENS", GeminiResponse.parse("""{"candidates":[{"finishReason":"MAX_TOKENS"}]}""")!!.finishReason)
        assertEquals("SAFETY", GeminiResponse.parse("""{"promptFeedback":{"blockReason":"SAFETY"}}""")!!.blockReason)
    }

    @Test
    fun `an error object is flagged without its message`() {
        val c = GeminiResponse.parse("""{"error":{"code":429,"message":"quota","status":"RESOURCE_EXHAUSTED"}}""")!!
        assertTrue(c.isError)
        assertEquals(429, c.errorCode)
        assertEquals("", c.text)
    }

    /**
     * Phase 89 (Level 12, 89.2) — the provider's own counts. This belongs here,
     * not in a plain-JVM class: `org.json` is a framework class and this project
     * sets `isReturnDefaultValues = true`, so an un-Robolectric case would read
     * `null` from every method and prove nothing (CI round 2, 2026-10-04).
     */
    @Test
    fun `usageMetadata is read when present and stays null when absent`() {
        val counted = GeminiResponse.parse(
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hi\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":1812,\"candidatesTokenCount\":640}}"
        )!!
        assertEquals("hi", counted.text)
        assertEquals(AiTokenUsage(prompt = 1_812, output = 640), counted.usage)

        assertNull("no metadata means not reported, not zero", GeminiResponse.parse("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hi\"}]}}]}")!!.usage)
        // A half-reported object keeps the missing side unknown.
        assertEquals(
            AiTokenUsage(prompt = 30, output = null),
            GeminiResponse.parse("{\"usageMetadata\":{\"promptTokenCount\":30}}")!!.usage
        )
    }

    @Test
    fun `malformed payloads decode to null`() {
        assertNull(GeminiResponse.parse("not json"))
        assertNull(GeminiResponse.parse(""))
    }

    @Test
    fun `a recorded stream decodes end to end`() {
        val wire = listOf(
            "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"The \"}],\"role\":\"model\"}}]}",
            "",
            "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"loop never ends.\"}],\"role\":\"model\"},\"finishReason\":\"STOP\"}]}",
            ""
        )
        val sse = SseLineSplitter()
        val acc = AiAnswerAccumulator()
        wire.forEach { line -> sse.feed(line)?.let { GeminiResponse.parse(it)?.let(acc::accept) } }
        assertEquals(AiOutcome.Answer("The loop never ends.", cutShort = false), acc.outcome())
    }
}
