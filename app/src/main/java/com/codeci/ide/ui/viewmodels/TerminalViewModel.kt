package com.codeci.ide.ui.viewmodels

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeci.ide.ui.services.CompilerSettings
import com.codeci.ide.ui.services.TerminalForegroundService
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.theme.TerminalThemeType
import com.codeci.ide.ui.theme.ThemeManager
import com.codeci.ide.ui.terminal.CodecApiBridge
import com.codeci.ide.ui.terminal.CodecApiProtocol
import com.codeci.ide.ui.terminal.InstallProgress
import com.codeci.ide.ui.terminal.PreparedShell
import com.codeci.ide.ui.terminal.SetupAnnouncer
import com.codeci.ide.ui.terminal.SetupFacts
import com.codeci.ide.ui.terminal.SetupGatePolicy
import com.codeci.ide.ui.terminal.SetupIssue
import com.codeci.ide.ui.terminal.SetupLedger
import com.codeci.ide.ui.terminal.SetupLedgerPrefs
import com.codeci.ide.ui.terminal.SetupPhase
import com.codeci.ide.ui.terminal.SetupRecoveryGate
import com.codeci.ide.ui.terminal.SetupStage
import com.codeci.ide.ui.terminal.SetupTracker
import com.codeci.ide.ui.terminal.ShellBootstrap
import com.codeci.ide.ui.terminal.ShellEnvironment
import com.codeci.ide.ui.terminal.TerminalLine
import com.codeci.ide.ui.terminal.TerminalSession
import com.codeci.ide.ui.terminal.TerminalSessionItem
import com.codeci.ide.ui.terminal.TerminalSessionManager
import com.codeci.ide.ui.terminal.TerminalSnapshot
import com.codeci.ide.ui.terminal.TerminalHandoff
import com.codeci.ide.ui.terminal.TerminalLifecycle
import com.codeci.ide.ui.terminal.TerminalStartMeasurement
import com.codeci.ide.ui.terminal.OrderedReadinessQueue
import com.codeci.ide.ui.terminal.PreparedShellCacheKey
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
    private val prefixDir = ShellEnvironment.prefixDir(application.filesDir)

    /**
     * Phase 44.2 — the durable install ledger (SharedPreferences + commit()).
     * The SAME store the boot repair in `MainActivity.onCreate` reads, so a
     * kill between the two renames of `swapPrefix` is describable on the next
     * launch instead of leaving a phone with no `usr` at all.
     */
    private val setupLedger = SetupLedgerPrefs.ledger(application)
    private val userland = UserlandInstaller(application, ledger = setupLedger)
    private val manager = TerminalSessionManager()

    // ---- Phase 44.1 — the setup truth every tab can render -----------------

    /**
     * Seeded from the disk, not from optimism: a returning user with a working
     * prefix starts at READY (no bar, no false "don't close the app"), a fresh
     * install starts at CHECKING and moves as the installer reports.
     */
    private val setupTracker = SetupTracker(initialSetupProgress(prefixDir, setupLedger))
    private val _setupProgress = MutableStateFlow(setupTracker.state)

    /** What is happening with the one-time setup, in structured form. */
    val setupProgress: StateFlow<InstallProgress> = _setupProgress.asStateFlow()

    private val _setupFacts = MutableStateFlow(computeSetupFacts(setupTracker.state))

    /** Stage + the two cheap filesystem capabilities the gate needs. */
    val setupFacts: StateFlow<SetupFacts> = _setupFacts.asStateFlow()

    /** True while THIS ViewModel owns the foreground service for the setup. */
    @Volatile private var setupKeepAlive = false
    private val notificationLock = Any()
    private var lastAnnounced: InstallProgress? = null
    private var lastAnnouncedAtMs = 0L

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

    /** Commands are scoped to a session; one slow shell cannot steal another's queue. */
    private val commandQueues = mutableMapOf<String, OrderedReadinessQueue>()
    private val pendingBeforeSession = ArrayDeque<String>()
    private val commandQueueLock = Any()

    /** PreparedShell is reusable only for the exact settings/userland generation. */
    private var preparedShellCache: Pair<PreparedShellCacheKey, PreparedShell>? = null
    private val measurementLock = Any()
    private val measurements = mutableMapOf<String, TerminalStartMeasurement>()
    private val _lastStartMeasurement = MutableStateFlow<TerminalStartMeasurement?>(null)
    val lastStartMeasurement: StateFlow<TerminalStartMeasurement?> = _lastStartMeasurement.asStateFlow()

    // ---- merged-across-sessions event relays --------------------------------

    private val _storagePermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val storagePermissionRequests: SharedFlow<Unit> = _storagePermissionRequests.asSharedFlow()

    private val _bellEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val bellEvents: SharedFlow<Unit> = _bellEvents.asSharedFlow()

    /**
     * CodeCApi requests parked on a runtime permission — `notify.send`
     * (POST_NOTIFICATIONS, Phase 4.8) and `camera.capture` (CAMERA,
     * Phase 18). The screen dispatches to the matching activity launcher.
     */
    private val _permissionRequests =
        MutableSharedFlow<CodecApiProtocol.Request>(extraBufferCapacity = 16)
    val permissionRequests: SharedFlow<CodecApiProtocol.Request> =
        _permissionRequests.asSharedFlow()

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

    private val startMutex = Mutex()

    /** Last measured grid, applied before spawning a new PTY. */
    @Volatile private var terminalCols = 80
    @Volatile private var terminalRows = 24

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

    val lifecycle: StateFlow<TerminalLifecycle> = activeItem
        .flatMapLatest { it?.session?.lifecycle ?: flowOf(TerminalLifecycle.STARTING) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TerminalLifecycle.STARTING)

    val exitCode: StateFlow<Int?> = activeItem
        .flatMapLatest { it?.session?.exitCode ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Phase 44.1 — publish the disk-derived truth immediately, so a
        // surface that asks before the installer has spoken (the editor's
        // install prompt) never falls back to an optimistic guess.
        com.codeci.ide.ui.terminal.SetupStateBridge.publish(_setupFacts.value)
        // Phase 6.1 wake lock, Phase 7 (D8): held while ANY session is alive.
        viewModelScope.launch(Dispatchers.Main) {
            manager.anyAlive.collect { anyAlive ->
                try {
                    if (anyAlive) {
                        // Phase 44.1 — a live shell means the session keep-alive
                        // owns the service from here (the setup's own copy is
                        // replaced by the plain terminal notification below).
                        setupKeepAlive = false
                        // The foreground service protects the app process when
                        // the activity is backgrounded; the partial wake lock
                        // keeps a package download/PTY reader moving through
                        // Doze. Do not use the old ten-minute timeout: a real
                        // package transaction can legitimately run longer.
                        TerminalForegroundService.start(getApplication<Application>())
                        wakeLock?.let { if (!it.isHeld) it.acquire() }
                    } else if (!setupKeepAlive) {
                        // Phase 44.1 — while the setup owns the keep-alive the
                        // session teardown must not stop it: the download runs
                        // BEFORE any PTY exists, so `anyAlive` is false for the
                        // whole install (and this collector's first emission is
                        // exactly that false).
                        TerminalForegroundService.stop(getApplication<Application>())
                        wakeLock?.let { if (it.isHeld) it.release() }
                    }
                } catch (e: Exception) {
                    AppLogger.e("TerminalViewModel", "terminal background keep-alive error", e)
                }
            }
        }
        // Phase 44.2 (device round 1) — the boot repair runs on a daemon thread
        // in MainActivity.onCreate and can clear a stale ledger AFTER this
        // ViewModel has already read it. Take one more look from the disk when
        // the repair is done, or a phone whose tools are fine keeps showing the
        // "still need one install" bar until something else happens to refresh.
        viewModelScope.launch(Dispatchers.IO) {
            SetupRecoveryGate.awaitFinished()
            refreshSetupFromDiskWhenIdle()
        }
        // Auto-start the first session exactly as before Phase 7.
        viewModelScope.launch(Dispatchers.IO) { startInternal() }
    }

    private fun activeSession(): TerminalSession? = manager.activeItem()?.session

    // ---- per-session collectors (D4: one CodeCApi collector per session) ----

    private fun attachSession(item: TerminalSessionItem) {
        val app = getApplication<Application>()
        synchronized(commandQueueLock) {
            commandQueues.getOrPut(item.id) { OrderedReadinessQueue() }
        }
        val jobs = listOf(
            viewModelScope.launch(Dispatchers.IO) {
                item.session.codecApiRequests.collect { payload ->
                    CodecApiBridge.handle(
                        app,
                        payload,
                        codecApiDir,
                        onPermissionRequired = { request, _ ->
                            _permissionRequests.tryEmit(request)
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
            // Flush only on the shell's private readiness marker, never on a
            // guessed delay. The queue belongs to this session.
            viewModelScope.launch(Dispatchers.IO) {
                item.session.shellReadyEvents.collect {
                    val measurement = synchronized(measurementLock) {
                        measurements[item.id]?.prompt(SystemClock.elapsedRealtime())
                            ?.also { updated -> measurements[item.id] = updated }
                    }
                    if (measurement != null) {
                        _lastStartMeasurement.value = measurement
                        AppLogger.i(
                            "TerminalViewModel",
                            "terminal startup tap→userland=${measurement.tapToUserlandMs}ms " +
                                "userland→prepare=${measurement.userlandToPrepareMs}ms " +
                                "prepare→prompt=${measurement.prepareToPromptMs}ms"
                        )
                    }
                    flushSessionCommands(item)
                }
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
        synchronized(commandQueueLock) { commandQueues.remove(id) }
        synchronized(measurementLock) { measurements.remove(id) }
    }

    private fun flushSessionCommands(item: TerminalSessionItem) {
        val commands = synchronized(commandQueueLock) {
            commandQueues[item.id]?.markReady().orEmpty()
        }
        commands.forEach { command ->
            if (item.session.shellReady.value) item.session.sendCommand(command)
        }
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
                synchronized(commandQueueLock) {
                    val queue = commandQueues.getValue(item.id)
                    while (pendingBeforeSession.isNotEmpty()) {
                        queue.enqueue(pendingBeforeSession.removeFirst())
                    }
                }
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
    private suspend fun startItem(
        item: TerminalSessionItem,
        forceInstall: Boolean = false,
        reason: String? = null
    ) {
        item.session.beginStarting()
        synchronized(commandQueueLock) {
            commandQueues.getOrPut(item.id) { OrderedReadinessQueue() }.reset()
        }
        val initial = TerminalStartMeasurement(SystemClock.elapsedRealtime())
        synchronized(measurementLock) { measurements[item.id] = initial }
        try {
            if (reason != null) item.session.notice("[terminal] $reason")
            if (forceInstall) preparedShellCache = null
            installUserlandInternal(item.session, force = forceInstall)
            synchronized(measurementLock) {
                measurements[item.id] = (measurements[item.id] ?: initial)
                    .userlandDone(SystemClock.elapsedRealtime())
            }
            val prepared = prepareShell()
            synchronized(measurementLock) {
                measurements[item.id] = (measurements[item.id] ?: initial)
                    .prepareDone(SystemClock.elapsedRealtime())
            }
            // Seed the emulator and PTY with the current grid before exec.
            // This avoids a post-attach TIOCSWINSZ/SIGWINCH when a newly
            // created session becomes visible and Bash redraws its prompt.
            item.session.resize(terminalCols, terminalRows)
            item.session.start(prepared)
            _started.value = true
            // Phase 44.1 — the setup keep-alive ends here: either the shell is
            // up (the session keep-alive takes over) or it never will be, and a
            // foreground service protecting nothing is a battery lie.
            if (item.session.alive.value) {
                setupKeepAlive = false
            } else {
                stopSetupKeepAliveIfIdle()
            }
            // A very fast shell can emit the marker before the collector is
            // scheduled. The state check makes that path lossless as well.
            if (item.session.shellReady.value) flushSessionCommands(item)
        } catch (e: Exception) {
            _started.value = false
            stopSetupKeepAliveIfIdle()
            item.session.startupFailed("shell startup failed: ${e.message ?: e.javaClass.simpleName}")
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
                startItem(item, reason = "shell restarted")
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
                startItem(
                    item,
                    forceInstall = true,
                    reason = "userland was updated — session restarted"
                )
            }
        }
    }

    private fun installUserlandInternal(target: TerminalSession, force: Boolean) {
        // Phase 44.2 — never race the boot repair: an orphan sweep or a
        // mid-swap restore touches the same directories (bounded wait).
        SetupRecoveryGate.awaitFinished()
        if (force) publishSetup(setupTracker.restart("reinstall requested"))
        // Phase 44.1 — protect the download BEFORE it starts. Today the
        // service (and the wake lock) only came up with the first live PTY,
        // i.e. after the install, so Android could kill the process mid-download
        // with nothing on screen and nothing holding it.
        val usableBefore = SetupGatePolicy.packageManagerPresent(prefixDir) &&
            SetupGatePolicy.shellPresent(prefixDir)
        if (force || !usableBefore) startSetupKeepAlive()
        val status = try {
            userland.installIfNeeded(
                force = force,
                // Opening a terminal is not an update check. The explicit
                // "Install userland" action uses force=true and remains the
                // deliberate upgrade/reinstall path.
                checkForUpgrade = false
            ) { msg ->
                target.notice(msg)
                // The SAME strings the terminal prints, now structured for
                // every other tab (SetupGatePolicy.parse is the only reader).
                publishSetup(setupTracker.observe(msg))
            }
        } catch (t: Throwable) {
            publishSetup(setupTracker.fail(t.message ?: t.javaClass.simpleName))
            throw t
        }
        when (status) {
            is UserlandStatus.Installed -> {
                preparedShellCache = null
                // The install is complete and consistent: the ledger goes
                // quiet so the next boot repairs nothing.
                setupLedger.clear()
                publishSetup(setupTracker.ready(status.releaseTag))
            }
            is UserlandStatus.AlreadyInstalled -> {
                // 44.2's whole principle, applied to the transition and not only
                // to the facts: `installIfNeeded(force = false)` answers
                // AlreadyInstalled from the MARKER alone (the fast warm-open
                // path), and a marker written by a pre-44 build — or by a kill
                // after the marker but before the swap landed — can sit on top of
                // a tree with no working `bin/pkg`. That is the owner's device
                // report of 2026-09-12: the bar said "the Linux tools still need
                // one install" forever, because the stage said READY while the
                // disk said not usable. The disk decides.
                val phase = runCatching { setupLedger.read().phase }
                    .getOrDefault(SetupPhase.IDLE)
                if (SetupGatePolicy.userlandUsable(prefixDir, phase)) {
                    publishSetup(setupTracker.ready("installed"))
                } else {
                    target.notice(
                        "userland: marker present but the tools do not run — tap ⬇ to install again"
                    )
                    publishSetup(
                        setupTracker.fail(
                            "installed marker but bin/pkg does not run",
                            SetupIssue.BROKEN_USERLAND
                        )
                    )
                }
            }
            is UserlandStatus.SkippedOffline -> {
                // Was: "userland: offline — using built-in cc (TCC)" and
                // nothing else — a sentence that reads like success while the
                // phone has no `pkg` at all.
                publishSetup(setupTracker.fail("offline", SetupIssue.OFFLINE))
            }
            is UserlandStatus.SkippedNoRelease ->
                publishSetup(setupTracker.unsupported("no bootstrap for this device"))
            is UserlandStatus.Failed -> {
                target.notice("userland: failed — ${status.message}")
                publishSetup(setupTracker.fail(status.message))
            }
        }
        refreshSetupFacts()
        // The keep-alive is NOT stopped here on purpose: `startItem` continues
        // straight into the shell that the setup exists for, and tearing the
        // service down in between would leave a window (Android 12+ refuses a
        // background FGS start) with no protection at all. `startItem` hands it
        // over when the PTY is up, and stops it when startup fails.
    }

    // ---- Phase 44.1/44.2 setup plumbing ------------------------------------

    /**
     * Publishes to the StateFlows and to the notification, both throttled: the
     * installer reports one line per 16 KiB, and neither Compose nor the
     * notification may be rewritten that often. The filesystem facts are
     * re-read only when the published state actually moved.
     */
    private fun publishSetup(progress: InstallProgress) {
        if (SetupAnnouncer.shouldPublishState(_setupProgress.value, progress)) {
            _setupProgress.value = progress
            val facts = computeSetupFacts(progress)
            _setupFacts.value = facts
            // Surfaces that do not own this ViewModel (the editor's install
            // prompt) read the same truth instead of guessing.
            com.codeci.ide.ui.terminal.SetupStateBridge.publish(facts)
        }
        announceSetupNotification(progress)
    }

    /**
     * Re-reads stage + capabilities from the disk once the boot repair has
     * finished. Never touches an install that is actually in flight: while the
     * tracker says DOWNLOADING/EXTRACTING the installer owns the state.
     */
    private fun refreshSetupFromDiskWhenIdle() {
        try {
            val current = _setupProgress.value
            if (current.inFlight) return
            val phase = runCatching { setupLedger.read().phase }.getOrDefault(SetupPhase.IDLE)
            if (SetupGatePolicy.userlandUsable(prefixDir, phase) &&
                current.stage != SetupStage.READY
            ) {
                publishSetup(setupTracker.ready("installed"))
            }
            refreshSetupFacts()
        } catch (t: Throwable) {
            AppLogger.w("TerminalViewModel", "post-repair setup refresh failed: ${t.message}")
        }
    }

    /** Re-reads the filesystem capabilities (after an install, a repair, …). */
    private fun refreshSetupFacts() {
        val facts = computeSetupFacts(_setupProgress.value)
        _setupFacts.value = facts
        com.codeci.ide.ui.terminal.SetupStateBridge.publish(facts)
    }

    private fun computeSetupFacts(progress: InstallProgress): SetupFacts {
        val phase = runCatching { setupLedger.read().phase }.getOrDefault(SetupPhase.IDLE)
        return SetupGatePolicy.factsFor(prefixDir, phase, progress)
    }

    private fun announceSetupNotification(progress: InstallProgress) {
        if (!setupKeepAlive) return
        val now = SystemClock.elapsedRealtime()
        val publish = synchronized(notificationLock) {
            val ok = SetupAnnouncer.shouldPublish(lastAnnounced, progress, now, lastAnnouncedAtMs)
            if (ok) {
                lastAnnounced = progress
                lastAnnouncedAtMs = now
            }
            ok
        }
        if (!publish) return
        try {
            TerminalForegroundService.updateStatus(
                getApplication<Application>(),
                SetupGatePolicy.notificationText(progress),
                progress.percent
            )
        } catch (e: Exception) {
            AppLogger.e("TerminalViewModel", "setup notification update failed", e)
        }
    }

    private fun startSetupKeepAlive() {
        if (setupKeepAlive) return
        try {
            val app = getApplication<Application>()
            setupKeepAlive = true
            TerminalForegroundService.start(
                app,
                SetupGatePolicy.notificationText(_setupProgress.value),
                _setupProgress.value.percent
            )
            wakeLock?.let { if (!it.isHeld) it.acquire() }
            AppLogger.i("TerminalViewModel", "setup keep-alive started (foreground service + wake lock)")
        } catch (e: Exception) {
            // A denied notification permission or a background-start refusal
            // must never stop the install: the in-app bar is the real surface.
            setupKeepAlive = false
            AppLogger.e("TerminalViewModel", "setup keep-alive failed", e)
        }
    }

    private fun stopSetupKeepAliveIfIdle() {
        if (!setupKeepAlive) return
        setupKeepAlive = false
        try {
            val app = getApplication<Application>()
            if (manager.anyAlive.value) {
                // A live shell is what the service protects now: restore the
                // plain terminal copy and hand the service to the session
                // keep-alive (which also owns the wake lock from here).
                TerminalForegroundService.updateStatus(app, null, null)
                return
            }
            TerminalForegroundService.stop(app)
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            AppLogger.e("TerminalViewModel", "setup keep-alive stop failed", e)
        }
    }

    /** The setup state to show before the installer has said anything. */
    private fun initialSetupProgress(prefix: File, ledger: SetupLedger): InstallProgress {
        val phase = runCatching { ledger.read().phase }.getOrDefault(SetupPhase.IDLE)
        if (phase == SetupPhase.SWAPPING) {
            return InstallProgress(SetupStage.CHECKING, detail = "repairing an interrupted setup")
        }
        val usable = SetupGatePolicy.packageManagerPresent(prefix) &&
            SetupGatePolicy.shellPresent(prefix)
        return if (usable) {
            InstallProgress(SetupStage.READY, detail = "installed")
        } else {
            InstallProgress(SetupStage.CHECKING)
        }
    }

    private fun userlandStamp(): String {
        val prefix = bootstrap.prefixDir()
        val release = userland.installedRelease(prefix) ?: "unmarked"
        val marker = File(prefix, ".bootstrap-v${ShellEnvironment.BOOTSTRAP_VERSION}")
        val bootstrapGeneration = marker.takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.ifEmpty { "missing" }
            ?: "missing"
        val shell = ShellEnvironment.resolveShell(prefix)
        return listOf(
            release,
            bootstrapGeneration,
            shell.absolutePath,
            shell.lastModified(),
            shell.length()
        ).joinToString(":")
    }

    private suspend fun prepareShell(): PreparedShell {
        val compilerSettings = compilerSettingsFrom(settings)
        val key = PreparedShellCacheKey(compilerSettings, userlandStamp())
        preparedShellCache?.takeIf { it.first == key }?.let {
            AppLogger.i("TerminalViewModel", "prepared shell cache hit")
            // The environment object is reusable, but the cc frontend is
            // deliberately refreshed for every RUN/start (never stale).
            withContext(Dispatchers.IO) { bootstrap.rewriteCompilerFrontend() }
            return it.second
        }
        return withContext(Dispatchers.IO) {
            val prepared = bootstrap.prepare(compilerSettings)
            // prepare() refreshes the bootstrap marker, so key the cached
            // result from the post-prepare userland/bootstrap generation.
            preparedShellCache = PreparedShellCacheKey(compilerSettings, userlandStamp()) to prepared
            prepared
        }
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
        if (command.isBlank()) return
        val item = manager.activeItem()
        val session = item?.session
        val queueHasPending = item?.let {
            synchronized(commandQueueLock) { (commandQueues[it.id]?.size ?: 0) > 0 }
        } ?: false
        if (item == null || session?.shellReady?.value != true || queueHasPending) {
            synchronized(commandQueueLock) {
                if (item != null) {
                    commandQueues.getOrPut(item.id) { OrderedReadinessQueue() }.enqueue(command)
                } else {
                    pendingBeforeSession.addLast(command)
                }
            }
            if (item != null && session?.shellReady?.value == true) {
                // A command can arrive after the marker but before its
                // collector gets scheduled. Drain the older commands first.
                flushSessionCommands(item)
            } else {
                ensureStarted()
            }
            return
        }
        // The first-prompt marker is the readiness boundary. Once it has
        // fired, preserve the old asynchronous handoff without adding a
        // startup race or reordering commands.
        viewModelScope.launch(Dispatchers.IO) { session.sendCommand(command) }
    }

    fun sendKey(sequence: String) {
        activeSession()?.send(sequence)
    }

    fun resize(cols: Int, rows: Int) {
        terminalCols = cols
        terminalRows = rows
        // Keep every PTY at the same terminal geometry. Resizing only the
        // active session meant switching to an older session delivered a
        // SIGWINCH on every switch, which Bash rendered as extra blank
        // prompts/enters. New sessions receive the same geometry before their
        // first prompt in startItem().
        manager.sessions.value.forEach { item ->
            item.session.resize(cols, rows)
        }
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
            TerminalForegroundService.stop(getApplication<Application>())
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
