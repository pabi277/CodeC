package com.codeci.ide

import com.codeci.ide.ui.services.LanAddress
import com.codeci.ide.ui.services.ServerEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 37.1 — the "two addresses" projection and the three reasons a LAN URL
 * may legitimately not exist. The panel shows exactly what these return, so
 * pinning them pins the UX claim without a device.
 */
class ServerEndpointsTest {

    @Test
    fun `loopback always exists, lan only when shared and wildcard-bound`() {
        val shared = ServerEndpoints.of(8080, LanAddress.WILDCARD_HOST, "192.168.1.20", lanShared = true)
        assertEquals("http://127.0.0.1:8080", shared.loopbackUrl)
        assertEquals("http://192.168.1.20:8080", shared.lanUrl)
        assertEquals("192.168.1.20", shared.lanAddress)
        assertTrue(shared.hasLan())
        assertNull(shared.notice())

        val off = ServerEndpoints.of(8080, LanAddress.WILDCARD_HOST, "192.168.1.20", lanShared = false)
        assertFalse(off.hasLan())
        assertNull(off.lanUrl)
        assertNull(off.notice()) // sharing is off: nothing to explain
    }

    @Test
    fun `no wifi address says so instead of showing a dead url`() {
        val noNetwork = ServerEndpoints.of(5000, LanAddress.WILDCARD_HOST, null, lanShared = true)
        assertNull(noNetwork.lanUrl)
        assertEquals(ServerEndpoints.NO_ADDRESS, noNetwork.notice())
    }

    @Test
    fun `a loopback-bound server cannot be shared`() {
        val private = ServerEndpoints.of(5000, LanAddress.LOOPBACK_HOST, "192.168.1.20", lanShared = true)
        assertNull(private.lanUrl)
        assertEquals(ServerEndpoints.NOT_WILDCARD, private.notice())
    }

    @Test
    fun `an unusable address never becomes a url`() {
        // The caller may hand over a link-local address (DHCP failure); the
        // value refuses it rather than advertising something peers cannot reach.
        val linkLocal = ServerEndpoints.of(5000, LanAddress.WILDCARD_HOST, "169.254.9.9", lanShared = true)
        assertNull(linkLocal.lanUrl)
        assertNull(linkLocal.lanAddress)
        assertEquals(ServerEndpoints.NO_ADDRESS, linkLocal.notice())
    }

    @Test
    fun `badge names the mode and the port`() {
        assertEquals(
            "LAN :8080",
            ServerEndpoints.of(8080, LanAddress.WILDCARD_HOST, "10.0.0.9", lanShared = true).badge()
        )
        assertEquals(
            "device :8080",
            ServerEndpoints.of(8080, LanAddress.LOOPBACK_HOST, "10.0.0.9", lanShared = false).badge()
        )
    }
}
