package com.codeci.bench.core

/**
 * Phase 25.1 candidate C-compose2 — visible-window math.
 *
 * Only the visible lines (plus overscan) are ever laid out; this pure helper
 * decides the rendered range from the scroll state. Host-tested in CI.
 */
object VisibleWindow {

    /** Lines rendered beyond the viewport on each side. */
    const val OVERSCAN = 8

    /**
     * The rendered line range: `[firstVisible - overscan, firstVisible +
     * visibleCount + overscan)` clamped to `[0, lineCount)` and never empty
     * (a viewport showing nothing still renders the first line).
     */
    fun range(firstVisible: Int, visibleCount: Int, overscan: Int = OVERSCAN, lineCount: Int): IntRange {
        require(lineCount > 0) { "lineCount must be > 0" }
        require(overscan >= 0) { "overscan must be >= 0" }
        val first = (firstVisible - overscan).coerceAtLeast(0)
        val last = (firstVisible + visibleCount.coerceAtLeast(1) + overscan).coerceAtMost(lineCount - 1)
        return first..last
    }
}
