package com.codeci.ide.ui.services

/**
 * Phase 37.2 — the keep-alive notification text as a pure formatter.
 *
 * `RunForegroundService` already owns the notification (Phase 24.2); what the
 * server needs on top of it is an honest title: "Serving <project> on
 * <ip>:<port>". Composing it here keeps the mapping (which URL wins, what a
 * loopback-only server says, what "no Wi-Fi" says) host-testable instead of
 * buried in `Service` code the unit tests cannot reach.
 */
object ServerNotification {

    /**
     * The notification title for a live server. Prefers the peer-facing LAN
     * address (that is the whole point of keeping the process alive), falls
     * back to the loopback address, and never prints a URL it does not have.
     */
    fun title(project: String?, endpoints: ServerEndpoints?, fallback: String = "CodeC"): String {
        val name = project?.trim()?.takeIf { it.isNotEmpty() } ?: "CodeC"
        val address = endpoints?.let {
            it.lanUrl?.removePrefix("http://") ?: "${LanAddress.LOOPBACK_HOST}:${it.port}"
        } ?: return "Serving $name"
        return "Serving $name on $address"
    }

    /** The second line: what LAN sharing means right now, in the user's terms. */
    fun body(endpoints: ServerEndpoints?): String = when {
        endpoints == null -> "Tap to return"
        endpoints.hasLan() -> "Anyone on this Wi-Fi can open these files"
        endpoints.lanShared -> "LAN share is on, but the phone has no Wi-Fi address"
        else -> "On this device only (LAN share is off)"
    }

    /**
     * The Output Panel's multi-server summary: one server gets its own line,
     * several get a count, none get nothing (null → the row is not drawn).
     */
    fun summary(entries: List<ServerEntry>): String? = when (entries.size) {
        0 -> null
        1 -> entries.first().let { "${it.project} · :${it.port}${if (it.lanShared) " · LAN" else ""}" }
        else -> "${entries.size} servers serving · tap to stop all"
    }
}
