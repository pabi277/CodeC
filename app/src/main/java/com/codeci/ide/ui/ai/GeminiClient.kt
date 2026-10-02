package com.codeci.ide.ui.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Phase 76 — the one network call of the AI helper (decision record §4.2).
 *
 * Dependency-free on purpose, like `GitHubPublishApi`: `HttpURLConnection`
 * over HTTPS with explicit timeouts, off the main thread. Every decision is
 * delegated to pure, host-tested code — [GeminiRequest] (URL/headers/body),
 * [SseLineSplitter] (stream framing), [GeminiResponse] (decoding),
 * [AiAnswerAccumulator] (caps/blocks) and [AiErrors] (messages).
 *
 * Cancel: cancelling the calling coroutine disconnects the socket, which
 * unblocks a pending read — no hidden request survives Stop/Cancel.
 * Nothing here logs; exception messages are never surfaced (they are mapped
 * to fixed [AiErrors] sentences).
 */
class GeminiClient {

    /** Streams one request; [onText] receives the whole answer so far after each event. */
    suspend fun stream(
        apiKey: String,
        model: String,
        body: String,
        /**
         * Phase 81 — the most characters this one reply may add. Defaults to
         * [AiLimits.MAX_REPLY_CHARS]; a continuation passes what is left of
         * [AiContinuation.MAX_TOTAL_CHARS], so one visible answer stays bounded.
         */
        maxChars: Int = AiLimits.MAX_REPLY_CHARS,
        onText: (String) -> Unit
    ): AiOutcome =
        withContext(Dispatchers.IO) {
            val url = GeminiRequest.streamUrl(model)
                ?: return@withContext AiOutcome.Failed(AiErrors.fail(AiFailureKind.MODEL_NOT_FOUND))
            val connection = URL(url).openConnection() as HttpURLConnection
            val cancelHook = coroutineContext[Job]?.invokeOnCompletion { runCatching { connection.disconnect() } }
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = AiLimits.CONNECT_TIMEOUT_MS
                connection.readTimeout = AiLimits.READ_TIMEOUT_MS
                connection.doOutput = true
                connection.useCaches = false
                GeminiRequest.headers(apiKey).forEach { (k, v) -> connection.setRequestProperty(k, v) }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                if (code !in 200..299) {
                    val errorBody = runCatching {
                        connection.errorStream?.use { s -> readCapped(s, ERROR_BODY_CAP) }
                    }.getOrNull()
                    return@withContext AiOutcome.Failed(AiErrors.forHttp(code, errorBody))
                }

                val accumulator = AiAnswerAccumulator(maxChars = maxChars)
                val sse = SseLineSplitter()
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val line = reader.readLine() ?: break
                        val event = sse.feed(line) ?: continue
                        if (!feed(event, accumulator, onText)) return@use
                    }
                    sse.finish()?.let { feed(it, accumulator, onText) }
                }
                accumulator.outcome()
            } catch (e: CancellationException) {
                throw e
            } catch (_: UnknownHostException) {
                AiOutcome.Failed(AiErrors.fail(AiFailureKind.OFFLINE))
            } catch (_: SocketTimeoutException) {
                AiOutcome.Failed(AiErrors.fail(AiFailureKind.TIMEOUT))
            } catch (_: IOException) {
                coroutineContext.ensureActive()
                AiOutcome.Failed(AiErrors.fail(AiFailureKind.NETWORK))
            } catch (_: Exception) {
                coroutineContext.ensureActive()
                AiOutcome.Failed(AiErrors.fail(AiFailureKind.NETWORK))
            } finally {
                cancelHook?.dispose()
                runCatching { connection.disconnect() }
            }
        }

    private fun feed(event: String, accumulator: AiAnswerAccumulator, onText: (String) -> Unit): Boolean {
        val chunk = GeminiResponse.parse(event) ?: return true
        val more = accumulator.accept(chunk)
        onText(accumulator.text)
        return more
    }

    /** Reads at most [cap] bytes (no `readNBytes`: API 33+, CodeC's minSdk is 24). */
    private fun readCapped(input: java.io.InputStream, cap: Int): String {
        val buf = ByteArray(cap)
        var total = 0
        while (total < cap) {
            val n = input.read(buf, total, cap - total)
            if (n <= 0) break
            total += n
        }
        return String(buf, 0, total, Charsets.UTF_8)
    }

    private companion object {
        /** Enough to spot `API_KEY_INVALID`; the body itself is never shown or stored. */
        const val ERROR_BODY_CAP = 8 * 1024
    }
}
