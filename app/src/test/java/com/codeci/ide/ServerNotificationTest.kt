package com.codeci.ide

import com.codeci.ide.ui.services.LanAddress
import com.codeci.ide.ui.services.ServerEndpoints
import com.codeci.ide.ui.services.ServerKind
import com.codeci.ide.ui.services.ServerNotification
import com.codeci.ide.ui.services.ServerRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 37.2 — the keep-alive notification text, host-tested through the pure
 * formatter (the spec's rule: no `Service` in the unit test).
 */
class ServerNotificationTest {

    private fun endpoints(
        port: Int = 8080,
        bind: String = LanAddress.WILDCARD_HOST,
        address: String? = "192.168.1.20",
        lanShared: Boolean = true
    ) = ServerEndpoints.of(port, bind, address, lanShared)

    @Test
    fun `the title leads with the peer-facing address`() {
        assertEquals(
            "Serving demo on 192.168.1.20:8080",
            ServerNotification.title("demo", endpoints())
        )
    }

    @Test
    fun `without lan the title says loopback rather than nothing`() {
        assertEquals(
            "Serving demo on 127.0.0.1:8080",
            ServerNotification.title("demo", endpoints(lanShared = false))
        )
        assertEquals(
            "Serving demo on 127.0.0.1:8080",
            ServerNotification.title("demo", endpoints(address = null))
        )
    }

    @Test
    fun `no endpoints is still an honest title`() {
        // The 5-second promotion can fire before the bind line arrives.
        assertEquals("Serving demo", ServerNotification.title("demo", null))
        assertEquals("Serving CodeC", ServerNotification.title(null, null))
        assertEquals("Serving CodeC", ServerNotification.title("   ", null))
    }

    @Test
    fun `the body states what sharing means`() {
        assertTrue(ServerNotification.body(endpoints()).contains("Anyone on this Wi-Fi"))
        assertEquals("On this device only (LAN share is off)", ServerNotification.body(endpoints(lanShared = false)))
        assertTrue(ServerNotification.body(endpoints(address = null)).contains("no Wi-Fi address"))
        assertEquals("Tap to return", ServerNotification.body(null))
    }

    @Test
    fun `the summary counts live servers and stays quiet with none`() {
        val registry = ServerRegistry { 0L }
        registry.register("a", "alpha", ServerKind.STATIC_PREVIEW, 8100, LanAddress.LOOPBACK_HOST, false)
        assertEquals("alpha · :8100", ServerNotification.summary(registry.snapshot()))
        registry.register("b", "beta", ServerKind.PROCESS, 8101, LanAddress.WILDCARD_HOST, true)
        assertEquals("2 servers serving · tap to stop all", ServerNotification.summary(registry.snapshot()))
        assertEquals(null, ServerNotification.summary(emptyList()))
    }
}
