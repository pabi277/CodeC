package com.codeci.ide

import com.codeci.ide.ui.terminal.TerminalSession
import com.codeci.ide.ui.terminal.TerminalSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7 host tests for [TerminalSessionManager] — pure JVM, no PTY:
 * [TerminalSession] spawns no process until `start()`, and liveness is
 * injected through the `aliveOf` seam so the anyAlive wake-lock source can
 * be exercised deterministically (design notes D2/D3/D6/D7/D8).
 */
class TerminalSessionManagerTest {

    private val aliveFlags = mutableMapOf<TerminalSession, MutableStateFlow<Boolean>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun newManager(maxSessions: Int = TerminalSessionManager.DEFAULT_MAX_SESSIONS): TerminalSessionManager =
        TerminalSessionManager(
            createTerminalSession = { TerminalSession() },
            aliveOf = { session -> aliveFlags.getOrPut(session) { MutableStateFlow(false) } },
            maxSessions = maxSessions,
            scope = scope
        )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `createSession selects the new session and numbers monotonically`() {
        val manager = newManager()
        val first = manager.createSession()
        val second = manager.createSession()

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(1, first!!.sessionNumber)
        assertEquals(2, second!!.sessionNumber)
        assertEquals(listOf(first.id, second.id), manager.sessions.value.map { it.id })
        assertEquals(second.id, manager.activeSessionId.value)
        assertEquals(second.id, manager.activeItem()?.id)
    }

    @Test
    fun `switchSession changes the active session and ignores unknown ids`() {
        val manager = newManager()
        val first = manager.createSession()!!
        val second = manager.createSession()!!
        assertEquals(second.id, manager.activeItem()?.id)

        manager.switchSession(first.id)
        assertEquals(first.id, manager.activeItem()?.id)

        manager.switchSession("does-not-exist")
        assertEquals(first.id, manager.activeItem()?.id)
    }

    @Test
    fun `closeSession selects the adjacent session - middle close picks next`() {
        val manager = newManager()
        val one = manager.createSession()!!
        val two = manager.createSession()!!
        val three = manager.createSession()!!

        val next = manager.closeSession(two.id)
        assertNotNull(next)
        assertEquals(three.id, manager.activeSessionId.value)
        assertEquals(three.id, next!!.id)
        assertEquals(listOf(one.id, three.id), manager.sessions.value.map { it.id })
    }

    @Test
    fun `closeSession selects the adjacent session - last close picks previous`() {
        val manager = newManager()
        val one = manager.createSession()!!
        val two = manager.createSession()!!

        val next = manager.closeSession(two.id)
        assertEquals(one.id, manager.activeSessionId.value)
        assertEquals(one.id, next?.id)
    }

    @Test
    fun `closing the final session auto-creates a fresh replacement`() {
        val manager = newManager()
        manager.createSession()!!
        val replacement = manager.closeSession(manager.activeSessionId.value!!)

        // D6: exactly one session remains, it is a *new* item with the next
        // monotonic number (never reused), already selected.
        assertNotNull(replacement)
        assertEquals(1, manager.sessions.value.size)
        assertEquals(2, replacement!!.sessionNumber)
        assertEquals(replacement.id, manager.activeSessionId.value)
        assertEquals("Session 2", replacement.displayTitle)
    }

    @Test
    fun `closeSession returns null for unknown id`() {
        val manager = newManager()
        manager.createSession()
        assertNull(manager.closeSession("nope"))
        assertEquals(1, manager.sessions.value.size)
    }

    @Test
    fun `renameSession overrides displayTitle and blank clears it`() {
        val manager = newManager()
        val item = manager.createSession()!!

        manager.renameSession(item.id, "Build")
        assertEquals("Build", manager.sessions.value.first { it.id == item.id }.displayTitle)

        // Leading/trailing whitespace is trimmed.
        manager.renameSession(item.id, "  Server  ")
        assertEquals("Server", manager.sessions.value.first { it.id == item.id }.displayTitle)

        // A blank rename falls back to the default title (D1).
        manager.renameSession(item.id, "   ")
        assertEquals("Session 1", manager.sessions.value.first { it.id == item.id }.displayTitle)
    }

    @Test
    fun `session cap is enforced`() {
        val manager = newManager(maxSessions = 2)
        val one = manager.createSession()
        val two = manager.createSession()
        assertNotNull(one)
        assertNotNull(two)

        val third = manager.createSession()
        assertNull(third)
        assertEquals(2, manager.sessions.value.size)
        assertEquals(two!!.id, manager.activeSessionId.value)
    }

    @Test
    fun `anyAlive tracks injected liveness and recomputes on close`() = runBlocking {
        val manager = newManager()
        val one = manager.createSession()!!
        val two = manager.createSession()!!
        assertFalse(manager.anyAlive.value)

        aliveFlags.getValue(one.session).value = true
        assertTrue(awaitTrue { manager.anyAlive.value })

        // Closing the alive session must clear the flag even while the other
        // replacement session stays dead.
        manager.closeSession(one.id)
        assertTrue(awaitTrue { !manager.anyAlive.value })

        aliveFlags.getValue(two.session).value = true
        assertTrue(awaitTrue { manager.anyAlive.value })

        manager.closeAll()
        assertTrue(awaitTrue { !manager.anyAlive.value })
        assertTrue(manager.sessions.value.isEmpty())
        assertNull(manager.activeSessionId.value)
    }

    @Test
    fun `displayTitle falls back to Session N by default`() {
        val manager = newManager()
        val item = manager.createSession()!!
        assertEquals("Session 1", item.displayTitle)
    }

    /** Bounded wait for Unconfined-watcher propagation (flaky-free polling). */
    private suspend fun awaitTrue(predicate: () -> Boolean): Boolean =
        withTimeout(1_000) {
            while (!predicate()) kotlinx.coroutines.delay(10)
            true
        }
}
