package com.codeci.ide.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.codeci.ide.ui.theme.CodecPalette
import com.codeci.ide.ui.theme.CodecTokens

/**
 * Phase 99 — the one way CodeC draws an illustration card.
 *
 * The first-run tour (`FirstRunIntroScreen`) introduced these cards in Phase 98:
 * a square, softly rounded panel of the render's own pale backdrop
 * ([CodecPalette.ART_CARD_BACKDROP]) with the 900 px WebP inside it. The setup
 * flow now shows one on every beat, so the card moved here rather than being
 * copied — and a copy would have drifted, because the things that make it safe
 * are easy to lose:
 *
 * 1. **The backdrop is the render's own colour, not a theme role.** The
 *    illustrations are photographic 3D renders with a pale ground baked in;
 *    tinting them per accent looks like a colour cast. Every *role* around the
 *    card (chip, heading, buttons, dots) is still the theme's.
 * 2. **The description is required.** Art with no words is a hole for a screen
 *    reader, so `description` has no default.
 * 3. **The reveal is optional and honest.** `reveal` is the caller's own
 *    reduced-motion-aware value (the tour passes `CodecMotion.introReveal`);
 *    the default is "already there".
 *
 * Bundled art lives in `app/src/main/res/drawable-nodpi/` as `intro_*.webp`
 * (the tour) and `setup_*.webp` (the setup flow) — see
 * `docs/phases/09-onboarding-setup/chat-phase99/PART_99_SETUP_ART.md`.
 */
@Composable
fun StepArt(
    @DrawableRes art: Int,
    /** What the illustration shows, for a screen reader. */
    description: String,
    modifier: Modifier = Modifier,
    /** 0..1 entrance, for callers that have their own motion policy. */
    reveal: Float = 1f,
) {
    val settled = reveal.coerceIn(0f, 1f)
    Card(
        modifier = modifier.graphicsLayer {
            alpha = settled
            scaleX = 0.94f + 0.06f * settled
            scaleY = scaleX
        },
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.XL)),
        colors = CardDefaults.cardColors(containerColor = Color(CodecPalette.ART_CARD_BACKDROP)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = CodecTokens.elevation(CodecTokens.Elevation.CARD),
        ),
    ) {
        Image(
            painter = painterResource(art),
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(CodecTokens.space(CodecTokens.Space.S)),
        )
    }
}
