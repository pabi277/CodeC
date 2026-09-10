package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.projects.WelcomeStarter
import com.codeci.ide.ui.theme.CodecPalette

@Composable
fun StarterIconView(starter: WelcomeStarter, modifier: Modifier = Modifier) {
    val background = when (starter.id) {
        "python" -> Color(CodecPalette.TILE_BLUE)
        "web" -> Color(CodecPalette.TILE_GREEN)
        else -> Color(CodecPalette.TILE_ORANGE)
    }
    
    val icon = when (starter.id) {
        "python" -> FileIcon.Python
        "web" -> FileIcon.Html
        else -> FileIcon.C
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon.vector,
            contentDescription = null,
            tint = if (icon.tintable) Color.White else Color.Unspecified,
            modifier = Modifier.size(if (icon.tintable) 30.dp else 34.dp)
        )
    }
}
