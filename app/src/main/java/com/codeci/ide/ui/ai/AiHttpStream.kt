package com.codeci.ide.ui.ai

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * Phase 82B — the shared, dependency-free HTTPS/SSE edge. No retry policy here:
 * one attempt closes before the VM starts a visible, cancellable wait.
 * Cancellation disconnects immediately, not just after a blocked read returns.
 * Redirects are disabled: credentials must never follow a different recipient.
 * Injectable connection/clock make actual response/header/cleanup wiring testable.
 */
class AiHttpStream(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    suspend fun stream(
        url: String,
        headers: Map<String, String>,
        body: String,
        provider: AiProviderId,
        accumulator: AiAnswerAccumulator,
        decode: (String) -> GeminiChunk?,
        onText: (String) -> Unit
    ): AiOutcome = withContext(Dispatchers.IO) {
        val context = coroutineContext
        context.ensureActive()
        val connection = try {
            open(URL(url))
        } catch (_: Exception) {
            context.ensureActive()
            return@withContext AiOutcome.Failed(AiErrors.fail(AiFailureKind.NETWORK, provider))
        }
        suspendCancellableCoroutine<AiOutcome> { continuation ->
            val disconnected = AtomicBoolean(false)
            fun disconnect() {
                if (disconnected.compareAndSet(false, true)) runCatching { connection.disconnect() }
            }
            continuation.invokeOnCancellation { disconnect() }
            try {
                context.ensureActive()
                connection.requestMethod = "POST"
                connection.connectTimeout = AiLimits.CONNECT_TIMEOUT_MS
                connection.readTimeout = AiLimits.READ_TIMEOUT_MS
                connection.doOutput = true
                connection.useCaches = false
                connection.instanceFollowRedirects = false
                headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                context.ensureActive()
                val code = connection.responseCode
                val outcome = if (provider == AiProviderId.NVIDIA && code == 202) {
                    // Build may queue an async invocation. No undisclosed polling or false "Connected".
                    AiOutcome.Failed(AiErrors.forHttp(code, null, provider = provider))
                } else if (code !in 200..299) {
                    val retryAfter = connection.getHeaderField("Retry-After")
                    val errorBody = runCatching {
                        connection.errorStream?.use { readCapped(it, ERROR_BODY_CAP) }
                    }.getOrNull()
                    context.ensureActive()
                    AiOutcome.Failed(AiErrors.forHttp(code, errorBody, retryAfter, nowMs(), provider))
                } else {
                    val sse = SseLineSplitter()
                    var finished = false
                    fun feed(event: String) {
                        if (event.trim() == "[DONE]" && provider == AiProviderId.NVIDIA) {
                            finished = true
                            return
                        }
                        val chunk = decode(event) ?: GeminiChunk(
                            isError = true, errorFailure = AiErrors.fail(AiFailureKind.NETWORK, provider)
                        )
                        context.ensureActive()
                        finished = !accumulator.accept(chunk)
                        onText(accumulator.text)
                    }
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        while (!finished) {
                            context.ensureActive()
                            val line = reader.readLine() ?: break
                            sse.feed(line)?.let(::feed)
                        }
                        if (!finished) sse.finish()?.let(::feed)
                    }
                    context.ensureActive()
                    accumulator.outcome()
                }
                disconnect() // resource cleanup completes before the caller can start a countdown
                continuation.resume(outcome)
            } catch (e: CancellationException) {
                continuation.cancel(e)
            } catch (_: UnknownHostException) {
                continuation.resume(AiOutcome.Failed(AiErrors.fail(AiFailureKind.OFFLINE, provider)))
            } catch (_: SocketTimeoutException) {
                continuation.resume(AiOutcome.Failed(AiErrors.fail(AiFailureKind.TIMEOUT, provider)))
            } catch (_: IOException) {
                continuation.resume(AiOutcome.Failed(AiErrors.fail(AiFailureKind.NETWORK, provider)))
            } catch (_: Exception) {
                continuation.resume(AiOutcome.Failed(AiErrors.fail(AiFailureKind.NETWORK, provider)))
            } finally {
                disconnect()
            }
        }
    }

    /** minSdk 24: no readNBytes/readAllBytes/java.nio.file. Body discarded after classification. */
    private fun readCapped(input: InputStream, cap: Int): String {
        val bytes = ByteArray(cap)
        var total = 0
        while (total < cap) {
            val count = input.read(bytes, total, cap - total)
            if (count <= 0) break
            total += count
        }
        return String(bytes, 0, total, Charsets.UTF_8)
    }

    private companion object {
        const val ERROR_BODY_CAP = 8 * 1024
    }
}
