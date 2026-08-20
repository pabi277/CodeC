package com.codeci.ide.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.ide.R
import com.codeci.ide.ui.terminal.CellFlags
import com.codeci.ide.ui.terminal.TerminalLine
import com.codeci.ide.ui.terminal.TerminalSnapshot
import com.codeci.ide.ui.terminal.XtermColors
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import kotlin.math.max

private val DefaultBg = Color(0xFF121212)
private const val DefaultFgRgb = 0xE5E5E5
private val CursorColor = Color(0xFF55FF55)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TerminalEmulatorView(
    snapshot: TerminalSnapshot,
    fontSizeSp: Float,
    onInput: (String) -> Unit,
    onResize: (cols: Int, rows: Int) -> Unit,
    onFontScale: (Float) -> Unit,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
    cursorSequence: (Char) -> String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    var followOutput by remember { mutableStateOf(true) }

    val cellW = remember(fontSizeSp, density) {
        with(density) { max(fontSizeSp.sp.toPx() * 0.6f, 1f) }
    }
    val cellH = remember(fontSizeSp, density) {
        with(density) { max(fontSizeSp.sp.toPx() * 1.25f, 1f) }
    }

    val displayLines = remember(snapshot) {
        val lastContent = snapshot.lines.indexOfLast { line ->
            line.text.any { !it.isWhitespace() }
        }
        val liveEnd = maxOf(snapshot.cursorY, lastContent).coerceAtLeast(0)
        snapshot.scrollbackLines + snapshot.lines.take(liveEnd + 1)
    }
    val cursorRow = snapshot.scrollbackCount + snapshot.cursorY

    LaunchedEffect(snapshot.generation, displayLines.size, cursorRow) {
        if (displayLines.isEmpty()) return@LaunchedEffect
        followOutput = true
        val target = cursorRow.coerceIn(0, displayLines.lastIndex)
        listState.scrollToItem(target)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DefaultBg)
            .pointerInput(fontSizeSp) {
                detectTwoFingerPinch { zoom ->
                    if (zoom != 1f) onFontScale(fontSizeSp * zoom)
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                handleHardwareKey(event.key, event.isCtrlPressed, onInput, cursorSequence)
            }
    ) {
        val cols = max(1, (with(density) { maxWidth.toPx() } / cellW).toInt())
        val rows = max(1, (with(density) { maxHeight.toPx() } / cellH).toInt())
        LaunchedEffect(cols, rows) { onResize(cols, rows) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(displayLines) { index, line ->
                val showCursor = snapshot.cursorVisible && index == cursorRow
                TextLine(
                    line = line,
                    fontSizeSp = fontSizeSp,
                    cursorX = if (showCursor) snapshot.cursorX else -1,
                    onTap = {
                        followOutput = true
                        focusRequester.requestFocus()
                        keyboard?.show()
                    },
                    onCopy = onCopy,
                    onPaste = onPaste
                )
            }
        }

        BasicTextField(
            value = field,
            onValueChange = { next ->
                val inserted = insertedText(field, next)
                if (inserted.isNotEmpty()) {
                    followOutput = true
                    onInput(inserted)
                }
                field = TextFieldValue("")
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.None,
                keyboardType = KeyboardType.Ascii
            ),
            modifier = Modifier
                .size(1.dp)
                .alpha(0.01f)
                .focusRequester(focusRequester)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextLine(
    line: TerminalLine,
    fontSizeSp: Float,
    cursorX: Int,
    onTap: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Text(
            text = line.toAnnotatedString(cursorX),
            fontFamily = FontFamily.Monospace,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.25f).sp,
            color = packedColor(DefaultFgRgb),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = { menu = true }
                )
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.terminal_copy)) },
                onClick = {
                    menu = false
                    onCopy()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.terminal_paste)) },
                onClick = {
                    menu = false
                    onPaste()
                }
            )
        }
    }
}

