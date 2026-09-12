package com.codeci.ide

import com.codeci.ide.ui.terminal.SetupLedger
import com.codeci.ide.ui.terminal.SetupPhase
import com.codeci.ide.ui.terminal.SetupResume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 44.2 — the install ledger (spec:
 * docs/chat-phase44/PART_44_2_ATOMIC_SETUP.md §1).
 *
 * The kill matrix is pinned here as a table instead of arriving as a field bug
 * report: [SetupLedger.resumePlan] is pure, so "what does a leftover record
 * MEAN on boot" is answerable on the host JVM.
 */
class SetupLedgerTest {

    /** Synchronous, in-memory, and it counts writes (the durability contract). */
    private class FakeStore(
        var phase: String? = null,
        var release: String? = null,
        var startedAt: Long? = null,
        var throwOnRead: Boolean = false
    ) : SetupLedger.Store {
        var writes = 0
        var clears = 0

        override fun readPhase(): String? {
            if (throwOnRead) throw IllegalStateException("prefs unreadable")
            return phase
        }

        override fun readRelease(): String? {
            if (throwOnRead) throw IllegalStateException("prefs unreadable")
            return release
        }

        override fun readStartedAt(): Long? {
            if (throwOnRead) throw IllegalStateException("prefs unreadable")
            return startedAt
        }

        override fun write(phase: String, release: String, startedAt: Long) {
            // The real edge writes with SharedPreferences.commit(): the value
            // is on disk when write() returns. The fake models exactly that —
            // synchronously visible, no batching.
            writes++
            this.phase = phase
            this.release = release
            this.startedAt = startedAt
        }

        override fun clear() {
            clears++
            phase = null
            release = null
            startedAt = null
        }
    }

    @Test
    fun `a fresh ledger reads as idle with nothing recorded`() {
        val store = FakeStore()
        val ledger = SetupLedger(store)
        val record = ledger.read()
        assertEquals(SetupPhase.IDLE, record.phase)
        assertEquals("", record.release)
        assertEquals(0L, record.startedAt)
        assertFalse(record.inFlight)
    }

    @Test
    fun `note writes synchronously and is readable straight back`() {
        val store = FakeStore()
        val ledger = SetupLedger(store)
        ledger.note(SetupPhase.DOWNLOADING, "userland-v2-dev", nowMs = 1_000L)
        assertEquals("the marker must be durable before the risky step", 1, store.writes)
        val record = ledger.read()
        assertEquals(SetupPhase.DOWNLOADING, record.phase)
        assertEquals("userland-v2-dev", record.release)
        assertEquals(1_000L, record.startedAt)
        assertTrue(record.inFlight)
    }

    @Test
    fun `the same phase keeps its start time, a new phase resets it`() {
        val store = FakeStore()
        val ledger = SetupLedger(store)
        ledger.note(SetupPhase.DOWNLOADING, "r", nowMs = 1_000L)
        ledger.note(SetupPhase.DOWNLOADING, "r", nowMs = 5_000L)
        assertEquals(1_000L, ledger.read().startedAt)
        ledger.note(SetupPhase.EXTRACTING, "r", nowMs = 6_000L)
        assertEquals(SetupPhase.EXTRACTING, ledger.read().phase)
        assertEquals(6_000L, ledger.read().startedAt)
        ledger.note(SetupPhase.SWAPPING, "r", nowMs = 7_000L)
        assertTrue(ledger.read().inFlight)
        ledger.note(SetupPhase.DONE, "r", nowMs = 8_000L)
        assertFalse(ledger.read().inFlight)
    }

    @Test
    fun `clear returns the ledger to idle`() {
        val store = FakeStore()
        val ledger = SetupLedger(store)
        ledger.note(SetupPhase.SWAPPING, "r", nowMs = 1L)
        ledger.clear()
        assertEquals(1, store.clears)
        assertEquals(SetupPhase.IDLE, ledger.read().phase)
    }

    @Test
    fun `a corrupt or unreadable store degrades to idle, never to a crash`() {
        val corrupt = SetupLedger(FakeStore(phase = "NOT_A_PHASE"))
        assertEquals(SetupPhase.IDLE, corrupt.read().phase)

        val throwing = SetupLedger(FakeStore(throwOnRead = true))
        assertEquals(SetupPhase.IDLE, throwing.read().phase)
        // Writing into a broken store must not take the app down either.
        throwing.note(SetupPhase.DOWNLOADING, "r")
        throwing.clear()
    }

