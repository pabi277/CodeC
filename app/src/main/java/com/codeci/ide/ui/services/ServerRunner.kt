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

/**
 * A bind line detected in a server's output (Phase 14, LAN-aware since 37.1).
 *
 * [url] is always the loopback address — that is what the on-device WebView
 * loads, and it never changes. [bind] is what the process *said* it listens
 * on, which decides whether a LAN URL is honest: a server bound to
 * `127.0.0.1` cannot be opened from another device, so [isWildcard] gates the
 * second address instead of the app advertising a URL that would time out.
 */
data class DetectedServerUrl(
    val url: String,
    val port: Int,
    val bind: String = LanAddress.LOOPBACK_HOST
) {
    /** Bound to every interface — peers on the Wi-Fi can reach it. */
    val isWildcard: Boolean get() = bind == LanAddress.WILDCARD_HOST
}

/**
 * Phase 14 — pure port-detection for long-lived server processes. Scans each
 * streamed line for the binding report a dev server prints when it is ready
 * to accept connections, and returns the loopback URL the Web Preview should
 * load. Deliberately pattern-based (a random `http://127.0.0.1:…` inside
 * rendered content must NOT match), and only 127.0.0.1/localhost/0.0.0.0 lines
 * are accepted — the URL is always rewritten to 127.0.0.1 because that is the
 * only address the app's own WebView is guaranteed to reach.
 *
 * Phase 37.2 adds the mirror-image rule: [detectBindFailure] recognises the
 * "somebody already has this port" line, so a clash reads as a message with a
 * next step instead of a server that exits after three silent seconds.
 */
object ServerPortDetector {

    private const val HOST_GROUP = 1
    private const val PORT_GROUP = 2

    private val BIND_PATTERNS = listOf(
        // Flask dev server: `* Running on http://127.0.0.1:5000`
        Regex("""\* Running on https?://(127\.0\.0\.1|localhost|\[::1\]|0\.0\.0\.0):(\d+)"""),
        // Uvicorn (FastAPI): `Uvicorn running on http://127.0.0.1:8000`
        Regex("""Uvicorn running on https?://(127\.0\.0\.1|localhost|\[::1\]|0\.0\.0\.0):(\d+)"""),
        // `python -m http.server`: `Serving HTTP on 127.0.0.1 port 8000 ...`
        Regex("""Serving HTTP on (127\.0\.0\.1|localhost|0\.0\.0\.0) port (\d+)"""),
        // CodeC C microservice template
        Regex("""CodeC server listening on https?://(127\.0\.0\.1|localhost|\[::1\]|0\.0\.0\.0):(\d+)"""),
        // Other well-behaved dev servers
        Regex(
            """listening on https?://(127\.0\.0\.1|localhost|\[::1\]|0\.0\.0\.0):(\d+)""",
            RegexOption.IGNORE_CASE
        )
    )

    /** A bind error, with the port when the framework names it (0 = unknown). */
    data class BindFailure(val message: String, val port: Int)

    private val BIND_FAILURE_PATTERNS = listOf(
        // Python stdlib / Werkzeug: `OSError: [Errno 98] Address already in use`
        Regex("""\[Errno 98\]\s*(Address already in use|address already in use)"""),
        // Uvicorn: `ERROR: [Errno 98] error while attempting to bind on address ('0.0.0.0', 8080): address already in use`
        Regex("""attempt(?:ing)? to bind on address \([^)]*[^0-9](\d{1,5})\)""", RegexOption.IGNORE_CASE),
        // Node / Java style
        Regex("""EADDRINUSE[^\d]*(\d{1,5})?""", RegexOption.IGNORE_CASE),
        Regex("""java\.net\.BindException:\s*Address already in use""", RegexOption.IGNORE_CASE),
        // C `perror("bind")` on Android prints exactly this
        Regex("""^bind:\s*Address already in use""", RegexOption.IGNORE_CASE)
    )

    fun detect(line: String): DetectedServerUrl? {
        for (pattern in BIND_PATTERNS) {
            val match = pattern.find(line) ?: continue
            val port = match.groupValues.getOrNull(PORT_GROUP)?.toIntOrNull() ?: continue
            if (port !in 1..65535) continue
            val bind = match.groupValues.getOrNull(HOST_GROUP).orEmpty()
                .removeSurrounding("[", "]")
            return DetectedServerUrl(
                url = "http://${LanAddress.LOOPBACK_HOST}:$port",
                port = port,
                bind = if (bind == LanAddress.WILDCARD_HOST) bind else LanAddress.LOOPBACK_HOST
            )
        }
        return null
    }

