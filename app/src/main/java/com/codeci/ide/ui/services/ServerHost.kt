package com.codeci.ide.ui.services

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phase 37.2 — the process-lifetime owner of every background server.
 *
 * Two things make this a separate object instead of a field on the
 * [com.codeci.ide.ui.viewmodels.EditorViewModel]:
 *
 *  1. **Survival.** A LAN server is only useful while the user is doing
 *     something else, so the child process must not die when the editor tab is
 *     left or Android destroys the activity. A ViewModel's scope is cancelled
 *     at exactly that moment, which would cancel the flow collection and —
 *     through `ServerRunner.awaitClose` — kill the process and stop draining
 *     its stdout (a server nobody reads blocks on a full pipe). So the
 *     *collection* lives here, in a scope nobody tears down, and the ViewModel
 *     is only an observer of the replayed event stream.
 *  2. **One truth about ports.** The Output Panel, the preview screen and the
 *     foreground notification all need the same answer to "what is serving
 *     where". That answer is [registry]; this class is its only writer.
 *
 * Loopback-only servers behave exactly as before: the screen that started one
 * stops it when it goes away.
 */
class ServerHost(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val registry: ServerRegistry = ServerRegistry()
) {

    private val lock = Any()
    private val sessions = LinkedHashMap<String, Session>()

    /** The device IPv4 peers use, published by the Android side (null = no Wi-Fi). */
    @Volatile
    private var deviceAddress: String? = null

    private val _servers = MutableStateFlow<List<ServerEntry>>(emptyList())

    /** Live servers, oldest first — the single list every surface renders. */
    val servers: StateFlow<List<ServerEntry>> = _servers.asStateFlow()

    /** Result of asking the host to serve a folder statically (Phase 9.1 + 37.1). */
    sealed interface StaticOutcome {
        /** The server is up; [entry] carries both URLs. */
        data class Serving(val entry: ServerEntry) : StaticOutcome

        /** Nothing was bound — [message] says why (port clash, unreadable folder). */
        data class Refused(val message: String) : StaticOutcome
    }

    private class Session(val id: String) {
        val events = MutableSharedFlow<ServerEvent>(
            replay = EVENT_REPLAY,
            extraBufferCapacity = EVENT_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        var job: Job? = null
        var preview: WebPreviewServer? = null
        var runner: ServerRunner? = null

        /** True while a process run is still being watched by this host. */
        fun isWatched(): Boolean = job?.isActive == true
    }

    /**
     * Publishes the phone's LAN address and re-points every live entry at it,
     * so a URL that just became (un)reachable is never shown stale.
     */
    fun publishLanAddress(address: String?): List<ServerEntry> = synchronized(lock) {
        deviceAddress = address?.takeIf { LanAddress.isPeerReachable(it) }
        registry.snapshot().forEach { entry ->
            registry.update(entry.id, lanAddress = deviceAddress)
        }
        publish()
        registry.snapshot()
    }

    fun lanAddress(): String? = deviceAddress

    /**
     * Serves [root] on a CodeC-owned [WebPreviewServer]. Idempotent per [id]:
     * returning to the preview screen re-attaches to the live server (same
     * port, same QR) instead of binding a second socket. Flipping LAN sharing
     * is the only change that rebinds — the bind address is fixed at `listen`.
     */
    fun serveStatic(
        id: String,
        project: String,
        root: File,
        lan: Boolean,
        forceRestart: Boolean = false
    ): StaticOutcome = synchronized(lock) {
        val existing = sessions[id]
        val live = registry.findById(id)?.takeIf { it.alive }
        if (existing != null && live != null && live.lanShared == lan && !forceRestart) {
            return@synchronized StaticOutcome.Serving(live)
        }
        if (existing != null) stopLocked(id)

        val bind = if (lan) WebPreviewServer.LAN_HOST else WebPreviewServer.LOOPBACK_HOST
        val wanted = if (lan) {
            ServerRegistry.nextFreePort(registry.snapshot().map { it.port })
        } else {
            0 // loopback previews are private to the phone: ephemeral is fine
        }
        var started = WebPreviewServer.startAt(root, bind, wanted)
        if (started is WebPreviewServer.PreviewStart.PortInUse && wanted > 0) {
            // The preferred LAN port was taken outside CodeC: an ephemeral one
            // still serves the folder, so the preview works and only the
            // convenience of a stable port is lost.
            started = WebPreviewServer.startAt(root, bind, 0)
        }
        if (started !is WebPreviewServer.PreviewStart.Ready) {
            val message = when (started) {
                is WebPreviewServer.PreviewStart.PortInUse ->
                    ServerRegistry.portInUseMessage(wanted, registry.ownerOfPort(wanted))
                is WebPreviewServer.PreviewStart.Failed -> started.message
                else -> "Could not start the preview server"
            }
            return@synchronized StaticOutcome.Refused(message)
        }
        val server = started.server
        when (
            val registration = registry.register(
                id = id,
                project = project,
                kind = ServerKind.STATIC_PREVIEW,
                port = server.port,
                bind = server.bindAddress,
                lanShared = lan,
                lanAddress = deviceAddress
            )
        ) {
            is ServerRegistry.Registration.Conflict -> {
                server.stop()
                return@synchronized StaticOutcome.Refused(
                    ServerRegistry.portInUseMessage(server.port, registration.conflicting)
                )
            }
            is ServerRegistry.Registration.Registered -> {
                val session = Session(id)
                session.preview = server
                sessions[id] = session
                publish()
                StaticOutcome.Serving(registration.entry)
            }
        }
    }

    /**
     * Takes ownership of [runner] for [id] and returns its event stream.
     *
     * The stream is a replaying shared flow: a ViewModel that comes back later
     * (activity recreated, tab re-opened) gets the tail of the output and the
     * `Ready` event again, while the process never notices the observer change.
     * Re-attaching to a live [id] returns the same stream and starts nothing.
     */
    fun attachProcess(
        id: String,
        project: String,
        runner: ServerRunner,
        lan: Boolean
    ): Flow<ServerEvent> = synchronized(lock) {
        val existing = sessions[id]
        if (existing != null && existing.isWatched()) {
            return@synchronized existing.events.asSharedFlow()
        }
        val session = Session(id)
        session.runner = runner
        sessions[id] = session
        registry.register(
            id = id,
            project = project,
            kind = ServerKind.PROCESS,
            port = 0, // known only once the bind line is seen
            bind = if (lan) LanAddress.WILDCARD_HOST else LanAddress.LOOPBACK_HOST,
            lanShared = lan,
            lanAddress = deviceAddress
        )
        publish()
        session.job = scope.launch {
            try {
                runner.start().collect { event ->
                    onProcessEvent(id, event)
                    session.events.tryEmit(event)
                }
            } catch (_: CancellationException) {
                // The observer stopped watching on purpose; a server is only
                // ever killed through stop()/stopAll(), never by a dying viewer.
            } finally {
                onProcessEnd(id)
            }
        }
        session.events.asSharedFlow()
    }

    /** The live stream for [id], or null when this host does not own one. */
    fun events(id: String): Flow<ServerEvent>? = synchronized(lock) {
        sessions[id]?.events?.asSharedFlow()
    }

    fun entryFor(id: String): ServerEntry? = registry.findById(id)

    fun endpointsOf(id: String): ServerEndpoints? = registry.findById(id)?.endpoints

    fun isServing(id: String): Boolean = registry.findById(id)?.alive == true

    /** Servers this host can stop, in the order a "stop all" would use. */
    fun stoppableIds(): List<String> = registry.stoppableIds()

    /** Tears one server down and releases its port. Returns true when one ran. */
    fun stop(id: String): Boolean = synchronized(lock) {
        val torn = stopLocked(id) || registry.remove(id) != null
        if (torn) publish()
        torn
    }

    /** The registry-level "stop all servers" (37.2 §3). Returns how many died. */
    fun stopAll(): Int = synchronized(lock) {
        var stopped = 0
        sessions.keys.toList().forEach { id -> if (stopLocked(id)) stopped++ }
        registry.clear()
        publish()
        stopped
    }

    /**
     * The LAN toggle moved while servers are live: re-publish every entry's
     * endpoints so the panel, the preview and the notification never show a URL
     * that is no longer accurate. A *process* server's bind address still
     * needs a restart — the entry then says so through [ServerEndpoints.notice].
     */
    fun refreshLan(lan: Boolean): List<ServerEntry> = synchronized(lock) {
        registry.snapshot().forEach { entry ->
            registry.update(entry.id, lanShared = lan, lanAddress = deviceAddress)
        }
        publish()
        registry.snapshot()
    }

    // ---- internals --------------------------------------------------------

    private fun onProcessEvent(id: String, event: ServerEvent) {
        when (event) {
            is ServerEvent.Ready -> {
                registry.update(id, port = event.port, bind = event.bind, lanAddress = deviceAddress)
                publish()
            }
            is ServerEvent.Exited, is ServerEvent.Failed -> {
                registry.markStopped(id)
                publish()
            }
            else -> Unit
        }
    }

    private fun onProcessEnd(id: String) {
        synchronized(lock) {
            val session = sessions[id] ?: return@synchronized
            if (session.runner != null) sessions.remove(id)
            registry.markStopped(id)
            publish()
        }
    }

    /** Returns true when a session existed and was torn down. Never throws. */
    private fun stopLocked(id: String): Boolean {
        val session = sessions.remove(id) ?: return false
        session.job?.cancel()
        session.job = null
        session.runner?.stop()
        session.runner = null
        session.preview?.stop()
        session.preview = null
        registry.remove(id)
        return true
    }

    private fun publish() {
        _servers.value = registry.snapshot()
    }

    companion object {
        /** Enough tail for a re-attaching panel to show what just happened. */
        private const val EVENT_REPLAY = 96
        private const val EVENT_BUFFER = 256

        /**
         * The id a LAN server run is owned under. The project name is the key
         * so re-opening the same project re-attaches instead of double-binding.
         */
        fun processId(project: String): String = "process:$project"

        /** The id a static folder preview is owned under. */
        fun staticId(project: String): String = "static:$project"
    }
}

/**
 * The app-wide [ServerHost]. Kept as a one-line holder instead of globals
 * sprinkled through the UI: the class stays testable with an injected scope,
 * and the app has exactly one instance of it.
 */
object ServerHosts {
    val shared: ServerHost by lazy { ServerHost() }
}
