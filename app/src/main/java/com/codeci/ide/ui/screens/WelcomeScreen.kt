package com.codeci.ide.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codeci.ide.R
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Radius
import com.codeci.ide.ui.theme.CodecTokens.Space
import com.codeci.ide.ui.components.SpckIcons
import com.codeci.ide.ui.components.StarterIconView
import com.codeci.ide.ui.projects.WelcomeStarter
import com.codeci.ide.ui.projects.WelcomeStarters

/**
 * Phase 33.1 — the first-run screen: three starter tiles only (C / Python /
 * HTML), mirroring Pydroid's "pick a language" opening. Rendered instead of
 * the whole Scaffold, so there is no bottom tab bar here — the user's entire
 * first decision is one tap. Tapping a tile hands the [WelcomeStarter] back;
 * the caller creates-or-opens the starter project, saves the launch state,
 * and marks the welcome complete.
 */
@Composable
fun WelcomeScreen(
    onStarterChosen: (WelcomeStarter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CodecTokens.space(Space.XXL), vertical = CodecTokens.space(Space.XL)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(CodecTokens.space(Space.XXL)))

        // Phase 51.1 — the app has a logo and never showed it. `app_mark.xml`
        // is Phase 38.1's in-app mark (the ">_" glyph on its own tile); at 96 dp
        // it is display art, not chrome, so it stays a raw size by 50.1's rule.
        Image(
            painter = painterResource(R.drawable.app_mark),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(CodecTokens.space(Space.L)))
        Text(
            text = "CodeC",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(CodecTokens.space(Space.S)))
        Text(
            text = stringResource(R.string.welcome_tagline),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CodecTokens.space(Space.M)))
        // Phase 51.1 — the one fact that stops a nervous first-run exit
        // (Phase 44's whole problem was a user closing the app mid-download):
        // C needs nothing, ever. A visible reassurance, not a grey line.
        OfflineCBadge()

        Spacer(Modifier.height(CodecTokens.space(Space.XL)))
        Text(
            text = stringResource(R.string.welcome_pick_language),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(CodecTokens.space(Space.M)))

        WelcomeStarters.starters.forEach { starter ->
            StarterTile(
                starter = starter,
                onClick = { onStarterChosen(starter) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(CodecTokens.space(Space.M)))
        }

        Spacer(Modifier.height(CodecTokens.space(Space.XXL)))
    }
}

/**
 * One starter tile, shared by the first-run welcome and the Projects empty
 * state (33.3): a coloured leading square (C orange / Python blue / web
 * green) with the language mark, then the title + one-line promise. Spck-style
 * flat card, no elevation.
 */
@Composable
fun StarterTile(
    starter: WelcomeStarter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Phase 51.1 — identity and hierarchy on the first screen a user ever sees:
    // a 50.1 token radius + the 50.1 CARD elevation (it was flat, zero-
    // elevation surfaceVariant: three identical grey rows), the language's own
    // colour carried by StarterIconView, and one line saying what the tap does.
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(CodecTokens.radius(Radius.L)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = CodecTokens.elevation(CodecTokens.Elevation.CARD)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodecTokens.space(Space.L), vertical = CodecTokens.space(Space.L)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StarterIconView(starter)
            Spacer(Modifier.width(CodecTokens.space(Space.L)))
            Column(Modifier.weight(1f)) {
                Text(
                    text = starter.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = starter.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Phase 51.1 — "what happens next", the line the research says
                // a first-run screen owes the user before they commit a tap.
                Text(
                    text = starter.nextStep,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION)),
            )
        }
    }
}

/**
 * Phase 51.1 — "C works offline" as a badge, not a grey line.
 *
 * The owner's own Phase 33.1 tiles open with C because it needs nothing: the
 * compiler is in the APK (`EmbeddedCompiler`), so a nervous first-run user can
 * run something without waiting for a download. Phase 44's entire problem was a
 * user who closed the app during the Python userland download — this badge is
 * the sentence that prevents that decision before it is made.
 */
@Composable
private fun OfflineCBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CodecTokens.radius(Radius.XL)))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(
                horizontal = CodecTokens.space(Space.M),
                vertical = CodecTokens.space(Space.S),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.INLINE)),
        )
        Spacer(Modifier.width(CodecTokens.space(Space.S)))
        Text(
            text = stringResource(R.string.welcome_offline_c),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
