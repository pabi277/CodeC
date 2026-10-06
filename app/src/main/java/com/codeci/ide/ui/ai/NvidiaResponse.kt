package com.codeci.ide.ui.ai

import org.json.JSONObject

/** Platform JSON, no new dependency. Only visible content is rendered; reasoning/native tools are not executed. */
object NvidiaResponse {
    fun parse(payload: String): GeminiChunk? {
        if (payload.trim() == "[DONE]") return GeminiChunk(finishReason = "STOP")
        return try {
            val root = JSONObject(payload)
            val error = root.optJSONObject("error")
            if (error != null) {
                val code = error.optInt("code", 0).takeIf { it != 0 }
                GeminiChunk(
                    isError = true, errorCode = code,
                    errorFailure = AiErrors.forHttp(code ?: 0, payload, provider = AiProviderId.NVIDIA)
                )
            } else {
                val choice = root.optJSONArray("choices")?.optJSONObject(0)
                val usageObject = root.optJSONObject("usage")
                if (choice == null) {
                    // Some compatible streams include a final usage-only event.
                    // It never carried answer text and it still carries none;
                    // Phase 89 reads its counts when the provider named the two
                    // sides (`prompt_tokens` / `completion_tokens`). The null-ness
                    // contract is unchanged: an event with a `usage` object is a
                    // parseable event (chunk, empty text), an event without one is
                    // not a chunk at all. A `total_tokens`-only report yields no
                    // usable pair, so the readout says "not reported" — unknown is
                    // never turned into a number.
                    return if (usageObject != null) GeminiChunk(usage = usageFrom(usageObject)) else null
                }
                val delta = choice.optJSONObject("delta")
                val content = delta?.opt("content") as? String ?: ""
                val reason = choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" }
                val finish = when (reason) {
                    "length" -> "MAX_TOKENS"
                    "content_filter" -> "SAFETY"
                    else -> reason
                }
                GeminiChunk(text = content, finishReason = finish, usage = usageFrom(usageObject))
            }
        } catch (_: Exception) {
            null
        }
    }

    /** OpenAI-compatible `usage.prompt_tokens` / `.completion_tokens`; absent fields stay null. */
    private fun usageFrom(usage: JSONObject?): AiTokenUsage? {
        if (usage == null) return null
        val prompt = if (usage.has("prompt_tokens")) usage.optInt("prompt_tokens") else null
        val output = if (usage.has("completion_tokens")) usage.optInt("completion_tokens") else null
        return if (prompt == null && output == null) null else AiTokenUsage(prompt = prompt, output = output)
    }
}
