package com.codeci.ide

import com.codeci.ide.ui.crash.SafeMode
import com.codeci.ide.ui.crash.StartupLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 42.3 — the crash-loop ledger as a pure state machine, pinned
 * against the spec's exact exit list (PART_42_3 Tests plan). The in-memory
 * store stands in for the production SharedPreferences-backed one; the
 * corrupt store stands in for a ledger file a device could not read.
 */
class StartupLedgerTest {

    private class MapStore : StartupLedger.Store {
        var starting = false
        var count = 0
        var writes = mutableListOf<Pair<Boolean, Int>>()

        override fun readStarting(): Boolean = starting
        override fun readCount(): Int = count
        override fun write(starting: Boolean, count: Int) {
            this.starting = starting
            this.count = count
            writes.add(starting to count)
        }
    }

    /** A store whose every read throws — a corrupt ledger made real. */
    private class CorruptStore : StartupLedger.Store {
        override fun readStarting(): Boolean = error("corrupt")
        override fun readCount(): Int = error("corrupt")
        override fun write(starting: Boolean, count: Int) = error("corrupt")
    }

    @Test
    fun `the first launch of a fresh install is normal`() {
        val ledger = StartupLedger(MapStore())
        assertEquals(StartupLedger.Plan.NORMAL, ledger.noteLaunch())
    }

    @Test
    fun `one interrupted start stays normal - one crash is not a loop`() {
        val store = MapStore()
        // Launch 1 leaves the marker set (the "crash" is simply that
        // noteStartupFinished never ran).
        assertEquals(StartupLedger.Plan.NORMAL, StartupLedger(store).noteLaunch())
        assertEquals(
            "a single crash must never offer safe mode (one-off errors are not loops)",
            StartupLedger.Plan.NORMAL,
            StartupLedger(store).noteLaunch()
        )
    }

    @Test
    fun `two interrupted starts offer safe mode on the third launch`() {
        val store = MapStore()
        StartupLedger(store).noteLaunch()              // crash (no finish)
        StartupLedger(store).noteLaunch()              // crash (no finish)
        assertEquals(
            StartupLedger.Plan.LOOP_SUSPECTED,
            StartupLedger(store).noteLaunch()
        )
        // And the marker persisted the count so a FOURTH launch without the
        // loop door being taken keeps suspecting the loop.
        assertEquals(
            StartupLedger.Plan.LOOP_SUSPECTED,
            StartupLedger(store).noteLaunch()
        )
    }

    @Test
    fun `a finished start clears the counter for the next launch`() {
        val store = MapStore()
        StartupLedger(store).noteLaunch()              // crash
        StartupLedger(store).noteLaunch()              // crash
        StartupLedger(store).noteLaunch()              // LOOP_SUSPECTED, user picks safe mode…
        StartupLedger(store).noteStartupFinished()     // …which boots fine
        assertEquals(false to 0, store.writes.last())
        assertEquals(
            "after a successful (reduced) start the next launch is normal again",
            StartupLedger.Plan.NORMAL,
            StartupLedger(store).noteLaunch()
        )
    }

    @Test
    fun `a corrupt ledger is treated as NO crash - never trap the user`() {
        // The rule: an unreadable counter must never put the app into safe
        // mode, or a one-off disk hiccup would look like a crash loop.
        repeat(4) {
            assertEquals(
                StartupLedger.Plan.NORMAL,
                StartupLedger(CorruptStore()).noteLaunch()
            )
        }
    }

    @Test
    fun `garbage counts are clamped, not trusted`() {
        val store = MapStore().apply { starting = true; count = 987_654 }
        val plan = StartupLedger(store).noteLaunch()
        assertEquals(StartupLedger.Plan.LOOP_SUSPECTED, plan)
        assertTrue("the clamp lives at 1 000", store.count <= 1_000)
    }

    @Test
    fun `safe mode itself writes nothing - the marker is the only write`() {
        // "Repaired by deleting your settings" is what these guards do when
        // nobody checks: noteLaunch writes ONLY the marker cell, even when
        // it suspects a loop — nothing else in the store is touched.
        val store = MapStore()
        repeat(3) { StartupLedger(store).noteLaunch() }
        assertTrue(store.writes.all { (starting, _) -> starting })
        assertEquals(listOf(0, 1, 2), store.writes.map { it.second })
    }

    @Test
    fun `safe mode resolves persisted values while inactive, defaults while active`() {
        // The whole bypass law in one table: nothing about the persisted
        // value is modified; it is simply not APPLIED while active.
        SafeMode.active = false
        assertEquals("persisted", SafeMode.resolve("default", "persisted"))
        SafeMode.activateForSession()
        assertTrue(SafeMode.active)
        assertEquals("default", SafeMode.resolve("default", "persisted"))
        // The flag is process-lifetime: restore it so no other test inherits it.
        SafeMode.active = false
        assertFalse(SafeMode.active)
    }
}
