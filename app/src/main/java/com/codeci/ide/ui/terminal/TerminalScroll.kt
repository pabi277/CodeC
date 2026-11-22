package com.codeci.ide.ui.terminal

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sign

/**
 * Phase 103.2 — scroll physics: rest position, fling, scrollbar.
 *
 * **The owner's bug (2026-10-14):** *"after open the app first time then when
 * i scroll up then i saw empty space in the terminal"*. The cause was in the
 * view, not the buffer: `ScrollMath.step` keeps the sub-row remainder so the
 * grid can follow the finger pixel-smoothly, and the finger-lift path never
 * touched it — so a fresh session could rest displaced by up to a full row,
 * with the vacated band drawn above the first row. Termux discards its
 * `mScrollRemainder` when the finger leaves the glass (read in the
 * 2026-10-14 audit; behaviour only), and the same rule is written here as
 * [rest], pure and testable.
 *
 * Two more pieces belong with it because they were equally absent:
 *
 * - **[flingPx]** — a velocity model with the platform's exponential decay.
 *   The view feeds it a velocity and runs the returned pixel steps through
 *   the existing frame pump; the math is here so it is testable and so a
 *   clamp can never be off by one.
 * - **[Scrollbar]** — the thumb's geometry from offsets alone. A scrollbar is
 *   a *picture of the buffer*, so it is computed, not hand-tuned: at the top
 *   the thumb starts at 0, at the bottom it ends flush, and a buffer that fits
 *   the screen has no travel (the view hides the thumb).
 *
 * Every function in this file is total over its inputs (a zero cell height,
 * a negative scrollback, a nonsensical row count all coerce) because it runs
 * against live geometry on every finger frame.
 */
object TerminalScroll {

    /**
     * Finger-up: the remaining fraction of a row is discarded.
     *
     * Rounding (not truncating) at rest is what makes the canvas and the
     * offset agree: after this call `subRowPx == 0`, so a resting terminal is
     * always translated by exactly zero and the band cannot exist. The
     * `Round` result reports the snap so a caller that must keep the *cell*
     * under the finger (a selection drag) can account for it.
     *
     * @param subRowPx the accumulated remainder, in px, within (−cellH, cellH)
     * @param cellH row height in px
     * @param topRow the row offset (0 = live, negative = history)
     * @param minTop the oldest reachable row (≤ 0)
     */
    data class Rest(val topRow: Int, val snappedRows: Int)

    fun rest(subRowPx: Float, cellH: Float, topRow: Int, minTop: Int): Rest {
        // A remainder is a FRACTION of one row by definition (ScrollMath keeps
        // |subRowPx| < cellH), so a lift never changes which row the view is
        // on: it only discards the fraction. Clamping is defensive, for a
        // snapshot that shrank under a scrolled-up view.
        val row = topRow.coerceIn(minTop.coerceAtMost(0), 0)
        return Rest(row, row - topRow)
    }

    // ---- fling -----------------------------------------------------------

    /**
     * The platform's scroller decay: the classic Android `ViewConfiguration`
     * pair, expressed per millisecond. It is a *model*, not a copy of code:
     * `distance = |v| / (1 − decay)`, sampled at [FRAME_MS] intervals with the
     * same geometric falloff, which is what makes a fling feel identical to
     * every other scrollable surface on the phone.
     */
    const val DECAY_PER_MS = 0.994f
    const val FRAME_MS = 16L

    /** The tap below this is not a fling at all (ViewConfiguration's floor). */
    const val MIN_VELOCITY_PX_PER_MS = 0.05f

    /**
     * The whole fling, in pixel steps, one per frame.
     *
     * The steps always carry the sign of [velocityPxPerMs] (positive = toward
     * newer content, the web convention [ScrollMath] uses), are bounded by
     * [maxPx] so a wild velocity cannot outrun the scrollback, and are cut off
     * at [MAX_FRAMES] — a fling that never ends is a bug, not inertia.
     */
    fun flingPx(velocityPxPerMs: Float, maxPx: Float): List<Float> {
        if (!velocityPxPerMs.isFinite()) return emptyList()
        val v0 = velocityPxPerMs.coerceIn(-MAX_VELOCITY, MAX_VELOCITY)
        if (abs(v0) < MIN_VELOCITY_PX_PER_MS) return emptyList()
        val cap = abs(maxPx.coerceAtLeast(0f))
        val steps = ArrayList<Float>()
        var v = v0
        var travelled = 0f
        var frames = 0
        while (abs(v) >= MIN_VELOCITY_PX_PER_MS && frames < MAX_FRAMES && travelled < cap) {
            var step = v * FRAME_MS
            if (travelled + abs(step) > cap) {
                step = sign(step) * (cap - travelled)
            }
            if (step != 0f) steps.add(step)
            travelled += abs(step)
            v *= DECAY_PER_FRAME
            frames++
        }
        return steps
    }

    /** decay^FRAME — the per-frame factor the sampler uses (no `kotlin.math.pow` for Floats). */
    private val DECAY_PER_FRAME: Float = run {
        var f = 1f
        repeat(FRAME_MS.toInt()) { f *= DECAY_PER_MS }
        f
    }

