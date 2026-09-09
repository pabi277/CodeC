package com.codeci.ide.ui.services

/**
 * Phase 37.1 — pure address logic for "the phone is a server".
 *
 * A LAN URL is only useful if peers can actually reach the address in it, so
 * the whole selection rule lives here, Android-free: the caller hands over
 * candidate addresses (from `LinkProperties`, `NetworkInterface`, or
 * `WifiManager`) and [pickBest] decides which one to publish — or returns
 * null so the UI can say "connect the phone to Wi-Fi" instead of showing a
 * dead URL. Keeping the rule pure makes the OEM-quirk behaviour (link-local
 * addresses, tethering gateways, IPv6-only networks) testable on the host.
 */
object LanAddress {

    /** Bind that accepts LAN connections; [LOOPBACK_HOST] only answers on the device. */
    const val WILDCARD_HOST = "0.0.0.0"

    /** The on-device preview address; never advertised to peers. */
    const val LOOPBACK_HOST = "127.0.0.1"

    /** A wildcard bind is what makes the LAN URL reachable (Phase 37.1 §1). */
    const val ANY_HOST = WILDCARD_HOST

    private const val LINK_LOCAL_PREFIX = "169.254."

    /** True for a dotted quad `a.b.c.d` with every octet in 0..255. */
    fun isIpv4(text: String): Boolean {
        val parts = text.split('.')
        if (parts.size != 4) return false
        for (part in parts) {
            if (part.isEmpty() || part.length > 3) return false
            if (part.any { it < '0' || it > '9' }) return false
            if (part.length > 1 && part[0] == '0') return false // 01 → not canonical
            if (part.toInt() > 255) return false
        }
        return true
    }

    /**
     * Can another device open this address? Excludes loopback, the wildcard
     * itself, `0.x`, link-local (DHCP failures), and multicast/reserved
     * ranges — none of them is a URL a peer can dial.
     */
    fun isPeerReachable(text: String): Boolean {
        if (!isIpv4(text)) return false
        if (text == LOOPBACK_HOST || text == WILDCARD_HOST) return false
        if (text.startsWith(LINK_LOCAL_PREFIX)) return false
        val octets = text.split('.').map { it.toInt() }
        if (octets[0] == 0 || octets[0] == 127) return false
        if (octets[0] >= 224) return false // multicast + reserved
        return true
    }

    /** Private (RFC 1918) ranges — what home Wi-Fi and hotspots hand out. */
    fun isSiteLocal(text: String): Boolean {
        if (!isIpv4(text)) return false
        val octets = text.split('.').map { it.toInt() }
        return when (octets[0]) {
            10 -> true
            172 -> octets[1] in 16..31
            192 -> octets[1] == 168
            else -> false
        }
    }

    /**
     * The best address to publish, from an ordered candidate list:
     * site-local first (the range peers on the same Wi-Fi live in), then any
     * other reachable IPv4 in caller order. Null when nothing is usable.
     */
    fun pickBest(candidates: List<String>): String? {
        val usable = candidates.filter { isPeerReachable(it) }
        return usable.firstOrNull { isSiteLocal(it) } ?: usable.firstOrNull()
    }

    /**
     * `WifiManager.connectionInfo.ipAddress` is a packed int in *little-endian*
     * byte order (that is why `Formatter.formatIpAddress` exists). Converted
     * by hand so the rule is host-testable and needs no Android `Formatter`.
     */
    fun fromWifiAddress(packed: Int): String? {
        if (packed == 0) return null
        val text = "${packed and 0xFF}.${(packed shr 8) and 0xFF}." +
            "${(packed shr 16) and 0xFF}.${(packed shr 24) and 0xFF}"
        return if (isPeerReachable(text)) text else null
    }

    /**
     * `http://host:port` for a peer, or null when the host is not something a
     * peer could open (loopback, link-local, wildcard, malformed) or the port
     * is not real. One gate for both halves, so no caller has to remember it.
     */
    fun url(host: String, port: Int): String? {
        if (!isPeerReachable(host) || port !in 1..65535) return null
        return "http://$host:$port"
    }

    /** `host:port` for the keep-alive notification title. */
    fun hostPort(host: String?, port: Int): String =
        if (host.isNullOrBlank()) ":$port" else "$host:$port"
}
