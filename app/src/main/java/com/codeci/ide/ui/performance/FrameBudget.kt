package com.codeci.ide.ui.performance

/** The result of comparing one rendered frame with the 60 Hz budget. */
enum class FrameVerdict {
    SMOOTH,
    SLOW,
    JANK,
}

/**
 * A shipping-build-free statement of the frame budget. The bench APK owns
 * collection; this pure policy owns the words used in its report.
 */
object FrameBudget {
    const val TARGET_MS = 16L
    const val JANK_MS = 32L

    fun verdictFor(frameMs: Long): FrameVerdict = when {
        frameMs < TARGET_MS -> FrameVerdict.SMOOTH
        frameMs < JANK_MS -> FrameVerdict.SLOW
        else -> FrameVerdict.JANK
    }

    /** Percentage of captured frames that missed at least two 60 Hz frames. */
    fun jankPercent(frames: List<Long>): Int {
        if (frames.isEmpty()) return 0
        return (frames.count { it >= JANK_MS } * 100) / frames.size
    }
}

enum class PaintVerdict {
    INSTANT,
    OK,
    SLOW,
}

object SpeedVerdict {
    const val INSTANT_LIMIT_MS = 500L
    const val OK_LIMIT_MS = 1_500L

    /**
     * Negative and absurd values are never allowed to look "instant". They
     * are reported as slow until the device log has been corrected.
     */
    fun firstPaintVerdict(ms: Long): PaintVerdict = when {
        ms in 0 until INSTANT_LIMIT_MS -> PaintVerdict.INSTANT
        ms in INSTANT_LIMIT_MS until OK_LIMIT_MS -> PaintVerdict.OK
        else -> PaintVerdict.SLOW
    }
}
