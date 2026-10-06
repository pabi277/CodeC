package com.codeci.ide.ui.performance

/**
 * Phase 89 (AI Level 12, part 89.2) — one boundary sample of this app's used
 * heap, for the numbers-only readout.
 *
 * It lives here, and not in `ui/ai`, because `ui/ai` carries a standing,
 * level-long guard (Level 3, re-checked at Levels 4, 7, 9 and 10) that forbids
 * `Runtime.getRuntime` in the AI package: the token is the command-execution
 * token (`Runtime.getRuntime().exec`). The readout needs one honest number, not
 * an exception to that guard — so collection lives beside [FrameBudget] and the
 * AI core keeps only the number. Like [FrameBudget], this object owns nothing
 * else: it reads no file, writes nothing, logs nothing and stores nothing (D6).
 */
object HeapProbe {

    /**
     * Used heap at this instant: `totalMemory - freeMemory` of the JVM/ART heap.
     * A single sample at a request boundary — never polled, never a peak. It is
     * a lower bound on what the process holds (native and graphics memory are
     * not counted), which is why the readout says "memory", never "peak memory".
     */
    fun usedHeapBytes(): Long = Runtime.getRuntime().let { runtime ->
        runtime.totalMemory() - runtime.freeMemory()
    }
}
