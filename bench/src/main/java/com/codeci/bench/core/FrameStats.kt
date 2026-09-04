package com.codeci.bench.core

/**
 * Phase 25.1 — pure reducer over `android.view.FrameMetrics.TOTAL_DURATION`
 * samples (nanoseconds, collected by `harness.FrameCapture`).
 *
 * Pure Kotlin on purpose: the percentile/jank math is host-tested in CI; the
 * device side only collects samples. A frame "misses the 60 fps budget" when
 * its TOTAL_DURATION exceeds 16.667 ms (one missed frame), and is BAD when it
 * exceeds 33.333 ms (two or more missed frames).
 */
data class FrameSummary(
    val frames: Int,
    val p50Ms: Double,
    val p90Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
    val jankyFrames: Int,
    val badFrames: Int
) {
    val jankRatio: Double
        get() = if (frames == 0) 0.0 else jankyFrames.toDouble() / frames

    /** One compact line for the results sheet. */
    fun line(): String =
        "frames=%d p50=%.1fms p90=%.1fms p95=%.1fms p99=%.1fms max=%.1fms jank=%d (%.1f%%) bad=%d"
            .format(frames, p50Ms, p90Ms, p95Ms, p99Ms, maxMs, jankyFrames, jankRatio * 100.0, badFrames)

    companion object {
        val EMPTY = FrameSummary(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0)
    }
}

object FrameStats {

    /** One 60 fps frame: 16.666… ms in nanoseconds. */
    const val FRAME_BUDGET_NS: Long = 16_666_667L

    /** Two missed frames. */
    const val DOUBLE_BUDGET_NS: Long = 33_333_333L

    /** Index into a sorted sample array for percentile [p] in [0,1]. */
    fun percentileIndex(size: Int, p: Double): Int {
        require(size > 0) { "size must be > 0" }
        require(p in 0.0..1.0) { "p must be within [0,1]" }
        return (p * (size - 1)).toInt().coerceIn(0, size - 1)
    }

    fun summarize(totalDurationsNs: LongArray): FrameSummary {
        if (totalDurationsNs.isEmpty()) return FrameSummary.EMPTY
        val sorted = totalDurationsNs.copyOf().also { it.sort() }
        fun ms(ns: Long): Double = ns / 1_000_000.0
        fun pct(p: Double): Double = ms(sorted[percentileIndex(sorted.size, p)])
        var janky = 0
        var bad = 0
        for (ns in sorted) {
            if (ns > FRAME_BUDGET_NS) janky++
            if (ns > DOUBLE_BUDGET_NS) bad++
        }
        return FrameSummary(
            frames = sorted.size,
            p50Ms = pct(0.50),
            p90Ms = pct(0.90),
            p95Ms = pct(0.95),
            p99Ms = pct(0.99),
            maxMs = ms(sorted.last()),
            jankyFrames = janky,
            badFrames = bad
        )
    }

    /** Median of a list of per-rep summaries (median of p95 across reps etc.). */
    fun medianReps(reps: List<FrameSummary>): FrameSummary? {
        if (reps.isEmpty()) return null
        fun medianOf(select: (FrameSummary) -> Double): Double {
            val values = reps.map(select).sorted()
            val mid = values.size / 2
            return if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2.0
        }
        return FrameSummary(
            frames = reps.sumOf { it.frames },
            p50Ms = medianOf { it.p50Ms },
            p90Ms = medianOf { it.p90Ms },
            p95Ms = medianOf { it.p95Ms },
            p99Ms = medianOf { it.p99Ms },
            maxMs = medianOf { it.maxMs },
            jankyFrames = reps.sumOf { it.jankyFrames },
            badFrames = reps.sumOf { it.badFrames }
        )
    }
}
