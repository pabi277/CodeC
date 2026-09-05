package com.codeci.ide.ui.editor.sora

import android.graphics.Canvas
import io.github.rosemoe.sora.graphics.InlayHintRenderParams
import io.github.rosemoe.sora.graphics.Paint
import io.github.rosemoe.sora.graphics.inlayHint.InlayHintRenderer
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Phase 27.1 — the sora-side ghost text. A [GhostInlayHint] rides sora's
 * inlay-hint lane (point-anchored, auto-shifted on edits by the widget,
 * zero document mutation — G2's "typing never changes" is structural), and
 * this renderer paints it as plain dimmed code text at the FULL text size:
 * no rounded chip background (the stock `TextInlayHintRenderer` look), so it
 * reads as "not real text" (G5).
 *
 * The ghost color is the comment color at 38 % alpha, per the contrast law;
 * themed by the host whenever the editor scheme changes (the renderer reads
 * the field per frame, so a color mutation needs no re-registration).
 */
class GhostInlayHint(line: Int, column: Int, val text: String) :
    InlayHint(line, column, TYPE_NAME) {
    companion object {
        const val TYPE_NAME = "codec.ghost"
    }
}

class GhostHintRenderer(@Volatile var ghostColorArgb: Int) : InlayHintRenderer() {

    private val localPaint = Paint().also { it.isAntiAlias = true }

    override val typeName: String
        get() = GhostInlayHint.TYPE_NAME

    override fun onMeasure(inlayHint: InlayHint, paint: Paint, params: InlayHintRenderParams): Float {
        localPaint.typeface = paint.typeface
        localPaint.textSize = paint.textSize
        return localPaint.measureText((inlayHint as? GhostInlayHint)?.text ?: "")
    }

    override fun onRender(
        inlayHint: InlayHint,
        canvas: Canvas,
        paint: Paint,
        params: InlayHintRenderParams,
        colorScheme: EditorColorScheme,
        measuredWidth: Float
    ) {
        val hint = inlayHint as? GhostInlayHint ?: return
        localPaint.typeface = paint.typeface
        localPaint.textSize = paint.textSize
        localPaint.color = ghostColorArgb
        // The canvas is pre-translated to (hint left, row top); the baseline
        // offset in params is relative to that row top.
        canvas.drawText(hint.text, 0f, params.textBaseline.toFloat(), localPaint)
    }
}
