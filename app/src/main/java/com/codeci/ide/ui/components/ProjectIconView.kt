package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.projects.HubIconToken
import com.codeci.ide.ui.projects.ProjectHubEntry

@Composable
fun ProjectIconView(entry: ProjectHubEntry, modifier: Modifier = Modifier) {
    val background = when (entry.icon) {
        HubIconToken.C_ORANGE -> Color(0xFFF0863C)
        HubIconToken.PY_BLUE -> Color(0xFF3E7CC1)
        HubIconToken.SERVER_PURPLE -> Color(0xFF8B5CF6)
        HubIconToken.WEB_GREEN -> Color(0xFF4CAF50)
        HubIconToken.GENERIC_GRAY -> Color(0xFF6B7280)
    }
    
    val icon = when (entry.icon) {
        HubIconToken.C_ORANGE -> FileIcon.C
        HubIconToken.PY_BLUE -> FileIcon.Python
        HubIconToken.WEB_GREEN -> FileIcon.Html
        HubIconToken.SERVER_PURPLE -> FileIcon.Default // Can be a server icon or default
        else -> null
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon.vector,
                contentDescription = null,
                tint = if (icon.tintable) Color.White else Color.Unspecified,
                modifier = Modifier.size(if (icon.tintable) 30.dp else 34.dp) // Python/HTML might need 34dp to fit nicely as they have extra margins inside their original vector or we match the previous sizing
            )
        } else {
            Text(
                entry.iconLabel.ifEmpty { entry.name.take(1).uppercase() },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
