package com.codeci.ide.ui.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import java.net.NetworkInterface

/**
 * Phase 37.1 — the Android half of "what address do peers dial?".
 *
 * The *rule* lives in [LanAddress] and is host-tested; this class only
 * collects candidates, from three sources in trust order:
 *
 *  1. `ConnectivityManager.getLinkProperties(activeNetwork).linkAddresses` —
 *     the modern path, API 21+ and the one that survives OEM Wi-Fi quirks.
 *  2. `NetworkInterface` enumeration — catches the hotspot/tethering cases
 *     (`ap0`, `rndis0`) where the active network is not the interface peers
 *     actually reach.
 *  3. `WifiManager.connectionInfo.ipAddress` — the classic fallback, still the
 *     only answer on a few old OEM builds; its int is little-endian packed.
 *
 * Needs no runtime permission: `ACCESS_NETWORK_STATE` (already declared) covers
 * the modern path and Phase 37.1 adds `ACCESS_WIFI_STATE` for the classic one,
 * which is also wrapped because `connectionInfo` throws on some locked-down
 * ROMs.
 */
class LanAddressProvider(private val context: Context) {

    /** The best peer-reachable IPv4, or null when the device has none. */
    fun resolve(): String? = LanAddress.pickBest(candidates())

    /** Raw candidates in source order — exposed for the host test. */
    fun candidates(): List<String> = buildList {
        addAll(fromConnectivityManager())
        addAll(fromNetworkInterfaces())
        fromWifiManager()?.let { add(it) }
    }

    private fun fromConnectivityManager(): List<String> {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return emptyList()
        return runCatching {
            val network = manager.activeNetwork ?: return emptyList()
            val properties = manager.getLinkProperties(network) ?: return emptyList()
            properties.linkAddresses.mapNotNull { link ->
                val address = link.address ?: return@mapNotNull null
                if (address.isLoopbackAddress || address.isLinkLocalAddress ||
                    address.isMulticastAddress || address.hostAddress == null
                ) {
                    return@mapNotNull null
                }
                // IPv6 carries a `%wlan0` scope suffix; only IPv4 goes in a URL.
                address.hostAddress!!.substringBefore('%')
            }
        }.getOrDefault(emptyList())
    }

    private fun fromNetworkInterfaces(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { interface_ -> interface_.isUp && !interface_.isLoopback }
            .flatMap { interface_ -> interface_.inetAddresses.toList() }
            .filter { address ->
                !address.isLoopbackAddress && !address.isLinkLocalAddress &&
                    address.isSiteLocalAddress && address.hostAddress != null
            }
            .map { address -> address.hostAddress!!.substringBefore('%') }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun fromWifiManager(): String? {
        val manager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val packed = runCatching { manager.connectionInfo?.ipAddress ?: 0 }.getOrDefault(0)
        return if (packed == 0) null else LanAddress.fromWifiAddress(packed)
    }

    companion object {
        /**
         * Resolves and publishes the address into the shared [ServerHost] so the
         * registry, the panel and the notification all speak the same URL.
         * Suspend + `Dispatchers.IO`: the system calls are fast but not free,
         * and this runs on the tap-to-run path.
         */
        suspend fun refresh(
            context: Context,
            host: ServerHost = ServerHosts.shared
        ): String? {
            val address = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                LanAddressProvider(context.applicationContext).resolve()
            }
            host.publishLanAddress(address)
            return address
        }
    }
}
