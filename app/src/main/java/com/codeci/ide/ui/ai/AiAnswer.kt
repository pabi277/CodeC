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
    /**
     * [usage] is the provider's own report for the request, when it sent one
     * (Phase 89, Level 12). Null means **not reported**, never zero; the readout
     * renders the difference. It is display state only and is never persisted
     * (D6), and it is sanitised by [AiMeasurePolicy] before display (S3).
     */
    data class Answer(
        val text: String,
        val cutShort: Boolean,
        val usage: AiTokenUsage? = null
    ) : AiOutcome()
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
    private var usage: AiTokenUsage? = null

    val text: String get() = sb.toString()

    /** Phase 89 — the last report the provider made; null when it made none. */
    val reportedUsage: AiTokenUsage? get() = usage

    /** True while more chunks are welcome; false once the answer is final (cap, error). */
    fun accept(chunk: GeminiChunk): Boolean {
        // Phase 89: a usage-only event must still be received — it carries no
        // text and no finish reason, but it is the provider's own count. Record
        // it before the early exits so a late usage event is not lost.
        if (chunk.usage != null) usage = chunk.usage
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
        return AiOutcome.Answer(t, cutShort, usage)
    }
}
