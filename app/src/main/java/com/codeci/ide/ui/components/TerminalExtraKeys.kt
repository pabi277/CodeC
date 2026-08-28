package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
 * Termux-style configurable extra key grid so a phone keyboard can still send
 * ESC/TAB/CTRL/ALT, symbols, cursor keys, and macros into the PTY.
 * Uses FlowRow to wrap cleanly across screen widths without horizontal scroll clipping.
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
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ExtraKey("ESC", latched = false) { onKey("\u001b") }
        ExtraKey("TAB", latched = false) { onKey("\t") }
        ExtraKey("CTRL", latched = ctrlLatched, onClick = onCtrl)
        ExtraKey("ALT", latched = altLatched, onClick = onAlt)
        ExtraKey("-") { onKey("-") }
        ExtraKey("/") { onKey("/") }
        ExtraKey("|") { onKey("|") }
        ExtraKey("~") { onKey("~") }
        ExtraKey("HOME") { onKey("\u001b[H") }
        ExtraKey("END") { onKey("\u001b[F") }
        ExtraKey("↑") { onKey(cursorSequence('A')) }
        ExtraKey("↓") { onKey(cursorSequence('B')) }
        ExtraKey("←") { onKey(cursorSequence('D')) }
        ExtraKey("→") { onKey(cursorSequence('C')) }
        customMacros.forEach { (label, command) ->
            ExtraKey(label) { onKey(command) }
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
    latched: Boolean = false,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (latched) Color(0x3380CBC4) else Color(0x1AFFFFFF),
            contentColor = if (latched) Color(0xFF80CBC4) else Color(0xFFE0E0E0)
        ),
        modifier = Modifier.heightIn(min = 32.dp)
    ) {
        Text(
            text = label,
            color = if (latched) Color(0xFF80CBC4) else Color(0xFFE0E0E0),
            fontFamily = FontFamily.Monospace,
            fontWeight = if (latched) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp
        )
    }
}
