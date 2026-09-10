package com.codeci.ide

import com.codeci.ide.ui.services.LanAddress
import com.codeci.ide.ui.services.ServerEntry
import com.codeci.ide.ui.services.ServerKind
import com.codeci.ide.ui.services.ServerRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 37.2 — the registry is the single answer to "what is serving on which
 * port", so the whole lifecycle (register → ready → conflict → stop → port
 * free again) is pinned here, on a fixed fake clock.
 */
class ServerRegistryTest {

    private var now = 1_000L
    private val registry = ServerRegistry { now.also { now += 1_000L } }

    private fun register(
        id: String,
        project: String = "demo",
        port: Int,
        bind: String = LanAddress.LOOPBACK_HOST,
        lan: Boolean = false,
        address: String? = "192.168.1.20"
    ): ServerEntry {
        val result = registry.register(
            id = id,
            project = project,
            kind = ServerKind.STATIC_PREVIEW,
            port = port,
            bind = bind,
            lanShared = lan,
            lanAddress = address
        )
        assertTrue("expected a registration for $id, got $result", result is ServerRegistry.Registration.Registered)
        return (result as ServerRegistry.Registration.Registered).entry
    }

    @Test
    fun `register keeps insertion order and exposes one snapshot`() {
        register("a", project = "alpha", port = 8100)
        register("b", project = "beta", port = 8101)
        assertEquals(listOf("a", "b"), registry.snapshot().map { it.id })
        assertEquals(2, registry.liveCount())
        assertTrue(registry.hasLiveServers())
    }

    @Test
    fun `two live servers never share a port`() {
        val first = register("a", project = "alpha", port = 8080)
        val second = registry.register(
            id = "b",
            project = "beta",
            kind = ServerKind.PROCESS,
            port = 8080,
            bind = LanAddress.WILDCARD_HOST,
            lanShared = true
        )
        assertTrue(second is ServerRegistry.Registration.Conflict)
        assertEquals("alpha", (second as ServerRegistry.Registration.Conflict).conflicting.project)
        assertEquals("a", first.id)
        assertEquals(1, registry.liveCount())
    }

    @Test
    fun `a port zero claim is not a conflict because nothing is bound yet`() {
        // Process runs register before the bind line exists; two of them must
        // not block each other, and the first real port still wins.
        val a = register("a", port = 0, bind = LanAddress.WILDCARD_HOST, lan = true)
        val b = register("b", port = 0, bind = LanAddress.WILDCARD_HOST, lan = true)
        assertEquals(0, a.port)
        assertNull(a.endpoints)
        assertEquals(2, registry.liveCount())
        assertNotNull(b)
    }

    @Test
    fun `update after the bind line fills in the port and the endpoints`() {
        register("a", port = 0, bind = LanAddress.LOOPBACK_HOST, lan = true)
        val ready = registry.update(
            id = "a",
            port = 8000,
            bind = LanAddress.WILDCARD_HOST,
            lanAddress = "10.0.0.9"
        )!!
        assertEquals(8000, ready.port)
        assertEquals("http://10.0.0.9:8000", ready.endpoints?.lanUrl)
        assertEquals("http://127.0.0.1:8000", ready.endpoints?.loopbackUrl)
        assertEquals("http://10.0.0.9:8000", ready.openUrl())
        assertNull(ready.conflictWith)
    }

    @Test
    fun `an update that lands on a taken port tags the loser`() {
        register("a", project = "alpha", port = 8080)
        register("b", project = "beta", port = 0)
        val loser = registry.update(id = "b", port = 8080, bind = LanAddress.WILDCARD_HOST)!!
        assertTrue(loser.hasPortConflict())
        assertEquals("a", loser.conflictWith)
        val message = loser.portConflictMessage(registry.findById("a"))
        assertTrue(message, message.contains("Port 8080 is in use"))
        assertTrue(message, message.contains("alpha"))
        assertTrue(message, message.contains("change the port"))
    }

    @Test
    fun `stopping frees the port for the next server`() {
        register("a", port = 8123)
        assertNotNull(registry.ownerOfPort(8123))
        val stopped = registry.markStopped("a")
        assertEquals("a", stopped?.id)
        assertFalse(stopped!!.alive)
        assertNull(registry.ownerOfPort(8123))
        assertTrue(registry.snapshot().isEmpty())
        // A dead row is still visible until it is removed, but never live.
        assertEquals(1, registry.allEntries().size)
        assertFalse(registry.allEntries().first().alive)
        registry.remove("a")
        assertNull(registry.findById("a"))
    }

    @Test
    fun `findbyurl answers for both addresses`() {
        register("a", port = 8100, bind = LanAddress.WILDCARD_HOST, lan = true)
        assertEquals("a", registry.findByUrl("http://127.0.0.1:8100")?.id)
        assertEquals("a", registry.findByUrl("http://192.168.1.20:8100")?.id)
        assertNull(registry.findByUrl("http://127.0.0.1:9999"))
    }

    @Test
    fun `clear forgets everything and reports what went`() {
        register("a", port = 8100)
        register("b", port = 8101)
        assertEquals(listOf("a", "b"), registry.clear())
        assertFalse(registry.hasLiveServers())
    }

    @Test
    fun `stoppable ids go newest first`() {
        register("a", port = 8100)
        register("b", port = 8101)
        register("c", port = 8102)
        assertEquals(listOf("c", "b", "a"), registry.stoppableIds())
    }

    @Test
    fun `describe carries the project the port and the mode`() {
        val shared = register("a", project = "site", port = 8100, bind = LanAddress.WILDCARD_HOST, lan = true)
        assertEquals("site · 8100 · LAN", shared.describe())
        val private = register("b", project = "solo", port = 8101)
        assertEquals("solo · 8101", private.describe())
    }

    @Test
    fun `the lan port pool skips what is taken and falls back to zero`() {
        assertEquals(8100, ServerRegistry.nextFreePort(emptyList()))
        assertEquals(8102, ServerRegistry.nextFreePort(listOf(8100, 8101)))
        assertEquals(8100, ServerRegistry.nextFreePort(listOf(9000)))
        val full = (8100..8199).toList()
        assertEquals(0, ServerRegistry.nextFreePort(full))
    }

    @Test
    fun `a conflict outside the registry still names the port`() {
        val message = ServerRegistry.portInUseMessage(5000, null)
        assertEquals("Port 5000 is in use. Stop the other server or change the port.", message)
    }
}
