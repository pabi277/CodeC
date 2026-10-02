package com.codeci.ide.ui.ai

/** Phase 82B (Level 5A). Fixed recipients, never a user-supplied host or a fallback list. */
enum class AiProviderId(val slot: String, val label: String) {
    GEMINI("gemini", "Google Gemini"),
    NVIDIA("nvidia", "NVIDIA Build · dev/test only")
}

/** A dated capability row, not a promise that a user's key can access this model. Null = unknown. */
data class AiModelCapabilities(
    val streaming: Boolean? = null,
    val nativeTools: Boolean? = null,
    val structuredOutput: Boolean? = null,
    val contextTokens: Int? = null,
    val outputTokens: Int? = null,
    val sourceDate: String? = null
)

/** Pure provider/model decisions. Tools and file permissions do not consult provider capabilities. */
object AiProviders {
    const val NVIDIA_DEFAULT = "nvidia/nemotron-3-super-120b-a12b"
    const val NVIDIA_TERMS_VERSION = 1
    private val nvidiaId = Regex("[a-z0-9][a-z0-9._-]*/[a-z0-9][a-z0-9._-]*")

    fun defaultModel(provider: AiProviderId): String = when (provider) {
        AiProviderId.GEMINI -> AiModel.DEFAULT
        AiProviderId.NVIDIA -> NVIDIA_DEFAULT
    }

    fun isValidModel(provider: AiProviderId, model: String): Boolean = when (provider) {
        AiProviderId.GEMINI -> AiModel.isValid(model)
        AiProviderId.NVIDIA -> model.trim().let {
            it.length in 3..128 && nvidiaId.matches(it) && !it.contains("..")
        }
    }

    fun termsVersion(provider: AiProviderId): Int = when (provider) {
        AiProviderId.GEMINI -> AiKeySetup.TERMS_VERSION
        AiProviderId.NVIDIA -> NVIDIA_TERMS_VERSION
    }

    fun canSaveKey(provider: AiProviderId, raw: String, model: String, confirmed: Boolean): Boolean =
        AiKeySetup.canSave(raw, confirmed) && isValidModel(provider, model)

    /** No wildcard row: an unknown model cannot inherit a named model's limits. */
    fun capabilities(provider: AiProviderId, model: String): AiModelCapabilities = when {
        provider == AiProviderId.GEMINI && model.trim() == AiModel.DEFAULT ->
            AiModelCapabilities(true, true, true, 1_048_576, 65_536, "2026-10-02")
        provider == AiProviderId.NVIDIA && model.trim() == NVIDIA_DEFAULT ->
            AiModelCapabilities(streaming = true, outputTokens = 32_768, sourceDate = "2026-10-02")
        else -> AiModelCapabilities()
    }

    /** The owner raised the budget globally; only a known model ceiling may lower it. */
    fun outputBudget(provider: AiProviderId, model: String, requested: Int = AiLimits.MAX_OUTPUT_TOKENS): Int =
        minOf(requested.coerceAtLeast(1), capabilities(provider, model).outputTokens ?: requested.coerceAtLeast(1))

    fun capabilityLine(provider: AiProviderId, model: String): String {
        val c = capabilities(provider, model)
        fun known(value: Boolean?) = when (value) { true -> "yes"; false -> "no"; null -> "unknown" }
        return "Streaming: ${known(c.streaming)} · native tools: ${known(c.nativeTools)} · " +
            "structured output: ${known(c.structuredOutput)}\n" +
            "Context tokens: ${c.contextTokens ?: "unknown"} · output ceiling: ${c.outputTokens ?: "unknown"}. " +
            "CodeC uses its own bounded text tools, not native tools." +
            (c.sourceDate?.let { "\nReference checked $it; availability can change." }
                ?: "\nUntested model id: check its limits and use Test connection.")
    }

    fun constraintLine(provider: AiProviderId): String = when (provider) {
        AiProviderId.GEMINI -> "Quotas vary by model and account; Google AI Studio is authoritative."
        AiProviderId.NVIDIA -> "Internal testing/evaluation only, not production. Shared rate limits; " +
            "about 40 requests/minute is a reported estimate, not a guarantee. No published daily cap does not mean unlimited."
    }
}

/** Pure request dispatch. The prompt's recipient/model are immutable, including Continue and retry. */
object AiProviderRequests {
    fun body(prompt: AiPrompt): String = when (prompt.provider) {
        AiProviderId.GEMINI -> GeminiRequest.body(prompt)
        AiProviderId.NVIDIA -> NvidiaRequest.body(prompt)
    }

    fun body(provider: AiProviderId, model: String, systemInstruction: String, userText: String): String =
        when (provider) {
            AiProviderId.GEMINI -> GeminiRequest.body(systemInstruction, userText, AiProviders.outputBudget(provider, model))
            AiProviderId.NVIDIA -> NvidiaRequest.body(model, systemInstruction, userText, AiProviders.outputBudget(provider, model))
        }
}
