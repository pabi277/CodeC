package com.codeci.ide.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.codeci.ide.ui.theme.CodecMotion
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Radius
import com.codeci.ide.ui.theme.CodecTokens.Space

/**
 * Phase 51.3 — a loading placeholder that looks like the content it stands in
 * for.
 *
 * The evidence (2026-09-21, `main` @ `0f1b650`): the hub rendered the empty state
 * while its project list was still being read (`FileManagerViewModel.hubEntries`
 * starts as `emptyList()`), so "still reading" and "you have no projects" were
 * the same screen for as long as the disk took. A spinner would say *wait*; the
 * fix is the list's own shape, in the list's own rhythm, so the answer to "are
 * my projects gone?" arrives with the layout itself.
 *
 * The animation is [CodecMotion.shimmer], the shared vocabulary's only infinite
 * spec, so the platform's remove-animations switch flattens this to a static
 * shape — which is what a placeholder should be in that case. Tokens only: the
 * radius is 50.1's, the colour is 50.2's container role.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CodecTokens.radius(Radius.M)),
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = CodecMotion.shimmer,
        label = "skeletonAlpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = alpha))
    )
}

/**
 * One placeholder card of the Projects hub: the same internal rhythm the real
 * `ProjectHubCard` has (a leading tile, two text lines), so the row count the
 * policy declares is the count the eye sees — the reason `SkeletonStabilityTest`
 * can pin it at all.
 */
@Composable
fun SkeletonHubCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CodecTokens.radius(Radius.L)))
            .background(MaterialTheme.colorScheme.surface)
            .padding(CodecTokens.space(Space.L)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(modifier = Modifier.size(CodecTokens.space(Space.XXL)))
        Spacer(Modifier.size(CodecTokens.space(Space.L)))
        Column(Modifier.weight(1f)) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(CodecTokens.space(Space.M))
            )
            Spacer(Modifier.height(CodecTokens.space(Space.S)))
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(CodecTokens.space(Space.S))
            )
        }
    }
}
