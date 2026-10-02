package com.codeci.ide

import com.codeci.ide.ui.ai.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NvidiaRequestTest {
    private val key = "nvapi-" + "x".repeat(40)
    private fun prompt() = (AiContextBuilder.fromSelection("print(\"hi\")\n", 0, 12, "main.py", "Python", false, "why?") as AiContextResult.Ready).prompt
        .copy(provider = AiProviderId.NVIDIA, model = AiProviders.NVIDIA_DEFAULT)

    @Test fun `endpoint is fixed HTTPS and key lives only in one Authorization header`() {
        assertEquals("https://integrate.api.nvidia.com/v1/chat/completions", NvidiaRequest.URL)
        val headers = NvidiaRequest.headers(key)
        assertEquals("Bearer $key", headers["Authorization"])
        assertEquals(1, headers.values.count { it.contains(key) })
        assertEquals("text/event-stream", headers["Accept"])
        assertFalse(NvidiaRequest.URL.contains(key))
        assertFalse(NvidiaRequest.body(prompt()).contains(key))
    }
    @Test fun `body carries exactly system and user preview strings and the selected model`() {
        val p = prompt()
        val body = NvidiaRequest.body(p)
        assertTrue(body.contains("\"role\":\"system\",\"content\":" + GeminiRequest.json(p.systemInstruction)))
        assertTrue(body.contains("\"role\":\"user\",\"content\":" + GeminiRequest.json(p.userText)))
        assertTrue(body.contains("\"model\":" + GeminiRequest.json(p.model)))
        assertEquals(2, Regex("\"role\":").findAll(body).count())
    }
    @Test fun `stream and raised output budget are explicit`() {
        val body = NvidiaRequest.body(prompt())
        assertTrue(body.endsWith("\"stream\":true}"))
        assertTrue(body.contains("\"max_tokens\":32768"))
    }
    @Test fun `request is stateless and does not send unsupported Google flags or native tools`() {
        val body = NvidiaRequest.body(prompt())
        for (bad in listOf("conversation_id", "interaction_id", "previous_response", "\"store\"", "\"tools\"", "tool_choice")) {
            assertFalse(bad, body.contains(bad))
        }
    }
    @Test fun `content-free test has one fixed user message no system or project text`() {
        val body = NvidiaRequest.testBody(AiProviders.NVIDIA_DEFAULT)
        assertTrue(body.contains(GeminiRequest.json(GeminiRequest.TEST_PROMPT)))
        assertFalse(body.contains("system"))
        assertFalse(body.contains("print"))
        assertEquals(1, Regex("\"role\":").findAll(body).count())
        assertTrue(body.contains("\"max_tokens\":32768"))
    }
    @Test fun `JSON control characters and quotes cannot become new messages`() {
        val text = "quote \" slash \\ newline\n\u0000"
        val body = NvidiaRequest.body(AiProviders.NVIDIA_DEFAULT, "system", text, 32_768)
        assertTrue(body.contains(GeminiRequest.json(text)))
        assertFalse(body.contains('\u0000'))
        assertFalse(body.contains('\n'))
    }
    @Test fun `provider dispatch preserves exact strings on either wire format`() {
        for (provider in AiProviderId.entries) {
            val p = prompt().copy(provider = provider, model = AiProviders.defaultModel(provider))
            val body = AiProviderRequests.body(p)
            assertTrue(body.contains(GeminiRequest.json(p.systemInstruction)))
            assertTrue(body.contains(GeminiRequest.json(p.userText)))
        }
    }
}
