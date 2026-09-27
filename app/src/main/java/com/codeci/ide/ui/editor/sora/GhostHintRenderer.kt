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
 * zero document mutation — G2's "typing never changes" is structural).
 *
 * Phase 69.2 — the LOOK changed because the owner's report says the 27.1 bet
 * was wrong (2026-09-28, verbatim): *"Sometimes the ghost suggestions text are
 * way too real i think as i wrote the wrong word then about a second it
 * vanished the ghost suggestions fix it"*, and the chosen answer is *"A — Both:
 * unmistakable look + stop it vanishing (recommended)"*.
 *
 * 27.1 painted plain dimmed code text at the full text size and argued that
 * "plain text reads as not real text". Measured on the phone's real themes
 * that text sits at 1.8:1-2.0:1 against the editor background — faint, but
 * indistinguishable in SHAPE from something the owner typed, which is exactly
 * what confused them. Brightness was never the missing cue; a container was.
 * So the hint is drawn inside the suggestion box sora's own inlay hints use
 * ([io.github.rosemoe.sora.graphics.inlayHint.TextInlayHintRenderer]'s rounded
 * background, same geometry family), while the text keeps the G5 colour law:
 * the theme's comment colour at 38 % alpha, now pinned by GhostContrastTest
 * from both ends (visible on the phone, always far below real code text).
 *
 * Both colours are themed by the host whenever the editor scheme changes (the
 * renderer reads the fields per frame, so a colour mutation needs no
 * re-registration).
 */
class GhostInlayHint(line: Int, column: Int, val text: String) :
    InlayHint(line, column, TYPE_NAME) {
    companion object {
        const val TYPE_NAME = "codec.ghost"
    }
}

class GhostHintRenderer(
    @Volatile var ghostColorArgb: Int,
    @Volatile var chipColorArgb: Int
) : InlayHintRenderer() {

    private val localPaint = Paint().also { it.isAntiAlias = true }

    override val typeName: String
        get() = GhostInlayHint.TYPE_NAME

    override fun onMeasure(inlayHint: InlayHint, paint: Paint, params: InlayHintRenderParams): Float {
        localPaint.typeface = paint.typeface
        localPaint.textSize = paint.textSize
        // Half a space of box on each side of the text (the box spans the whole
        // measured width, so the click area the widget hit-tests covers the box).
        return localPaint.measureText((inlayHint as? GhostInlayHint)?.text ?: "") +
            localPaint.measureText(" ")
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
        val margin = localPaint.measureText(" ") * 0.5f
        val baseline = params.textBaseline.toFloat()
        // The box hugs the ghost text: top/bottom are the text's own ascent and
        // descent (plus a whisker), so it fits the row at any font size.
        val top = baseline + localPaint.ascent() - margin * 0.5f
        val bottom = baseline + localPaint.descent() + margin * 0.5f
        val radius = (bottom - top) * 0.35f
        // 1. The suggestion box — the "this is not your text" cue (69.2).
        localPaint.color = chipColorArgb
        canvas.drawRoundRect(0f, top, measuredWidth, bottom, radius, radius, localPaint)
        // 2. The ghost text itself, G5's comment colour at 38 % alpha. The
        // canvas is pre-translated to (hint left, row top), so the box starts
        // exactly at the caret and never covers what was typed before it.
        localPaint.color = ghostColorArgb
        canvas.drawText(hint.text, margin, baseline, localPaint)
    }
}
