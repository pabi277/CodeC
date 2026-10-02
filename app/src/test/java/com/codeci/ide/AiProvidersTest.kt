package com.codeci.ide

import com.codeci.ide.ui.ai.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiProvidersTest {
    @Test fun `two fixed providers have distinct slots and defaults`() {
        assertEquals(2, AiProviderId.entries.size)
        assertEquals(setOf("gemini", "nvidia"), AiProviderId.entries.map { it.slot }.toSet())
        assertEquals(AiModel.DEFAULT, AiProviders.defaultModel(AiProviderId.GEMINI))
        assertEquals("nvidia/nemotron-3-super-120b-a12b", AiProviders.defaultModel(AiProviderId.NVIDIA))
    }
    @Test fun `provider-specific model validation accepts known and editable ids`() {
        assertTrue(AiProviders.isValidModel(AiProviderId.NVIDIA, " nvidia/nemotron-3-super-120b-a12b "))
        assertTrue(AiProviders.isValidModel(AiProviderId.NVIDIA, "moonshotai/kimi-k2-instruct"))
        assertFalse(AiProviders.isValidModel(AiProviderId.GEMINI, AiProviders.NVIDIA_DEFAULT))
        assertFalse(AiProviders.isValidModel(AiProviderId.NVIDIA, AiModel.DEFAULT))
    }
    @Test fun `models cannot smuggle URLs headers JSON or traversal`() {
        for (bad in listOf("", "nvidia/", "/model", "nvidia/../model", "nvidia/a..b", "NVIDIA/Model", "http://host/model", "nvidia/x?key=secret", "nvidia/x\nAuthorization", "nvidia/x\"}", "nvidia/" + "x".repeat(150))) {
            assertFalse(bad, AiProviders.isValidModel(AiProviderId.NVIDIA, bad))
        }
    }
    @Test fun `Gemini capability row carries its documented ceiling`() {
        val c = AiProviders.capabilities(AiProviderId.GEMINI, AiModel.DEFAULT)
        assertEquals(true, c.streaming)
        assertEquals(1_048_576, c.contextTokens)
        assertEquals(65_536, c.outputTokens)
        assertNotNull(c.sourceDate)
    }
    @Test fun `NVIDIA row only claims verified fields`() {
        val c = AiProviders.capabilities(AiProviderId.NVIDIA, AiProviders.NVIDIA_DEFAULT)
        assertEquals(true, c.streaming)
        assertEquals(32_768, c.outputTokens)
        assertNull(c.nativeTools)
        assertNull(c.structuredOutput)
        assertNull(c.contextTokens)
    }
    @Test fun `unknown models keep unknown capabilities no wildcard ceiling`() {
        for (p in AiProviderId.entries) {
            assertEquals(AiModelCapabilities(), AiProviders.capabilities(p, "unknown/model"))
            assertTrue(AiProviders.capabilityLine(p, "unknown/model").contains("unknown"))
        }
    }
    @Test fun `output budget respects a known ceiling and keeps unknowns explicit`() {
        assertEquals(32_768, AiProviders.outputBudget(AiProviderId.GEMINI, AiModel.DEFAULT))
        assertEquals(32_768, AiProviders.outputBudget(AiProviderId.NVIDIA, AiProviders.NVIDIA_DEFAULT))
        assertEquals(65_536, AiProviders.outputBudget(AiProviderId.GEMINI, AiModel.DEFAULT, 100_000))
        assertEquals(32_768, AiProviders.outputBudget(AiProviderId.NVIDIA, AiProviders.NVIDIA_DEFAULT, 100_000))
        assertEquals(32_768, AiProviders.outputBudget(AiProviderId.NVIDIA, "other/model"))
    }
    @Test fun `each provider needs explicit confirmation and a sane key and model`() {
        for (p in AiProviderId.entries) {
            val model = AiProviders.defaultModel(p)
            assertTrue(AiProviders.canSaveKey(p, "test-" + "k".repeat(35), model, true))
            assertFalse(AiProviders.canSaveKey(p, "test-" + "k".repeat(35), model, false))
            assertFalse(AiProviders.canSaveKey(p, "", model, true))
            assertFalse(AiProviders.canSaveKey(p, "k".repeat(35), "https://endpoint", true))
        }
    }
    @Test fun `NVIDIA disclosures explicitly prohibit production and shared keys`() {
        assertTrue(AiCopy.NVIDIA_SETUP_INTRO.contains("not for production"))
        assertTrue(AiCopy.NVIDIA_SETUP_INTRO.contains("shared key"))
        assertTrue(AiCopy.NVIDIA_CONFIRM.contains("internal testing and evaluation"))
        assertTrue(AiCopy.NVIDIA_CONFIRM.contains("not in production"))
        assertTrue(AiCopy.NVIDIA_DATA_NOTE.contains("confidential"))
    }
    @Test fun `every preview and content-free test names its actual recipient`() {
        for (p in AiProviderId.entries) {
            val model = AiProviders.defaultModel(p)
            assertTrue(AiCopy.previewHeader(p, model, 42).contains(p.label))
            assertTrue(AiCopy.previewHeader(p, model, 42).contains(model))
            assertTrue(AiCopy.testNote(p, model).contains(GeminiRequest.TEST_PROMPT))
            assertTrue(AiCopy.testNote(p, model).contains("System instruction: none"))
            assertTrue(AiCopy.testNote(p, model).contains("No code is sent"))
        }
    }
    @Test fun `Continue retains original recipient model and exact prompt strings`() {
        val original = (AiContextBuilder.fromSelection("int x=1;", 0, 8, "main.c", "C", false, "explain") as AiContextResult.Ready).prompt
            .copy(provider = AiProviderId.NVIDIA, model = AiProviders.NVIDIA_DEFAULT)
        val continued = original.copy(continuation = AiContinuationRequest(1, "tail here"))
        assertEquals(original.provider, continued.provider)
        assertEquals(original.model, continued.model)
        assertEquals(original.systemInstruction, continued.systemInstruction)
        assertTrue(continued.userText.startsWith(original.userText))
        assertTrue(AiProviderRequests.body(continued).contains(GeminiRequest.json(continued.userText)))
    }
    @Test fun `request timeline rows are disclosure not model tool results`() {
        val steps = listOf(AiAgentStep(AiAgentStepKind.REQUEST, "request", sentSystemInstruction = "private system", sentUserText = "private user"))
        val packed = AiAgentPrompt.pack("task", "map", steps).text
        assertFalse(packed.contains("private system"))
        assertFalse(packed.contains("private user"))
    }
}
