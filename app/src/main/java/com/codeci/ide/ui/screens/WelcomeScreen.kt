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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        Spacer(Modifier.height(CodecTokens.space(Space.HUGE)))

        Text(
            text = "CodeC",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(CodecTokens.space(Space.M)))
        Text(
            text = "Write and run C, Python, JavaScript, and HTML on your phone.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CodecTokens.space(Space.S)))
        Text(
            text = "C works offline with no setup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(CodecTokens.space(Space.XXL)))
        Text(
            text = "Pick a language to get started",
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
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(CodecTokens.radius(Radius.L)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = CodecTokens.elevation(CodecTokens.Elevation.FLAT)),
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

