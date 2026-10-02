package com.codeci.ide.ui.ai

/**
 * Phase 76 — turns a stream of [GeminiChunk]s into what the panel shows.
 * Pure (host-tested by `AiAnswerTest`): the network client only feeds it.
 *
 *  - text grows chunk by chunk, but never past [AiLimits.MAX_REPLY_CHARS];
 *  - a block reason with no text is a [AiFailureKind.BLOCKED] failure;
 *  - an `error` event is a failure mapped by code, its details dropped;
 *  - `MAX_TOKENS` or the local cap marks the answer as cut short.
 */
sealed class AiOutcome {
    data class Answer(val text: String, val cutShort: Boolean) : AiOutcome()
    data class Failed(val failure: AiFailure) : AiOutcome()
}

class AiAnswerAccumulator(
    private val maxChars: Int = AiLimits.MAX_REPLY_CHARS,
    private val provider: AiProviderId = AiProviderId.GEMINI
) {
    private val sb = StringBuilder()
    private var cutShort = false
    private var blocked = false
    private var failure: AiFailure? = null

    val text: String get() = sb.toString()

    /** True while more chunks are welcome; false once the answer is final (cap, error). */
    fun accept(chunk: GeminiChunk): Boolean {
        if (failure != null) return false
        if (chunk.isError) {
            failure = chunk.errorFailure ?: AiErrors.forHttp(chunk.errorCode ?: 0, null, provider = provider)
            return false
        }
        if (AiErrors.isBlockReason(chunk.blockReason)) blocked = true
        if (chunk.text.isNotEmpty()) {
            val room = maxChars - sb.length
            if (chunk.text.length > room) {
                sb.append(chunk.text, 0, room.coerceAtLeast(0))
                cutShort = true
                return false
            }
            sb.append(chunk.text)
        }
        when {
            chunk.finishReason.equals("MAX_TOKENS", ignoreCase = true) -> cutShort = true
            AiErrors.isBlockReason(chunk.finishReason) -> blocked = true
        }
        return true
    }

    fun outcome(): AiOutcome {
        failure?.let { return AiOutcome.Failed(it) }
        val t = sb.toString()
        if (t.isBlank()) {
            return AiOutcome.Failed(AiErrors.fail(if (blocked) AiFailureKind.BLOCKED else AiFailureKind.EMPTY, provider))
        }
        return AiOutcome.Answer(t, cutShort)
    }
}
