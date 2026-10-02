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
                if (choice == null) {
                    // Some compatible streams include a final usage-only event. Never render it.
                    return if (root.optJSONObject("usage") != null) GeminiChunk() else null
                }
                val delta = choice.optJSONObject("delta")
                val content = delta?.opt("content") as? String ?: ""
                val reason = choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" }
                val finish = when (reason) {
                    "length" -> "MAX_TOKENS"
                    "content_filter" -> "SAFETY"
                    else -> reason
                }
                GeminiChunk(text = content, finishReason = finish)
            }
        } catch (_: Exception) {
            null
        }
    }
}
