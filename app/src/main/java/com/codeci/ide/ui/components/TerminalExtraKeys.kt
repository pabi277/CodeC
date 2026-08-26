package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Termux-style extra key row so a phone keyboard can still send ESC/TAB/CTRL
 * and arrows into the PTY.
 */
@Composable
fun TerminalExtraKeys(
    ctrlLatched: Boolean,
    altLatched: Boolean,
    onCtrl: () -> Unit,
    onAlt: () -> Unit,
    onKey: (String) -> Unit,
    cursorSequence: (Char) -> String,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 4.dp, vertical = 4.dp),
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
        ExtraKey("HOME") { onKey("\u001b[H") }
        ExtraKey("↑") { onKey(cursorSequence('A')) }
        ExtraKey("↓") { onKey(cursorSequence('B')) }
        ExtraKey("←") { onKey(cursorSequence('D')) }
        ExtraKey("→") { onKey(cursorSequence('C')) }
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
        modifier = Modifier.height(36.dp)
    ) {
        Text(
            text = label,
            color = if (latched) Color(0xFF80CBC4) else Color(0xFFE0E0E0),
            fontFamily = FontFamily.Monospace,
            fontWeight = if (latched) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp
        )
    }
}
