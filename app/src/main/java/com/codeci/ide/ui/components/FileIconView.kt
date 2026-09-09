package com.codeci.ide.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun FileIconView(
    name: String,
    isDirectory: Boolean = false,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val icon = FileIcon.resolve(name, isDirectory)
    Icon(
        imageVector = icon.vector,
        contentDescription = null,
        modifier = modifier,
        tint = if (icon.tintable) tint else Color.Unspecified
    )
}
