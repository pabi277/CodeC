package com.codeci.ide

import com.codeci.ide.ui.ai.*
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiRetryTest {
    private val limited = AiOutcome.Failed(AiErrors.forHttp(429, "GenerateRequestsPerMinute", "3"))
    private val answer = AiOutcome.Answer("done", false)

    @Test fun `one successful resend follows all countdown ticks and clears the wait`() = runBlocking {
        var attempts = 0
        var waited = 0L
        val ticks = mutableListOf<AiRetryCountdown?>()
        val result = AiRetry.once(
            attempt = { if (++attempts == 1) limited else answer },
            wait = { waited += it }, onCountdown = { ticks += it }
        )
        assertEquals(answer, result)
        assertEquals(2, attempts)
        assertEquals(3_000L, waited)
        assertEquals(listOf(3L, 2L, 1L, 0L), ticks.filterNotNull().map { it.seconds })
        assertNull(ticks.last())
    }
    @Test fun `a second rate rejection is final not a recursive retry`() = runBlocking {
        var attempts = 0
        val result = AiRetry.once({ attempts++; limited }, {}, {})
        assertEquals(limited, result)
        assertEquals(2, attempts)
    }
    @Test fun `nonrate errors and successful first answers never wait`() = runBlocking {
        for (first in listOf(answer, AiOutcome.Failed(AiErrors.fail(AiFailureKind.SERVER)), AiOutcome.Failed(AiErrors.fail(AiFailureKind.OFFLINE)))) {
            var attempts = 0
            val result = AiRetry.once({ attempts++; first }, { fail("must not wait") }, { fail("must not show a countdown") })
            assertEquals(first, result)
            assertEquals(1, attempts)
        }
    }
    @Test fun `partial text daily exhaustion and long server delay never replay`() = runBlocking {
        for ((first, partial) in listOf(
            limited to true,
            AiOutcome.Failed(AiErrors.forHttp(429, "GenerateRequestsPerDay")) to false,
            AiOutcome.Failed(AiErrors.forHttp(429, null, "3600")) to false
        )) {
            var attempts = 0
            assertEquals(first, AiRetry.once({ attempts++; first }, { fail("no wait") }, {}, { partial }))
            assertEquals(1, attempts)
        }
    }
    @Test fun `zero server delay permits exactly one immediate resend`() = runBlocking {
        var attempts = 0
        val ticks = mutableListOf<AiRetryCountdown?>()
        AiRetry.once({ attempts++; AiOutcome.Failed(AiErrors.forHttp(429, null, "0")) }, { fail("zero delay") }, { ticks += it })
        assertEquals(2, attempts)
        assertEquals(0L, ticks.first()!!.seconds)
        assertNull(ticks.last())
    }
    @Test fun `deadline denial before waiting sends nothing else`() = runBlocking {
        var attempts = 0
        val result = AiRetry.once({ attempts++; limited }, { fail("deadline") }, {}, allowed = { false })
        assertEquals(limited, result)
        assertEquals(1, attempts)
    }
    @Test fun `deadline is checked during countdown and clears the pending retry`() = runBlocking {
        var attempts = 0
        var waits = 0
        val ticks = mutableListOf<AiRetryCountdown?>()
        val result = AiRetry.once({ attempts++; limited }, { waits++ }, { ticks += it }, allowed = { waits == 0 })
        assertEquals(limited, result)
        assertEquals(1, attempts)
        assertEquals(1, waits)
        assertNull(ticks.last())
    }
    @Test fun `Stop cancels the real coroutine wait and no retry follows`() = runBlocking {
        var attempts = 0
        val waiting = CompletableDeferred<Unit>()
        val ticks = mutableListOf<AiRetryCountdown?>()
        val job = launch {
            AiRetry.once({ attempts++; limited }, { waiting.complete(Unit); delay(60_000) }, { ticks += it })
        }
        waiting.await()
        job.cancelAndJoin()
        assertEquals(1, attempts)
        assertTrue(job.isCancelled)
        assertNull(ticks.last())
    }
    @Test fun `retry preserves captured payload and remaining budget`() = runBlocking {
        val payload = "exact approved strings"
        val calls = mutableListOf<Pair<String, Int>>()
        AiRetry.once({ calls += payload to 16_000; if (calls.size == 1) limited else answer }, {}, {})
        assertEquals(listOf(payload to 16_000, payload to 16_000), calls)
    }
    @Test fun `cancellation thrown by an attempt is propagated not classified as failure`() = runBlocking {
        var attempts = 0
        try {
            AiRetry.once({ attempts++; throw CancellationException("private text") }, {}, {})
            fail("cancellation must escape")
        } catch (_: CancellationException) {
            assertEquals(1, attempts)
        }
    }
    @Test fun `a late partial callback during countdown still prevents replay`() = runBlocking {
        var attempts = 0
        var partial = false
        val ticks = mutableListOf<AiRetryCountdown?>()
        val result = AiRetry.once({ attempts++; limited }, { partial = true }, { ticks += it }, { partial })
        assertEquals(limited, result)
        assertEquals(1, attempts)
        assertNull(ticks.last())
    }
}
