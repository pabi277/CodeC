package com.codeci.ide

import com.codeci.ide.ui.ai.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderClientTest {
    private data class Call(val key: String, val model: String, val body: String, val chars: Int)
    private class Spy(private val outcome: AiOutcome) : AiStreamClient {
        val calls = mutableListOf<Call>()
        override suspend fun request(apiKey: String, model: String, body: String, maxChars: Int, onText: (String) -> Unit): AiOutcome {
            calls += Call(apiKey, model, body, maxChars)
            return outcome
        }
    }
    @Test fun `NVIDIA failure never silently falls back to Gemini`() = runBlocking {
        val failed = AiOutcome.Failed(AiErrors.forHttp(429, null, provider = AiProviderId.NVIDIA))
        val gemini = Spy(AiOutcome.Answer("do not use", false))
        val nvidia = Spy(failed)
        val client = AiProviderClient(gemini, nvidia)
        assertEquals(failed, client.stream(AiProviderId.NVIDIA, "nvidia-key", "nvidia/model", "body", onText = {}))
        assertTrue(gemini.calls.isEmpty())
        assertEquals(listOf(Call("nvidia-key", "nvidia/model", "body", 48_000)), nvidia.calls)
    }
    @Test fun `Gemini failure never silently falls back to NVIDIA`() = runBlocking {
        val failed = AiOutcome.Failed(AiErrors.fail(AiFailureKind.BAD_KEY))
        val gemini = Spy(failed)
        val nvidia = Spy(AiOutcome.Answer("do not use", false))
        assertEquals(failed, AiProviderClient(gemini, nvidia).stream(AiProviderId.GEMINI, "google-key", AiModel.DEFAULT, "body", onText = {}))
        assertTrue(nvidia.calls.isEmpty())
        assertEquals(1, gemini.calls.size)
    }
    @Test fun `manual selection routes only the captured key model strings and remaining budget`() = runBlocking {
        val gemini = Spy(AiOutcome.Answer("ok", false))
        val nvidia = Spy(AiOutcome.Answer("ok", false))
        val client = AiProviderClient(gemini, nvidia)
        client.stream(AiProviderId.NVIDIA, "own-key", AiProviders.NVIDIA_DEFAULT, "exact preview", 16_000, {})
        client.stream(AiProviderId.GEMINI, "other-own-key", AiModel.DEFAULT, "other preview", 48_000, {})
        assertEquals(listOf(Call("own-key", AiProviders.NVIDIA_DEFAULT, "exact preview", 16_000)), nvidia.calls)
        assertEquals(listOf(Call("other-own-key", AiModel.DEFAULT, "other preview", 48_000)), gemini.calls)
    }
    @Test fun `a retry stays on the originally chosen provider and sends the same request`() = runBlocking {
        val gemini = Spy(AiOutcome.Answer("never", false))
        val nvidia = Spy(AiOutcome.Failed(AiErrors.forHttp(429, null, "0", provider = AiProviderId.NVIDIA)))
        val client = AiProviderClient(gemini, nvidia)
        AiRetry.once({ client.stream(AiProviderId.NVIDIA, "own-key", AiProviders.NVIDIA_DEFAULT, "exact preview", 16_000, {}) }, {}, {})
        assertTrue(gemini.calls.isEmpty())
        assertEquals(2, nvidia.calls.size)
        assertEquals(nvidia.calls.first(), nvidia.calls.last())
    }
}