    /**
     * True when [line] reports that the server could not take its port. The
     * runner turns the first hit into [ServerEvent.BindFailed]; the message
     * names the port when the framework was polite enough to print it.
     */
    fun detectBindFailure(line: String): BindFailure? {
        for (pattern in BIND_FAILURE_PATTERNS) {
            val match = pattern.find(line) ?: continue
            val port = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull()
                ?.takeIf { it in 1..65535 } ?: 0
            return BindFailure(line.trim(), port)
        }
        return null
    }
}

/**
 * Phase 37.2 — how the shell is asked to run a server command.
 *
 * `sh -c "python3 app.py"` puts a *shell* in front of the program on many
 * builds: destroying that shell (Stop) orphans the server, and the port keeps
 * answering with nobody able to stop it — exactly the failure 37.2's exit
 * condition #3 forbids ("Stop … releases the port"). `exec` replaces the shell
 * with the program, so the pid the runner kills IS the server. Only a single
 * simple command can be exec'd safely, so anything with shell structure keeps
 * today's behaviour untouched.
 */
object ServerLaunch {

    /** Shell structures that make `exec` wrong (or impossible) for a command. */
    private val COMPOUND_MARKERS = listOf("&&", "||", ";", "|", "&", "\n", "`", "$(", ">", "<")

    /**
     * Shell builtins are deliberately not exec'd: `exec exit 1` / `exec cd x`
     * mean different things in different shells, and a server that will not
     * start is worse than one whose Stop needs a second try.
     */
    private val BUILTINS = setOf(
        "exit", "cd", "pwd", "read", "wait", "set", "export", "local", "shift",
        "trap", "eval", "alias", "unalias", "jobs", "fg", "bg", "kill", ":", "true", "false"
    )

    fun isSingleSimpleCommand(command: String): Boolean {
        val text = command.trim()
        if (text.isEmpty()) return false
        if (COMPOUND_MARKERS.any { text.contains(it) }) return false
        if (text.startsWith("exec ")) return false
        val first = text.substringBefore(' ').trim()
        return first.isNotEmpty() && first !in BUILTINS && !first.contains('=')
    }

    /** The argument passed to `sh -c` (trimmed when we add `exec`, untouched otherwise). */
    fun shellArgument(command: String): String =
        if (isSingleSimpleCommand(command)) "exec ${command.trim()}" else command
}

/** Events streamed by [ServerRunner] while a background server process lives. */
sealed class ServerEvent {
    /** One merged stdout/stderr line. */
    data class Output(val line: String) : ServerEvent()

    /**
     * The server printed its bind line. [url] is the on-device loopback
     * address; [bind] says whether peers could reach it too (Phase 37.1).
     */
    data class Ready(val url: String, val port: Int, val bind: String = LanAddress.LOOPBACK_HOST) :
        ServerEvent()

    /** Process is alive but no bind line appeared within the readiness window. */
    data class ReadyTimeout(val message: String) : ServerEvent()

    /**
     * Phase 37.2 — the process reported it could not take its port. A server
     * that dies on `Address already in use` used to look like a crash; this
     * carries the actionable sentence instead.
     */
    data class BindFailed(val message: String, val port: Int = 0) : ServerEvent()

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
    private val readyTimeoutSeconds: Long = 20L,
    /** The port the project config asks for — only used to name it in a clash message. */
    private val preferredPort: Int = 0
) {

    private val processLock = Any()
    private var currentProcess: Process? = null
    private val readyReported = AtomicBoolean(false)
    private val bindFailureReported = AtomicBoolean(false)

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
            ProcessBuilder(shell.absolutePath, "-c", ServerLaunch.shellArgument(command))
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

        val reader = Thread({
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        trySend(ServerEvent.Output(line))
                        if (!readyReported.get()) {
                            ServerPortDetector.detect(line)?.let { detected ->
                                if (readyReported.compareAndSet(false, true)) {
                                    trySend(
                                        ServerEvent.Ready(detected.url, detected.port, detected.bind)
                                    )
                                }
                            }
                            // Phase 37.2 — name a port clash while the server is
                            // still trying to come up, instead of letting it exit
                            // into an anonymous "exited with code 1".
                            if (!bindFailureReported.get()) {
                                ServerPortDetector.detectBindFailure(line)?.let { failure ->
                                    if (bindFailureReported.compareAndSet(false, true)) {
                                        val port = failure.port.takeIf { it > 0 } ?: preferredPort
                                        trySend(
                                            ServerEvent.BindFailed(
                                                if (port > 0) {
                                                    "Port $port is in use — stop the other " +
                                                        "server or change the port."
                                                } else {
                                                    "The server could not bind its port — " +
                                                        "it is already in use."
                                                },
                                                port
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Stream closed by stop()/death — nothing to forward.
            }
        }, "codec-server-reader").apply {
            isDaemon = true
            start()
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
            // The last lines (a bind line, an `Address already in use`) are read
            // on another thread: completing the flow first would drop them, so
            // give the reader its EOF moment before exiting the stream.
            runCatching { reader.join(2_000) }
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
