package com.codeci.ide

import com.codeci.ide.ui.ai.AiAnswerAccumulator
import com.codeci.ide.ui.ai.AiOutcome
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
