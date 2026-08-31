package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codeci.ide.R

/**
 * Phase 9 status bar, Phase 16 Spck additions: the language label sits
 * between encoding and indent (`UTF-8 · C · Spaces: 4`), the LF/CRLF chip
 * toggles the active file's ending on tap (null [onLineEndingClick] — scratch
 * buffers — renders it inert), and the diagnostics chip keeps doubling as the
 * errors badge (counts live in it; tap reviews and jumps).
 */
@Composable
fun EditorStatusBar(
    line: Int,
    column: Int,
    selectionLength: Int,
    tabSize: Int,
    errorCount: Int,
    warningCount: Int,
    onDiagnosticsClick: () -> Unit,
    languageLabel: String? = null,
    lineEnding: String = "LF",
    onLineEndingClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.status_ln_col, line, column),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "UTF-8",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (languageLabel != null) {
            Text(
                text = languageLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.status_spaces, tabSize),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = lineEnding + if (onLineEndingClick != null) " ▾" else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (onLineEndingClick != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            },
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (onLineEndingClick != null) {
                        Modifier.clickable { onLineEndingClick() }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        if (selectionLength > 0) {
            Text(
                text = stringResource(R.string.status_selection, selectionLength),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(
            modifier = Modifier
                .clickable(enabled = errorCount + warningCount > 0, onClick = onDiagnosticsClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (errorCount > 0) {
                DiagnosticsDot(color = Color(0xFFFF5555))
                Text(
                    text = "✕ $errorCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF5555)
                )
            }
            if (warningCount > 0) {
                DiagnosticsDot(color = Color(0xFFFFB347))
                Text(
                    text = "⚠ $warningCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFB347)
                )
            }
            if (errorCount == 0 && warningCount == 0) {
                Text(
                    text = stringResource(R.string.diagnostics),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsDot(color: Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
}
