package com.codeci.ide

import com.codeci.ide.ui.ai.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual transport with fake connections. No key or request reaches the network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AiHttpStreamTest {
    private class Connection(
        url: URL,
        private val status: Int,
        private val wire: String = "",
        private val error: String = "",
        private val retryAfter: String? = null,
        private val stream: InputStream? = null
    ) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        val headers = linkedMapOf<String, String>()
        var closed = 0
        var cancellationRelease: (() -> Unit)? = null
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { closed++; cancellationRelease?.invoke() }
        override fun setRequestProperty(key: String, value: String) { headers[key] = value }
        override fun getOutputStream() = sent
        override fun getResponseCode() = status
        override fun getHeaderField(name: String) = if (name.equals("Retry-After", true)) retryAfter else null
        override fun getErrorStream() = ByteArrayInputStream(error.toByteArray())
        override fun getInputStream() = stream ?: ByteArrayInputStream(wire.toByteArray())
    }
    private fun http(c: Connection) = AiHttpStream(open = { c }, nowMs = { 1_000_000L })
    private fun google(text: String) = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + GeminiRequest.json(text) + "}]},\"finishReason\":\"STOP\"}]}\n\n"
    private fun nvidia(text: String) = "data: {\"choices\":[{\"delta\":{\"content\":" + GeminiRequest.json(text) + "},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"

    @Test fun `Gemini HTTP 429 really carries header and body markers and closes before retry`() = runBlocking {
        val c = Connection(URL("https://generativelanguage.googleapis.com/"), 429, error = "GenerateRequestsPerMinute SECRET", retryAfter = "12")
        val result = GeminiClient(http(c)).stream("own-key", AiModel.DEFAULT, "approved body", onText = {}) as AiOutcome.Failed
        assertEquals(AiQuotaWindow.MINUTE, result.failure.rateLimit!!.window)
        assertEquals(12L, result.failure.rateLimit!!.retryAfterSeconds)
        assertEquals(1, c.closed)
        assertFalse(result.toString().contains("SECRET"))
        assertEquals("own-key", c.headers[GeminiRequest.HEADER_KEY])
        assertEquals("approved body", c.sent.toString("UTF-8"))
    }
    @Test fun `automatic retry transmits byte-identical body headers and remaining budget`() = runBlocking {
        val calls = mutableListOf<Connection>()
        val transport = AiHttpStream(open = { url ->
            Connection(url, if (calls.isEmpty()) 429 else 200,
                wire = google("tail"), retryAfter = "0").also { calls += it }
        })
        val client = GeminiClient(transport)
        val result = AiRetry.once({ client.stream("own-key", AiModel.DEFAULT, "approved\nbody", 16_000, {}) }, {}, {})
        assertEquals(AiOutcome.Answer("tail", false), result)
        assertEquals(2, calls.size)
        assertEquals(calls[0].sent.toString("UTF-8"), calls[1].sent.toString("UTF-8"))
        assertEquals(calls[0].headers, calls[1].headers)
        assertTrue(calls.all { it.closed == 1 })
    }
    @Test fun `NVIDIA SSE stops at DONE and never renders reasoning or following data`() = runBlocking {
        val c = Connection(URL(NvidiaRequest.URL), 200, nvidia("answer") + "data: not json\n\n")
        val answer = NvidiaClient(http(c)).request("nvapi-own", AiProviders.NVIDIA_DEFAULT, "body", 48_000, {})
        assertEquals(AiOutcome.Answer("answer", false), answer)
        assertEquals("Bearer nvapi-own", c.headers["Authorization"])
        assertFalse(c.instanceFollowRedirects)
        assertEquals(1, c.closed)
    }
    @Test fun `shared transport enforces Continue's smaller reply cap on either provider`() = runBlocking {
        for (provider in AiProviderId.entries) {
            val c = Connection(URL("https://example.invalid/"), 200, if (provider == AiProviderId.GEMINI) google("x".repeat(20_000)) else nvidia("x".repeat(20_000)))
            val client: AiStreamClient = if (provider == AiProviderId.GEMINI) GeminiClient(http(c)) else NvidiaClient(http(c))
            val result = client.request("own-key", AiProviders.defaultModel(provider), "body", AiContinuation.requestBudget(48_000), {}) as AiOutcome.Answer
            assertEquals(16_000, result.text.length)
            assertTrue(result.cutShort)
        }
    }
    @Test fun `an SSE RESOURCE_EXHAUSTED before text retains retry metadata`() = runBlocking {
        val wire = "data: {\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"12s\"}]}}\n\n"
        val c = Connection(URL("https://generativelanguage.googleapis.com/"), 200, wire)
        val result = GeminiClient(http(c)).stream("own-key", AiModel.DEFAULT, "body", onText = {}) as AiOutcome.Failed
        assertEquals(12L, result.failure.rateLimit!!.retryAfterSeconds)
        assertEquals(AiFailureKind.RATE_LIMIT, result.failure.kind)
    }
    @Test fun `malformed SSE is a fixed failure not a successful partial tool answer`() = runBlocking {
        val c = Connection(URL(NvidiaRequest.URL), 200, "data: not-json-SECRET\n\n")
        val f = (NvidiaClient(http(c)).request("own-key", AiProviders.NVIDIA_DEFAULT, "body", 48_000, {}) as AiOutcome.Failed).failure
        assertEquals(AiFailureKind.NETWORK, f.kind)
        assertFalse(f.message.contains("SECRET"))
    }
    @Test fun `async 202 is not a connected answer and redirects cannot forward credentials`() = runBlocking {
        for (code in listOf(202, 302)) {
            val c = Connection(URL(NvidiaRequest.URL), code, wire = "private queued message")
            val result = NvidiaClient(http(c)).request("own-key", AiProviders.NVIDIA_DEFAULT, "body", 48_000, {}) as AiOutcome.Failed
            if (code == 202) {
                assertTrue(result.failure.message.contains("pending request"))
                assertTrue(result.failure.message.contains("does not poll"))
                assertEquals(AiFailureKind.SERVER, result.failure.kind)
            }
            assertFalse(c.instanceFollowRedirects)
            assertEquals(1, c.closed)
        }
    }
    @Test fun `cancelling a blocked read disconnects immediately and unblocks the worker`() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val stream = object : InputStream() {
            override fun read(): Int {
                entered.countDown()
                if (!released.await(3, TimeUnit.SECONDS)) throw IOException("test deadline")
                throw IOException("private exception on disconnect")
            }
        }
        val c = Connection(URL("https://generativelanguage.googleapis.com/"), 200, stream = stream)
        c.cancellationRelease = { released.countDown() }
        val job = launch(Dispatchers.Default) {
            GeminiClient(http(c)).stream("own-key", AiModel.DEFAULT, "body", onText = {})
            fail("Stop must cancel rather than return a displayed failure")
        }
        assertTrue("transport must enter blocked read", entered.await(3, TimeUnit.SECONDS))
        withTimeout(2_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
        assertEquals(0L, released.count)
        assertTrue(c.closed >= 1)
    }
    @Test fun `a rate error after visible partial output is not replayed on either provider`() = runBlocking {
        for (provider in AiProviderId.entries) {
            var opened = 0
            var partial = ""
            val visible = if (provider == AiProviderId.GEMINI) google("partial") else nvidia("partial").substringBefore("data: [DONE]")
            val rejected = "data: {\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}\n\n"
            val transport = AiHttpStream(open = { url -> opened++; Connection(url, 200, visible + rejected) })
            val client: AiStreamClient = if (provider == AiProviderId.GEMINI) GeminiClient(transport) else NvidiaClient(transport)
            val result = AiRetry.once(
                { client.request("own-key", AiProviders.defaultModel(provider), "body", 48_000) { partial = it } },
                { fail("partial output must never wait for replay") }, {}, { partial.isNotEmpty() }
            ) as AiOutcome.Failed
            assertEquals(AiFailureKind.RATE_LIMIT, result.failure.kind)
            assertEquals("partial", partial)
            assertEquals(1, opened)
        }
    }
    @Test fun `error bodies are byte bounded before classification and raw bytes are discarded`() = runBlocking {
        val raw = "x".repeat(9_000) + "GenerateRequestsPerDay SECRET"
        val c = Connection(URL("https://generativelanguage.googleapis.com/"), 429, error = raw)
        val result = GeminiClient(http(c)).stream("own-key", AiModel.DEFAULT, "body", onText = {}) as AiOutcome.Failed
        assertEquals(AiQuotaWindow.UNKNOWN, result.failure.rateLimit!!.window)
        assertFalse(result.toString().contains("SECRET"))
        assertFalse(result.toString().contains("xxxx"))
        assertEquals(1, c.closed)
    }
    @Test fun `socket exceptions are fixed network failures and do not trigger automatic retry`() = runBlocking {
        var opened = 0
        val broken = object : InputStream() { override fun read(): Int = throw IOException("private key and URL") }
        val c = Connection(URL("https://generativelanguage.googleapis.com/"), 200, stream = broken)
        val client = GeminiClient(AiHttpStream(open = { opened++; c }))
        val result = AiRetry.once({ client.stream("own-key", AiModel.DEFAULT, "body", onText = {}) }, { fail("not a quota error") }, {}) as AiOutcome.Failed
        assertEquals(AiFailureKind.NETWORK, result.failure.kind)
        assertFalse(result.toString().contains("private key"))
        assertEquals(1, opened)
        assertEquals(1, c.closed)
    }
    @Test fun `NVIDIA invalid model is refused locally before the network edge`() = runBlocking {
        val client = NvidiaClient(AiHttpStream(open = { fail("invalid model must not open a connection") }))
        assertEquals(AiFailureKind.MODEL_NOT_FOUND, (client.request("own-key", "https://host", "body", 48_000, {}) as AiOutcome.Failed).failure.kind)
    }
    @Test fun `countdown begins only after the failed socket has disconnected`() = runBlocking {
        val calls = mutableListOf<Connection>()
        val transport = AiHttpStream(open = { url -> Connection(url, 429, retryAfter = "1").also { calls += it } })
        val client = GeminiClient(transport)
        AiRetry.once({ client.stream("own-key", AiModel.DEFAULT, "body", onText = {}) }, {}, {
            if (it != null) assertEquals(1, calls.first().closed)
        })
        assertEquals(2, calls.size)
        assertTrue(calls.all { it.closed == 1 })
    }
}
