package com.codeci.ide.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import com.codeci.ide.ui.theme.CodecMotion
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Radius
import com.codeci.ide.ui.theme.CodecTokens.Space

/**
 * Phase 51.4 — one rule for every tappable surface this phase repaints:
 * **a tappable thing has a shape, a container role, and a press state.**
 *
 * Material 3 hands every `clickable` a ripple for free; what the app was missing
 * is *containment*. The two call sites this phase found were exactly that case —
 * the Packages section header (`ModulesScreen.kt`, a bare `Row` with a
 * `clickable`) and the hub's New-Project sheet rows (`FileManagerScreen.kt`,
 * `clip` + `clickable` with no container): a press had no edge to happen inside
 * and the surface had no edge until it was touched. This composable is the fix
 * and the vocabulary: token radius (50.1), a container role (50.2), the touch
 * floor (50.1's `MIN_TOUCH`), and the press state itself — the surface eases to
 * 0.98 and back on the shared `effectsSpring` (50.4), which collapses to instant
 * when the platform says animations are off (Phase 50.4's `MotionPolicy`).
 *
 * Deliberately *not* a new button widget: it renders its content verbatim, so an
 * existing row can move onto it without changing what it shows — the surfaces
 * stay the ones the owner device-tested; they only stop being invisible.
 */
@Composable
fun PressableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CodecTokens.radius(Radius.M)),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = CodecTokens.space(Space.L),
        vertical = CodecTokens.space(Space.M),
    ),
    contentAlignment: Alignment = Alignment.CenterStart,
    /**
     * False for a surface that is *not* a touch target this phase owns — the
     * collapsible section header that is just a label renders through this
     * component with the containment switched off, so one call site keeps one
     * shape instead of two branches that can drift.
     */
    contained: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The press state itself: 0.98 is enough to feel the finger land and too
    // little to move the text under it. No layout animation — the size never
    // changes, only the layer, so nothing re-measures while the user is typing.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = CodecMotion.effectsSpring,
        label = "pressScale",
    )
    Box(
        modifier = modifier
            .then(
                if (contained) {
                    Modifier.heightIn(min = CodecTokens.space(CodecTokens.MIN_TOUCH))
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(containerColor)
            .then(
                if (contained) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}
