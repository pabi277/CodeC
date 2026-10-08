package com.codeci.ide.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.codeci.ide.ui.theme.CodecMotion
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.rememberMotionSpecs

/**
 * Phase 100 — the onboarding's primary action: a gradient CTA with a press state
 * you can feel.
 *
 * Why not `Button(colors = …)`: a Material container colour cannot carry a
 * gradient, and the two workarounds are worse — a transparent `Button` with a
 * background modifier paints the ripple *under* our gradient, and the M3
 * `Surface(brush = …)` overload the theme does not pin a version for. This
 * composable draws the gradient, the press state and the label itself, so what
 * is on screen is exactly what is written here.
 *
 * The three states, and why each exists:
 *
 * - **Rest** — the accent gradient, dark ink on it ([onAccent] from the caller's
 *   palette: 9.1:1 and 7.5:1 on the stage's two ends).
 * - **Pressed** — the button scales to [PRESSED_SCALE] *and* an ink wash at
 *   [PRESSED_WASH] rides over the gradient. Scale alone is invisible under a
 *   fingertip and a wash alone is easy to miss on the gradient's teal end;
 *   together they read as "this landed" on both. The label is drawn after the
 *   wash, so the words stay crisp while the surface responds.
 * - **Disabled** — a flat fill with muted ink, so a blocked action looks blocked
 *   (the setup uses this while it is building the project).
 *
 * Hover is not implemented, because a touch screen has no hover: the Android
 * equivalents are the pressed state above and the focus state, which
 * `clickable`'s own indication draws for keyboard and D-pad users — it is off
 * here in favour of the explicit wash, so the two can never stack.
 *
 * The scale rides [rememberMotionSpecs], so with the platform's "remove
 * animations" switch (or TalkBack's reduce-motion flag) on, the press snaps:
 * the wash still says "this landed", and nothing moves.
 */
@Composable
fun GradientButton(
    label: String,
    onClick: () -> Unit,
    /** The accent gradient: the tour's fixed one, or the theme's own. */
    brush: Brush,
    /** Ink that sits on the gradient; dark, and tested against both ends. */
    onAccent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledContainer: Color = MaterialTheme.colorScheme.surfaceVariant,
    disabledContent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = rememberMotionSpecs()
    // No layout animation: the size never changes, only the layer, so nothing
    // re-measures while the flow is working.
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESSED_SCALE else 1f,
        animationSpec = motion.floatOrSnap(CodecMotion.effectsSpring),
        label = "ctaPressScale",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CodecTokens.space(CodecTokens.MIN_TOUCH))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.L)))
            .background(if (enabled) brush else Brush.horizontalGradient(listOf(disabledContainer, disabledContainer)))
            .then(
                if (enabled && pressed) {
                    Modifier.background(onAccent.copy(alpha = PRESSED_WASH))
                } else {
                    Modifier
                }
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = CodecTokens.space(CodecTokens.Space.L),
                vertical = CodecTokens.space(CodecTokens.Space.M),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (enabled) onAccent else disabledContent,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Enough movement to feel the finger land, too little to move the label under it. */
private const val PRESSED_SCALE = 0.97f

/** The press wash: ink over the gradient, at rest-invisible strength. */
private const val PRESSED_WASH = 0.18f
