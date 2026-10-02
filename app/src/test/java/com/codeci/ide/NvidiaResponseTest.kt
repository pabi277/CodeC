package com.codeci.ide

import com.codeci.ide.ui.ai.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class NvidiaResponseTest {
    @Test fun `visible delta is accumulated but reasoning and null content are not`() {
        val c = NvidiaResponse.parse("""
            {"choices":[{"delta":{"role":"assistant","content":"Hello","reasoning_content":"private thinking"},"finish_reason":null}]}
        """.trimIndent())!!
        assertEquals("Hello", c.text)
        assertNull(c.finishReason)
        val empty = NvidiaResponse.parse("{\"choices\":[{\"delta\":{\"reasoning_content\":\"hidden\",\"content\":null}}]}")!!
        assertEquals("", empty.text)
    }
    @Test fun `length maps to Continue's MAX_TOKENS contract`() {
        val c = NvidiaResponse.parse("{\"choices\":[{\"delta\":{\"content\":\"tail\"},\"finish_reason\":\"length\"}]}")!!
        val acc = AiAnswerAccumulator(provider = AiProviderId.NVIDIA)
        acc.accept(c)
        assertEquals(AiOutcome.Answer("tail", true), acc.outcome())
    }
    @Test fun `content filter is blocked with provider-specific fixed words`() {
        val c = NvidiaResponse.parse("{\"choices\":[{\"delta\":{},\"finish_reason\":\"content_filter\"}]}")!!
        val acc = AiAnswerAccumulator(provider = AiProviderId.NVIDIA)
        acc.accept(c)
        val f = (acc.outcome() as AiOutcome.Failed).failure
        assertEquals(AiFailureKind.BLOCKED, f.kind)
        assertTrue(f.message.contains("NVIDIA"))
    }
    @Test fun `error metadata is classified with no provider prose retained`() {
        val c = NvidiaResponse.parse("""
            {"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"GenerateRequestsPerMinute secret nvapi-DO_NOT_SHOW","details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"12.1s"}]}}
        """.trimIndent())!!
        assertTrue(c.isError)
        assertEquals(AiQuotaWindow.MINUTE, c.errorFailure!!.rateLimit!!.window)
        assertEquals(13L, c.errorFailure!!.rateLimit!!.retryAfterSeconds)
        assertFalse(c.toString().contains("DO_NOT_SHOW"))
        assertFalse(c.toString().contains("RESOURCE_EXHAUSTED"))
    }
    @Test fun `string RESOURCE_EXHAUSTED code still classifies as a rate limit`() {
        val c = NvidiaResponse.parse("{\"error\":{\"code\":\"RESOURCE_EXHAUSTED\",\"message\":\"quota\"}}")!!
        assertEquals(AiFailureKind.RATE_LIMIT, c.errorFailure!!.kind)
    }
    @Test fun `DONE and usage-only events carry no answer text`() {
        assertEquals("STOP", NvidiaResponse.parse("[DONE]")!!.finishReason)
        assertEquals("", NvidiaResponse.parse("{\"choices\":[],\"usage\":{\"total_tokens\":4}}")!!.text)
    }
    @Test fun `malformed and unknown payloads fail parsing instead of becoming prose`() {
        for (s in listOf("", "not json", "{", "{\"unexpected\":\"private\"}")) assertNull(s, NvidiaResponse.parse(s))
        assertNotNull(NvidiaResponse.parse("{\"choices\":[{\"delta\":{}}]}"))
    }
    @Test fun `NVIDIA's recorded SSE uses the same bounded tool protocol and no native tool execution`() {
        val tool = "<<<CODEC_TOOL name=\"read_file\">>>\npath: main.c\nstart: 1\nend: 3\n<<<END_CODEC_TOOL>>>"
        val payload = "{\"choices\":[{\"delta\":{\"content\":" + GeminiRequest.json(tool) + "}}]}"
        val chunk = NvidiaResponse.parse(payload)!!
        val calls = AiToolProtocol.parse(chunk.text) as AiToolParse.Calls
        assertTrue(AiToolPolicy.validate(calls.calls.single(), AiToolProjectView(setOf("main.c"), 2)) is AiToolVerdict.Allowed)
        val native = NvidiaResponse.parse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"function\":{\"name\":\"run_shell\"}}]}}]}")!!
        assertEquals("", native.text)
        assertFalse(native.text.contains("run_shell"))
    }
}
