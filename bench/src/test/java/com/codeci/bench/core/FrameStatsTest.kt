package com.codeci.bench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameStatsTest {

    @Test
    fun `empty samples produce the empty summary`() {
        val s = FrameStats.summarize(LongArray(0))
        assertEquals(0, s.frames)
        assertEquals(FrameSummary.EMPTY, s)
    }

    @Test
    fun `single sample maps to every percentile`() {
        val s = FrameStats.summarize(longArrayOf(8_333_333L))
        assertEquals(1, s.frames)
        assertEquals(8.333, s.p50Ms, 0.01)
        assertEquals(s.p50Ms, s.p99Ms, 0.0)
        assertEquals(s.maxMs, s.p50Ms, 0.0)
        assertEquals(0, s.jankyFrames)
    }

    @Test
    fun `percentiles interpolate over the sorted array`() {
        // 10 samples: 1..10 ms (in shuffled order — summarize must sort).
        val samples = longArrayOf(5, 2, 9, 1, 7, 3, 10, 4, 6, 8).map { it * 1_000_000L }.toLongArray()
        val s = FrameStats.summarize(samples)
        assertEquals(10, s.frames)
        // nearest-rank on size 10: p50 -> index 4 (5 ms), p90 -> index 8 (9 ms)
        assertEquals(5.0, s.p50Ms, 0.001)
        assertEquals(9.0, s.p90Ms, 0.001)
        assertEquals(10.0, s.maxMs, 0.001)
    }

    @Test
    fun `jank thresholds count one and two missed frames`() {
        val oneMissed = FrameStats.FRAME_BUDGET_NS + 1
        val twoMissed = FrameStats.DOUBLE_BUDGET_NS + 1
        val within = FrameStats.FRAME_BUDGET_NS
        val s = FrameStats.summarize(longArrayOf(within, oneMissed, twoMissed))
        assertEquals(2, s.jankyFrames) // oneMissed + twoMissed
        assertEquals(1, s.badFrames)   // twoMissed only
    }

    @Test
    fun `median across reps takes the middle value`() {
        fun rep(p95: Double) = FrameSummary(
            frames = 100, p50Ms = 4.0, p90Ms = 8.0, p95Ms = p95, p99Ms = 20.0,
            maxMs = 25.0, jankyFrames = 5, badFrames = 1
        )
        val median = FrameStats.medianReps(listOf(rep(10.0), rep(12.0), rep(14.0)))!!
        assertEquals(12.0, median.p95Ms, 0.0)
        assertEquals(300, median.frames)
        assertEquals(15, median.jankyFrames)
    }

    @Test
    fun `median across an even number of reps averages the middle pair`() {
        fun rep(p50: Double) = FrameSummary(
            frames = 10, p50Ms = p50, p90Ms = 1.0, p95Ms = 1.0, p99Ms = 1.0,
            maxMs = 1.0, jankyFrames = 0, badFrames = 0
        )
        val median = FrameStats.medianReps(listOf(rep(6.0), rep(8.0)))!!
        assertEquals(7.0, median.p50Ms, 0.0)
    }

    @Test
    fun `summary line is human readable`() {
        val line = FrameStats.summarize(longArrayOf(20_000_000L)).line()
        assertTrue(line.contains("frames=1"))
        assertTrue(line.contains("jank=1"))
    }

    @Test
    fun `percentile index is clamped`() {
        assertEquals(0, FrameStats.percentileIndex(1, 0.95))
        assertEquals(9, FrameStats.percentileIndex(10, 1.0))
        assertEquals(0, FrameStats.percentileIndex(10, 0.0))
    }
}
