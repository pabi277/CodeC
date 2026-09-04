package com.codeci.bench.harness

import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window

/**
 * Phase 25.1 — frame capture via the PLATFORM FrameMetrics API
 * (`Window.addOnFrameMetricsAvailableListener`, API 24+, = our minSdk).
 *
 * The spec named `FrameMetricsAggregator`; that androidx class was removed
 * from `metrics-performance` in favor of JankStats, so the bench attaches to
 * the platform listener directly and copies `TOTAL_DURATION` per frame
 * (the object is recycled by the framework — copy immediately, per the
 * contract) into a plain list. All reduction happens in pure
 * `core.FrameStats`, host-tested.
 */
class FrameCapture(private val window: Window) {

    private val samples = mutableListOf<Long>()
    private val handler = Handler(Looper.getMainLooper())

    private val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
        synchronized(samples) {
            samples += frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
        }
    }

    fun start() {
        synchronized(samples) { samples.clear() }
        window.addOnFrameMetricsAvailableListener(listener, handler)
    }

    /** Stops capture and returns the TOTAL_DURATION samples in nanoseconds. */
    fun stop(): LongArray {
        window.removeOnFrameMetricsAvailableListener(listener)
        return synchronized(samples) { samples.toLongArray() }
    }
}
