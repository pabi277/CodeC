package com.codeci.ide.ui.terminal

import com.codeci.ide.ui.utils.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /**
     * Phase 19.3: snapshots are published by a frame-paced emitter instead of
     * once per PTY chunk. Conflated StateFlow + per-chunk publishes dropped
     * intermediate frames, so streaming output only appeared when it finished.
     */
    private val renderPump = RenderPump { publish() }
    private var renderJob: Job? = null

    private val _snapshot = MutableStateFlow(emulator.snapshot())
    val snapshot: StateFlow<TerminalSnapshot> = _snapshot.asStateFlow()

    private val _alive = MutableStateFlow(false)
    val alive: StateFlow<Boolean> = _alive.asStateFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    private val _storagePermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val storagePermissionRequests: SharedFlow<Unit> = _storagePermissionRequests.asSharedFlow()

    private val _codecApiRequests = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val codecApiRequests: SharedFlow<String> = _codecApiRequests.asSharedFlow()

    private val _bellEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val bellEvents: SharedFlow<Unit> = _bellEvents.asSharedFlow()

    init {
        emulator.onStoragePermissionRequested = {
            _storagePermissionRequests.tryEmit(Unit)
        }
        emulator.onCodecApiRequest = { payload ->
            _codecApiRequests.tryEmit(payload)
        }
        emulator.onBell = {
            _bellEvents.tryEmit(Unit)
        }
    }

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
            renderJob?.cancel()
            renderJob = renderPump.start(scope)
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
        val clean = command.trim()
        send("$clean\r")
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
        renderJob?.cancel()
        renderJob = null
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
                // Phase 19.3: mark dirty; the frame-paced RenderPump publishes
                // at ~60 fps so streaming output animates instead of appearing
                // only when the program finishes.
                renderPump.markDirty()
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
            // A newer start() already replaced [pty]. Do not paint
            // "[process exited with 137]" (SIGKILL from the restart) onto
            // the live shell, and do not flip alive=false under it.
            val superseded = synchronized(this) { pty !== session }
            if (!superseded) {
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
    }

    /**
     * Immediate, out-of-band publish. Used for user-visible low-frequency
     * events (resize, notice, reset, start failure) and the reader's exit
     * path — the final state must always land even mid-frame.
     */
    private fun publish() {
        _snapshot.value = synchronized(emulator) { emulator.snapshot() }
    }

    fun resetEmulator() {
        synchronized(emulator) { emulator.reset() }
        publish()
    }

    fun transcriptText(): String = synchronized(emulator) { emulator.transcriptText() }

    /** Paint a status line on the grid without going through the PTY. */
    fun notice(text: String) {
        synchronized(emulator) { emulator.feed("\r\n$text\r\n") }
        publish()
    }
}
