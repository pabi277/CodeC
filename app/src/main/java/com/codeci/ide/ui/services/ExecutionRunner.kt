package com.codeci.ide.ui.services

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Phase 11 — batch execution of the build/run commands for the editor's
 * Output Panel. Runs the exact shell command strings the terminal handoff
 * produces (e.g. `cc main.c -o bin/app`, `./bin/app`) through the app's
 * resolved userland shell with the CodeC environment, streaming stdout+stderr
 * line by line.
 *
 * Deliberately Android-free so the runner itself is unit-testable on the
 * host JVM: the caller supplies the shell binary and the environment map
 * (see [com.codeci.ide.ui.terminal.ShellBootstrap.prepare]).
 */
data class RunSpec(
    val workDir: File,
    val buildCommand: String?,
    val runCommand: String?
)

enum class RunPhase { BUILDING, RUNNING }

sealed class RunEvent {
    data class PhaseChanged(val phase: RunPhase) : RunEvent()
    data class Output(val phase: RunPhase, val line: String) : RunEvent()
    data class BuildFinished(val exitCode: Int, val durationMs: Long, val timedOut: Boolean) : RunEvent()
    data class RunFinished(val exitCode: Int, val durationMs: Long, val timedOut: Boolean) : RunEvent()
    data class Failed(val message: String) : RunEvent()
}

class ExecutionRunner(
    private val shell: File,
    private val environment: Map<String, String>,
    private val buildTimeoutSeconds: Long = 30L,
    private val runTimeoutSeconds: Long = 10L
) {

    companion object {
        /** Conventional timeout exit code, matching `timeout(1)` and the old runner. */
        const val TIMED_OUT_EXIT_CODE = 124
    }

    private data class ProcessResult(
        val exitCode: Int,
        val durationMs: Long,
        val timedOut: Boolean
    )

    /**
     * Build (when configured) then run (when configured and the build
     * succeeded). A failing build stops the pipeline — the run command never
     * executes. Cancelling the collection destroys the live process.
     */
    fun run(spec: RunSpec): Flow<RunEvent> = callbackFlow {
        var currentProcess: Process? = null
        fun kill() {
            currentProcess?.let(::destroyProcess)
            currentProcess = null
        }
        try {
            val build = spec.buildCommand?.trim().orEmpty()
            val run = spec.runCommand?.trim().orEmpty()
            if (build.isEmpty() && run.isEmpty()) {
                trySend(RunEvent.Failed("No build or run command configured."))
                close()
                return@callbackFlow
            }
            if (!spec.workDir.isDirectory) {
                trySend(RunEvent.Failed("Working directory does not exist: ${spec.workDir.absolutePath}"))
                close()
                return@callbackFlow
            }
            if (build.isNotEmpty()) {
                trySend(RunEvent.PhaseChanged(RunPhase.BUILDING))
                val result = runProcess(
                    workDir = spec.workDir,
                    command = build,
                    timeoutSeconds = buildTimeoutSeconds,
                    onProcess = { process -> currentProcess = process },
                    onLine = { line -> trySend(RunEvent.Output(RunPhase.BUILDING, line)) }
                )
                trySend(RunEvent.BuildFinished(result.exitCode, result.durationMs, result.timedOut))
                if (result.exitCode != 0) {
                    close()
                    return@callbackFlow
                }
            }
            if (run.isNotEmpty()) {
                trySend(RunEvent.PhaseChanged(RunPhase.RUNNING))
                val result = runProcess(
                    workDir = spec.workDir,
                    command = run,
                    timeoutSeconds = runTimeoutSeconds,
                    onProcess = { process -> currentProcess = process },
                    onLine = { line -> trySend(RunEvent.Output(RunPhase.RUNNING, line)) }
                )
                trySend(RunEvent.RunFinished(result.exitCode, result.durationMs, result.timedOut))
            }
            close()
        } catch (e: CancellationException) {
            // Stop pressed while a process was alive: kill it, then propagate.
            kill()
            throw e
        } catch (e: Exception) {
            trySend(RunEvent.Failed(e.message ?: "Failed to run command."))
        } finally {
            close()
        }
        awaitClose { kill() }
    }.flowOn(Dispatchers.IO)

    /**
     * Runs [command] via the configured shell in [workDir], forwarding every
     * stdout/stderr line to [onLine] as it arrives. Returns when the process
     * exits, times out, or the collecting coroutine is cancelled.
     */
    private suspend fun runProcess(
        workDir: File,
        command: String,
        timeoutSeconds: Long,
        onProcess: (Process) -> Unit,
        onLine: (String) -> Unit
    ): ProcessResult {
        val process = ProcessBuilder(shell.absolutePath, "-c", command)
            .directory(workDir)
            .redirectErrorStream(true)
            .apply {
                environment().clear()
                environment().putAll(environment)
            }
            .start()
        onProcess(process)

        val readerThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val text = line ?: continue
                        if (text.isNotEmpty()) onLine(text)
                    }
                }
            } catch (_: Exception) {
                // Process killed or stream closed — nothing to forward.
            }
        }.also {
            it.isDaemon = true
            it.start()
        }

        val startedAt = System.currentTimeMillis()
        // Poll instead of blocking waitFor so cancellation (Stop) is delivered
        // within one poll tick instead of after the full timeout.
        val timedOut = !awaitExit(process, timeoutSeconds * 1000L)
        val durationMs = System.currentTimeMillis() - startedAt
        if (timedOut) destroyProcess(process)
        readerThread.join(500)
        val exitCode = if (timedOut) {
            TIMED_OUT_EXIT_CODE
        } else {
            try {
                process.exitValue()
            } catch (_: IllegalThreadStateException) {
                1
            }
        }
        return ProcessResult(exitCode, durationMs, timedOut)
    }

    private suspend fun awaitExit(process: Process, timeoutMillis: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        while (true) {
            if (!isAlive(process)) return true
            if (System.nanoTime() >= deadline) return false
            delay(50)
        }
    }

    private fun isAlive(process: Process): Boolean = try {
        process.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    /**
     * destroy() first (all API levels), then destroyForcibly() when the
     * runtime provides it (API 26+). Reflective so this class stays free of
     * the Android `Build` class and runs unchanged on the host JVM.
     */
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
