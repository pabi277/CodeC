package com.codeci.ide.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeci.ide.ui.services.CompilerSettings
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.terminal.PreparedShell
import com.codeci.ide.ui.terminal.ShellBootstrap
import com.codeci.ide.ui.terminal.TerminalSession
import com.codeci.ide.ui.terminal.TerminalSnapshot
import com.codeci.ide.ui.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity-scoped so the shell survives tab switches. Owns bootstrap +
 * the PTY session and exposes an immutable [TerminalSnapshot] for Compose.
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsManager(application)
    private val bootstrap = ShellBootstrap(application)
    private val session = TerminalSession()

    val snapshot: StateFlow<TerminalSnapshot> = session.snapshot
    val alive: StateFlow<Boolean> = session.alive
    val exitCode: StateFlow<Int?> = session.exitCode

    val fontSizeSp: StateFlow<Float> = settings.terminalFontSizeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 14f)

    private val _ctrlLatched = MutableStateFlow(false)
    val ctrlLatched: StateFlow<Boolean> = _ctrlLatched.asStateFlow()

    private val _altLatched = MutableStateFlow(false)
    val altLatched: StateFlow<Boolean> = _altLatched.asStateFlow()

    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started.asStateFlow()

    private var queuedCommand: String? = null

    init {
        viewModelScope.launch(Dispatchers.IO) { ensureStarted() }
    }

    fun ensureStarted() {
        if (session.alive.value && _started.value) return
        viewModelScope.launch(Dispatchers.IO) { startInternal() }
    }

    fun restart() {
        viewModelScope.launch(Dispatchers.IO) {
            session.stop()
            session.resetEmulator()
            _started.value = false
            startInternal()
        }
    }

    private suspend fun startInternal() {
        try {
            val prepared = prepareShell()
            session.start(prepared)
            _started.value = true
            val pending = queuedCommand
            queuedCommand = null
            if (!pending.isNullOrBlank()) {
                session.sendCommand(pending)
            }
        } catch (e: Exception) {
            AppLogger.e("TerminalViewModel", "start failed", e)
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
        if (!_started.value) {
            queuedCommand = command
            ensureStarted()
            return
        }
        session.sendCommand(command)
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

    fun wrapPaste(text: String): String = session.wrapPaste(text)

    fun cursorKey(direction: Char): String = session.cursorKey(direction)

    fun setFontSize(size: Float) {
        viewModelScope.launch { settings.setTerminalFontSize(size) }
    }

    override fun onCleared() {
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
