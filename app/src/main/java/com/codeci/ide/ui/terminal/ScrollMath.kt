package com.codeci.ide.ui.terminal

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Phase 19.2 device round 3 (owner: "not smooth scrolling"): drag scrolling
 * moved in WHOLE-ROW jumps (`(dy / cellH).toInt()`), which reads as notchy
 * next to pixel-smooth touch scrolling. This accumulator keeps the
 * sub-row remainder in pixels so the renderer can translate the grid by a
 * fraction of a row — finger-follower smoothing, Termux-style.
 *
 * Phase 103.2 extends the same policy (no second scroll model):
 *  - [rest] discards the sub-row remainder on finger-lift (Termux
 *    `mScrollRemainder` is reset when the finger leaves the glass), so a
 *    rest position is always whole rows;
 *  - [step] drops the remainder at BOTH rails — the old code only dropped it
 *    when a whole-row target overshot, so dragging "older" in a terminal with
 *    no scrollback kept a negative remainder and drew the blank band the owner
 *    saw on first open;
 *  - [planFling] / [flingOffset] give the release velocity a decaying travel
 *    (clean-room, behaviour spec: Termux `TerminalView` fling);
 *  - [thumb] is the proportional scrollbar geometry.
 *
 * `topRow` semantics: 0 = live screen, negative = scrolled into scrollback.
 * `deltaPx` is web-convention scroll (positive = view newer content).
 */
object ScrollMath {

    data class Result(
        val topRow: Int,
        /** Sub-row remainder in px, always within (-cellH, cellH). */
        val subRowPx: Float
    )

    /**
     * Advance the scroll position by [deltaPx].
     *
     * @param subRowPx the previous sub-row remainder (px)
     * @param cellH row height in px (>= 1)
     * @param topRow the previous row offset (<= 0)
     * @param minTop the oldest reachable row (-scrollbackCount)
     */
    fun step(
        subRowPx: Float,
        deltaPx: Float,
        cellH: Float,
        topRow: Int,
        minTop: Int
    ): Result {
        val h = cellH.coerceAtLeast(1f)
        val floor = minTop.coerceAtMost(0)
        var px = subRowPx + deltaPx
        var row = topRow
        // Absorb whole rows in one go (a fling can jump many rows).
        val whole = (px / h).toInt()
        px -= whole * h
        val target = row + whole
        row = target.coerceIn(floor, 0)
        // Hit a rail — drop the overshoot. Phase 103.2: "at a rail" also means
        // the remainder points PAST the rail with no whole row to absorb it
        // (live edge pulled toward newer, oldest row pushed toward older).
        if (row != target) px = 0f
        if (row == 0 && px > 0f) px = 0f
        if (row == floor && px < 0f) px = 0f
        return Result(row, px)
    }

    /**
     * Phase 103.2: a finger that has lifted discards its sub-row remainder.
     * The offset is already whole rows, so this only clamps to the rails
     * ([minTop]..0) and zeroes the remainder — identity for whole-row rests.
     */
    fun rest(topRow: Int, subRowPx: Float, minTop: Int): Result =
        Result(topRow.coerceIn(minTop.coerceAtMost(0), 0), 0f)

    // ---- fling -------------------------------------------------------------

    /**
     * Velocity decays exponentially with this time constant (s); the travel is
     * `v0 * TAU`. 0.325 s is the scale at which a platform-default fling reads
     * as "a flick, not a drag" — a behaviour choice, not a copied curve.
     */
    const val FLING_TAU_S = 0.325f

    /** Below this release speed (px/s) a lift is a stop, not a fling. */
    const val FLING_MIN_PX_PER_S = 150f

    /** Upper clamp so a wild flick cannot travel thousands of rows. */
    const val FLING_MAX_PX_PER_S = 16_000f

    /** The fling ends when its decayed speed falls below this (px/s). */
    const val FLING_STOP_PX_PER_S = 20f

    /**
     * One planned fling. [totalPx] is signed in web convention (positive = view
     * newer). [durationMs] == 0 means "instant jump" (reduced motion).
     */
    data class Fling(val totalPx: Float, val durationMs: Long)

    /**
     * Plan a fling from a release velocity (px/s, web convention: positive =
     * view newer). Returns null for a slow lift. [animatorScale] is the
     * platform's animator duration scale; 0 means the user turned animations
     * off, and the fling degrades to an instant jump to its resting place.
     */
    fun planFling(velocityPxPerS: Float, animatorScale: Float): Fling? {
        val mag = abs(velocityPxPerS)
        if (mag < FLING_MIN_PX_PER_S || velocityPxPerS.isNaN()) return null
        val v0 = velocityPxPerS.coerceIn(-FLING_MAX_PX_PER_S, FLING_MAX_PX_PER_S)
        val total = v0 * FLING_TAU_S
        if (animatorScale <= 0f) return Fling(total, 0L)
        // Time for the decayed speed to fall to FLING_STOP_PX_PER_S.
        val seconds = FLING_TAU_S * ln(abs(v0) / FLING_STOP_PX_PER_S)
        return Fling(total, (seconds * 1000f).toLong().coerceAtLeast(1L))
    }

    /** Signed travel (px) of [fling] after [elapsedMs]; clamps to [Fling.totalPx] at the end. */
    fun flingOffset(fling: Fling, elapsedMs: Long): Float {
        if (fling.durationMs <= 0L || elapsedMs >= fling.durationMs) return fling.totalPx
        val t = elapsedMs.coerceAtLeast(0L) / 1000f
        return fling.totalPx * (1f - exp(-t / FLING_TAU_S))
    }

    // ---- scrollbar ---------------------------------------------------------

    /** Thumb geometry in track px. */
    data class Thumb(val offsetPx: Float, val lengthPx: Float)

    /**
     * Proportional thumb: its length is the visible fraction of everything
     * (history + live rows), its offset the scroll position across the free
     * track. Null when there is no history to scroll (no thumb is drawn).
     *
     * @param minLengthPx floor so a very deep history still shows a grabbable thumb
     */
    fun thumb(
        trackPx: Float,
        scrollbackCount: Int,
        rows: Int,
        topRow: Int,
        minLengthPx: Float
    ): Thumb? {
        if (scrollbackCount <= 0 || rows <= 0 || trackPx <= 0f) return null
        val total = (scrollbackCount + rows).toFloat()
        val floorLen = minOf(minLengthPx, trackPx)
        val length = (trackPx * rows / total).coerceIn(floorLen, trackPx)
        // 0 = oldest row, 1 = live bottom.
        val fraction = ((topRow + scrollbackCount).toFloat() / scrollbackCount).coerceIn(0f, 1f)
        return Thumb(offsetPx = (trackPx - length) * fraction, lengthPx = length)
    }
}
