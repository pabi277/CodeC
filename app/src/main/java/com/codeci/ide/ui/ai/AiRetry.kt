package com.codeci.ide.ui.ai

/**
 * Phase 82 — one retry of the SAME approved request. No HTTP/Android/clock dependency.
 * Wait and cancellation are injected: production uses cancellable coroutine delay;
 * tests use virtual time or a real cancelled coroutine. A rejected agent turn is
 * still one logical turn, with no tool/run execution here.
 */
object AiRetry {
    suspend fun once(
        attempt: suspend () -> AiOutcome,
        wait: suspend (millis: Long) -> Unit,
        onCountdown: (AiRetryCountdown?) -> Unit,
        hasPartialText: () -> Boolean = { false },
        /** Called before waiting and each tick/resend (agent wall clock / caller cancellation). */
        allowed: (waitSeconds: Long) -> Boolean = { true }
    ): AiOutcome {
        val first = attempt()
        val failure = (first as? AiOutcome.Failed)?.failure ?: return first
        val seconds = AiRateLimits.retrySeconds(failure, retriesUsed = 0, hasPartialText = hasPartialText())
            ?: return first
        if (!allowed(seconds)) return first
        val window = failure.rateLimit?.window ?: AiQuotaWindow.UNKNOWN
        try {
            var left = seconds
            onCountdown(AiRetryCountdown(window, left))
            while (left > 0) {
                wait(1_000) // never a blocking sleep; Stop cancels this suspension
                left--
                if (hasPartialText() || !allowed(left)) return first
                onCountdown(AiRetryCountdown(window, left))
            }
            if (hasPartialText() || !allowed(0)) return first
            return attempt() // exactly once; its rejection is FINAL (no recursive retry)
        } finally {
            onCountdown(null)
        }
    }
}
