package com.codeci.bench.keys

import com.codeci.bench.core.FrameStats

/**
 * Phase 28.1 — the spike's measurement primitives, PURE so the math the
 * decision sheet prints is host-tested, exactly like 25.1's FrameStats.
 *
 * The budget law is 25.1's: "key down → glyph committed + rendered ≤ 1 frame
 * p95". We measure the two halves separately:
 *  - [LatencyStats] — key DOWN → commit done (the part the keyboard owns);
 *  - FrameCapture/FrameStats (reused from 25.1) — whole-frame cost while the
 *    burst plays (the part the renderer owns).
 * The go/no-go table needs both p95s under the 16.7 ms frame budget.
 */

data class LatencySummary(
    val count: Int,
    val p50Ms: Double,
    val p90Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
    /** Commits whose DOWN→done exceeded one 60 fps frame. */
    val overFrameBudget: Int
) {
    /** One compact line for the results sheet (same style as FrameSummary.line). */
    fun line(): String = if (count == 0) "no commits" else
        "keys=%d p50=%.2fms p95=%.2fms p99=%.2fms max=%.2fms over1f=%d"
            .format(count, p50Ms, p95Ms, p99Ms, maxMs, overFrameBudget)
}

object LatencyStats {

    /** One frame at 60 fps, in nanoseconds — same constant law as FrameStats. */
    const val FRAME_BUDGET_NS: Long = FrameStats.FRAME_BUDGET_NS

    fun summarize(samplesNs: LongArray): LatencySummary {
        if (samplesNs.isEmpty()) {
            return LatencySummary(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)
        }
        val sorted = samplesNs.copyOf().also { it.sort() }
        fun ms(ns: Long): Double = ns / 1_000_000.0
        fun pct(p: Double): Double = ms(sorted[FrameStats.percentileIndex(sorted.size, p)])
        var over = 0
        for (ns in sorted) if (ns > FRAME_BUDGET_NS) over++
        return LatencySummary(
            count = sorted.size,
            p50Ms = pct(0.50),
            p90Ms = pct(0.90),
            p95Ms = pct(0.95),
            p99Ms = pct(0.99),
            maxMs = ms(sorted.last()),
            overFrameBudget = over
        )
    }
}

/**
 * Fixed-capacity FIFO of DOWN→commit samples. The live UI reads
 * [snapshot] mid-session, so the ring keeps only the recent window (the
 * feel question is "does it STAY instant", not "what happened at minute one").
 */
class KeyLatencyLedger(val capacity: Int = 1024) {

    private val ring = LongArray(capacity)
    private var next = 0
    private var filled = 0

    fun record(elapsedNs: Long) {
        ring[next] = elapsedNs
        next = (next + 1) % capacity
        if (filled < capacity) filled++
    }

    fun clear() {
        next = 0
        filled = 0
    }

    fun size(): Int = filled

    fun snapshot(): LatencySummary =
        LatencyStats.summarize(LongArray(filled) { ring[(next - filled + it + capacity * 2) % capacity] })
}

/** Result of auditing a scripted burst against the editor's tap echo. */
data class TapAudit(
    val expected: Int,
    val landed: Int,
    val dropped: Int,
    val duplicated: Int,
    /**
     * Strict law: every echoed event must pair 1:1 onto the script in order.
     * True when the echo is NOT an ordered subsequence of the script — catches
     * reorders, phantoms, and double-fires alike (a repeated "b" has no
     * partner in the script, so it is also a "swap" — [duplicated] tells you
     * which flavor; `exact` is the one flag the PASS line uses).
     */
    val swapped: Boolean,
    val exact: Boolean
) {
    fun line(): String =
        "tap=%d/%d drop=%d dup=%d swap=%s".format(landed, expected, dropped, duplicated, if (swapped) "YES" else "no")
}

object TapAuditor {

    /**
     * Audit [actual] (what the editor echoed) against [expected] (what the
     * script pressed). Rules:
     *  - every actual item must be an expected item, in expected order
     *    (longest-common-subsequence length == actual size) — otherwise swapped;
     *  - duplicates = actual occurrences beyond the expected multiplicity;
     *  - dropped = expected occurrences not matched.
     */
    fun verify(expected: List<String>, actual: List<String>): TapAudit {
        // Multiset difference for duplicates.
        val pool = HashMap<String, Int>()
        for (e in expected) pool.merge(e, 1) { a, b -> a + b }
        var duplicated = 0
        for (a in actual) {
            val left = pool[a] ?: 0
            if (left == 0) duplicated++ else pool[a] = left - 1
        }
        val lcs = lcsLength(expected, actual)
        val swapped = lcs != actual.size
        val dropped = expected.size - lcs
        return TapAudit(
            expected = expected.size,
            landed = actual.size,
            dropped = dropped,
            duplicated = duplicated,
            swapped = swapped,
            exact = expected == actual
        )
    }

    /** O(n·m) DP on tiny scripts (≤ a few hundred) — fine for a spike. */
    private fun lcsLength(a: List<String>, b: List<String>): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        var prev = IntArray(b.size + 1)
        var curr = IntArray(b.size + 1)
        for (i in 1..a.size) {
            for (j in 1..b.size) {
                curr[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1
                else maxOf(prev[j], curr[j - 1])
            }
            val t = prev; prev = curr; curr = t
            curr.fill(0)
        }
        return prev[b.size]
    }
}

/** What the IME inset did around a run — 28.1's "no IME flicker" probe. */
data class ImeProbeResult(val samples: Int, val maxPx: Int, val everShown: Boolean) {
    fun line(): String =
        if (samples == 0) "ime: no samples (pre-API 30 probe)" else
            "ime: max=%dpx over %d samples %s".format(
                maxPx, samples, if (everShown) "FLICKER" else "never opened"
            )
}

object ImeFlicker {

    /** `true` when any sample saw the soft IME eating layout space. */
    fun analyze(samplesPx: IntArray): ImeProbeResult {
        var max = 0
        for (px in samplesPx) if (px > max) max = px
        return ImeProbeResult(samplesPx.size, max, max > 0)
    }
}
