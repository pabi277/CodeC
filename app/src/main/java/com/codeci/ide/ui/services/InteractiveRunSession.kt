package com.codeci.ide.ui.services

import com.codeci.ide.ui.terminal.PtyNative
import com.codeci.ide.ui.terminal.PtySession
import com.codeci.ide.ui.terminal.ShellEnvironment
import java.io.File

/**
 * Decodes a raw waitpid status into a shell-style exit code: the low 7 bits
 * are the signal (0 = exited normally), bits 8..15 the exit code. Signaled
 * children report 128 + signal, matching `timeout(1)` conventions.
 */
fun decodeExitStatus(status: Int): Int {
    if (status < 0) return 1
    val signal = status and 0x7F
    return if (signal == 0) (status ushr 8) and 0xFF else 128 + signal
}

/**
 * Assembles raw PTY bytes into lines. Complete lines (ending in \n) are
 * emitted with partial = false; a trailing unterminated fragment is emitted
 * as a partial line every time it changes (so a `printf("Enter your name: ")`
 * prompt with no newline still appears immediately). CR is stripped (PTY
 * line discipline emits \r\n). Pure — host-unit-testable.
 */
class PtyLineBuffer(
    private val onCompleteLine: (String) -> Unit,
    private val onPartialLine: (String) -> Unit
) {
    private val pending = StringBuilder()

    fun append(bytes: ByteArray, length: Int) {
        if (length <= 0) return
        pending.append(String(bytes, 0, length, Charsets.UTF_8))
        var newline = pending.indexOf("\n")
        while (newline >= 0) {
            val line = pending.substring(0, newline).trimEnd('\r')
            pending.delete(0, newline + 1)
            if (line.isNotEmpty()) onCompleteLine(line)
            newline = pending.indexOf("\n")
        }
        if (pending.isNotEmpty()) onPartialLine(pending.toString())
    }

    /** Emits any leftover fragment as a final complete line (called on EOF). */
    fun flush() {
        if (pending.isNotEmpty()) {
            onCompleteLine(pending.toString().trimEnd('\r'))
            pending.setLength(0)
        }
    }
}

/**
 * Phase 11 (D9) — runs the program on a real PTY so interactive programs
 * (scanf/gets) behave like a terminal: output is line-buffered so every
 * prompt appears when printed, each scanf consumes exactly one entered line,
 * and typed input is echoed. Android-only (needs `libcodec-pty.so`);
 * [start] returns null when the native library is unavailable so callers
 * fall back to the piped [ExecutionRunner].
 */
class InteractiveRunSession private constructor(
    private val session: PtySession,
    private val readerThread: Thread,
    @Volatile private var stopped: Boolean = false
) {

    /** Sends one input line; the PTY's canonical mode delivers it to scanf. */
    fun sendLine(text: String) {
        if (stopped) return
        try {
            session.write(text + "\r")
        } catch (_: Exception) {
        }
    }

    /** Kills the program and frees the PTY. */
    fun stop() {
        if (stopped) return
        stopped = true
        try {
            session.close()
        } catch (_: Exception) {
        }
        try {
            readerThread.interrupt()
        } catch (_: Exception) {
        }
    }

    companion object {
        /**
         * Execs `sh -c [command]` with [env] in [workDir] under a fresh PTY.
         * [shellFile] is the resolved userland shell (ELF bash/busybox or
         * /system/bin/sh). [onOutput] receives (text, isPartial) lines as
         * they arrive; [onExit] fires once with the decoded exit code.
         */
        fun start(
            command: String,
            workDir: File,
            env: Map<String, String>,
            shellFile: File,
            onOutput: (text: String, partial: Boolean) -> Unit,
            onExit: (exitCode: Int) -> Unit
        ): InteractiveRunSession? {
            if (!PtyNative.nativeAvailable) return null
            val session = try {
                PtySession.start(
                    file = shellFile.absolutePath,
                    args = arrayOf("sh", "-c", command),
                    env = ShellEnvironment.envToArray(env),
                    cwd = workDir.absolutePath
                )
            } catch (_: Exception) {
                return null
            }
            val lineBuffer = PtyLineBuffer(
                onCompleteLine = { onOutput(it, false) },
                onPartialLine = { onOutput(it, true) }
            )
            val reader = Thread {
                val buffer = ByteArray(8192)
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        val count = session.read(buffer)
                        if (count <= 0) break
                        lineBuffer.append(buffer, count)
                    }
                } catch (_: Exception) {
                } finally {
                    lineBuffer.flush()
                    val status = try {
                        PtyNative.waitPid(session.pid, hang = true)
                    } catch (_: Exception) {
                        -1
                    }
                    onExit(decodeExitStatus(status))
                }
            }.also {
                it.isDaemon = true
                it.start()
            }
            return InteractiveRunSession(session, reader)
        }
    }
}
