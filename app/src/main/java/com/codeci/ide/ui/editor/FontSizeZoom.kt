package com.codeci.ide.ui.editor

/**
 * Phase 16 — pinch-to-zoom math for the editor font (the terminal's reactive
 * pinch, in Spck spirit, but on the Compose editor: the gesture accumulates
 * the finger-distance ratio and commits stepped sizes). Pure so CI tests it.
 */
object FontSizeZoom {

    const val MIN_SIZE_SP = 8f
    const val MAX_SIZE_SP = 30f

    /**
     * [current] sp scaled by pinch [zoom], snapped to half-point steps and
     * clamped to the readable range. Degenerate zooms (NaN/0/negative, the
     * fingers-crossed case) return [current] unchanged so the gesture never
     * blanks the font.
     */
    fun applyZoom(current: Float, zoom: Float): Float {
        if (!zoom.isFinite() || zoom <= 0f || !current.isFinite()) return current
        val scaled = current * zoom
        val stepped = ((scaled * 2f) + 0.5f).toLong() / 2f
        return stepped.coerceIn(MIN_SIZE_SP, MAX_SIZE_SP)
    }
}
