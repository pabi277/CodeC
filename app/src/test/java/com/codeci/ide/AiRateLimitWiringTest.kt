package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRateLimitWiringTest {
    private val dir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private fun src(name: String) = RepoFiles.codeOnly(File(dir, name).readText())
    @Test fun `three original stream sites share one private retry helper with no fourth call`() {
        val vm = src("AiViewModel.kt")
        assertEquals(3, Regex("client[.]stream[(]").findAll(vm).count())
        assertEquals(4, Regex("streamWithRetry[(]").findAll(vm).count()) // three callers + declaration
        val helper = vm.substringAfter("private suspend fun streamWithRetry(").substringBefore("override fun onCleared()")
        assertTrue(helper.contains("AiRetry.once("))
        assertTrue(helper.contains("delay(it)"))
        assertTrue(helper.contains("hasPartialText"))
        assertFalse(helper.contains("client.stream("))
        assertTrue(vm.contains("if (_state.value.phase != AiPhase.PREVIEW"))
    }
    @Test fun `Stop clears testing and countdown and cancels the same coroutine job`() {
        val vm = src("AiViewModel.kt")
        val stop = vm.substringAfter("fun stop()").substringBefore("fun retry()")
        assertTrue(stop.contains("job?.cancel()"))
        assertTrue(stop.contains("retryCountdown = null"))
        assertTrue(stop.contains("testing = false"))
        val clear = vm.substringAfter("fun clear()").substringBefore("fun dismissNotice()")
        assertTrue(clear.contains("job?.cancel()"))
        assertTrue(clear.contains("retryCountdown = null"))
        assertTrue(vm.substringAfter("fun deleteKey()").substringBefore("fun testConnection()").contains("clear()"))
    }
    @Test fun `both chat and connection test show the countdown and Stop`() {
        for (name in listOf("AiHome.kt", "AiChatSheet.kt")) {
            assertTrue(name, src(name).contains("AiRateLimits.countdownLine(it)"))
            assertTrue(name, src(name).contains("onClick = onStop"))
            assertTrue(name, src(name).contains("AiCopy.STOP"))
        }
    }
    @Test fun `HTTP adapter really reads Retry-After disconnects on cancel and never follows redirects`() {
        val http = src("AiHttpStream.kt")
        assertTrue(http.contains("connection.getHeaderField("))
        assertTrue(File(dir, "AiHttpStream.kt").readText().contains("\"Retry-After\""))
        assertTrue(http.contains("AiErrors.forHttp(code, errorBody, retryAfter, nowMs(), provider)"))
        assertTrue(http.contains("continuation.invokeOnCancellation"))
        assertTrue(http.contains("connection.disconnect()"))
        assertTrue(http.contains("connection.instanceFollowRedirects = false"))
        assertFalse(http.contains("Thread.sleep"))
    }
    @Test fun `agent retry stays inside task wall clock with no new turn charge inside helper`() {
        val vm = src("AiViewModel.kt")
        val helper = vm.substringAfter("private suspend fun streamWithRetry(").substringBefore("override fun onCleared()")
        assertTrue(helper.contains("AiAgentLimits.MAX_WALL_CLOCK_MS"))
        assertTrue(helper.contains("withTimeout(remaining)"))
        assertFalse(helper.contains("withTurn("))
        assertFalse(helper.contains("withToolCalls("))
        assertFalse(helper.contains("withRun("))
        assertFalse(helper.contains("continuations ="))
    }
    @Test fun `retry policy cannot persist raw provider text or a countdown`() {
        for (name in listOf("AiRateLimit.kt", "AiRetry.kt")) {
            val code = src(name)
            for (bad in listOf("android.", "java.io", "Properties", "SharedPreferences", "writeText", "Log.", "AppLogger")) {
                assertFalse("$name $bad", code.contains(bad))
            }
        }
        val state = src("AiViewModel.kt").substringAfter("data class AiUiState(").substringBefore(")\n")
        assertTrue(state.contains("val retryCountdown: AiRetryCountdown? = null"))
        assertFalse(state.contains("errorBody"))
        assertFalse(state.contains("retryAfter: String"))
    }
    @Test fun `a connection test cannot race a Send into a stuck testing spinner`() {
        val vm = src("AiViewModel.kt")
        val send = vm.substringAfter("fun send()").substringBefore("fun stop()")
        assertTrue(send.contains("_state.value.testing) return"))
        assertTrue(src("AiChatSheet.kt").contains("if (!state.testing && !state.configuring)"))
        assertTrue(src("AiHome.kt").contains("onOpenChat, enabled = !state.testing && !state.configuring"))
    }
    @Test fun `pending or empty NVIDIA test never says answered and does not poll`() {
        val vm = src("AiViewModel.kt")
        val test = vm.substringAfter("fun testConnection()").substringBefore("private class AgentDeadlineReached")
        assertTrue(test.contains("provider == AiProviderId.GEMINI && outcome.failure.kind == AiFailureKind.EMPTY"))
        assertFalse(src("AiHttpStream.kt").contains("requestId"))
        assertFalse(src("AiProviderClient.kt").contains("requestId"))
        assertTrue(src("AiHttpStream.kt").contains("provider == AiProviderId.NVIDIA && code == 202"))
    }
}
