package com.codeci.ide

import com.codeci.ide.ui.ai.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Response strings never survive classification. Dates use an injected receiving clock. */
class AiRateLimitTest {
    private val now = 1_000_000L
    private fun failure(body: String? = null, header: String? = null) = AiErrors.forHttp(429, body, header, now)
    private fun retry(body: String? = null, header: String? = null) = AiRateLimits.retrySeconds(failure(body, header), 0, false)

    @Test fun `numeric Retry-After is the exact minimum and zero is valid`() {
        assertEquals(12L, retry(header = "12"))
        assertEquals(60L, retry(header = " 60 "))
        assertEquals(0L, retry(header = "0"))
    }
    @Test fun `HTTP dates round up and past dates allow an immediate retry`() {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }
        val date = format.format(java.util.Date(now + 12_000))
        assertEquals(12L, AiRateLimits.headerDelay(date, now))
        assertEquals(12L, AiRateLimits.headerDelay(date, now + 1))
        assertEquals(0L, AiRateLimits.headerDelay(date, now + 20_000))
    }
    @Test fun `obsolete HTTP date formats are accepted without java-time`() {
        assertEquals(12L, AiRateLimits.headerDelay("Thursday, 01-Jan-70 00:16:52 GMT", now))
        assertEquals(12L, AiRateLimits.headerDelay("Thu Jan 1 00:16:52 1970", now))
    }
    @Test fun `negative malformed and fractional headers are ignored not wrapped`() {
        for (bad in listOf("", "-1", "1.5", "NaN", "12junk", "Thu, 99 Jan 1970 00:00:00 GMT", "12\nsecret")) {
            assertNull(bad, AiRateLimits.headerDelay(bad, now))
            assertEquals(AiRateLimits.DEFAULT_BACKOFF_SECONDS, retry(header = bad))
        }
    }
    @Test fun `overflow and long server waits never become short retries`() {
        assertEquals(Long.MAX_VALUE, AiRateLimits.headerDelay("999999999999999999999999", now))
        assertNull(retry(header = "999999999999999999999999"))
        assertNull(retry(header = "121"))
        assertEquals(120L, retry(header = "120"))
        assertTrue(failure(header = "900").message.contains("No early retry"))
    }
    @Test fun `RetryInfo fractional seconds round UP`() {
        val body = """
            {"error":{"status":"RESOURCE_EXHAUSTED","details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"12.001s"}]}}
        """.trimIndent()
        assertEquals(13L, retry(body))
        assertEquals(15L, retry(body, "15"))
        assertEquals(13L, retry(body, "10"))
    }
    @Test fun `RetryInfo is recognized regardless of property order and max of details wins`() {
        val body = """
            {"details":[{"retryDelay":"1s","@type":"type.googleapis.com/google.rpc.RetryInfo"},{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"20s"}]}
        """.trimIndent()
        assertEquals(20L, retry(body))
    }
    @Test fun `arbitrary prose and untyped delay fields do not set a reset`() {
        assertEquals(12L, retry("retry in 999 seconds"))
        assertEquals(12L, retry("{\"retryDelay\":\"999s\"}"))
        assertEquals(12L, retry("{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"-1s\"}"))
    }
    @Test fun `RESOURCE_EXHAUSTED is a rate failure even without HTTP 429`() {
        val f = AiErrors.forHttp(400, "{\"status\":\"RESOURCE_EXHAUSTED\"}")
        assertEquals(AiFailureKind.RATE_LIMIT, f.kind)
        assertEquals(12L, AiRateLimits.retrySeconds(f, 0, false))
    }
    @Test fun `per-minute request and token quota markers say minute`() {
        for (marker in listOf("GenerateRequestsPerMinutePerProjectPerModel-FreeTier", "generate_tokens_per_minute", "RPM", "TPM")) {
            val f = failure(marker)
            assertEquals(AiQuotaWindow.MINUTE, f.rateLimit!!.window)
            assertTrue(f.message.contains("per-minute"))
        }
    }
    @Test fun `daily markers say daily and do not pretend a short reset exists`() {
        for (marker in listOf("GenerateRequestsPerDayPerProjectPerModel-FreeTier", "generate_requests_per_day", "RPD")) {
            val f = failure(marker)
            assertEquals(AiQuotaWindow.DAY, f.rateLimit!!.window)
            assertTrue(f.message.contains("daily"))
            assertNull(AiRateLimits.retrySeconds(f, 0, false))
        }
        assertEquals(12L, retry("GenerateRequestsPerDay", "12"))
    }
    @Test fun `daily wins over minute when both are exhausted`() {
        assertEquals(AiQuotaWindow.DAY, failure("GenerateRequestsPerDay GenerateTokensPerMinute").rateLimit!!.window)
        assertNull(retry("GenerateRequestsPerDay GenerateTokensPerMinute"))
    }
    @Test fun `ambiguous 429 never invents a minute or day window`() {
        val f = failure("quota reached")
        assertEquals(AiQuotaWindow.UNKNOWN, f.rateLimit!!.window)
        assertFalse(f.message.contains("daily"))
        assertFalse(f.message.contains("per-minute"))
    }
    @Test fun `key and region failures retain precedence and are never retried`() {
        for (f in listOf(AiErrors.forHttp(401, "RESOURCE_EXHAUSTED"), AiErrors.forHttp(400, "API_KEY_INVALID RESOURCE_EXHAUSTED"), AiErrors.forHttp(403, "FAILED_PRECONDITION RESOURCE_EXHAUSTED"))) {
            assertTrue(f.kind == AiFailureKind.BAD_KEY || f.kind == AiFailureKind.NOT_AVAILABLE)
            assertNull(AiRateLimits.retrySeconds(f, 0, false))
        }
    }
    @Test fun `raw provider messages keys urls and headers never become display text or metadata`() {
        val secret = "nvapi-" + "s".repeat(40)
        for (provider in AiProviderId.entries) {
            val f = AiErrors.forHttp(429, "RESOURCE_EXHAUSTED GenerateRequestsPerMinute $secret https://example.invalid/raw", "bad $secret", now, provider)
            assertFalse(f.toString().contains(secret))
            assertFalse(f.toString().contains("https:"))
            assertFalse(f.toString().contains("RESOURCE_EXHAUSTED"))
        }
    }
    @Test fun `countdown copy says the classified window and one cancelable retry`() {
        assertTrue(AiRateLimits.countdownLine(AiRetryCountdown(AiQuotaWindow.MINUTE, 12)).contains("retrying in 12s"))
        assertTrue(AiRateLimits.countdownLine(AiRetryCountdown(AiQuotaWindow.DAY, 2)).contains("Daily"))
        assertTrue(AiRateLimits.countdownLine(AiRetryCountdown(AiQuotaWindow.UNKNOWN, 0)).contains("retrying now"))
        assertTrue(AiRateLimits.countdownLine(AiRetryCountdown(AiQuotaWindow.UNKNOWN, 12)).contains("Stop cancels"))
    }
    @Test fun `partial output and a spent retry are hard stops`() {
        val f = failure(header = "1")
        assertNull(AiRateLimits.retrySeconds(f, 1, false))
        assertNull(AiRateLimits.retrySeconds(f, 0, true))
        assertEquals(1, AiRateLimits.MAX_RETRIES)
    }
}
