package com.codeci.ide.ui.ai

/**
 * Phase 76 — every way a request can end badly, as one short, fixed
 * sentence. Pure and host-tested (`AiErrorsTest`).
 *
 * The law (D3 / research addendum A7): a message NEVER contains the key, the
 * request body, the URL, or the provider's raw response text — only fixed
 * words chosen here. Provider bodies are inspected for well-known markers and
 * then dropped.
 */
enum class AiFailureKind {
    BAD_KEY,
    MODEL_NOT_FOUND,
    RATE_LIMIT,
    NOT_AVAILABLE,
    BAD_REQUEST,
    SERVER,
    OFFLINE,
    TIMEOUT,
    BLOCKED,
    EMPTY,
    NETWORK
}

data class AiFailure(val kind: AiFailureKind, val message: String)

object AiErrors {

    fun forHttp(code: Int, body: String?): AiFailure {
        val b = body.orEmpty()
        return when {
            b.contains("API_KEY_INVALID") || b.contains("API key not valid") || code == 401 ->
                fail(AiFailureKind.BAD_KEY)
            // Region/billing refusals first: Google may send them as 400 or 403.
            b.contains("FAILED_PRECONDITION") || b.contains("location is not supported", ignoreCase = true) ->
                fail(AiFailureKind.NOT_AVAILABLE)
            code == 403 -> fail(AiFailureKind.BAD_KEY)
            code == 404 -> fail(AiFailureKind.MODEL_NOT_FOUND)
            code == 429 -> fail(AiFailureKind.RATE_LIMIT)
            code in 400..499 -> fail(AiFailureKind.BAD_REQUEST)
            code >= 500 -> fail(AiFailureKind.SERVER)
            else -> fail(AiFailureKind.NETWORK)
        }
    }

    /** A finish/block reason that means Gemini declined to answer. */
    fun isBlockReason(reason: String?): Boolean = reason != null && reason.uppercase() in BLOCK_REASONS

    private val BLOCK_REASONS = setOf(
        "SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "OTHER",
        "IMAGE_SAFETY", "LANGUAGE"
    )

    fun fail(kind: AiFailureKind): AiFailure = AiFailure(kind, message(kind))

    fun message(kind: AiFailureKind): String = when (kind) {
        AiFailureKind.BAD_KEY -> "Google rejected this API key. Check it in AI settings, or create a new key."
        AiFailureKind.MODEL_NOT_FOUND -> "This model name was not found. Check the model in AI settings."
        AiFailureKind.RATE_LIMIT -> "Gemini's rate or quota limit was reached. Wait a moment and try again."
        AiFailureKind.NOT_AVAILABLE -> "Gemini is not available for this key here. Check your Google AI Studio settings."
        AiFailureKind.BAD_REQUEST -> "Gemini could not accept this request. Try a shorter selection."
        AiFailureKind.SERVER -> "Gemini had a server problem. Try again later."
        AiFailureKind.OFFLINE -> "You're offline. Connect to the internet and try again."
        AiFailureKind.TIMEOUT -> "Gemini took too long to answer. Try again."
        AiFailureKind.BLOCKED -> "Gemini declined to answer this request."
        AiFailureKind.EMPTY -> "Gemini sent an empty answer. Try again or rephrase."
        AiFailureKind.NETWORK -> "The request failed. Check your connection and try again."
    }

    /** Shown under an answer that hit a length limit. */
    const val CUT_SHORT = "The answer was cut short because it reached the length limit."
}
