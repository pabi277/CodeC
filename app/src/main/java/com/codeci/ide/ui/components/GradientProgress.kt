package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.theme.CodecTokens

/**
 * Phase 100 - a determinate progress bar whose filled part is a gradient.
 *
 * Material 3's [androidx.compose.material3.LinearProgressIndicator] takes a flat
 * [Color], and the setup flow's BUILD beat must keep showing the *real* number of
 * files the seeder has written (Phase 99 rule: never a timer). So the bar is
 * drawn by hand here and keeps the same semantics Material would have set, which
 * is what a screen reader reads out.
 *
 * @param progress 0f..1f, clamped. Values outside are pinned, never thrown on.
 * @param brush the accent gradient for the filled part.
 * @param height the track height; the setup's build bar is 6 dp.
 * @param track the empty lane. It defaults to the *surface* the bar sits on
 *   rather than [androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant],
 *   because every accent is lightness-corrected against `surface` (that is what
 *   `AccentPalette.rolesFor` does), so the fill is guaranteed ≥ 4.5:1 against
 *   its track. Against `surfaceVariant` the same fill measures as low as
 *   **2.46:1** (Violet/Teal in dark), which is under the 3:1 WCAG 2.2 §1.4.11
 *   floor for a graphical object — a bar you cannot see fill.
 * @param lane the hairline that makes the empty lane visible when the track is
 *   the same colour as the page (decorative; the bar's real value is announced
 *   through the semantics below). It is painted under the fill, so the filled
 *   run stays one unbroken colour.
 */
@Composable
fun GradientProgress(
    progress: Float,
    brush: Brush,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    track: Color = MaterialTheme.colorScheme.surface,
    lane: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    val safe = progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.XS))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(track)
            .border(1.dp, lane, shape)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(safe, 0f..1f) },
    ) {
        // A zero-width fill would still paint a hairline in some versions of
        // Skia, so the fill box is simply not emitted at 0.
        if (safe > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safe)
                    .height(height)
                    .clip(shape)
                    .background(brush),
            )
        }
    }
}
