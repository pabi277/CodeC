package com.codeci.ide.ui.services

/**
 * Phase 37.1 — the two addresses one CodeC server answers on, as one pure
 * value (spec: "the panel shows TWO addresses").
 *
 * The on-device WebView always uses [loopbackUrl] — that is the Phase 9/14
 * behaviour and it never changes. [lanUrl] is the peer-facing address, and it
 * only exists when the user opted into LAN sharing **and** the socket is bound
 * to the wildcard **and** the device has a reachable Wi-Fi address. When any
 * of those is missing the value says so through [notice] instead of printing a
 * URL that would hang on "connecting…".
 */
data class ServerEndpoints(
    /** 1..65535, the port the server actually listens on. */
    val port: Int,
    /** `http://127.0.0.1:<port>` — the on-device preview address. */
    val loopbackUrl: String,
    /** `http://<lan-ip>:<port>` or null when the phone has no peer address. */
    val lanUrl: String?,
    /** The LAN IP alone (for the notification title), null when unknown. */
    val lanAddress: String?,
    /** True when LAN sharing is switched on for this run. */
    val lanShared: Boolean,
    /** What the socket is bound to — `127.0.0.1` or `0.0.0.0`. */
    val bind: String
) {

    /** A peer can open [lanUrl]. */
    fun hasLan(): Boolean = lanUrl != null

    /**
     * What the share panel says under the LAN row, or null when there is
     * nothing to explain. Pure so the three failure modes are host-tested
     * rather than assembled in Compose.
     */
    fun notice(): String? = when {
        !lanShared -> null
        lanAddress == null -> NO_ADDRESS
        lanUrl == null -> NOT_WILDCARD
        else -> null
    }

    /** Short label for the Output Panel / preview badge. */
    fun badge(): String = if (hasLan()) "LAN :$port" else "device :$port"

    companion object {

        const val NO_ADDRESS =
            "No Wi-Fi address — connect the phone to a network to share this server"

        const val NOT_WILDCARD =
            "This server bound to 127.0.0.1 — it can only be opened on the phone"

        /** Port range CodeC hands out for LAN sharing (nothing below 1024: no root). */
        const val PREFERRED_PORT_FIRST = 8100
        const val PREFERRED_PORT_LAST = 8199

        /**
         * Builds the pair from what the server reported. [lanAddress] is the
         * device's Wi-Fi IPv4 (null when there is none), [lanShared] the
         * user's per-run switch, [bind] the address the socket listens on.
         */
        fun of(port: Int, bind: String, lanAddress: String?, lanShared: Boolean): ServerEndpoints {
            val wildcard = bind == LanAddress.WILDCARD_HOST || bind == "::" || bind == "*"
            // One gate for both fields: an address peers cannot reach (no
            // address, link-local, loopback) must not appear as a URL *or* as
            // an address, so no surface can render half of a broken pair.
            val usable = lanAddress?.takeIf { LanAddress.isPeerReachable(it) }
            val lanUrl = if (lanShared && wildcard) LanAddress.url(usable.orEmpty(), port) else null
            return ServerEndpoints(
                port = port,
                loopbackUrl = "http://${LanAddress.LOOPBACK_HOST}:$port",
                lanUrl = lanUrl,
                lanAddress = usable,
                lanShared = lanShared,
                bind = bind
            )
        }
    }
}
