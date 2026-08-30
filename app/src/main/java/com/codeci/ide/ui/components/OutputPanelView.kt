package com.codeci.ide.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.ide.R
import com.codeci.ide.ui.editor.CompilerDiagnostics
import com.codeci.ide.ui.editor.OutputDiagnostic
import com.codeci.ide.ui.editor.OutputLineParser
import com.codeci.ide.ui.viewmodels.OutputLine
import com.codeci.ide.ui.viewmodels.OutputLineKind
import com.codeci.ide.ui.viewmodels.OutputPhase
import com.codeci.ide.ui.viewmodels.OutputRunState

// Phase 6.1: URL detection helper for terminal text
fun extractUrls(text: String): List<String> {
    val regex = Regex("https?://[^\\s<>\"]+")
    return regex.findAll(text).map { it.value }.toList()
}

private val OUTPUT_COLORS = mapOf(
    OutputLineKind.COMMAND to Color(0xFF8A8A8A),
    OutputLineKind.BUILD to Color(0xFFAAAAAA),
    OutputLineKind.OUTPUT to Color(0xFFFFFFFF),
    OutputLineKind.ERROR to Color(0xFFFF5555),
    OutputLineKind.STATS to Color(0xFF55FF55),
    OutputLineKind.SYSTEM to Color(0xFF66B2FF)
)

/**
 * Phase 11 — the split-screen Output Panel. Expanded: header (status + stop /
 * open-in-terminal / copy / clear / collapse) and the streaming line list.
 * Collapsed: a one-line strip showing the last output line; tapping expands.
 * Compiler diagnostic lines (`file:line:col: error: …`) render in red/orange
 * with an underline and are clickable — the editor jumps to that position.
 */
@Composable
fun OutputPanelView(
    state: OutputRunState,
    isExpanded: Boolean,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onToggleExpand: () -> Unit,
    onOpenInTerminal: () -> Unit,
    onDiagnosticTap: (OutputDiagnostic) -> Unit,
    onApplyFix: (OutputDiagnostic) -> Unit = {},
    onSendInput: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty() && isExpanded) {
            listState.animateScrollToItem(state.lines.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252526))
                .then(
                    if (!isExpanded) {
                        Modifier.clickable { onToggleExpand() }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Output",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.width(10.dp))
            state.summary?.let { summary ->
                Text(
                    text = summary,
                    color = if (state.busy) Color(0xFF66B2FF) else Color(0xFF8A8A8A),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.weight(1f))

            if (state.busy) {
                IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Stop",
                        tint = Color(0xFFFF5555),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // Always offer the interactive escape hatch once a run exists —
            // stdin-blocking programs (scanf/gets) need the terminal session,
            // so the terminal icon stays visible during and after a run.
            if (state.lastTerminalCommand != null) {
                IconButton(onClick = onOpenInTerminal, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = "Open in Terminal",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(
                onClick = {
                    val fullText = state.lines.joinToString("\n") { it.text }
                    if (fullText.isNotEmpty()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("CodeC Output", fullText))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = state.lines.isNotEmpty(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onClear,
                enabled = state.lines.isNotEmpty(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Clear",
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.Default.KeyboardArrowUp
                    },
                    contentDescription = "Toggle Output",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (isExpanded) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(state.lines) { line ->
                    OutputLineItem(
                        line = line,
                        onDiagnosticTap = onDiagnosticTap,
                        onApplyFix = onApplyFix
                    )
                }
            }
            // Phase 11 (owner decision 2026-08-30): interactive programs
            // (scanf/gets) get an input field while they run — typed lines go
            // to the program's stdin; the Open-in-Terminal icon stays for the
            // full PTY experience.
            if (state.phase == OutputPhase.RUNNING) {
                OutputInputRow(onSendInput = onSendInput)
            }
        } else {
            // Collapsed strip: preview the last line.
            val last = state.lines.lastOrNull()
            if (last != null) {
                OutputLineItem(
                    line = last,
                    onDiagnosticTap = onDiagnosticTap,
                    singleLine = true,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            } else {
                Text(
                    text = "Run ▶ to compile and execute here",
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun OutputLineItem(
    line: OutputLine,
    onDiagnosticTap: (OutputDiagnostic) -> Unit,
    onApplyFix: (OutputDiagnostic) -> Unit = {},
    singleLine: Boolean = false,
    modifier: Modifier = Modifier
) {
    val diagnostic = remember(line) { OutputLineParser.parseLine(line.text) }
    val baseColor = OUTPUT_COLORS[line.kind] ?: Color(0xFFFFFFFF)
    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = MaterialTheme.typography.bodySmall.fontSize,
        color = baseColor
    )
    if (diagnostic != null) {
        val color = if (diagnostic.isError) Color(0xFFFF5555) else Color(0xFFFFB347)
        val annotated = buildAnnotatedString {
            withStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline)) {
                append(line.text)
            }
        }
        val fixable = CompilerDiagnostics.semicolonFixLabel(diagnostic) != null
        if (fixable && !singleLine) {
            // Phase 11: a fixable error gets a one-tap Apply action under the line.
            Column(modifier = modifier) {
                ClickableText(
                    text = annotated,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                    onClick = { onDiagnosticTap(diagnostic) }
                )
                TextButton(
                    onClick = { onApplyFix(diagnostic) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.fix_add_semicolon),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            ClickableText(
                text = annotated,
                style = style,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                onClick = { onDiagnosticTap(diagnostic) },
                modifier = modifier
            )
        }
    } else {
        Text(
            text = line.text,
            style = style,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = modifier
        )
    }
}

/**
 * Phase 11 — the interactive-input row. Visible while a program is running;
 * Enter (or the send icon) writes the line to the program's stdin.
 */
@Composable
private fun OutputInputRow(onSendInput: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun send() {
        if (text.isNotBlank()) {
            onSendInput(text)
            text = ""
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            ),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = "Input for the program (Enter sends)",
                        color = Color(0xFF666666),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                innerTextField()
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 4.dp)
        )
        IconButton(onClick = { send() }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Send,
                contentDescription = "Send input",
                tint = Color(0xFF66B2FF),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
