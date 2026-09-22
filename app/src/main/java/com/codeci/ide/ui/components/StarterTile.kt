package com.codeci.ide.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import com.codeci.ide.ui.projects.WelcomeStarter
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Radius
import com.codeci.ide.ui.theme.CodecTokens.Space

/**
 * One starter tile (33.1), now used by the **Projects empty state** (33.3).
 *
 * It was shared with the first-run welcome until Phase 58.1 retired that screen
 * (owner: *"first open is the editor, on a snake sample"*); the tile itself is
 * unchanged and still lives here, because the hub's empty state is where a user
 * with no projects meets C / Python / HTML. A coloured leading square (C orange
 * / Python blue / web green) with the language mark, then the title and the one
 * line that says what the tap does.
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
