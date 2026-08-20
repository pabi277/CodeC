package com.codeci.ide.ui.terminal

import com.codeci.ide.ui.utils.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Glue between a [PtySession] and the [TerminalEmulator]: one reader
 * coroutine feeds bytes into the VT parser; keyboard input is written to
 * the PTY master. Safe to construct off the main thread.
 */
class TerminalSession(
    private val emulator: TerminalEmulator = TerminalEmulator()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var pty: PtySession? = null
    private var readerJob: Job? = null

    private val _snapshot = MutableStateFlow(emulator.snapshot())
    val snapshot: StateFlow<TerminalSnapshot> = _snapshot.asStateFlow()

    private val _alive = MutableStateFlow(false)
    val alive: StateFlow<Boolean> = _alive.asStateFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    val bracketedPaste: Boolean get() = synchronized(emulator) { emulator.bracketedPaste }

    fun wrapPaste(text: String): String = synchronized(emulator) { emulator.wrapPaste(text) }

    fun cursorKey(direction: Char): String =
        synchronized(emulator) { emulator.cursorKey(direction) }

    @Synchronized
    fun start(prepared: PreparedShell) {
        stopLocked()
        try {
            val session = PtySession.startShell(prepared)
            pty = session
            running.set(true)
            _alive.value = true
            _exitCode.value = null
            session.setWindowSize(emulator.rows, emulator.cols)
            readerJob = scope.launch { readLoop(session) }
        } catch (e: Exception) {
            AppLogger.e("TerminalSession", "failed to start shell", e)
            val message = "\r\n[terminal] could not start a shell: ${e.message}\r\n" +
                "[terminal] PTY JNI=${PtyNative.nativeAvailable}\r\n"
            synchronized(emulator) { emulator.feed(message) }
            publish()
            _alive.value = false
        }
    }

    fun send(text: String) {
        val session = pty ?: return
        if (text.isEmpty() || session.closed) return
        try {
            session.write(text)
        } catch (e: Exception) {
            AppLogger.e("TerminalSession", "write failed", e)
        }
    }

    fun sendCommand(command: String) {
        if (command.isBlank()) return
        send(command.trimEnd() + "\n")
    }

    fun resize(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        synchronized(emulator) {
            if (cols == emulator.cols && rows == emulator.rows) return
            emulator.resize(cols, rows)
        }
        pty?.setWindowSize(rows, cols)
        publish()
    }

    fun stop() {
        synchronized(this) { stopLocked() }
    }

    private fun stopLocked() {
        running.set(false)
        readerJob?.cancel()
        readerJob = null
        try {
            pty?.close()
        } catch (_: Exception) {
        }
        pty = null
        _alive.value = false
    }

    private suspend fun readLoop(session: PtySession) {
        val buf = ByteArray(4096)
        try {
            while (running.get() && scope.isActive && !session.closed) {
                val n = withContext(Dispatchers.IO) { session.read(buf) }
                if (n < 0) break
                if (n == 0) {
                    val exit = session.pollExit()
                    if (exit >= 0) {
                        _exitCode.value = exit
                        break
                    }
                    continue
                }
                synchronized(emulator) { emulator.feed(buf, 0, n) }
                publish()
            }
        } catch (e: Exception) {
            if (running.get()) {
                AppLogger.e("TerminalSession", "reader stopped", e)
            }
        } finally {
            val exit = try {
                session.pollExit()
            } catch (_: Exception) {
                -1
            }
            if (exit >= 0) _exitCode.value = exit
            val code = _exitCode.value
            val notice = if (code != null) {
                "\r\n[process exited with $code]\r\n"
            } else {
                "\r\n[process exited]\r\n"
            }
            synchronized(emulator) { emulator.feed(notice) }
            publish()
            _alive.value = false
            running.set(false)
        }
    }

    private fun publish() {
        _snapshot.value = synchronized(emulator) { emulator.snapshot() }
    }

    fun resetEmulator() {
        synchronized(emulator) { emulator.reset() }
        publish()
    }
}
