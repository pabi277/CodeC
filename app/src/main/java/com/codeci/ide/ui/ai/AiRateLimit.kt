package com.codeci.ide.ui.ai

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Only classified metadata survives a response. Never retain a provider body or header string. */
enum class AiQuotaWindow { MINUTE, DAY, UNKNOWN }

data class AiRateLimit(val window: AiQuotaWindow = AiQuotaWindow.UNKNOWN, val retryAfterSeconds: Long? = null)

/** In-memory countdown. Declared outside AiUiState to preserve its source-slice safety pin. */
data class AiRetryCountdown(val window: AiQuotaWindow, val seconds: Long)

/** Phase 82 — conservative marker classification and server-minimum waits, all host-testable. */
object AiRateLimits {
    const val DEFAULT_BACKOFF_SECONDS = 12L
    const val MAX_AUTO_WAIT_SECONDS = 120L
    const val MAX_RETRIES = 1

    private val day = Regex("per[_ -]?day|\\bRPD\\b|daily[_ -]?quota", RegexOption.IGNORE_CASE)
    private val minute = Regex("per[_ -]?minute|\\bRPM\\b|\\bTPM\\b", RegexOption.IGNORE_CASE)
    private val infoObject = Regex("\\{[^{}]*\\}")
    private val retryType = Regex("\"@type\"\\s*:\\s*\"type.googleapis.com/google[.]rpc[.]RetryInfo\"")
    private val retryDelay = Regex("\"retryDelay\"\\s*:\\s*\"([0-9]+(?:[.][0-9]{1,9})?)s\"")
    private val digits = Regex("[0-9]+")
    private val dateFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz", // IMF-fixdate
        "EEEE, dd-MMM-yy HH:mm:ss zzz", // obsolete RFC 850 (HTTP recipients still accept it)
        "EEE MMM d HH:mm:ss yyyy"       // obsolete asctime
    )

    fun fromResponse(body: String?, retryAfter: String?, nowMs: Long): AiRateLimit {
        val b = body.orEmpty()
        val window = when {
            day.containsMatchIn(b) -> AiQuotaWindow.DAY // daily wins if several limits are reported
            minute.containsMatchIn(b) -> AiQuotaWindow.MINUTE
            else -> AiQuotaWindow.UNKNOWN
        }
        val header = headerDelay(retryAfter, nowMs)
        val detail = infoObject.findAll(b).filter { retryType.containsMatchIn(it.value) }
            .mapNotNull { retryDelay.find(it.value)?.groupValues?.get(1)?.let(::durationSeconds) }.maxOrNull()
        return AiRateLimit(window, listOfNotNull(header, detail).maxOrNull())
    }

    /** RFC 9110 Retry-After is a nonnegative integer or HTTP-date, never a floating delay. */
    fun headerDelay(raw: String?, nowMs: Long): Long? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= 128 } ?: return null
        if (digits.matches(value)) {
            // Overflow still means a very long server wait, NOT permission to retry sooner.
            return value.toLongOrNull() ?: Long.MAX_VALUE
        }
        for (format in dateFormats) {
            val parser = SimpleDateFormat(format, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
                isLenient = false
            }
            val position = ParsePosition(0)
            val date = parser.parse(value, position) ?: continue
            if (position.index != value.length) continue
            val ms = (date.time - nowMs).coerceAtLeast(0)
            return ms / 1_000 + if (ms % 1_000 == 0L) 0 else 1
        }
        return null
    }

    private fun durationSeconds(value: String): Long? = runCatching {
        val seconds = BigDecimal(value).setScale(0, RoundingMode.CEILING)
        if (seconds > BigDecimal.valueOf(Long.MAX_VALUE)) Long.MAX_VALUE else seconds.toLong()
    }.getOrNull()

    /** No pointless day retry, long hidden sleep, or replay of a partially streamed answer. */
    fun retrySeconds(failure: AiFailure, retriesUsed: Int, hasPartialText: Boolean): Long? {
        if (failure.kind != AiFailureKind.RATE_LIMIT || retriesUsed >= MAX_RETRIES || hasPartialText) return null
        val rate = failure.rateLimit ?: AiRateLimit()
        if (rate.window == AiQuotaWindow.DAY && rate.retryAfterSeconds == null) return null
        val seconds = rate.retryAfterSeconds ?: DEFAULT_BACKOFF_SECONDS
        return seconds.takeIf { it in 0..MAX_AUTO_WAIT_SECONDS }
    }

    /** Fixed copy only. The numeric countdown is classified metadata, not provider prose. */
    fun countdownLine(value: AiRetryCountdown): String {
        val limit = when (value.window) {
            AiQuotaWindow.MINUTE -> "Per-minute limit hit"
            AiQuotaWindow.DAY -> "Daily limit hit"
            AiQuotaWindow.UNKNOWN -> "Limit hit"
        }
        return if (value.seconds == 0L) "$limit; retrying now (once). Stop cancels." else
            "$limit; retrying in ${value.seconds}s (once). Stop cancels."
    }
}
