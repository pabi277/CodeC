package com.codeci.ide.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun SymbolBar(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    tabSize: Int = 4,
    modifier: Modifier = Modifier
) {
    val symbols = listOf("{", "}", "(", ")", "[", "]", ";", ",", ".", "#", "\"", "'", "&", "|", "=", "+", "-", "*", "/")
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        symbols.forEach { symbol ->
            FilledTonalButton(
                onClick = { insertText(symbol, textFieldValue, onValueChange) },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        FilledTonalButton(
            onClick = { insertText(" ".repeat(tabSize.coerceIn(2, 8)), textFieldValue, onValueChange) },
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(
                text = "Tab",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun insertText(
    textToInsert: String,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    val currentText = textFieldValue.text
    val selection = textFieldValue.selection
    
    // Safely calculate the insertion points in case of selection range
    val min = selection.min.coerceIn(0, currentText.length)
    val max = selection.max.coerceIn(0, currentText.length)
    
    val newText = currentText.substring(0, min) + textToInsert + currentText.substring(max)
    val newCursorPos = min + textToInsert.length
    
    onValueChange(TextFieldValue(text = newText, selection = TextRange(newCursorPos)))
}
