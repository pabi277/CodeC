package com.codeci.ide.ui.ai

/**
 * Fixed, short error sentences only (D3). Response bodies are inspected for
 * well-known markers and immediately dropped. Never surface the key, prompt,
 * URL, raw body/header, or an exception's text, with either provider.
 */
enum class AiFailureKind {
    BAD_KEY, MODEL_NOT_FOUND, RATE_LIMIT, NOT_AVAILABLE, BAD_REQUEST,
    SERVER, OFFLINE, TIMEOUT, BLOCKED, EMPTY, NETWORK
}

data class AiFailure(val kind: AiFailureKind, val message: String, val rateLimit: AiRateLimit? = null)

object AiErrors {
    fun forHttp(
        code: Int,
        body: String?,
        retryAfter: String? = null,
        nowMs: Long = System.currentTimeMillis(),
        provider: AiProviderId = AiProviderId.GEMINI
    ): AiFailure {
        val b = body.orEmpty()
        return when {
            provider == AiProviderId.NVIDIA && code == 202 -> AiFailure(
                AiFailureKind.SERVER,
                "NVIDIA returned a pending request, not a streamed answer. CodeC does not poll it; try again later."
            )
            b.contains("API_KEY_INVALID") || b.contains("API key not valid") || code == 401 ->
                fail(AiFailureKind.BAD_KEY, provider)
            // Preserve the established region/billing precedence, before quota markers.
            b.contains("FAILED_PRECONDITION") || b.contains("location is not supported", ignoreCase = true) ->
                fail(AiFailureKind.NOT_AVAILABLE, provider)
            code == 429 || b.contains("RESOURCE_EXHAUSTED", ignoreCase = true) -> {
                val rate = AiRateLimits.fromResponse(b, retryAfter, nowMs)
                AiFailure(AiFailureKind.RATE_LIMIT, rateMessage(provider, rate), rate)
            }
            code == 403 -> fail(AiFailureKind.BAD_KEY, provider)
            code == 404 -> fail(AiFailureKind.MODEL_NOT_FOUND, provider)
            code in 400..499 -> fail(AiFailureKind.BAD_REQUEST, provider)
            code >= 500 -> fail(AiFailureKind.SERVER, provider)
            else -> fail(AiFailureKind.NETWORK, provider)
        }
    }

    fun isBlockReason(reason: String?): Boolean = reason != null && reason.uppercase() in BLOCK_REASONS

    private val BLOCK_REASONS = setOf(
        "SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "OTHER", "IMAGE_SAFETY", "LANGUAGE"
    )

    fun fail(kind: AiFailureKind, provider: AiProviderId = AiProviderId.GEMINI): AiFailure =
        AiFailure(kind, message(kind, provider))

    private fun name(provider: AiProviderId): String = when (provider) {
        AiProviderId.GEMINI -> "Gemini"
        AiProviderId.NVIDIA -> "NVIDIA"
    }

    fun rateMessage(provider: AiProviderId, rate: AiRateLimit): String {
        val subject = name(provider)
        val main = when (rate.window) {
            AiQuotaWindow.DAY -> "$subject's daily quota was reached. Try again after the quota resets; check your provider account."
            AiQuotaWindow.MINUTE -> "$subject's per-minute rate limit was reached. Wait a moment and try again."
            AiQuotaWindow.UNKNOWN -> "$subject's rate or quota limit was reached. Wait a moment and try again."
        }
        return main + if ((rate.retryAfterSeconds ?: 0) > AiRateLimits.MAX_AUTO_WAIT_SECONDS) {
            " The provider requested a wait longer than the app's automatic retry window. No early retry was sent."
        } else ""
    }

    fun message(kind: AiFailureKind, provider: AiProviderId = AiProviderId.GEMINI): String {
        val subject = name(provider)
        return when (kind) {
            AiFailureKind.BAD_KEY -> if (provider == AiProviderId.GEMINI) {
                "Google rejected this API key. Check it in AI settings, or create a new key."
            } else "NVIDIA rejected this API key. Check it in AI settings, or create a new key."
            AiFailureKind.MODEL_NOT_FOUND -> "This model name was not found. Check the model in AI settings."
            AiFailureKind.RATE_LIMIT -> rateMessage(provider, AiRateLimit())
            AiFailureKind.NOT_AVAILABLE -> if (provider == AiProviderId.GEMINI) {
                "Gemini is not available for this key here. Check your Google AI Studio settings."
            } else "NVIDIA is not available for this key here. Check your NVIDIA account."
            AiFailureKind.BAD_REQUEST -> "$subject could not accept this request. Check the model's limits or try a shorter selection."
            AiFailureKind.SERVER -> "$subject had a server problem. Try again later."
            AiFailureKind.OFFLINE -> "You're offline. Connect to the internet and try again."
            AiFailureKind.TIMEOUT -> "$subject took too long to answer. Try again."
            AiFailureKind.BLOCKED -> "$subject declined to answer this request."
            AiFailureKind.EMPTY -> "$subject sent an empty answer. Try again or rephrase."
            AiFailureKind.NETWORK -> "The request failed. Check your connection and try again."
        }
    }

    const val CUT_SHORT = "The answer was cut short because it reached the length limit."
}
