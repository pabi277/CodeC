package com.codeci.ide.ui.services

/**
 * Phase 37.2 — the single truth about "what is serving on which port".
 *
 * Before this phase every surface that cared about a running server kept its
 * own copy of the URL: the Output Panel had `serverUrl`, the preview screen
 * remembered its `WebPreviewServer`, the notification had a title string. A
 * LAN server now outlives the editor tab, so those copies could disagree —
 * and a phone with two servers on one port needs one owner to answer "is
 * 8080 taken?" [ServerRegistry] is that owner: pure state, no Android, no
 * coroutine dependency, safe to call from the UI thread, the runner thread and
 * the service's Stop callback.
 */
class ServerRegistry(private val clock: () -> Long = System::currentTimeMillis) {

    private val lock = Any()
    private val entries = LinkedHashMap<String, ServerEntry>()

    /** Outcome of [register] — a port conflict is a value, never an exception. */
    sealed interface Registration {
        data class Registered(val entry: ServerEntry) : Registration
        data class Conflict(val conflicting: ServerEntry) : Registration
    }

    /**
     * Adds a live server; refuses a second live entry on the same port.
     * [lanAddress] is the device address the caller resolved (null = none), so
     * the registry stays free of every Android network API.
     */
    fun register(
        id: String,
        project: String,
        kind: ServerKind,
        port: Int,
        bind: String,
        lanShared: Boolean,
        lanAddress: String? = null
    ): Registration = synchronized(lock) {
        // port 0 = "the OS will pick one" (a process run before its bind line),
        // which is not a claim on any port, so it can never conflict.
        val conflict = if (port > 0) {
            entries.values.firstOrNull { it.alive && it.port == port && it.id != id }
        } else {
            null
        }
        if (conflict != null) return@synchronized Registration.Conflict(conflict)
        val entry = ServerEntry(
            id = id,
            project = project,
            kind = kind,
            port = port,
            bind = bind,
            lanShared = lanShared,
            lanAddress = lanAddress?.takeIf { LanAddress.isPeerReachable(it) },
            startedAt = clock(),
            // port 0 = nothing is bound yet, so there is no URL to publish.
            endpoints = if (port > 0) ServerEndpoints.of(port, bind, lanAddress, lanShared) else null
        )
        entries[id] = entry
        Registration.Registered(entry)
    }

    /** Re-points an existing entry at a new bind/port/lan address (a restart keeps its id). */
    fun update(
        id: String,
        port: Int = -1,
        bind: String? = null,
        lanShared: Boolean? = null,
        lanAddress: String? = null
    ): ServerEntry? = synchronized(lock) {
        val current = entries[id] ?: return@synchronized null
        val nextPort = if (port > 0) port else current.port
        // Two live servers on one port is a real state (both projects ask for
        // 8080): the loser keeps its row, tagged with who owns the port, so the
        // panel can say "stop the other server or change the port" (§4).
        val clash = entries.values.firstOrNull {
            it.alive && it.id != id && nextPort > 0 && it.port == nextPort
        }
        val next = current.copy(
            port = nextPort,
            bind = bind ?: current.bind,
            lanShared = lanShared ?: current.lanShared,
            lanAddress = lanAddress ?: current.lanAddress,
            conflictWith = clash?.id
        ).withRecomputedEndpoints()
        entries[id] = next
        next
    }

    /** Marks a server dead (its port is free again) but keeps it for the panel. */
    fun markStopped(id: String): ServerEntry? = synchronized(lock) {
        val current = entries[id] ?: return@synchronized null
        if (!current.alive) return@synchronized null
        val stopped = current.copy(alive = false)
        entries[id] = stopped
        stopped
    }

    /** Drops an entry entirely (a stopped static preview has nothing to show). */
    fun remove(id: String): ServerEntry? = synchronized(lock) { entries.remove(id) }

    fun findById(id: String): ServerEntry? = synchronized(lock) { entries[id] }

    /** The live entry serving [url] — the preview screen finds its server this way. */
    fun findByUrl(url: String): ServerEntry? = synchronized(lock) {
        entries.values.firstOrNull {
            it.alive && (it.endpoints?.loopbackUrl == url || it.endpoints?.lanUrl == url)
        }
    }

