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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.editor.EditorKeyDef
import com.codeci.ide.ui.editor.EditorKeySet
import com.codeci.ide.ui.utils.LanguageType

/**
 * Phase 16 — the snippet / extra-keys row docked above the status bar
 * (Spck's signature row). Data-driven from [EditorKeySet.keysFor]: general
 * keys, a small per-language tail, then the user's custom snippets. The keys
 * are a little taller than the old buttons and shaped like keycaps; the row
 * scrolls horizontally so growth stays safe on phones.
 */
@Composable
fun EditorKeysRow(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    tabSize: Int = 4,
    language: LanguageType? = null,
    customSnippets: String? = null,
    modifier: Modifier = Modifier
) {
    val keys = remember(language, customSnippets) {
        EditorKeySet.keysFor(language, customSnippets)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { def ->
            EditorKeyCap(def = def) {
                onValueChange(EditorKeySet.apply(def.key, textFieldValue, tabSize))
            }
        }
    }
}

@Composable
private fun EditorKeyCap(def: EditorKeyDef, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = if (def.wide) 52.dp else 38.dp, minHeight = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = def.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
