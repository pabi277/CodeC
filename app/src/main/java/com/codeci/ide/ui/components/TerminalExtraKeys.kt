package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Termux-style configurable 2-row extra key grid so a phone keyboard can still send
 * ESC/TAB/CTRL/ALT, symbols, cursor keys, and macros into the PTY.
 * Formatted cleanly into exactly 2 rows of 7 equal-width buttons.
 */
@Composable
fun TerminalExtraKeys(
    ctrlLatched: Boolean,
    altLatched: Boolean,
    onCtrl: () -> Unit,
    onAlt: () -> Unit,
    onKey: (String) -> Unit,
    cursorSequence: (Char) -> String,
    customMacros: List<Pair<String, String>> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Row 1 (7 keys, equal weight)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ExtraKey("ESC", modifier = Modifier.weight(1f)) { onKey("\u001b") }
            ExtraKey("TAB", modifier = Modifier.weight(1f)) { onKey("\t") }
            ExtraKey("CTRL", latched = ctrlLatched, modifier = Modifier.weight(1f), onClick = onCtrl)
            ExtraKey("ALT", latched = altLatched, modifier = Modifier.weight(1f), onClick = onAlt)
            ExtraKey("-", modifier = Modifier.weight(1f)) { onKey("-") }
            ExtraKey("/", modifier = Modifier.weight(1f)) { onKey("/") }
            ExtraKey("|", modifier = Modifier.weight(1f)) { onKey("|") }
        }

        // Row 2 (7 keys, equal weight)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ExtraKey("~", modifier = Modifier.weight(1f)) { onKey("~") }
            ExtraKey("HOME", modifier = Modifier.weight(1f)) { onKey("\u001b[H") }
            ExtraKey("END", modifier = Modifier.weight(1f)) { onKey("\u001b[F") }
            ExtraKey("↑", modifier = Modifier.weight(1f)) { onKey(cursorSequence('A')) }
            ExtraKey("↓", modifier = Modifier.weight(1f)) { onKey(cursorSequence('B')) }
            ExtraKey("←", modifier = Modifier.weight(1f)) { onKey(cursorSequence('D')) }
            ExtraKey("→", modifier = Modifier.weight(1f)) { onKey(cursorSequence('C')) }
        }

        // Custom user macros row (scrollable if configured)
        if (customMacros.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                customMacros.forEach { (label, command) ->
                    ExtraKey(label) { onKey(command) }
                }
            }
        }
    }
}

fun parseExtraKeysMacros(raw: String): List<Pair<String, String>> {
    if (raw.isBlank()) return emptyList()
    return raw.split(",", "\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { item ->
            if (item.contains(":")) {
                val label = item.substringBefore(":").trim()
                val cmd = item.substringAfter(":").trim()
                label to (if (cmd.endsWith("\n")) cmd else "$cmd\n")
            } else {
                item to (if (item.endsWith("\n")) item else "$item\n")
            }
        }
}

@Composable
private fun ExtraKey(
    label: String,
    modifier: Modifier = Modifier,
    latched: Boolean = false,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (latched) Color(0x3380CBC4) else Color(0x1AFFFFFF),
            contentColor = if (latched) Color(0xFF80CBC4) else Color(0xFFE0E0E0)
        ),
        modifier = modifier.height(34.dp)
    ) {
        Text(
            text = label,
            color = if (latched) Color(0xFF80CBC4) else Color(0xFFE0E0E0),
            fontFamily = FontFamily.Monospace,
            fontWeight = if (latched) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (label.length > 3) 10.sp else 11.sp,
            maxLines = 1
        )
    }
}
