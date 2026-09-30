package com.codeci.ide.ui.ai

import java.util.Locale

/** Which side of the code area the bubble rests against. */
enum class AiBubbleEdge { LEFT, RIGHT }

/**
 * Where the bubble lives, **independent of pixel sizes** so it survives
 * rotation, a different screen and the keyboard coming and going: a side and
 * a fraction of the code area's height (of the bubble's CENTRE).
 */
data class AiBubblePosition(
    val edge: AiBubbleEdge = AiBubbleEdge.RIGHT,
    val yFraction: Float = AiBubblePolicy.DEFAULT_Y_FRACTION
) {
    /** "Move to other side" — same height, opposite edge. */
    fun otherSide(): AiBubblePosition =
        copy(edge = if (edge == AiBubbleEdge.LEFT) AiBubbleEdge.RIGHT else AiBubbleEdge.LEFT)
}

/**
 * Phase 77.1 — the floating AI button's geometry and rules, as pure code
 * (rule.md §4.4; host-tested by `AiBubblePolicyTest`).
 *
 * Owner (2026-10-01): *"chat will be a floating botton on the screen that can
 * be moved anywhere and one click to open full chat"*, answer to Q2 *"Yes i
 * think"* (remember the position). "Anywhere" is honoured as: any height, and
 * either side — a bubble parked mid-screen would sit on the code, so a
 * released drag snaps to the nearer edge.
 *
 * All coordinates are in the **code area** (the editor's own box between the
 * tab strip and the coding row): the area already ends above the keyboard and
 * the bottom bar, so those can never be covered; [offsetPx] still takes the
 * keyboard's top as a second guard.
 */
object AiBubblePolicy {
    /** The drawn circle. */
    const val VISUAL_DP = 40f

    /** The touch target (CodecTokens.MIN_TOUCH) — the drawn circle sits centred in it. */
    const val TOUCH_DP = 48f

    /** Gap between the bubble and the side edge (CodecTokens.Space.S). */
    const val EDGE_MARGIN_DP = 8f

    const val DEFAULT_Y_FRACTION = 0.62f
    const val MIN_Y_FRACTION = 0.05f
    const val MAX_Y_FRACTION = 0.95f

    val DEFAULT = AiBubblePosition()

    /**
     * The bubble shows only when the helper is usable (a project is open AND a
     * key is saved — the no-nag law: nobody who never set AI up ever sees it),
     * the owner's "Show AI button" choice is on, and the chat sheet is not
     * already open (the sheet IS the chat then).
     */
    fun visible(availability: AiAvailability, showSetting: Boolean, sheetOpen: Boolean): Boolean =
        availability == AiAvailability.READY && showSetting && !sheetOpen

    /**
     * Release after a drag. [xPx]/[yPx] is where the bubble's CENTRE was let
     * go, in code-area pixels. The nearer side wins (the exact middle goes
     * RIGHT, the thumb's side for most people); the height becomes a fraction
     * clamped away from the very top/bottom. Garbage in → the default out.
     */
    fun snap(xPx: Float, yPx: Float, areaW: Float, areaH: Float): AiBubblePosition {
        if (!xPx.isFinite() || !yPx.isFinite() || !areaW.isFinite() || !areaH.isFinite() || areaW <= 0f || areaH <= 0f) {
            return DEFAULT
        }
        val edge = if (xPx < areaW / 2f) AiBubbleEdge.LEFT else AiBubbleEdge.RIGHT
        return AiBubblePosition(edge, clampFraction(yPx / areaH))
    }

    /**
     * The bubble's **top-left** corner now, in code-area pixels. [bubblePx] is
     * the square being placed (the touch target). The vertical position
     * follows [AiBubblePosition.yFraction] of the area, then is pushed so the
     * whole square lies inside `0 … min(areaH, imeTopPx)` — never under the
     * keyboard, never outside a tiny landscape area (the top wins when even
     * one square does not fit).
     */
    fun offsetPx(
        p: AiBubblePosition,
        areaW: Float,
        areaH: Float,
        imeTopPx: Float?,
        bubblePx: Float,
        marginPx: Float
    ): Pair<Float, Float> {
        val w = areaW.coerceAtLeast(0f)
        val h = areaH.coerceAtLeast(0f)
        val x = when (p.edge) {
            AiBubbleEdge.LEFT -> marginPx
            AiBubbleEdge.RIGHT -> w - bubblePx - marginPx
        }.coerceIn(0f, (w - bubblePx).coerceAtLeast(0f))
        val floor = (imeTopPx?.takeIf { it.isFinite() } ?: h).coerceIn(0f, h)
        val top = clampFraction(p.yFraction) * h - bubblePx / 2f
        val y = top.coerceIn(0f, (floor - bubblePx).coerceAtLeast(0f))
        return x to y
    }

    /** "RIGHT:0.620" — stable, locale-independent, human-readable in the file. */
    fun encode(p: AiBubblePosition): String =
        p.edge.name + ":" + String.format(Locale.ROOT, "%.3f", clampFraction(p.yFraction))

    /** Garbage, null, empty or an unknown side → [DEFAULT]; a finite height out of range is clamped. */
    fun decode(s: String?): AiBubblePosition {
        if (s.isNullOrBlank()) return DEFAULT
        val parts = s.trim().split(':')
        if (parts.size != 2) return DEFAULT
        val edge = AiBubbleEdge.values().firstOrNull { it.name == parts[0] } ?: return DEFAULT
        val y = parts[1].toFloatOrNull()?.takeIf { it.isFinite() } ?: return DEFAULT
        return AiBubblePosition(edge, clampFraction(y))
    }

    private fun clampFraction(f: Float): Float =
        if (f.isFinite()) f.coerceIn(MIN_Y_FRACTION, MAX_Y_FRACTION) else DEFAULT_Y_FRACTION
}