    /** A fling cannot exceed this (px/ms): ~3× a decisive swipe on a phone. */
    const val MAX_VELOCITY = 12f

    /** Half a second at 60 fps: every real fling has stopped well before it. */
    const val MAX_FRAMES = 40

    // ---- scrollbar -------------------------------------------------------

    /**
     * Thumb geometry, in px, for a viewport [viewH] tall.
     *
     * @param topRow current offset (0 = live, negative = history)
     * @param lines visible rows
     * @param scrollback stored history rows (≥ 0)
     * @param viewH the viewport's height in px
     */
    data class Scrollbar(
        val offsetPx: Float,
        val lengthPx: Float,
        /** False = the buffer fits: draw nothing (or a full-height track, per the design). */
        val hasTravel: Boolean,
        /** Fraction of the track the thumb's top sits at (0 = oldest, 1 = live). */
        val position: Float,
        /** Fraction of the whole buffer visible (the thumb's length / track). */
        val fraction: Float
    ) {
        companion object {
            val NONE = Scrollbar(0f, 0f, hasTravel = false, position = 1f, fraction = 1f)
        }
    }

    /**
     * The thumb: proportional to the scrollback depth, flush at both ends.
     *
     * The mapping is the honest one: the *track* represents the whole buffer
     * (scrollback + screen), the thumb its visible window. With no history the
     * window is the whole buffer and there is nothing to travel — which is the
     * state the owner saw as an empty strip and the reason [MIN_THUMB_PX] only
     * applies once travel exists.
     */
    fun scrollbar(topRow: Int, lines: Int, scrollback: Int, viewH: Float): Scrollbar {
        val rows = lines.coerceAtLeast(1)
        val history = scrollback.coerceAtLeast(0)
        val total = history + rows
        if (history == 0 || viewH <= 0f || total <= rows) return Scrollbar.NONE
        val fraction = (rows.toFloat() / total).coerceIn(MIN_FRACTION, 1f)
        val track = viewH.coerceAtLeast(0f)
        val length = (track * fraction).coerceAtLeast(kotlin.math.min(MIN_THUMB_PX, track))
        val travel = (track - length).coerceAtLeast(0f)
        // topRow 0 = live = the bottom; -history = oldest = the top.
        val position = (1f + topRow.toFloat() / history).coerceIn(0f, 1f)
        return Scrollbar(
            offsetPx = travel * position,
            lengthPx = length,
            hasTravel = travel > 0f,
            position = position,
            fraction = fraction
        )
    }

    /** A thumb is never smaller than this, so a deep buffer still shows one. */
    const val MIN_THUMB_PX = 24f

    /** …but never smaller than this fraction of the track either. */
    const val MIN_FRACTION = 0.06f

        /**
     * Where a finger on the scrollbar drags the view to.
     *
     * The mapping is linear on the TRACK, in the same units the thumb is drawn
     * in: [y] = 0 is the oldest row (−scrollback), [y] = [viewH] is live (0).
     * A thumb's own length does NOT shorten this — the thumb is a picture of
     * the window, and the finger is pointing at a position, not dragging the
     * thumb's top edge. (An earlier version subtracted the thumb, which made
     * the last `thumb` pixels of track unreachable.)
     *
     * @param y px from the top of the track
     * @param viewH track height
     * @param lines visible rows
     * @param scrollback history rows
     * @return the new topRow (0 = live, −scrollback = oldest)
     */
    fun rowForThumb(y: Float, viewH: Float, lines: Int, scrollback: Int): Int {
        val history = scrollback.coerceAtLeast(0)
        if (history == 0 || viewH <= 0f) return 0
        val position = (y / viewH).coerceIn(0f, 1f)
        val rows = ((1f - position) * history).roundToLong().toInt()
        // The negation happens BEFORE the clamp on purpose. `-rows.roundToLong()
        // .toInt().coerceIn(-history, 0)` parses as `-(… coerceIn …)` — the
        // positive count is clamped to 0 and then negated, so every y mapped
        // to live. Caught by ScrollRestTest, kept as a comment because it is
        // the kind of one-token bug a reader will otherwise re-introduce.
        return (-rows).coerceIn(-history, 0)
    }
}

/**
 * Reduced motion: when the platform's animator scale is zero the fling
 * degrades to one instant jump, exactly as `CodecMotion` treats decoration.
 * Kept pure (the view passes `ValueAnimator.getDurationScale()`), so the
 * degradation is testable without a phone.
 */
object ScrollMotionPolicy {
    fun flingSteps(velocityPxPerMs: Float, maxPx: Float, animatorScale: Float): List<Float> {
        if (animatorScale <= 0f) {
            val instant = TerminalScroll.flingPx(velocityPxPerMs, maxPx).sum()
            return if (instant == 0f) emptyList() else listOf(instant)
        }
        return TerminalScroll.flingPx(velocityPxPerMs, maxPx)
    }
}
