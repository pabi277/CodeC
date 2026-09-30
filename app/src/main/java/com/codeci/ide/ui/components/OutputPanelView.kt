package com.codeci.ide.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.codeci.ide.ui.ai.AiCopy
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.ide.ui.theme.CodecMotion
import com.codeci.ide.ui.theme.rememberMotionSpecs
import com.codeci.ide.ui.theme.CodecType
import com.codeci.ide.R
import com.codeci.ide.ui.editor.CompilerDiagnostics
import com.codeci.ide.ui.theme.CodecPalette
import com.codeci.ide.ui.editor.OutputDiagnostic
import com.codeci.ide.ui.editor.OutputHead
import com.codeci.ide.ui.editor.OutputLineParser
import com.codeci.ide.ui.editor.OutputPanelStatus
import com.codeci.ide.ui.viewmodels.OutputLine
import com.codeci.ide.ui.viewmodels.OutputLineKind
import com.codeci.ide.ui.viewmodels.OutputPhase
import com.codeci.ide.ui.viewmodels.OutputRunState

// Phase 6.1: URL detection helper for terminal text
fun extractUrls(text: String): List<String> {
    val regex = Regex("https?://[^\\s<>\"]+")
    return regex.findAll(text).map { it.value }.toList()
}

/**
 * The panel's palette. Phase 70.1 keeps it (this is a terminal surface, not a
 * themed list) and pins it instead of eyeballing it: `OutputPanelContrastTest`
 * measures every entry against [PANEL_BACKGROUND] with the app's own
 * `Contrast` helper, which is the audit the review asked for.
 */
internal val OUTPUT_COLORS = mapOf(
    OutputLineKind.COMMAND to Color(0xFF8A8A8A),
    OutputLineKind.BUILD to Color(0xFFAAAAAA),
    OutputLineKind.OUTPUT to Color(0xFFFFFFFF),
    OutputLineKind.ERROR to Color(0xFFFF5555),
    OutputLineKind.STATS to Color(0xFF55FF55),
    OutputLineKind.SYSTEM to Color(0xFF66B2FF),
    // Phase 24.6 — test-runner colours (pytest / go test).
    OutputLineKind.TEST_PASS to Color(0xFF55FF55),
    OutputLineKind.TEST_FAIL to Color(0xFFFF5555),
    OutputLineKind.TEST_ERROR to Color(0xFFFFB347),
    OutputLineKind.TEST_SUMMARY to Color(0xFF66B2FF)
)

internal val PANEL_BACKGROUND = Color(0xFF121212)
internal val PANEL_HEADER_BACKGROUND = Color(0xFF252526)

