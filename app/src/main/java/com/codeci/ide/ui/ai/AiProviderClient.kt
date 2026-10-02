package com.codeci.ide.ui.ai

/** Provider seam (Level 5A). Neither implementation knows about agent/tools or editor state. */
interface AiStreamClient {
    suspend fun request(apiKey: String, model: String, body: String, maxChars: Int, onText: (String) -> Unit): AiOutcome
}

/** Exhaustive routing; a provider failure is returned, never sent to a second provider. */
class AiProviderClient(
    private val gemini: AiStreamClient = GeminiClient(),
    private val nvidia: AiStreamClient = NvidiaClient()
) {
    suspend fun stream(
        provider: AiProviderId,
        apiKey: String,
        model: String,
        body: String,
        maxChars: Int = AiLimits.MAX_REPLY_CHARS,
        onText: (String) -> Unit
    ): AiOutcome = when (provider) {
        AiProviderId.GEMINI -> gemini.request(apiKey, model, body, maxChars, onText)
        AiProviderId.NVIDIA -> nvidia.request(apiKey, model, body, maxChars, onText)
    }
}

class NvidiaClient(private val http: AiHttpStream = AiHttpStream()) : AiStreamClient {
    override suspend fun request(
        apiKey: String,
        model: String,
        body: String,
        maxChars: Int,
        onText: (String) -> Unit
    ): AiOutcome {
        if (!AiProviders.isValidModel(AiProviderId.NVIDIA, model)) {
            return AiOutcome.Failed(AiErrors.fail(AiFailureKind.MODEL_NOT_FOUND, AiProviderId.NVIDIA))
        }
        return http.stream(
            NvidiaRequest.URL, NvidiaRequest.headers(apiKey), body, AiProviderId.NVIDIA,
            AiAnswerAccumulator(maxChars = maxChars, provider = AiProviderId.NVIDIA), NvidiaResponse::parse, onText
        )
    }
}
