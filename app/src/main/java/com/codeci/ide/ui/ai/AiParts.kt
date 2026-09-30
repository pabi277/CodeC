package com.codeci.ide.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Space
import com.codeci.ide.ui.theme.CodecType

/*
 * Phase 77 — the small pieces the two AI surfaces share (the ✨ slot's AI home
 * and the chat sheet). Split out of Phase 76's single `AiPanel.kt` so neither
 * surface draws the other's half. Nothing here decides anything.
 */

/** The exact text that leaves the phone, monospaced and selectable (D4). */
@Composable
internal fun SentText(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = CodecTokens.space(Space.HUGE) * 5)
            .border(
                width = CodecTokens.space(Space.XXS) / 2,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.S))
            )
            .verticalScroll(rememberScrollState())
            .padding(CodecTokens.space(Space.S))
    ) {
        SelectionContainer {
            Text(text, fontFamily = CodecType.codeFamily, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun Answer(text: String) {
    SelectionContainer {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun Links(vararg links: Pair<String, String>) {
    val uri = LocalUriHandler.current
    Column {
        links.forEach { (label, url) ->
            // Opens the browser only on a tap; nothing is fetched by CodeC itself.
            TextButton(onClick = { runCatching { uri.openUri(url) } }) {
                Text(label, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

@Composable
internal fun Muted(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
internal fun ErrorLine(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error
)

/** The one way out of the AI surface (D1): Copy. There is no apply/insert/run. */
internal fun copyAnswer(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("CodeC AI answer", text))
    Toast.makeText(context, AiCopy.COPIED, Toast.LENGTH_SHORT).show()
}