/**
 * Phase 11 — the split-screen Output Panel. Expanded: a status row (Q2's
 * state word + icon + exit code), an action row whose **first** control is one
 * labelled 48 dp Stop (Q4), an error banner, the streaming line list, and the
 * panel's own command line. Collapsed: a one-line strip showing the last
 * output line — or, while a program waits for stdin, *“Waiting for input —
 * tap to answer”* (Q3).
 *
 * Compiler diagnostic lines (`file:line:col: error: …`) render as two rows:
 * the location and severity first, then the message — the owner's report was
 * *“terminal output is good enough but is hard to understand the error line
 * from terminal”*, and a raw `main.c:7:3: error: …` run together in one red
 * string is exactly what made it hard.
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
    /** Phase 23.1 — the panel's line changed (mirrored into the VM). */
    onInputChange: (String) -> Unit = {},
    /**
     * Phase 23.1 / 70.1 — Enter or the send icon. While a program waits this
     * submits the line to its stdin; with nothing waiting it is the panel's
     * own command line (the owner's 2026-09-28 answer: *“I type ls, python
     * main.py or pkg install … into the output area at any time and it runs
     * there, like a terminal.”*).
     */
    onSubmitInput: () -> Unit = {},
    /** Phase 14 — a running server project: open the Web Preview at state.serverUrl. */
    onOpenPreviewUrl: (String) -> Unit = {},
    /** Phase 37.1 — LAN sharing switch for the live server (restarts the run). */
    onToggleLanShare: (Boolean) -> Unit = {},
    /** Phase 37.2 — stop every server the shared host owns. */
    onStopAllServers: () -> Unit = {},
    /**
     * Phase 77.3 — "Explain with AI". Non-null ONLY when the latest run failed
     * AND the AI helper is ready (the caller decides both: `AiGate.runFailed`
     * + `AiAvailability.READY`); otherwise the panel shows nothing new. It
     * opens the chat sheet with the run-output preview — Send is still the
     * user's (D4).
     */
    onExplainWithAi: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    // Phase 50.4 — transition (5): the one motion hook; the run-state
    // summary below resolves through it, instant when the platform says so.
    val motion = rememberMotionSpecs()
    val head = OutputPanelStatus.head(state)
    val counts = remember(state.lines) { OutputPanelStatus.counts(state.lines.map { it.text }) }

    // Phase 23.1 — auto-scroll to the newest content. While a program is
    // waiting for input the last item IS the inline input row (index
    // `lines.size`), so it stays pinned at the bottom under fresh output;
    // otherwise the last output line is the target.
    LaunchedEffect(state.lines.size, state.waitingForInput) {
        if (!isExpanded) return@LaunchedEffect
        if (state.waitingForInput) {
            listState.animateScrollToItem(state.lines.size)
        } else if (state.lines.isNotEmpty()) {
            listState.animateScrollToItem(state.lines.size - 1)
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PANEL_BACKGROUND)
    ) {
        // Q4 measured: a status row, a 48 dp action row and the command line
        // are 144 dp before a single output line. Below that the action row
        // yields — the state, the line list and the command line never do.
        val roomy = maxHeight >= 220.dp
        Column(Modifier.fillMaxSize()) {
            OutputStatusRow(state = state, head = head, isExpanded = isExpanded, onStop = onStop, onToggleExpand = onToggleExpand)
            if (isExpanded && roomy) {
                OutputActionRow(
                    state = state,
                    onStop = onStop,
                    onClear = onClear,
                    onOpenInTerminal = onOpenInTerminal,
                    onOpenPreviewUrl = onOpenPreviewUrl,
                    onExplainWithAi = onExplainWithAi,
                    onCopy = {
                        val fullText = state.lines.joinToString("\n") { it.text }
                        if (fullText.isNotEmpty()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("CodeC Output", fullText))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            // The owner's second half of the report: a failed build says how
            // many errors and warnings it produced, before the red rows do.
            if (isExpanded && counts.hasErrors) OutputErrorBanner(counts)

            // Phase 37.1 — the two URLs (+ QR) for a live server, between the
            // header and the log: the panel is where RUN ▶ ends up, so this is
            // where the address a second device needs belongs.
            if (isExpanded && state.serverEndpoints != null) {
                ServerSharePanel(
                    endpoints = state.serverEndpoints,
                    lanShared = state.lanShared,
                    onToggleLan = onToggleLanShare,
                    servers = state.servers,
                    onOpenUrl = onOpenPreviewUrl,
                    onStopAll = onStopAllServers,
                    dense = true
                )
            }

            if (isExpanded) {
                if (state.lines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.output_empty_hint),
                        color = Color(CodecPalette.MUTED_TEXT),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = CodecType.codeFamily,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.weight(1f))
                } else {
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
                }
                // Phase 23.1 / 70.1 — the panel's own line, the last thing in
                // it. While a program waits it is that program's stdin; with
                // nothing waiting it is the console the owner types into.
                ConsoleLine(
                    buffer = state.inputBuffer,
                    waiting = state.waitingForInput,
                    onInputChange = onInputChange,
                    onSubmit = onSubmitInput
                )
            } else {
                CollapsedStrip(state = state, onDiagnosticTap = onDiagnosticTap)
            }
        }
    }
}

/**
 * Q2 — the state word. One icon, one word, and the exit code where a run
 * finished: `Failed · exit 1`, `Done · exit 0`, `Waiting for input`.
 */
