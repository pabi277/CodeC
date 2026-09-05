package com.codeci.bench.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28.1 — the spike's measurement math: percentile/over-budget latency
 * summaries, the ring ledger that feeds the live line, the tap-echo auditor
 * that catches dropped/swapped/duplicated key events, and the IME-flicker
 * reducer. The device side only collects; everything the go/no-go table
 * prints is decided here, in CI.
 */
class KeysMetricsTest {

    // ---- LatencyStats ------------------------------------------------------

    @Test fun `empty latency summarizes to zeros`() {
        val s = LatencyStats.summarize(LongArray(0))
        assertEquals(0, s.count)
        assertEquals(0.0, s.p95Ms, 0.0001)
    }

    @Test fun `percentiles come from the sorted samples`() {
        // 100 samples, 0..99 ms in ns. p50 → index 49 (nearest-rank like FrameStats),
        // max → 99 ms.
        val s = LatencyStats.summarize(LongArray(100) { it * 1_000_000L })
        assertEquals(100, s.count)
        assertEquals(49.0, s.p50Ms, 0.0001)
        assertEquals(99.0, s.maxMs, 0.0001)
    }

    @Test fun `over-frame-budget counts commits above one 60fps frame`() {
        val justUnder = LatencyStats.FRAME_BUDGET_NS - 1
        val justOver = LatencyStats.FRAME_BUDGET_NS + 1
        val s = LatencyStats.summarize(longArrayOf(justUnder, justOver, justOver, justUnder))
        assertEquals(2, s.overFrameBudget)
    }

    @Test fun `latency line reports keys count and over1f`() {
        val s = LatencyStats.summarize(longArrayOf(1_000_000L, 2_000_000L, 40_000_000L))
        assertTrue(s.line().startsWith("keys=3 "))
        assertTrue(s.line().contains("over1f=1"))
    }

    // ---- KeyLatencyLedger ---------------------------------------------------

    @Test fun `ledger snapshot summarizes what was recorded`() {
        val ledger = KeyLatencyLedger(capacity = 8)
        for (i in 1..5) ledger.record(i * 1_000_000L)
        val s = ledger.snapshot()
        assertEquals(5, s.count)
        assertEquals(5.0, s.maxMs, 0.0001)
    }

    @Test fun `ledger is a ring - oldest samples age out`() {
        val ledger = KeyLatencyLedger(capacity = 4)
        for (i in 1..6) ledger.record(i * 1_000_000L) // window keeps 3, 4, 5, 6 ms
        val s = ledger.snapshot()
        assertEquals(4, s.count)
        assertEquals(6.0, s.maxMs, 0.0001)
        // sorted [3,4,5,6]: p50 → index floor(0.5·3)=1 → 4 ms; p99 → index 2 → 5 ms.
        assertEquals(4.0, s.p50Ms, 0.0001)
        assertEquals(5.0, s.p99Ms, 0.0001)
    }

    @Test fun `ledger clear empties the window`() {
        val ledger = KeyLatencyLedger()
        ledger.record(5_000_000L)
        ledger.clear()
        assertEquals(0, ledger.snapshot().count)
    }

    // ---- TapAuditor ----------------------------------------------------------

    private fun chars(vararg s: String) = s.toList()

    @Test fun `exact run audits clean`() {
        val a = TapAuditor.verify(chars("a", "b", "c"), chars("a", "b", "c"))
        assertTrue(a.exact)
        assertEquals(0, a.dropped)
        assertEquals(0, a.duplicated)
        assertFalse(a.swapped)
    }

    @Test fun `a dropped key is counted and still order-clean`() {
        val a = TapAuditor.verify(chars("a", "b", "c"), chars("a", "c"))
        assertFalse(a.exact)
        assertEquals(1, a.dropped)
        assertFalse("a pure drop is not a swap", a.swapped)
    }

    @Test fun `a swapped arrival is flagged`() {
        // 'c' before 'b' — the strict subsequence law breaks. The unmatched
        // "b" counts as one drop (law: dropped = expected - LCS), and the
        // echo itself is complete; it is `swapped` that carries the signal.
        val a = TapAuditor.verify(chars("a", "b", "c"), chars("a", "c", "b"))
        assertEquals(1, a.dropped)
        assertTrue(a.swapped)
    }

    @Test fun `a double-fired cap counts as duplicate and breaks the subsequence`() {
        val a = TapAuditor.verify(chars("a", "b"), chars("a", "b", "b"))
        assertEquals(1, a.duplicated)
        assertEquals(0, a.dropped)
        // Strict law: the extra "b" has no partner in the script, so the echo
        // stops being a subsequence — `duplicated` says why (see TapAudit doc).
        assertTrue(a.swapped)
        assertEquals("tap=3/2 drop=0 dup=1 swap=YES", a.line())
    }

    @Test fun `phantom character not in expected is duplicate and swapped`() {
        val a = TapAuditor.verify(chars("a"), chars("a", "z"))
        assertEquals(1, a.duplicated)
        assertTrue(a.swapped)
    }

    // ---- ImeFlicker ----------------------------------------------------------

    @Test fun `all-zero inset means the IME never opened`() {
        val r = ImeFlicker.analyze(intArrayOf(0, 0, 0, 0))
        assertFalse(r.everShown)
        assertEquals(0, r.maxPx)
        assertTrue(r.line().contains("never opened"))
    }

    @Test fun `one nonzero sample is flicker`() {
        val r = ImeFlicker.analyze(intArrayOf(0, 720, 0))
        assertTrue(r.everShown)
        assertEquals(720, r.maxPx)
        assertTrue(r.line().contains("FLICKER"))
    }

    @Test fun `no samples is reported as the pre-api-30 n-a case`() {
        val r = ImeFlicker.analyze(IntArray(0))
        assertEquals(0, r.samples)
        assertTrue(r.line().contains("no samples"))
    }
}
