package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.projects.MarkSeat
import com.codeci.ide.ui.projects.ProjectHubEntry
import com.codeci.ide.ui.projects.ProjectMarks
import com.codeci.ide.ui.theme.CodecPalette

/**
 * Phase 59.2 — the project's leading square is now the project's **own mark**: the name's initials
 * on one of the app's five project-tile colours, chosen from a hash of the name.
 *
 * It used to be a **kind** glyph (every C project the same orange `C`). The spec's §1 asks for an
 * auto-generated, distinct logo per project, so this view no longer draws a kind at all: the
 * decision is `ProjectMark`'s (pure, host-tested), this composable only paints it — and the kind
 * stayed on the card, one line down in the subtitle (`ProjectsHub.kindLabel`).
 *
 * The five colours are `CodecPalette.TILE_*`, whose white-on-tile contrast Phase 50.1 measured and
 * corrected (all ≥ 4.83:1), so a mark never introduces an unreadable pairing.
 */
@Composable
fun ProjectIconView(entry: ProjectHubEntry, modifier: Modifier = Modifier) {
    val mark = remember(entry.name) { ProjectMarks.mark(entry.name) }
    val background = when (mark.seat) {
        MarkSeat.ORANGE -> Color(CodecPalette.TILE_ORANGE)
        MarkSeat.BLUE -> Color(CodecPalette.TILE_BLUE)
        MarkSeat.VIOLET -> Color(CodecPalette.TILE_VIOLET)
        MarkSeat.GREEN -> Color(CodecPalette.TILE_GREEN)
        MarkSeat.GRAY -> Color(CodecPalette.TILE_GRAY)
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = mark.initials,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
