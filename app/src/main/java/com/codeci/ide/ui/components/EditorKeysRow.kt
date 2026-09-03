package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.editor.EditorKeyDef
import com.codeci.ide.ui.editor.EditorKeySet
import com.codeci.ide.ui.editor.RunKey
import com.codeci.ide.ui.editor.RunKeySet

/**
 * Phase 16 — the snippet / extra-keys row docked directly above the status
 * bar (Spck's signature row, mockup-exact): flat keycaps — 40dp tall, 10dp
 * radius, a slightly lighter fill, NO border — on the editor's own
 * background, horizontally scrollable. Data-driven from [EditorKeySet]: the
 * caller supplies the precomputed [keys] (Phase 23.2 decides which set via
 * `keysForContext`), and a tap applies the key to the buffer.
 */
@Composable
fun EditorKeysRow(
    keys: List<EditorKeyDef>,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    tabSize: Int = 4,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { def ->
            KeyCap(def.label, def.wide) {
                onValueChange(EditorKeySet.apply(def.key, textFieldValue, tabSize))
            }
        }
    }
}

/**
 * Phase 23.2 — the interactive-run keys. Shown instead of [EditorKeysRow]
 * while an interactive program is waiting for stdin: submit the line, send
 * SIGINT, append a tab, and history arrows (no-op stubs for now). Actions go
 * through [onKeyAction] so the screen routes them to the ViewModel.
 */
@Composable
fun RunKeysRow(
    onKeyAction: (RunKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RunKeySet.KEYS.forEach { def ->
            KeyCap(def.label, def.wide) { onKeyAction(def.action) }
        }
    }
}

/** Phase 16 / 23.2 — the shared flat keycap (one renderer for both strips). */
@Composable
private fun KeyCap(label: String, wide: Boolean, onClick: () -> Unit) {
    val radius = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = if (wide) 56.dp else 44.dp, minHeight = 40.dp)
            .clip(radius)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}
