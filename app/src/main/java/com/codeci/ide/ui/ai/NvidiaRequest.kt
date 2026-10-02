package com.codeci.ide.ui.ai

/**
 * Phase 82B — NVIDIA Build's OpenAI-compatible text/SSE request. No key or URL in a body.
 * Reference checked 2026-10-02: nvidia-nemotron-3-super-120b-a12b-infer.
 * Stateless system+user messages, exactly the two preview strings. No unsupported
 * store flag: NVIDIA's own trial retention terms are disclosed instead, not overridden.
 */
object NvidiaRequest {
    const val URL = "https://integrate.api.nvidia.com/v1/chat/completions"
    const val TEST_PROMPT = GeminiRequest.TEST_PROMPT

    fun headers(apiKey: String): Map<String, String> = linkedMapOf(
        "Content-Type" to "application/json; charset=utf-8",
        "Accept" to "text/event-stream",
        "Authorization" to "Bearer $apiKey",
        "User-Agent" to "CodeC-IDE"
    )

    fun body(prompt: AiPrompt): String = body(
        prompt.model, prompt.systemInstruction, prompt.userText,
        AiProviders.outputBudget(AiProviderId.NVIDIA, prompt.model)
    )

    fun testBody(model: String): String = body(
        model, null, TEST_PROMPT, AiProviders.outputBudget(AiProviderId.NVIDIA, model)
    )

    fun body(model: String, systemInstruction: String?, userText: String, maxOutputTokens: Int): String = buildString {
        append("{\"model\":").append(GeminiRequest.json(model))
        append(",\"messages\":[")
        if (systemInstruction != null) {
            append("{\"role\":\"system\",\"content\":").append(GeminiRequest.json(systemInstruction)).append("},")
        }
        append("{\"role\":\"user\",\"content\":").append(GeminiRequest.json(userText)).append("}]")
        append(",\"max_tokens\":").append(maxOutputTokens)
        append(",\"stream\":true}")
    }
}