@Composable
private fun OutputStatusRow(
    state: OutputRunState,
    head: OutputHead,
    isExpanded: Boolean,
    onStop: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    val motion = rememberMotionSpecs()
    val tint = headTint(head)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(PANEL_HEADER_BACKGROUND)
            .then(if (!isExpanded) Modifier.clickable { onToggleExpand() } else Modifier)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Phase 24.6 kept: a test run still says so, beside the state word.
        if (state.testRun) {
            Text(
                text = stringResource(R.string.output_panel_tests),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
        }
        headWord(head)?.let { word ->
            Icon(
                headIcon(head),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = word,
                color = tint,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
        headExit(head)?.let { code ->
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.output_exit_short, code),
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(10.dp))
        val summary = state.summary
        if (summary != null) {
            // Phase 50.4 — transition (5): the RUN ▶ reveal — each new
            // run-state summary crossfades in on the shared spec.
            Crossfade(
                targetState = summary,
                animationSpec = motion.floatOrSnap(CodecMotion.crossfadeSpec),
                modifier = Modifier.weight(1f)
            ) { current ->
                Text(
                    text = current,
                    color = if (head is OutputHead.Failed) Color(0xFFFF8A80)
                    else if (state.busy) Color(0xFF66B2FF) else Color(CodecPalette.MUTED_TEXT),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        // Phase 37.1 — a live server announces itself in the header, so the
        // collapsed strip still tells you the phone is serving something.
        state.serverEndpoints?.let { endpoints ->
            Text(
                text = endpoints.badge(),
                color = if (endpoints.hasLan()) Color(0xFF55FF55) else Color(CodecPalette.MUTED_TEXT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        if (!isExpanded && state.busy) {
            TextButton(onClick = onStop) { Text(stringResource(R.string.output_stop)) }
        }
        IconButton(onClick = onToggleExpand) {
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
}

/**
 * Q4, verbatim: *“One labelled Stop at the leading edge, 48 dp.”* The row is
 * scrollable so the remaining 48 dp actions never squeeze the label or each
 * other on a narrow phone.
 */
@Composable
private fun OutputActionRow(
    state: OutputRunState,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onOpenInTerminal: () -> Unit,
    onOpenPreviewUrl: (String) -> Unit,
    onExplainWithAi: (() -> Unit)?,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onStop,
            enabled = state.busy,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(
                text = stringResource(R.string.output_stop),
                color = if (state.busy) Color(0xFFFF5555) else Color(CodecPalette.MUTED_TEXT)
            )
        }
        // Phase 77.3 — only after a failed run, only when AI is set up.
        if (onExplainWithAi != null) {
            TextButton(onClick = onExplainWithAi, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(text = AiCopy.EXPLAIN_WITH_AI, color = MaterialTheme.colorScheme.primary)
            }
        }
        // Phase 14: server projects — jump straight back to Web Preview.
        if (state.serverUrl != null) {
            IconButton(onClick = { state.serverUrl?.let(onOpenPreviewUrl) }) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = stringResource(R.string.open_preview),
                    tint = Color(0xFF55FF55),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // Always offer the interactive escape hatch once a run exists —
        // stdin-blocking programs (scanf/gets) need the terminal session,
        // so the terminal icon stays visible during and after a run.
        if (state.lastTerminalCommand != null) {
            IconButton(onClick = onOpenInTerminal) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = stringResource(R.string.output_open_in_terminal),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        IconButton(onClick = onCopy, enabled = state.lines.isNotEmpty()) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onClear, enabled = state.lines.isNotEmpty()) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Clear",
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** `3 errors · 1 warning` — the count the owner had to do by eye. */
@Composable
private fun OutputErrorBanner(counts: OutputPanelStatus.Counts) {
    val errors = if (counts.errors > 0) {
        pluralStringResource(R.plurals.output_error_count, counts.errors, counts.errors)
    } else {
        null
    }
    val warnings = if (counts.warnings > 0) {
        pluralStringResource(R.plurals.output_warning_count, counts.warnings, counts.warnings)
    } else {
        null
    }
    val text = listOfNotNull(errors, warnings).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .background(Color(0xFF2A1414))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = Color(0xFFFF8A80),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Q3 — while a program waits, the strip says what to do. */
@Composable
private fun CollapsedStrip(state: OutputRunState, onDiagnosticTap: (OutputDiagnostic) -> Unit) {
    if (state.waitingForInput) {
        Text(
            text = stringResource(R.string.output_waiting_tap),
            color = Color(0xFF66B2FF),
            fontFamily = CodecType.codeFamily,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        return
    }
    val last = state.lines.lastOrNull()
    if (last != null) {
        OutputLineItem(
            line = last,
            onDiagnosticTap = onDiagnosticTap,
            singleLine = true,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    } else {
        Text(
            text = stringResource(R.string.output_strip_hint),
            color = Color(CodecPalette.MUTED_TEXT),
            fontSize = 12.sp,
            fontFamily = CodecType.codeFamily,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
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
        fontFamily = CodecType.codeFamily,
        fontSize = MaterialTheme.typography.bodySmall.fontSize,
        color = baseColor
    )
    if (diagnostic != null && !singleLine) {
        // The owner's “hard to understand the error line”: location and
        // severity on their own row, then the message, then the fix.
        val color = if (diagnostic.isError) Color(0xFFFF5555) else Color(0xFFFFB347)
        val fixable = CompilerDiagnostics.semicolonFixLabel(diagnostic) != null
        Column(modifier = modifier.padding(vertical = 2.dp)) {
            Text(
                text = diagnosticLocation(diagnostic),
                color = Color(CodecPalette.MUTED_TEXT),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = CodecType.codeFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ClickableText(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline)) {
                        append(diagnostic.message)
                    }
                },
                style = style,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                onClick = { onDiagnosticTap(diagnostic) }
            )
            if (fixable) {
                TextButton(
                    onClick = { onApplyFix(diagnostic) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.fix_add_semicolon),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        return
    }
    if (diagnostic != null) {
        val color = if (diagnostic.isError) Color(0xFFFF5555) else Color(0xFFFFB347)
        ClickableText(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline)) {
                    append(line.text)
                }
            },
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onClick = { onDiagnosticTap(diagnostic) },
            modifier = modifier
        )
        return
    }
    Text(
        text = line.text,
        style = style,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier
    )
}

/** `main.c:12:5` — or `main.c:12` when the compiler printed no column (TCC). */
private fun diagnosticLocation(diagnostic: OutputDiagnostic): String {
    val head = "${diagnostic.file}:${diagnostic.line}"
    val withColumn = if (diagnostic.column > 0) "$head:${diagnostic.column}" else head
    return "$withColumn - ${diagnostic.kind}"
}

/** Q2's state word, from the tree above — never from the last line's colour. */
@Composable
private fun headWord(head: OutputHead): String? = when (head) {
    OutputHead.Idle -> null
    is OutputHead.Working -> stringResource(
        when {
            head.installing -> R.string.output_state_installing
            head.phase == OutputPhase.RUNNING -> R.string.output_state_running
            else -> R.string.output_state_building
        }
    )
    OutputHead.Waiting -> stringResource(R.string.output_state_waiting)
    is OutputHead.Done -> stringResource(R.string.output_state_done)
    is OutputHead.Failed -> stringResource(R.string.output_state_failed)
    OutputHead.Stopped -> stringResource(R.string.output_state_stopped)
    OutputHead.Serving -> stringResource(R.string.output_state_serving)
}

private fun headExit(head: OutputHead): Int? = when (head) {
    is OutputHead.Done -> head.exitCode
    is OutputHead.Failed -> head.exitCode
    else -> null
}

private fun headTint(head: OutputHead): Color = when (head) {
    is OutputHead.Failed -> Color(0xFFFF5555)
    OutputHead.Stopped -> Color(0xFFFFB347)
    OutputHead.Waiting -> Color(0xFF66B2FF)
    is OutputHead.Working -> Color(0xFF66B2FF)
    OutputHead.Serving -> Color(0xFF55FF55)
    is OutputHead.Done -> Color(0xFF55FF55)
    OutputHead.Idle -> Color(CodecPalette.MUTED_TEXT)
}

private fun headIcon(head: OutputHead): ImageVector = when (head) {
    is OutputHead.Working -> Icons.Default.PlayArrow
    OutputHead.Waiting -> Icons.Default.Send
    is OutputHead.Done -> Icons.Default.CheckCircle
    is OutputHead.Failed -> Icons.Default.Warning
    OutputHead.Stopped -> Icons.Default.Close
    OutputHead.Serving -> Icons.Default.Visibility
    OutputHead.Idle -> Icons.Default.Terminal
}

/**
 * Phase 23.1, extended by 70.1 — the panel's line, styled as terminal text so
 * the cursor reads as part of the output stream. It is the same field in both
 * modes: a waiting program's stdin, or a command of the user's own. Enter
 * submits via the keyboard's Send action (or the send icon for touch users);
 * `singleLine` + `ImeAction.Send` is the same proven wiring Phase 23.1 used.
 *
 * The field takes focus when a program starts waiting (Q3: *“the ↵ key opens/
 * focuses the field”*), so the soft keyboard is already up when the prompt
 * arrives.
 */
@Composable
private fun ConsoleLine(
    buffer: String,
    waiting: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(waiting) {
        if (waiting) runCatching { focusRequester.requestFocus() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(Color(0xFF1E1E1E))
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = buffer,
            onValueChange = onInputChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontFamily = CodecType.codeFamily,
                fontSize = 13.sp
            ),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { innerTextField ->
                if (buffer.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (waiting) R.string.output_console_stdin_hint
                            else R.string.output_console_hint
                        ),
                        color = Color(CodecPalette.MUTED_TEXT),
                        fontSize = 12.sp,
                        fontFamily = CodecType.codeFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                innerTextField()
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .focusRequester(focusRequester)
        )
        IconButton(onClick = onSubmit) {
            Icon(
                Icons.Default.Send,
                contentDescription = stringResource(R.string.output_console_send),
                tint = Color(0xFF66B2FF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