private fun TerminalLine.toAnnotatedString(cursorX: Int) = buildAnnotatedString {
    val padded = if (cursorX >= text.length) {
        text + " ".repeat(cursorX - text.length + 1)
    } else {
        text
    }
    val effectiveRuns = if (runs.isEmpty()) {
        listOf(com.codeci.ide.ui.terminal.StyleRun(0, padded.length, XtermColors.COLOR_DEFAULT_FG, XtermColors.COLOR_DEFAULT_BG, 0))
    } else {
        runs
    }
    for (run in effectiveRuns) {
        val start = run.start.coerceIn(0, padded.length)
        val end = run.end.coerceIn(start, padded.length)
        if (start >= end) continue
        appendStyledRange(padded, start, end, run.fg, run.bg, run.flags, cursorX)
    }
    if (cursorX >= 0 && cursorX >= (effectiveRuns.lastOrNull()?.end ?: 0) && cursorX < padded.length) {
        appendStyledRange(
            padded, cursorX, cursorX + 1,
            XtermColors.COLOR_DEFAULT_FG, XtermColors.COLOR_DEFAULT_BG, 0, cursorX
        )
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendStyledRange(
    text: String,
    start: Int,
    end: Int,
    fg: Int,
    bg: Int,
    flags: Int,
    cursorX: Int
) {
    var i = start
    while (i < end) {
        val inCursor = i == cursorX
        val runEnd = if (inCursor) i + 1 else if (cursorX in (i + 1) until end) cursorX else end
        val bold = flags and CellFlags.BOLD != 0
        val inverse = flags and CellFlags.INVERSE != 0
        val invisible = flags and CellFlags.INVISIBLE != 0
        val fgRgb = XtermColors.toRgb(fg, DefaultFgRgb, bold)
        val bgRgb = XtermColors.toRgb(bg, 0x121212, false)
        val drawFg = if (inverse) bgRgb else fgRgb
        val drawBg = if (inverse) fgRgb else bgRgb
        withStyle(
            SpanStyle(
                color = when {
                    inCursor -> Color(0xFF121212)
                    invisible -> Color.Transparent
                    else -> packedColor(drawFg)
                },
                background = when {
                    inCursor -> CursorColor
                    drawBg == 0x121212 -> Color.Unspecified
                    else -> packedColor(drawBg)
                },
                fontWeight = if (bold) FontWeight.Bold else null,
                textDecoration = if (flags and CellFlags.UNDERLINE != 0) {
                    TextDecoration.Underline
                } else {
                    null
                }
            )
        ) {
            append(text.substring(i, runEnd))
        }
        i = runEnd
    }
}

private fun packedColor(packed: Int): Color = Color(
    red = (packed shr 16) and 0xFF,
    green = (packed shr 8) and 0xFF,
    blue = packed and 0xFF
)

private suspend fun PointerInputScope.detectTwoFingerPinch(onZoom: (Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var lastDistance = 0f
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                val distance = (pressed[0].position - pressed[1].position).getDistance()
                if (lastDistance > 0f && lastDistance != distance) {
                    onZoom(distance / lastDistance)
                }
                lastDistance = distance
                pressed.forEach { it.consume() }
            } else {
                lastDistance = 0f
            }
        }
    }
}

private fun insertedText(old: TextFieldValue, next: TextFieldValue): String {
    if (next.text.isEmpty()) {
        return if (old.text.isNotEmpty()) "\u007f" else ""
    }
    if (next.composition != null && next.text == old.text) return ""
    if (next.text.startsWith(old.text)) return next.text.substring(old.text.length)
    return next.text
}

private fun handleHardwareKey(
    key: Key,
    ctrl: Boolean,
    onInput: (String) -> Unit,
    cursorSequence: (Char) -> String
): Boolean {
    when (key) {
        Key.Enter, Key.NumPadEnter -> {
            onInput("\r")
            return true
        }
        Key.Backspace -> {
            onInput("\u007f")
            return true
        }
        Key.Tab -> {
            onInput("\t")
            return true
        }
        Key.Escape -> {
            onInput("\u001b")
            return true
        }
        Key.DirectionUp -> {
            onInput(cursorSequence('A'))
            return true
        }
        Key.DirectionDown -> {
            onInput(cursorSequence('B'))
            return true
        }
        Key.DirectionRight -> {
            onInput(cursorSequence('C'))
            return true
        }
        Key.DirectionLeft -> {
            onInput(cursorSequence('D'))
            return true
        }
        Key.MoveHome, Key.Home -> {
            onInput("\u001b[H")
            return true
        }
        Key.MoveEnd -> {
            onInput("\u001b[F")
            return true
        }
        Key.PageUp -> {
            onInput("\u001b[5~")
            return true
        }
        Key.PageDown -> {
            onInput("\u001b[6~")
            return true
        }
        else -> {
            if (ctrl) {
                val name = key.toString().substringAfter("Key:").trim()
                val ch = name.singleOrNull()?.lowercaseChar()
                if (ch != null) {
                    onInput(TerminalViewModel.ctrl(ch).toString())
                    return true
                }
            }
            return false
        }
    }
}
