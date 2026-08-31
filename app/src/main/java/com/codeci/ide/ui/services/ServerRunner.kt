package com.codeci.ide.ui.services

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/** A loopback URL detected in a server's output (always 127.0.0.1, never 0.0.0.0). */
data class DetectedServerUrl(val url: String, val port: Int)

/**
 * Phase 14 — pure port-detection for long-lived server processes. Scans each
 * streamed line for the binding report a dev server prints when it is ready
 * to accept connections, and returns the loopback URL the Web Preview should
 * load. Deliberately pattern-based (a random `http://127.0.0.1:…` inside
 * rendered content must NOT match), and only 127.0.0.1/0.0.0.0 lines are
 * accepted — the address is rewritten to 127.0.0.1 because nothing else is
 * reachable from the app WebView.
 */
object ServerPortDetector {

    private val BIND_PATTERNS = listOf(
        // Flask dev server: `* Running on http://127.0.0.1:5000`
        Regex("""\* Running on http://(?:127\.0\.0\.1|0\.0\.0\.0):(\d+)/?"""),
        // Uvicorn (FastAPI): `Uvicorn running on http://127.0.0.1:8000`
        Regex("""Uvicorn running on http://(?:127\.0\.0\.1|0\.0\.0\.0):(\d+)/?"""),
        // `python -m http.server`: `Serving HTTP on 127.0.0.1 port 8000 ...`
        Regex("""Serving HTTP on (?:127\.0\.0\.1|0\.0\.0\.0) port (\d+)/?"""),
        // CodeC C microservice template
        Regex("""CodeC server listening on http://(?:127\.0\.0\.1|0\.0\.0\.0):(\d+)/?"""),
        // Other well-behaved dev servers
        Regex("""listening on http://(?:127\.0\.0\.1|0\.0\.0\.0):(\d+)/?""", RegexOption.IGNORE_CASE)
    )

    fun detect(line: String): DetectedServerUrl? {
        for (pattern in BIND_PATTERNS) {
            val port = pattern.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: continue
            if (port in 1..65535) {
                return DetectedServerUrl("http://127.0.0.1:$port", port)
            }
        }
        return null
    }
}

/** Events streamed by [ServerRunner] while a background server process lives. */
sealed class ServerEvent {
    /** One merged stdout/stderr line. */
    data class Output(val line: String) : ServerEvent()

    /** The server printed its bind line — the Web Preview URL is [url]. */
    data class Ready(val url: String, val port: Int) : ServerEvent()

    /** Process is alive but no bind line appeared within the readiness window. */
    data class ReadyTimeout(val message: String) : ServerEvent()

    /** The server process ended on its own. */
    data class Exited(val exitCode: Int) : ServerEvent()

    /** The server could not be started at all. */
    data class Failed(val message: String) : ServerEvent()
}

/**
 * Phase 14 — Android-free background runner for long-lived local servers
 * (Flask/FastAPI/`http.server`/C microservices).
 *
 * Unlike [ExecutionRunner] (batch build→run with a timeout), this runner
 * streams the process's merged output indefinitely, watches it for the
 * port-binding line, and emits [ServerEvent.Ready] once so the caller can
 * open the Web Preview. [stop] destroys the process (Stop button / leaving
 * the screen); cancelling the collection stops it too.
 */
class ServerRunner(
    private val shell: File,
    private val environment: Map<String, String>,
    private val command: String,
    private val workDir: File,
    private val readyTimeoutSeconds: Long = 20L
) {

    private val processLock = Any()
    private var currentProcess: Process? = null
    private val readyReported = AtomicBoolean(false)

    /** True while a live child process exists (tests use this to sync). */
    fun hasLiveProcess(): Boolean = synchronized(processLock) { currentProcess != null }

    /** Destroys the live child process if any. Safe to call repeatedly. */
    fun stop() {
        val process = synchronized(processLock) {
            val current = currentProcess
            currentProcess = null
            current
        }
        process?.let(::destroyProcess)
    }

    fun start(): Flow<ServerEvent> = callbackFlow {
        if (command.isBlank()) {
            trySend(ServerEvent.Failed("No run command configured for this server."))
            close()
            return@callbackFlow
        }
        if (!workDir.isDirectory) {
            trySend(ServerEvent.Failed("Working directory does not exist: ${workDir.absolutePath}"))
            close()
            return@callbackFlow
        }
        val process = try {
            ProcessBuilder(shell.absolutePath, "-c", command)
                .directory(workDir)
                .redirectErrorStream(true)
                .apply {
                    environment().clear()
                    environment().putAll(environment)
                }
                .start()
        } catch (e: Exception) {
            trySend(ServerEvent.Failed(e.message ?: "Could not start server."))
            close()
            return@callbackFlow
        }
        synchronized(processLock) { currentProcess = process }

        Thread({
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        trySend(ServerEvent.Output(line))
                        if (!readyReported.get()) {
                            ServerPortDetector.detect(line)?.let { detected ->
                                if (readyReported.compareAndSet(false, true)) {
                                    trySend(ServerEvent.Ready(detected.url, detected.port))
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Stream closed by stop()/death — nothing to forward.
            }
        }, "codec-server-reader").also {
            it.isDaemon = true
            it.start()
        }

        try {
            val deadline = System.nanoTime() + readyTimeoutSeconds * 1_000_000_000L
            var timeoutReported = false
            while (isAlive(process)) {
                if (!readyReported.get() && !timeoutReported && System.nanoTime() >= deadline) {
                    timeoutReported = true
                    trySend(
                        ServerEvent.ReadyTimeout(
                            "Server is running but no port line was detected in its output"
                        )
                    )
                }
                delay(100)
            }
            val exitCode = runCatching { process.exitValue() }.getOrDefault(1)
            trySend(ServerEvent.Exited(exitCode))
            close()
        } catch (e: CancellationException) {
            // Stop pressed: awaitClose below destroys the child, then rethrow.
            throw e
        } catch (_: Exception) {
            // Unexpected stream failure — finish cleanly.
        }
        awaitClose { stop() }
    }.flowOn(Dispatchers.IO)

    private fun isAlive(process: Process): Boolean = try {
        process.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    /** destroy() (all API levels) + reflective destroyForcibly() (API 26+), like ExecutionRunner. */
    private fun destroyProcess(process: Process) {
        try {
            process.destroy()
        } catch (_: Exception) {
        }
        try {
            val method = Process::class.java.getMethod("destroyForcibly")
            method.invoke(process)
        } catch (_: Exception) {
        }
    }
}
