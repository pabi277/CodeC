package com.codeci.ide

import com.codeci.ide.ui.services.LanAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 37.1 — which address a peer can actually dial. Every case here is a
 * real device situation the owner's cross-device round will hit; the Android
 * provider only feeds candidates into these rules.
 */
class LanAddressTest {

    @Test
    fun `dotted quads parse and everything else is refused`() {
        assertTrue(LanAddress.isIpv4("192.168.1.20"))
        assertTrue(LanAddress.isIpv4("10.0.0.1"))
        assertFalse(LanAddress.isIpv4("192.168.1"))
        assertFalse(LanAddress.isIpv4("192.168.1.20.5"))
        assertFalse(LanAddress.isIpv4("256.1.1.1"))
        assertFalse(LanAddress.isIpv4("fe80::1"))
        assertFalse(LanAddress.isIpv4("hostname.local"))
        assertFalse(LanAddress.isIpv4("192.168.01.20")) // leading zero is not canonical
        assertFalse(LanAddress.isIpv4(""))
    }

    @Test
    fun `the best candidate is the site-local one, not link-local or loopback`() {
        val picked = LanAddress.pickBest(
            listOf("127.0.0.1", "169.254.42.7", "192.168.1.20", "10.0.0.9")
        )
        assertEquals("192.168.1.20", picked)
    }

    @Test
    fun `only one candidate and it is still filtered`() {
        assertNull(LanAddress.pickBest(listOf("127.0.0.1")))
        assertNull(LanAddress.pickBest(listOf("169.254.1.1")))
        assertNull(LanAddress.pickBest(listOf("0.0.0.0")))
        assertNull(LanAddress.pickBest(listOf("fe80::2")))
        assertNull(LanAddress.pickBest(emptyList()))
        assertEquals("203.0.113.9", LanAddress.pickBest(listOf("203.0.113.9")))
    }

    @Test
    fun `site-local covers the three private ranges and nothing else`() {
        assertTrue(LanAddress.isSiteLocal("10.1.2.3"))
        assertTrue(LanAddress.isSiteLocal("172.16.0.1"))
        assertTrue(LanAddress.isSiteLocal("172.31.255.255"))
        assertTrue(LanAddress.isSiteLocal("192.168.0.5"))
        assertFalse(LanAddress.isSiteLocal("172.32.0.1"))
        assertFalse(LanAddress.isSiteLocal("192.169.0.5"))
    }

    @Test
    fun `loopback and multicast are never peer-reachable`() {
        assertFalse(LanAddress.isPeerReachable("127.0.0.1"))
        assertFalse(LanAddress.isPeerReachable("127.0.0.53"))
        assertFalse(LanAddress.isPeerReachable("224.0.0.251"))
        assertTrue(LanAddress.isPeerReachable("169.255.0.1"))
    }

    @Test
    fun `the wifi manager packed int is little-endian`() {
        // 0xC0A80114 as an int literal means bytes 14.01.A8.C0 → 192.168.1.20.
        assertEquals("192.168.1.20", LanAddress.fromWifiAddress(0x1401A8C0))
        assertNull(LanAddress.fromWifiAddress(0)) // "not associated"
        assertNull(LanAddress.fromWifiAddress(0x0100007F)) // 127.0.0.1
    }

    @Test
    fun `a url needs both a usable host and a real port`() {
        assertEquals("http://192.168.1.20:8080", LanAddress.url("192.168.1.20", 8080))
        assertNull(LanAddress.url("127.0.0.1", 8080))
        assertNull(LanAddress.url("0.0.0.0", 8080))
        assertNull(LanAddress.url("", 8080))
        assertNull(LanAddress.url("192.168.1.20", 0))
        assertNull(LanAddress.url("192.168.1.20", 70000))
    }

    @Test
    fun `hostPort degrades to a bare port when the address is unknown`() {
        assertEquals("192.168.1.20:8100", LanAddress.hostPort("192.168.1.20", 8100))
        assertEquals(":8100", LanAddress.hostPort(null, 8100))
        assertEquals(":8100", LanAddress.hostPort("  ", 8100))
    }
}
