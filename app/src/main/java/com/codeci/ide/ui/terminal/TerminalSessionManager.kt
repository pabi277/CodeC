package com.codeci.ide.ui.terminal

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One entry in the multi-terminal session list (Phase 7).
 *
 * The item is immutable state (rename = `copy()`); the live [TerminalSession]
 * it wraps owns its own PTY, emulator, and scrollback.
 */
data class TerminalSessionItem(
    val id: String = UUID.randomUUID().toString(),
    val sessionNumber: Int,
    val customTitle: String? = null,
    val session: TerminalSession,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Preference order (Phase 7 plan D1): a user-set title, then the live
     * shell title (`TerminalBuffer`'s default is exactly "Terminal", which is
     * not informative), then the plain session number.
     */
    val displayTitle: String
        get() {
            customTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            val shellTitle = runCatching { session.snapshot.value.title }.getOrNull()
            if (!shellTitle.isNullOrBlank() && shellTitle != "Terminal") return shellTitle
            return "Session $sessionNumber"
        }

    /** Running (PTY alive) or exited — for the switcher's status badge. */
    val isAlive: Boolean
        get() = runCatching { session.alive.value }.getOrDefault(false)
}

/**
 * Owns $N$ concurrent [TerminalSession]s for Phase 7 multi-terminal.
 *
 * Pure Kotlin on purpose (no Android imports) so the whole class is
 * host-testable: [TerminalSession] spawns no PTY until `start()`, the session
 * factory and the `alive` accessor are injectable, and all mutations are
 * short blocking calls under one lock ([D3] of the Phase 7 design notes —
 * a suspend/Mutex design would hang when `onCleared()` runs after
 * `viewModelScope` cancellation).
 *
 * The Android side (shell bootstrap, userland, wake lock, permission flows)
 * stays in `TerminalViewModel`, which delegates all session *state* here.
 */
class TerminalSessionManager(
    private val createTerminalSession: () -> TerminalSession = { TerminalSession() },
    /** Injectable so host tests can flip liveness without a real PTY. */
    private val aliveOf: (TerminalSession) -> StateFlow<Boolean> = { it.alive },
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
    /** Scope for per-session alive watchers only; injectable for tests. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val lock = Any()
    private val _sessions = MutableStateFlow<List<TerminalSessionItem>>(emptyList())
    val sessions: StateFlow<List<TerminalSessionItem>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _anyAlive = MutableStateFlow(false)
    val anyAlive: StateFlow<Boolean> = _anyAlive.asStateFlow()

    /** Monotonic, never reused within the app process (Termux behavior). */
    private var sessionCounter = 0

    private val aliveWatchers = mutableMapOf<String, Job>()

    /** Synchronous snapshot of the active item (Compose derives the same via flows). */
    fun activeItem(): TerminalSessionItem? = activeItem(_sessions.value, _activeSessionId.value)

    /**
     * Creates a new session item and makes it active.
     *
     * @return the new item, or `null` when the session cap ([maxSessions])
     *         is reached (the caller shows feedback; D7).
     */
    fun createSession(): TerminalSessionItem? {
        val item: TerminalSessionItem
        synchronized(lock) {
            if (_sessions.value.size >= maxSessions) return null
            sessionCounter += 1
            item = TerminalSessionItem(
                sessionNumber = sessionCounter,
                session = createTerminalSession()
            )
            _sessions.value = _sessions.value + item
            _activeSessionId.value = item.id
            aliveWatchers[item.id] = scope.launch {
                aliveOf(item.session).collect { recomputeAnyAlive() }
            }
            recomputeAnyAliveLocked()
        }
        return item
    }

    /** Makes `id` active; a no-op for unknown ids or the current selection. */
    fun switchSession(id: String) {
        synchronized(lock) {
            if (_activeSessionId.value == id) return
            if (_sessions.value.none { it.id == id }) return
            _activeSessionId.value = id
        }
    }

    /**
     * Stops and removes `id`, then selects the adjacent session (same index
     * in the shortened list, else the new last one). Closing the final
     * session auto-creates a fresh one so the Terminal tab always has a
     * usable shell (D6).
     *
     * @return the item that became active — which, when the last session was
     *         closed, is a **newly created not-yet-started** session the
     *         caller must bootstrap — or `null` for an unknown id.
     */
    fun closeSession(id: String): TerminalSessionItem? {
        synchronized(lock) {
            val list = _sessions.value
            val index = list.indexOfFirst { it.id == id }
            if (index < 0) return null
            val item = list[index]
            runCatching { item.session.stop() }
            aliveWatchers.remove(id)?.cancel()
            val remaining = list.toMutableList().also { it.removeAt(index) }
            _sessions.value = remaining
            if (_activeSessionId.value == id) {
                _activeSessionId.value = remaining.getOrNull(index)?.id ?: remaining.lastOrNull()?.id
            }
            recomputeAnyAliveLocked()
            if (remaining.isEmpty()) {
                // D6: never leave the terminal without a session.
                return createLocked()
            }
            return activeItem(remaining, _activeSessionId.value)
        }
    }

    /** Sets a custom title; a blank name clears it (falls back to shell title). */
    fun renameSession(id: String, name: String?) {
        synchronized(lock) {
            _sessions.value = _sessions.value.map { item ->
                if (item.id == id) {
                    item.copy(customTitle = name?.trim()?.takeIf { it.isNotEmpty() })
                } else {
                    item
                }
            }
        }
    }

    /**
     * Shuts everything down (used by ViewModel `onCleared` and by forced
     * userland re-install, D11). Session numbering keeps counting afterwards.
     */
    fun closeAll() {
        synchronized(lock) {
            _sessions.value.forEach { item -> runCatching { item.session.stop() } }
            aliveWatchers.values.forEach { it.cancel() }
            aliveWatchers.clear()
            _sessions.value = emptyList()
            _activeSessionId.value = null
            recomputeAnyAliveLocked()
        }
    }

    /** Also cancel the watcher scope itself; the manager is unusable after. */
    fun dispose() {
        closeAll()
        runCatching { scope.cancel() }
    }

    private fun createLocked(): TerminalSessionItem {
        sessionCounter += 1
        val item = TerminalSessionItem(
            sessionNumber = sessionCounter,
            session = createTerminalSession()
        )
        _sessions.value = _sessions.value + item
        _activeSessionId.value = item.id
        aliveWatchers[item.id] = scope.launch {
            aliveOf(item.session).collect { recomputeAnyAlive() }
        }
        recomputeAnyAliveLocked()
        return item
    }

    private fun recomputeAnyAliveLocked() {
        _anyAlive.value = _sessions.value.any { aliveOf(it.session).value }
    }

    // Watchers run off the lock; state reads inside are safe (StateFlow).
    private fun recomputeAnyAlive() {
        synchronized(lock) { recomputeAnyAliveLocked() }
    }

    private fun activeItem(
        list: List<TerminalSessionItem>,
        activeId: String?
    ): TerminalSessionItem? = list.firstOrNull { it.id == activeId } ?: list.firstOrNull()

    companion object {
        /** D7: PTYs cost fds + reader threads; 8 is far beyond phone use. */
        const val DEFAULT_MAX_SESSIONS = 8
    }
}
