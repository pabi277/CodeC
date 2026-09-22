package com.codeci.ide.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.codeci.ide.R
import com.codeci.ide.ui.editor.NoticeKind
import com.codeci.ide.ui.theme.CodecPalette
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Radius
import com.codeci.ide.ui.theme.CodecTokens.Space

/**
 * Phase 57.3 — one pill, for the short messages the shots show.
 *
 * The reference (`docs/spck-ui` 122157) floats it above the editor's bottom
 * edge: a rounded dark surface, one small icon, one bold line — “Refreshed
 * Files”. Not a dialog (nothing to answer), not a dump (nothing to scroll),
 * and not a `Toast` (the screen owns its lifetime, so it cannot outlive the
 * screen or race the editor's snackbar).
 *
 * The surface is Phase 51.4's [PressableSurface], not a new widget: one shape,
 * one container role, the touch floor, and the app's press state — a pill the
 * user taps to dismiss is a touch target like any other.
 *
 * The shot's own glyph is the store's mark; the clean room uses the app's
 * glyph for the same idea (the refresh arrow), tinted the reference's blue.
 * An unopenable file keeps the app's established danger red, so the two pills
 * are never mistaken for one another at a glance.
 */
@Composable
fun PillNotice(
    kind: NoticeKind,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (kind) {
        NoticeKind.FILES_REFRESHED -> R.string.notice_files_refreshed
        NoticeKind.FILE_OPEN_FAILED -> R.string.notice_file_open_failed
    }
    val glyph = when (kind) {
        NoticeKind.FILES_REFRESHED -> Icons.Default.Refresh
        NoticeKind.FILE_OPEN_FAILED -> Icons.Default.Info
    }
    val tint = when (kind) {
        NoticeKind.FILES_REFRESHED -> CodecPalette.INFO
        NoticeKind.FILE_OPEN_FAILED -> CodecPalette.DANGER
    }
    PressableSurface(
        onClick = onDismiss,
        shape = RoundedCornerShape(CodecTokens.radius(Radius.XL)),
        contentPadding = PaddingValues(
            horizontal = CodecTokens.space(Space.L),
            vertical = CodecTokens.space(Space.M),
        ),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = Color(tint),
                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION)),
            )
            Spacer(Modifier.width(CodecTokens.space(Space.S)))
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
