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
import com.codeci.ide.ui.terminal.TerminalLine
import com.codeci.ide.ui.terminal.TerminalSession
import com.codeci.ide.ui.terminal.TerminalSessionItem
import com.codeci.ide.ui.terminal.TerminalSessionManager
import com.codeci.ide.ui.terminal.TerminalSnapshot
import com.codeci.ide.ui.terminal.TerminalHandoff
import com.codeci.ide.ui.terminal.UserlandInstaller
import com.codeci.ide.ui.terminal.UserlandStatus
import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Activity-scoped so shells survive tab switches (Phase 1). Phase 7: session
 * *state* (list, active id, per-item liveness) is delegated to
 * [TerminalSessionManager]; this ViewModel keeps every Android concern —
 * settings, shell bootstrap, userland install, wake lock, permission flows —
 * and exposes the *active* session's [TerminalSnapshot] for Compose, exactly
 * preserving the pre-Phase-7 public surface (`send`, `sendCommand`, `resize`,
 * … all route to the active session, D5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsManager(application)
    private val themeManager = ThemeManager(application)
    private val bootstrap = ShellBootstrap(application)
    private val userland = UserlandInstaller(application)
    private val manager = TerminalSessionManager()

    private val codecApiDir = ShellEnvironment.codecApiDir(
        ShellEnvironment.prefixDir(application.filesDir)
    )

    private val wakeLock: PowerManager.WakeLock? = runCatching {
        (application.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "CodeC::TerminalWake"
        )
    }.getOrNull()

    /** Per-session bridge/permission/bell collector jobs, cancelled on close. */
    private val sessionJobs = mutableMapOf<String, List<kotlinx.coroutines.Job>>()
    private val jobsLock = Any()

    // ---- merged-across-sessions event relays --------------------------------

    private val _storagePermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val storagePermissionRequests: SharedFlow<Unit> = _storagePermissionRequests.asSharedFlow()

    private val _bellEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val bellEvents: SharedFlow<Unit> = _bellEvents.asSharedFlow()

    /** Requests that need the Android 13+ notification permission before they can run. */
    private val _notificationPermissionRequests =
        MutableSharedFlow<CodecApiProtocol.Request>(extraBufferCapacity = 16)
    val notificationPermissionRequests: SharedFlow<CodecApiProtocol.Request> =
        _notificationPermissionRequests.asSharedFlow()

    /** Emitted when "+" is tapped at the session cap (UI shows a toast). */
    private val _sessionLimitEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionLimitEvents: SharedFlow<Unit> = _sessionLimitEvents.asSharedFlow()

    // ---- settings-driven terminal preferences (unchanged) -------------------

    val extraKeysMacros: StateFlow<String> = settings.terminalExtraKeysMacrosFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val fontSizeSp: StateFlow<Float> = settings.terminalFontSizeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 12f)

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

    // ---- Phase 7 multi-session state ----------------------------------------

    val sessions: StateFlow<List<TerminalSessionItem>> = manager.sessions
    val activeSessionId: StateFlow<String?> = manager.activeSessionId

    val activeItem: StateFlow<TerminalSessionItem?> =
        combine(manager.sessions, manager.activeSessionId) { list, id ->
            list.firstOrNull { it.id == id } ?: list.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val emptySnapshot = TerminalSnapshot(
        cols = 1, rows = 1,
        lines = listOf(TerminalLine("", emptyList())),
        scrollbackLines = emptyList(),
        cursorX = 0, cursorY = 0, cursorVisible = false,
        title = "", generation = 0
    )

    val snapshot: StateFlow<TerminalSnapshot> = activeItem
        .flatMapLatest { it?.session?.snapshot ?: flowOf(emptySnapshot) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySnapshot)

    val alive: StateFlow<Boolean> = activeItem
        .flatMapLatest { it?.session?.alive ?: flowOf(false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val exitCode: StateFlow<Int?> = activeItem
        .flatMapLatest { it?.session?.exitCode ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Phase 6.1 wake lock, Phase 7 (D8): held while ANY session is alive.
        viewModelScope.launch(Dispatchers.Main) {
            manager.anyAlive.collect { anyAlive ->
                try {
                    if (anyAlive) {
                        wakeLock?.let { if (!it.isHeld) it.acquire(10 * 60 * 1000L) }
                    } else {
                        wakeLock?.let { if (it.isHeld) it.release() }
                    }
                } catch (e: Exception) {
                    AppLogger.e("TerminalViewModel", "wake lock error", e)
                }
            }
        }
        // Auto-start the first session exactly as before Phase 7.
        viewModelScope.launch(Dispatchers.IO) { startInternal() }
    }

    private fun activeSession(): TerminalSession? = manager.activeItem()?.session

    // ---- per-session collectors (D4: one CodeCApi collector per session) ----

    private fun attachSession(item: TerminalSessionItem) {
        val app = getApplication<Application>()
        val jobs = listOf(
            viewModelScope.launch(Dispatchers.IO) {
                item.session.codecApiRequests.collect { payload ->
                    CodecApiBridge.handle(
                        app,
                        payload,
                        codecApiDir,
                        onPermissionRequired = { request, _ ->
                            _notificationPermissionRequests.tryEmit(request)
                        }
                    )
                }
            },
            viewModelScope.launch(Dispatchers.IO) {
                item.session.storagePermissionRequests.collect {
                    _storagePermissionRequests.tryEmit(it)
                }
            },
            viewModelScope.launch(Dispatchers.IO) {
                item.session.bellEvents.collect { _bellEvents.tryEmit(it) }
            },
            // Phase 19.5: OSC 52 — a program asked to set the clipboard.
            viewModelScope.launch(Dispatchers.IO) {
                item.session.clipboardWrites.collect { text ->
                    runCatching {
                        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        cm?.setPrimaryClip(
                            android.content.ClipData.newPlainText("terminal", text)
                        )
                    }.onFailure { e ->
                        AppLogger.e("TerminalViewModel", "OSC 52 clipboard write failed", e)
                    }
                }
            }
        )
        synchronized(jobsLock) { sessionJobs[item.id] = jobs }
    }

    private fun detachSession(id: String) {
        val jobs = synchronized(jobsLock) { sessionJobs.remove(id) }
        jobs?.forEach { it.cancel() }
    }

    // ---- lifecycle ----------------------------------------------------------

    fun ensureStarted() {
        val item = manager.activeItem()
        if (item != null && item.session.alive.value && _started.value) return
        viewModelScope.launch(Dispatchers.IO) { startInternal() }
    }

    private suspend fun startInternal() {
        startMutex.withLock {
            var item = manager.activeItem()
            if (item == null) {
                item = manager.createSession() ?: return
                attachSession(item)
            }
            if (item.session.alive.value && _started.value) return
            startItem(item)
        }
    }

    /**
     * Bootstraps one session: install notices are painted on *its* screen
     * (the item is created before the install runs), then the shell starts
     * and any command queued while starting is dispatched.
     */
    private suspend fun startItem(item: TerminalSessionItem, forceInstall: Boolean = false) {
        try {
            installUserlandInternal(item.session, force = forceInstall)
            val prepared = prepareShell()
            item.session.start(prepared)
            _started.value = true
            val pending = queuedCommand
            queuedCommand = null
            if (!pending.isNullOrBlank()) {
                kotlinx.coroutines.delay(350)
                item.session.sendCommand(pending)
            }
        } catch (e: Exception) {
            _started.value = false
            AppLogger.e("TerminalViewModel", "start failed", e)
        }
    }

    /** UI "+": create, attach collectors, and bootstrap the new session (D6/D7). */
    fun newSession() {
        val item = manager.createSession()
        if (item == null) {
            _sessionLimitEvents.tryEmit(Unit)
            return
        }
        attachSession(item)
        viewModelScope.launch(Dispatchers.IO) {
            startMutex.withLock { startItem(item) }
        }
    }

    fun switchSession(id: String) {
        manager.switchSession(id)
    }

    /**
     * UI close. The manager stops the PTY, selects the adjacent session, and —
     * when the last one closed — auto-creates a replacement that we must
     * bootstrap here (D6).
     */
    fun closeSession(id: String) {
        val knownBefore = synchronized(jobsLock) { sessionJobs.keys.toSet() }
        val next = manager.closeSession(id) ?: return
        detachSession(id)
        if (next.id !in knownBefore) {
            attachSession(next)
            viewModelScope.launch(Dispatchers.IO) {
                startMutex.withLock { startItem(next) }
            }
        }
    }

    fun renameSession(id: String, name: String?) {
        manager.renameSession(id, name)
    }

    /** Toolbar restart: the active session restarts in place (D11). */
    fun restart() {
        viewModelScope.launch(Dispatchers.IO) {
            startMutex.withLock {
                val item = manager.activeItem() ?: return@withLock
                item.session.stop()
                item.session.resetEmulator()
                startItem(item)
            }
        }
    }

    /**
     * Forced userland re-install (D11): every running shell sat on the
     * userland that was just replaced, so all sessions are stopped and one
     * fresh session is bootstrapped.
     */
    fun installUserland() {
        viewModelScope.launch(Dispatchers.IO) {
            startMutex.withLock {
                val known = synchronized(jobsLock) { sessionJobs.keys.toList() }
                manager.closeAll()
                known.forEach { detachSession(it) }
                _started.value = false
                val item = manager.createSession() ?: return@withLock
                attachSession(item)
                startItem(item, forceInstall = true)
            }
        }
    }

    private fun installUserlandInternal(target: TerminalSession, force: Boolean) {
        val status = userland.installIfNeeded(force = force) { msg ->
            target.notice(msg)
        }
        when (status) {
            is UserlandStatus.Failed -> target.notice("userland: failed — ${status.message}")
            else -> { }
        }
    }

    private suspend fun prepareShell(): PreparedShell {
        val compilerSettings = compilerSettingsFrom(settings)
        return withContext(Dispatchers.IO) { bootstrap.prepare(compilerSettings) }
    }

    // ---- input routing (active session, D5) ----------------------------------

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
        activeSession()?.send(payload)
    }

    /**
     * Select a project in the terminal without entering it. The projects
     * directory is intentional: `ls` then shows the selected project as a
     * folder, while project build/run commands still `cd` to the project root
     * explicitly before using project-relative paths.
     */
    fun setProjectCwd(projectDir: File) {
        val projectsRoot = File(getApplication<Application>().filesDir, "CodeC/projects")
        val root = runCatching { projectsRoot.canonicalFile }.getOrNull() ?: return
        val project = runCatching { projectDir.canonicalFile }.getOrNull() ?: return
        if (!project.isDirectory ||
            project.parentFile?.path != root.path ||
            ProjectPathUtils.sanitizeProjectName(project.name) == null
        ) return
        sendCommand(TerminalHandoff.openInDirectoryCommand(root.path))
    }

    fun sendCommand(command: String) {
        val session = activeSession()
        if (session == null || !_started.value || !session.alive.value) {
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
        activeSession()?.send(sequence)
    }

    fun resize(cols: Int, rows: Int) {
        activeSession()?.resize(cols, rows)
    }

    fun toggleCtrl() {
        _ctrlLatched.value = !_ctrlLatched.value
        if (_ctrlLatched.value) _altLatched.value = false
    }

    fun toggleAlt() {
        _altLatched.value = !_altLatched.value
        if (_altLatched.value) _ctrlLatched.value = false
    }

    fun transcriptText(): String = activeSession()?.transcriptText().orEmpty()

    /** Phase 19.5: terminal RESET (RIS) — clears the active session's screen. */
    fun resetEmulator() {
        activeSession()?.resetEmulator()
    }

    fun wrapPaste(text: String): String =
        activeSession()?.wrapPaste(text) ?: text

    fun cursorKey(direction: Char): String =
        activeSession()?.cursorKey(direction) ?: "\u001b[A"

    // ---- preferences ---------------------------------------------------------

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
        // viewModelScope is already cancelled here; the manager is plain
        // blocking code (D3) so teardown cannot hang on a dead scope.
        manager.dispose()
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
