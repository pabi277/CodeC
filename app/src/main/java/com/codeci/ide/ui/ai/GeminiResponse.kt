package com.codeci.ide.ui.ai

import org.json.JSONObject

/**
 * Phase 76 — one decoded `alt=sse` event of `streamGenerateContent`.
 *
 * House style (`ReleaseFetch`): `org.json`, no new dependency; tested under
 * Robolectric (`GeminiResponseTest`) because `org.json` is a framework class.
 * Response shape per https://ai.google.dev/api/generate-content (read
 * 2026-09-30): `candidates[0].content.parts[].text`,
 * `candidates[0].finishReason`, `promptFeedback.blockReason`, or an
 * `error` object.
 */
data class GeminiChunk(
    /** Visible answer text in this event (thought parts are skipped). */
    val text: String = "",
    val finishReason: String? = null,
    val blockReason: String? = null,
    /** True when the event is an `error` object (details are never shown). */
    val isError: Boolean = false,
    val errorCode: Int? = null,
    /** Typed markers/delay only; the raw error payload is never retained. */
    val errorFailure: AiFailure? = null,
    /**
     * Phase 89 (Level 12) — the provider's own `usageMetadata` for this event,
     * when it sent one. Untrusted data (S3): it is sanitised by
     * [AiMeasurePolicy] before it can reach the readout, and it is display
     * state only (D6). Null means the provider reported nothing, never zero.
     */
    val usage: AiTokenUsage? = null
)

object GeminiResponse {

    /** Decodes one event's data; a malformed payload → null (the caller reports, never guesses). */
    fun parse(payload: String): GeminiChunk? = try {
        val root = JSONObject(payload)
        val error = root.optJSONObject("error")
        if (error != null) {
            GeminiChunk(
                isError = true,
                errorCode = error.optInt("code", 0).takeIf { it != 0 },
                errorFailure = AiErrors.forHttp(error.optInt("code", 0), payload)
            )
        } else {
            val block = root.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }
            val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
            val finish = candidate?.optString("finishReason")?.takeIf { it.isNotBlank() }
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
            val text = buildString {
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        if (part.optBoolean("thought", false)) continue
                        append(part.optString("text", ""))
                    }
                }
            }
            GeminiChunk(
                text = text,
                finishReason = finish,
                blockReason = block,
                // Phase 89: read the provider's own counts if it sent them. A
                // missing field stays null (unknown), never 0.
                usage = usageFrom(root.optJSONObject("usageMetadata"))
            )
        }
    } catch (_: Exception) {
        null
    }

    /** `usageMetadata.promptTokenCount` / `.candidatesTokenCount`; absent fields stay null. */
    private fun usageFrom(usage: JSONObject?): AiTokenUsage? {
        if (usage == null) return null
        val prompt = if (usage.has("promptTokenCount")) usage.optInt("promptTokenCount") else null
        val output = if (usage.has("candidatesTokenCount")) usage.optInt("candidatesTokenCount") else null
        return if (prompt == null && output == null) null else AiTokenUsage(prompt = prompt, output = output)
    }
}
