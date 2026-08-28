package com.codeci.ide.ui.viewmodels

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeci.ide.ui.services.CompilerSettings
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.theme.TerminalThemeType
import com.codeci.ide.ui.theme.ThemeManager
import com.codeci.ide.ui.terminal.CodecApiBridge
import com.codeci.ide.ui.terminal.CodecApiProtocol
import com.codeci.ide.ui.terminal.PreparedShell
import com.codeci.ide.ui.terminal.ShellBootstrap
import com.codeci.ide.ui.terminal.ShellEnvironment
import com.codeci.ide.ui.terminal.TerminalSession
import com.codeci.ide.ui.terminal.UserlandInstaller
import com.codeci.ide.ui.terminal.UserlandStatus
import com.codeci.ide.ui.terminal.TerminalSnapshot
import com.codeci.ide.ui.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Activity-scoped so the shell survives tab switches. Owns bootstrap +
 * the PTY session and exposes an immutable [TerminalSnapshot] for Compose.
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsManager(application)
    private val themeManager = ThemeManager(application)
    private val bootstrap = ShellBootstrap(application)
    private val userland = UserlandInstaller(application)
    private val session = TerminalSession()

    private val wakeLock: PowerManager.WakeLock? = runCatching {
        (application.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "CodeC::TerminalWake"
        )
    }.getOrNull()

    init {
        // Phase 6.1: wake lock when terminal session is active
        viewModelScope.launch(Dispatchers.Main) {
            session.alive.collect { alive ->
                try {
                    if (alive) {
                        wakeLock?.let { if (!it.isHeld) it.acquire(10 * 60 * 1000L) }
                    } else {
                        wakeLock?.let { if (it.isHeld) it.release() }
                    }
                } catch (e: Exception) {
                    AppLogger.e("TerminalViewModel", "wake lock error", e)
                }
            }
        }
    }

    val snapshot: StateFlow<TerminalSnapshot> = session.snapshot
    val alive: StateFlow<Boolean> = session.alive
    val exitCode: StateFlow<Int?> = session.exitCode
    val storagePermissionRequests: SharedFlow<Unit> = session.storagePermissionRequests
    val bellEvents: SharedFlow<Unit> = session.bellEvents

    val extraKeysMacros: StateFlow<String> = settings.terminalExtraKeysMacrosFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Requests that need the Android 13+ notification permission before they can run. */
    private val _notificationPermissionRequests =
        MutableSharedFlow<CodecApiProtocol.Request>(extraBufferCapacity = 16)
    val notificationPermissionRequests: SharedFlow<CodecApiProtocol.Request> =
        _notificationPermissionRequests.asSharedFlow()

    val fontSizeSp: StateFlow<Float> = settings.terminalFontSizeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 14f)

    val fontFamily: StateFlow<String> = settings.terminalFontFamilyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Monospace")

    val terminalTheme: StateFlow<TerminalThemeType> = themeManager.terminalThemeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, TerminalThemeType.DRACULA)

    private val _ctrlLatched = MutableStateFlow(false)
    val ctrlLatched: StateFlow<Boolean> = _ctrlLatched.asStateFlow()

    private val _altLatched = MutableStateFlow(false)
    val altLatched: StateFlow<Boolean> = _altLatched.asStateFlow()

    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started.asStateFlow()

    private var queuedCommand: String? = null
    private val startMutex = Mutex()

    init {
        viewModelScope.launch(Dispatchers.IO) { startInternal() }
        // Consume CodeCApi requests in the activity scope (not the Terminal
        // screen): the PTY/session survives tab switches, and a
        // SharedFlow emission is silently dropped when nothing is
        // collecting — so a `codec-clipboard` run from another tab or a
        // queued initial command must still be answered.
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            session.codecApiRequests.collect { payload ->
                CodecApiBridge.handle(
                    app,
                    payload,
                    ShellEnvironment.codecApiDir(
                        ShellEnvironment.prefixDir(app.filesDir)
                    ),
                    onPermissionRequired = { request, _ ->
                        _notificationPermissionRequests.tryEmit(request)
                    }
                )
            }
        }
    }

    fun ensureStarted() {
        if (session.alive.value && _started.value) return
        viewModelScope.launch(Dispatchers.IO) { startInternal() }
    }

    fun restart() {
        viewModelScope.launch(Dispatchers.IO) {
            startMutex.withLock {
                session.stop()
                session.resetEmulator()
                _started.value = false
                startLocked()
            }
        }
    }

    private suspend fun startInternal() {
        startMutex.withLock {
            if (session.alive.value && _started.value) return
            startLocked()
        }
    }

    private suspend fun startLocked() {
        try {
            installUserlandInternal(force = false)
            val prepared = prepareShell()
            session.start(prepared)
            _started.value = true
            val pending = queuedCommand
            queuedCommand = null
            if (!pending.isNullOrBlank()) {
                kotlinx.coroutines.delay(350)
                session.sendCommand(pending)
            }
        } catch (e: Exception) {
            _started.value = false
            AppLogger.e("TerminalViewModel", "start failed", e)
        }
    }

    fun installUserland() {
        viewModelScope.launch(Dispatchers.IO) {
            startMutex.withLock {
                installUserlandInternal(force = true)
                session.stop()
                session.resetEmulator()
                _started.value = false
                startLocked()
            }
        }
    }

    private fun installUserlandInternal(force: Boolean) {
        val status = userland.installIfNeeded(force = force) { msg ->
            session.notice(msg)
        }
        when (status) {
            is UserlandStatus.Failed -> session.notice("userland: failed — ${status.message}")
            else -> { }
        }
    }

    private suspend fun prepareShell(): PreparedShell {
        val compilerSettings = compilerSettingsFrom(settings)
        return withContext(Dispatchers.IO) { bootstrap.prepare(compilerSettings) }
    }

    fun send(text: String) {
        if (text.isEmpty()) return
        var payload = text
        if (_ctrlLatched.value && text.length == 1) {
            payload = ctrl(text[0]).toString()
            _ctrlLatched.value = false
        } else if (_altLatched.value) {
            payload = "\u001b$text"
            _altLatched.value = false
        }
        session.send(payload)
    }

    fun sendCommand(command: String) {
        if (!_started.value || !session.alive.value) {
            queuedCommand = command
            ensureStarted()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(80)
            session.sendCommand(command)
        }
    }

    fun sendKey(sequence: String) {
        session.send(sequence)
    }

    fun resize(cols: Int, rows: Int) {
        session.resize(cols, rows)
    }

    fun toggleCtrl() {
        _ctrlLatched.value = !_ctrlLatched.value
        if (_ctrlLatched.value) _altLatched.value = false
    }

    fun toggleAlt() {
        _altLatched.value = !_altLatched.value
        if (_altLatched.value) _ctrlLatched.value = false
    }

    fun transcriptText(): String = session.transcriptText()

    fun wrapPaste(text: String): String = session.wrapPaste(text)

    fun cursorKey(direction: Char): String = session.cursorKey(direction)

    fun setFontSize(size: Float) {
        viewModelScope.launch { settings.setTerminalFontSize(size) }
    }

    fun setFontFamily(family: String) {
        viewModelScope.launch { settings.setTerminalFontFamily(family) }
    }

    fun setTheme(theme: TerminalThemeType) {
        viewModelScope.launch { themeManager.setTerminalTheme(theme) }
    }

    override fun onCleared() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        session.stop()
        super.onCleared()
    }

    companion object {
        fun ctrl(ch: Char): Char {
            val lower = ch.lowercaseChar()
            return if (lower in 'a'..'z') {
                (lower.code - 'a'.code + 1).toChar()
            } else when (ch) {
                '[' -> '\u001b'
                '\\' -> '\u001c'
                ']' -> '\u001d'
                '^' -> '\u001e'
                '_' -> '\u001f'
                ' ' -> '\u0000'
                '?' -> '\u007f'
                else -> ch
            }
        }

        suspend fun compilerSettingsFrom(settingsManager: SettingsManager): CompilerSettings {
            val standard = settingsManager.cStandardFlow.first()
            val warningLevel = settingsManager.warningLevelFlow.first()
            val optimization = settingsManager.optimizationLevelFlow.first()
            return CompilerSettings(
                cStandard = standard.lowercase().removePrefix("c").let { "c$it" },
                warnings = !warningLevel.equals("None", ignoreCase = true),
                optimization = optimization.filter { it.isDigit() }.toIntOrNull() ?: 0
            )
        }
    }
}
