package com.codeci.ide.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.codeci.ide.R
import com.codeci.ide.ui.viewmodels.FindUiState

/**
 * Phase 9 — find/replace overlay bar for the editor.
 * Row 1: query, live match count, prev/next.
 * Row 2: replacement, Replace, Replace All, case/word/regex toggles, close.
 */
@Composable
fun FindReplaceBar(
    state: FindUiState,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onToggleCase: () -> Unit,
    onToggleWord: () -> Unit,
    onToggleRegex: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val hasMatches = state.matches.isNotEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(R.string.find_placeholder),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                isError = state.error != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onNext() },
                    onDone = { focusManager.clearFocus() }
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = state.error
                    ?: if (!hasMatches) {
                        if (state.query.isEmpty()) "" else stringResource(R.string.no_matches)
                    } else {
                        stringResource(R.string.match_count, state.activeIndex + 1, state.matches.size)
                    },
                style = MaterialTheme.typography.labelSmall,
                color = if (state.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .width(88.dp)
                    .padding(horizontal = 2.dp)
            )
            IconButton(onClick = onPrev, enabled = hasMatches, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.previous_match),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onNext, enabled = hasMatches, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.next_match),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = state.replacement,
                onValueChange = onReplacementChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(R.string.replace_placeholder),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { onReplace(); focusManager.clearFocus() }
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onReplace, enabled = hasMatches) {
                Text(stringResource(R.string.replace), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onReplaceAll, enabled = hasMatches) {
                Text(stringResource(R.string.replace_all), style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.options.matchCase,
                onClick = onToggleCase,
                label = { Text("Aa", style = MaterialTheme.typography.labelMedium) }
            )
            FilterChip(
                selected = state.options.wholeWord,
                onClick = onToggleWord,
                label = { Text("\\b", style = MaterialTheme.typography.labelMedium) }
            )
            FilterChip(
                selected = state.options.regex,
                onClick = onToggleRegex,
                label = { Text(".*", style = MaterialTheme.typography.labelMedium) }
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close_find),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
