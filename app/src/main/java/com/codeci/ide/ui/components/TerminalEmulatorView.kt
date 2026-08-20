package com.codeci.ide.ui.components

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import com.codeci.ide.ui.terminal.CellFlags
import com.codeci.ide.ui.terminal.TerminalSnapshot
import com.codeci.ide.ui.terminal.XtermColors
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import kotlin.math.max

private val DefaultBg = Color(0xFF121212)
private val CursorColor = Color(0xFF55FF55)

@Composable
fun TerminalEmulatorView(
    snapshot: TerminalSnapshot,
    fontSizeSp: Float,
    onInput: (String) -> Unit,
    onResize: (cols: Int, rows: Int) -> Unit,
    onFontScale: (Float) -> Unit,
    onPaste: () -> Unit,
    cursorSequence: (Char) -> String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue("")) }

    val paint = remember(fontSizeSp, density) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = with(density) { fontSizeSp.sp.toPx() }
            color = 0xFFE5E5E5.toInt()
        }
    }
    val cellW = remember(paint) { max(paint.measureText("M"), 1f) }
    val cellH = remember(paint) { max(paint.fontSpacing, paint.textSize * 1.2f) }
    val ascent = remember(paint) { paint.fontMetrics.ascent }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(DefaultBg)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val cols = max(1, (widthPx / cellW).toInt())
        val rows = max(1, (heightPx / cellH).toInt())
        LaunchedEffect(cols, rows) { onResize(cols, rows) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        },
                        onLongPress = { onPaste() }
                    )
                }
                .pointerInput(fontSizeSp) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) onFontScale(fontSizeSp * zoom)
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    handleHardwareKey(event.key, event.isCtrlPressed, onInput, cursorSequence)
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(DefaultBg)
                val visibleRows = minOf(snapshot.rows, rows)
                val visibleCols = minOf(snapshot.cols, cols)
                for (y in 0 until visibleRows) {
                    val line = snapshot.lines.getOrNull(y) ?: continue
                    for (run in line.runs) {
                        val start = run.start.coerceIn(0, visibleCols)
                        val end = run.end.coerceIn(0, visibleCols)
                        if (start >= end) continue
                        val bold = run.flags and CellFlags.BOLD != 0
                        val inverse = run.flags and CellFlags.INVERSE != 0
                        val invisible = run.flags and CellFlags.INVISIBLE != 0
                        val fgRgb = XtermColors.toRgb(
                            run.fg,
                            0xE5E5E5,
                            bold
                        )
                        val bgRgb = XtermColors.toRgb(run.bg, 0x121212, false)
                        val drawFg = if (inverse) bgRgb else fgRgb
                        val drawBg = if (inverse) fgRgb else bgRgb
                        if (drawBg != 0x121212) {
                            drawRect(
                                color = Color(
                                    red = (drawBg shr 16) and 0xFF,
                                    green = (drawBg shr 8) and 0xFF,
                                    blue = drawBg and 0xFF
                                ),
                                topLeft = Offset(start * cellW, y * cellH),
                                size = Size((end - start) * cellW, cellH)
                            )
                        }
                        if (invisible) continue
                        val slice = line.text.substring(
                            start.coerceAtMost(line.text.length),
                            end.coerceAtMost(line.text.length)
                        )
                        if (slice.isEmpty()) continue
                        paint.color = (0xFF000000.toInt()) or drawFg
                        paint.isFakeBoldText = bold
                        paint.isUnderlineText = run.flags and CellFlags.UNDERLINE != 0
                        paint.textSkewX = if (run.flags and CellFlags.ITALIC != 0) -0.25f else 0f
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                slice,
                                start * cellW,
                                y * cellH - ascent,
                                paint
                            )
                        }
                    }
                }
                if (snapshot.cursorVisible &&
                    snapshot.cursorX in 0 until visibleCols &&
                    snapshot.cursorY in 0 until visibleRows
                ) {
                    drawRect(
                        color = CursorColor.copy(alpha = 0.7f),
                        topLeft = Offset(snapshot.cursorX * cellW, snapshot.cursorY * cellH),
                        size = Size(cellW, cellH)
                    )
                }
            }

            BasicTextField(
                value = field,
                onValueChange = { next ->
                    val inserted = insertedText(field, next)
                    if (inserted.isNotEmpty()) onInput(inserted)
                    field = TextFieldValue("")
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.None,
                    keyboardType = KeyboardType.Ascii
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.01f)
                    .focusRequester(focusRequester)
            )
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
