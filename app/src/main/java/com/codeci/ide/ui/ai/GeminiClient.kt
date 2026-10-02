package com.codeci.ide.ui.ai

/** Native Gemini adapter; the shared HTTP edge and VM retry do not change Google's payload. */
class GeminiClient(private val http: AiHttpStream = AiHttpStream()) : AiStreamClient {
    override suspend fun request(
        apiKey: String, model: String, body: String, maxChars: Int, onText: (String) -> Unit
    ): AiOutcome = stream(apiKey, model, body, maxChars, onText)

    /** Phase 81: a continuation passes only the remaining shared answer budget. */
    suspend fun stream(
        apiKey: String,
        model: String,
        body: String,
        maxChars: Int = AiLimits.MAX_REPLY_CHARS,
        onText: (String) -> Unit
    ): AiOutcome {
        val url = GeminiRequest.streamUrl(model)
            ?: return AiOutcome.Failed(AiErrors.fail(AiFailureKind.MODEL_NOT_FOUND))
        val accumulator = AiAnswerAccumulator(maxChars = maxChars)
        return http.stream(url, GeminiRequest.headers(apiKey), body, AiProviderId.GEMINI, accumulator, GeminiResponse::parse, onText)
    }
}
