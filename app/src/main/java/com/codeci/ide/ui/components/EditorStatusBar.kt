package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

private val ErrorRed = Color(0xFFFF5555)
private val WarningAmber = Color(0xFFFFB347)

/**
 * Phase 16 (mockup-exact) status bar: one muted line of dot-separated
 * segments — `Ln 12, Col 4 · UTF-8 · C · Spaces: 4 · LF` — the line ending
 * being a plain (still-tappable) segment, with the selection count and the
 * errors/warnings badges appearing only when present (tap jumps to the
 * first diagnostic).
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(start = 12.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusSegment(stringResource(R.string.status_ln_col, line, column))
        StatusDot()
        StatusSegment("UTF-8")
        if (languageLabel != null) {
            StatusDot()
            StatusSegment(languageLabel)
        }
        StatusDot()
        StatusSegment(stringResource(R.string.status_spaces, tabSize))
        if (selectionLength > 0) {
            StatusDot()
            Text(
                text = stringResource(R.string.status_selection, selectionLength),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        // Mockup-exact: the line ending reads as a plain muted segment of
        // the same dot-separated line (no pill); it is still tappable to
        // toggle LF ⇄ CRLF (scratch buffers are inert).
        StatusDot()
        Text(
            text = lineEnding,
            style = MaterialTheme.typography.labelSmall,
            color = muted.copy(alpha = 0.7f),
            modifier = if (onLineEndingClick != null) {
                Modifier.clickable { onLineEndingClick() }
            } else {
                Modifier
            }
        )
        Spacer(Modifier.weight(1f))
        if (errorCount > 0) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onDiagnosticsClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DiagnosticsDot(color = ErrorRed)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "✕ $errorCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorRed
                )
            }
        }
        if (warningCount > 0) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onDiagnosticsClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DiagnosticsDot(color = WarningAmber)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "⚠ $warningCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningAmber
                )
            }
        }
    }
}

@Composable
private fun StatusSegment(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StatusDot() {
    Text(
        text = "  ·  ",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
}

@Composable
private fun DiagnosticsDot(color: Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
}