    /** Live entry owning [port], optionally ignoring [exceptId] (an id re-registering). */
    fun ownerOfPort(port: Int, exceptId: String? = null): ServerEntry? = synchronized(lock) {
        entries.values.firstOrNull { it.alive && it.port == port && it.id != exceptId }
    }

    /** Everything live, oldest first — the order the panel lists servers in. */
    fun snapshot(): List<ServerEntry> = synchronized(lock) {
        entries.values.filter { it.alive }.sortedBy { it.startedAt }
    }

    /** Live and just-stopped rows, for a surface that shows what recently served. */
    fun allEntries(): List<ServerEntry> = synchronized(lock) {
        entries.values.sortedBy { it.startedAt }
    }

    fun hasLiveServers(): Boolean = synchronized(lock) { entries.values.any { it.alive } }

    fun liveCount(): Int = synchronized(lock) { entries.values.count { it.alive } }

    /** Every entry a restart would have to clear, in reverse start order. */
    fun stoppableIds(): List<String> = synchronized(lock) {
        entries.values.filter { it.alive }.sortedByDescending { it.startedAt }.map { it.id }
    }

    /** Forgets everything (host tests + process death); returns the removed ids. */
    fun clear(): List<String> = synchronized(lock) {
        val ids = entries.keys.toList()
        entries.clear()
        ids
    }

    companion object {

        /**
         * The port the LAN preview tries first: a stable, high port so the QR
         * code and the notification stay meaningful across re-runs, never a
         * privileged one (ports below 1024 need root on Android).
         */
        fun nextFreePort(
            taken: Collection<Int>,
            first: Int = ServerEndpoints.PREFERRED_PORT_FIRST,
            last: Int = ServerEndpoints.PREFERRED_PORT_LAST
        ): Int {
            val busy = taken.toHashSet()
            var port = first
            while (port <= last) {
                if (port !in busy) return port
                port++
            }
            return 0 // pool exhausted → the OS picks an ephemeral port
        }

        /** The user-facing "port is taken" sentence (spec 37.2 §4). */
        fun portInUseMessage(port: Int, owner: ServerEntry?): String {
            val who = owner?.let { " — ${it.project} is using it" } ?: ""
            return "Port $port is in use$who. Stop the other server or change the port."
        }
    }
}

/** What kind of server a registry entry is. */
enum class ServerKind {
    /** CodeC's own [WebPreviewServer] over a project folder (Phase 9.1, 37.1 LAN mode). */
    STATIC_PREVIEW,

    /** A user process (Flask / uvicorn / `http.server` / C microservice, Phase 14). */
    PROCESS
}

/** One row of the [ServerRegistry]. */
data class ServerEntry(
    val id: String,
    val project: String,
    val kind: ServerKind,
    val port: Int,
    val bind: String,
    val lanShared: Boolean,
    val startedAt: Long,
    /** The device IPv4 peers use, null when the phone has no usable address. */
    val lanAddress: String? = null,
    val alive: Boolean = true,
    val endpoints: ServerEndpoints? = null,
    /** Id of the live entry that already owns [port], null while the port is free. */
    val conflictWith: String? = null
) {
    /** `project · 8100 · LAN` — one line, safe to build from any thread. */
    fun describe(): String = buildString {
        append(project)
        append(" · ")
        append(port)
        if (lanShared) append(" · LAN")
    }

    /** The address a peer opens, or the on-device one when LAN is off. */
    fun openUrl(): String? = endpoints?.let { it.lanUrl ?: it.loopbackUrl }

    /** True when another live server already owns [port] (37.2 §4). */
    fun hasPortConflict(): Boolean = conflictWith != null

    /** The actionable sentence for that conflict; [other] may be null (outside CodeC). */
    fun portConflictMessage(other: ServerEntry?): String =
        ServerRegistry.portInUseMessage(port, other)

    /**
     * Rebuilds [endpoints] from the entry's own fields — the single place the
     * "two URLs" projection happens, so a LAN toggle or a re-resolved Wi-Fi
     * IP can never leave a stale URL in the panel.
     */
    fun withRecomputedEndpoints(): ServerEntry = copy(
        endpoints = if (port > 0) ServerEndpoints.of(port, bind, lanAddress, lanShared) else null
    )
}
