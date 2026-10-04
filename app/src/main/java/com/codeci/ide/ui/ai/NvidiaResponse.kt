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
                // Phase 89: the usage-only event keeps carrying no answer text,
                // but it is no longer thrown away — it now carries the counts the
                // Level 12 readout needs (still never rendered as text).
                val usage = usageFrom(root.optJSONObject("usage"))
                if (choice == null) {
                    return if (usage != null) GeminiChunk(usage = usage) else null
                }
                val delta = choice.optJSONObject("delta")
                val content = delta?.opt("content") as? String ?: ""
                val reason = choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" }
                val finish = when (reason) {
                    "length" -> "MAX_TOKENS"
                    "content_filter" -> "SAFETY"
                    else -> reason
                }
                GeminiChunk(text = content, finishReason = finish, usage = usage)
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