    // ---- the kill matrix ----------------------------------------------------

    @Test
    fun `idle and done with nothing left over mean nothing to do`() {
        val ledger = SetupLedger(FakeStore())
        assertEquals(
            SetupResume.Nothing,
            ledger.resumePlan(ledger.read(), prefixExists = true, oldDirs = emptyList())
        )
        val done = SetupLedger(FakeStore(phase = SetupPhase.DONE.name, release = "r", startedAt = 1L))
        assertEquals(
            SetupResume.Nothing,
            done.resumePlan(done.read(), prefixExists = true, oldDirs = emptyList())
        )
    }

    @Test
    fun `a finished install with orphans left behind is a sweep`() {
        val ledger = SetupLedger(FakeStore(phase = SetupPhase.DONE.name, startedAt = 1L))
        val orphans = listOf("usr.old-100", "usr.old-200")
        assertEquals(
            SetupResume.SweepOnly(orphans),
            ledger.resumePlan(ledger.read(), prefixExists = true, oldDirs = orphans)
        )
        assertEquals(
            SetupResume.SweepOnly(orphans),
            ledger.resumePlan(
                SetupLedger(FakeStore()).read(),
                prefixExists = true,
                oldDirs = orphans
            )
        )
    }

    @Test
    fun `killed mid-download means resume the download`() {
        val ledger = SetupLedger(FakeStore(phase = SetupPhase.DOWNLOADING.name, startedAt = 1L))
        assertEquals(
            SetupResume.RetryDownload,
            ledger.resumePlan(ledger.read(), prefixExists = false, oldDirs = emptyList())
        )
    }

    @Test
    fun `killed mid-extract means re-extract, and the live prefix is fine`() {
        val ledger = SetupLedger(FakeStore(phase = SetupPhase.EXTRACTING.name, startedAt = 1L))
        assertEquals(
            SetupResume.Reextract,
            ledger.resumePlan(ledger.read(), prefixExists = true, oldDirs = emptyList())
        )
    }

    @Test
    fun `killed between the two renames restores the newest old prefix`() {
        val ledger = SetupLedger(FakeStore(phase = SetupPhase.SWAPPING.name, startedAt = 1L))
        val resume = ledger.resumePlan(
            ledger.read(),
            prefixExists = false,
            oldDirs = listOf("usr.old-100", "usr.old-900", "usr.old-500")
        )
        assertEquals(SetupResume.RestoreOld("usr.old-900"), resume)
    }

    @Test
    fun `killed mid-swap with nothing to restore falls back to a fresh download`() {
        val ledger = SetupLedger(FakeStore(phase = SetupPhase.SWAPPING.name, startedAt = 1L))
        assertEquals(
            SetupResume.RetryDownload,
            ledger.resumePlan(ledger.read(), prefixExists = false, oldDirs = emptyList())
        )
    }

    @Test
    fun `killed after the swap landed only needs the old tree swept`() {
        val ledger = SetupLedger(FakeStore(phase = SetupPhase.SWAPPING.name, startedAt = 1L))
        assertEquals(
            SetupResume.SweepOnly(listOf("usr.old-100")),
            ledger.resumePlan(ledger.read(), prefixExists = true, oldDirs = listOf("usr.old-100"))
        )
        assertEquals(
            SetupResume.Nothing,
            ledger.resumePlan(ledger.read(), prefixExists = true, oldDirs = emptyList())
        )
    }

    @Test
    fun `the repair messages are honest and only exist when something happened`() {
        val ledger = SetupLedger(FakeStore())
        assertTrue(
            ledger.resumeMessage(SetupResume.RestoreOld("usr.old-1"))!!
                .contains("restored your Linux tools")
        )
        assertTrue(
            ledger.resumeMessage(SetupResume.RetryDownload)!!.contains("needs the network once")
        )
        assertTrue(ledger.resumeMessage(SetupResume.Reextract)!!.contains("Finishing setup"))
        assertNull(ledger.resumeMessage(SetupResume.SweepOnly(listOf("usr.old-1"))))
        assertNull(ledger.resumeMessage(SetupResume.Nothing))
    }
}
