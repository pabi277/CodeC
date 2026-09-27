package com.codeci.ide.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.codeci.ide.R
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Space
import com.codeci.ide.ui.theme.CodecTokens.Radius
import com.codeci.ide.ui.theme.CodecType
import com.codeci.ide.ui.theme.Contrast

private val ErrorRed = Color(0xFFFF5555)
private val WarningAmber = Color(0xFFFFB347)

@OptIn(ExperimentalFoundationApi::class)
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
    pathLabel: String? = null,
    onPathLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val stripRgb = Contrast.composite(
        MaterialTheme.colorScheme.surfaceVariant.toArgb(),
        MaterialTheme.colorScheme.surface.toArgb(),
        0.5f
    )
    fun onStrip(color: Color): Color =
        Color(Contrast.ensureReadable(color.toArgb(), stripRgb, Contrast.AA_TEXT))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(
                start = CodecTokens.space(Space.S),
                end = CodecTokens.space(Space.S),
                top = CodecTokens.space(Space.XXS),
                bottom = CodecTokens.space(Space.XXS)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pathLabel != null) {
            Text(
                text = pathLabel,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = CodecType.codeFamily,
                color = onStrip(muted),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = if (onPathLongClick != null) {
                    Modifier.combinedClickable(onClick = {}, onLongClick = onPathLongClick)
                } else {
                    Modifier
                }
            )
            StatusDot()
        }
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
                color = onStrip(MaterialTheme.colorScheme.primary)
            )
        }
        StatusDot()
        Text(
            text = lineEnding,
            style = MaterialTheme.typography.labelSmall,
            color = onStrip(muted),
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
                    .clip(RoundedCornerShape(CodecTokens.radius(Radius.S)))
                    .clickable(onClick = onDiagnosticsClick)
                    .padding(
                        horizontal = CodecTokens.space(Space.XS + Space.XXS),
                        vertical = CodecTokens.space(Space.XXS)
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DiagnosticsDot(color = onStrip(ErrorRed))
                Spacer(Modifier.width(CodecTokens.space(Space.XS)))
                Text(
                    text = "✕ $errorCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = onStrip(ErrorRed)
                )
            }
        }
        if (warningCount > 0) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(CodecTokens.radius(Radius.S)))
                    .clickable(onClick = onDiagnosticsClick)
                    .padding(
                        horizontal = CodecTokens.space(Space.XS + Space.XXS),
                        vertical = CodecTokens.space(Space.XXS)
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DiagnosticsDot(color = onStrip(WarningAmber))
                Spacer(Modifier.width(CodecTokens.space(Space.XS)))
                Text(
                    text = "⚠ $warningCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = onStrip(WarningAmber)
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
            .size(CodecTokens.space(Space.S))
            .background(color, CircleShape)
    )
}
