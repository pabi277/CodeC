package com.codeci.ide

import com.codeci.ide.ui.ai.AiContextBuilder
import com.codeci.ide.ui.ai.AiContextResult
import com.codeci.ide.ui.ai.AiLimits
import com.codeci.ide.ui.ai.GeminiRequest
import com.codeci.ide.ui.ai.SseLineSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 76 — the wire contract of decision record §4.2 (O2): the key rides in
 * one header only, every body carries `store:false`, the body is built from
 * the same strings the preview showed, and the stream framing is SSE.
 */
class GeminiRequestTest {

    private val key = "AIzaSyTEST_key-0123456789abcdefghijklmn"

    private fun prompt() =
        (AiContextBuilder.fromSelection("print(\"hi\")\n", 0, 12, "main.py", "Python", false, "why?")
            as AiContextResult.Ready).prompt

    // ---- URL ------------------------------------------------------------

    @Test
    fun `the stream URL is Google's host, the model segment and alt=sse`() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:streamGenerateContent?alt=sse",
            GeminiRequest.streamUrl(" gemini-3-flash-preview ")
        )
        assertNull(GeminiRequest.streamUrl("x/../../y"))
        assertNull(GeminiRequest.streamUrl("x?key=abc"))
    }

    // ---- the key --------------------------------------------------------

    @Test
    fun `the key is sent only as the x-goog-api-key header`() {
        val h = GeminiRequest.headers(key)
        assertEquals(key, h["x-goog-api-key"])
        assertEquals(1, h.values.count { it.contains(key) })
        assertFalse(GeminiRequest.streamUrl("gemini-3-flash-preview")!!.contains("key"))
        assertFalse(GeminiRequest.body(prompt()).contains(key))
        assertEquals("text/event-stream", h["Accept"])
    }

    // ---- the body -------------------------------------------------------

    @Test
    fun `every body opts out of storage`() {
        assertTrue(GeminiRequest.body(prompt()).endsWith("\"store\":false}"))
        assertTrue(GeminiRequest.testBody().endsWith("\"store\":false}"))
        assertTrue(GeminiRequest.body(null, "x", 1).contains("\"store\":false"))
    }

    @Test
    fun `the body carries exactly the previewed instruction and text`() {
        val p = prompt()
        val body = GeminiRequest.body(p)
        assertTrue(body.contains(GeminiRequest.json(p.systemInstruction)))
        assertTrue(body.contains(GeminiRequest.json(p.userText)))
        assertTrue(body.contains("\"maxOutputTokens\":${AiLimits.MAX_OUTPUT_TOKENS}"))
        assertTrue(body.startsWith("{\"systemInstruction\":{\"parts\":[{\"text\":"))
        assertTrue(body.contains("\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":"))
    }

    @Test
    fun `test connection sends no code and no system instruction`() {
        val body = GeminiRequest.testBody()
        assertTrue(body.contains(GeminiRequest.json(GeminiRequest.TEST_PROMPT)))
        assertFalse(body.contains("systemInstruction"))
        assertFalse(body.contains("print"))
    }

    @Test
    fun `the JSON escaper handles quotes, backslashes and control characters`() {
        assertEquals("\"a\\\"b\\\\c\"", GeminiRequest.json("a\"b\\c"))
        assertEquals("\"\\n\\r\\t\\b\\f\"", GeminiRequest.json("\n\r\t\b\u000C"))
        assertEquals("\"\\u0001\\u001f\"", GeminiRequest.json("\u0001\u001F"))
        assertEquals("\"\\u2028\\u2029\"", GeminiRequest.json("\u2028\u2029"))
        assertEquals("\"héllo ✓ 😀\"", GeminiRequest.json("héllo ✓ 😀"))
    }

    // ---- SSE framing ----------------------------------------------------

    @Test
    fun `a data line followed by a blank line is one event`() {
        val s = SseLineSplitter()
        assertNull(s.feed("data: {\"a\":1}"))
        assertEquals("{\"a\":1}", s.feed(""))
        assertNull(s.feed(""))
    }

    @Test
    fun `multi-line data joins with newlines and comments or other fields are ignored`() {
        val s = SseLineSplitter()
        assertNull(s.feed(": keep-alive"))
        assertNull(s.feed("event: message"))
        assertNull(s.feed("data: one"))
        assertNull(s.feed("id: 7"))
        assertNull(s.feed("data:two"))
        assertEquals("one\ntwo", s.feed("\r"))
    }

    @Test
    fun `a last event without its blank line still counts at end of stream`() {
        val s = SseLineSplitter()
        s.feed("data: tail")
        assertEquals("tail", s.finish())
        assertNull(s.finish())
    }
}
