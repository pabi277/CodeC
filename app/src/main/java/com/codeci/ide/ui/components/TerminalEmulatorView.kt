package com.codeci.ide.ui.components

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.codeci.ide.R
import com.codeci.ide.ui.terminal.CellFlags
import com.codeci.ide.ui.terminal.StyleRun
import com.codeci.ide.ui.terminal.TerminalLine
import com.codeci.ide.ui.terminal.TerminalSnapshot
import com.codeci.ide.ui.terminal.XtermColors
import com.codeci.ide.ui.theme.DraculaTerminalTheme
import com.codeci.ide.ui.theme.TerminalThemeColors
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Termux-style terminal surface: a Canvas character grid whose cell size is
 * [Paint.measureText("X")] / [Paint.getFontSpacing], matching the PTY
 * cols/rows. Scrollback is [topRow] (0 = live screen, negative = history),
 * the same model as Termux's TerminalView.mTopRow.
 */
private val DefaultBg = Color(0xFF121212)
private const val DefaultFgRgb = 0xE5E5E5
private val CursorColor = Color(0xFF55FF55)
private val SelectionColor = Color(0x6680CBC4)

data class GridSelection(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int
)

@Composable
fun TerminalEmulatorView(
    snapshot: TerminalSnapshot,
    fontSizeSp: Float,
    fontFamily: String = "Monospace",
    theme: TerminalThemeColors = DraculaTerminalTheme,
    onInput: (String) -> Unit,
    onResize: (cols: Int, rows: Int) -> Unit,
    onFontScale: (Float) -> Unit,
    onPaste: () -> Unit,
    onCopyText: (String) -> Unit,
    cursorSequence: (Char) -> String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var keyView by remember { mutableStateOf<TerminalKeyView?>(null) }

    val typeface = remember(fontFamily) {
        when (fontFamily) {
            "Courier" -> Typeface.MONOSPACE
            "Sans Serif" -> Typeface.SANS_SERIF
            "Serif" -> Typeface.SERIF
            else -> Typeface.MONOSPACE
        }
    }

    val paint = remember(fontSizeSp, density, typeface) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = with(density) { fontSizeSp.sp.toPx() }
        }
    }
    // Termux TerminalRenderer: width = measureText("X"), height = fontSpacing.
    val cellW = remember(paint) { max(paint.measureText("X"), 1f) }
    val cellH = remember(paint) { max(paint.fontSpacing, 1f) }
    val ascent = remember(paint) { paint.fontMetrics.ascent }

    // Termux mTopRow: 0 = live screen, negative = scrolled into transcript.
    var topRow by remember { mutableIntStateOf(0) }
    var selection by remember { mutableStateOf<GridSelection?>(null) }
    var menu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var selecting by remember { mutableStateOf(false) }

    val minTop = -snapshot.scrollbackCount
    if (topRow < minTop) topRow = minTop
    if (topRow > 0) topRow = 0

    // Termux onScreenUpdated: jump to live screen unless a selection is active.
    LaunchedEffect(snapshot.generation) {
        if (selection == null) topRow = 0
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Enter is delivered via the IME TextField as \n → \r. Handling
                // it here as well duplicated the command (main defined twice).
                if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    return@onPreviewKeyEvent false
                }
                handleHardwareKey(event.key, event.isCtrlPressed, onInput, cursorSequence)
            }
    ) {
        val cols = max(1, (with(density) { maxWidth.toPx() } / cellW).toInt())
        val rows = max(1, (with(density) { maxHeight.toPx() } / cellH).toInt())
        LaunchedEffect(cols, rows) { onResize(cols, rows) }

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cellW, cellH, snapshot.generation, snapshot.scrollbackCount) {
                        detectTapGestures(
                            onTap = {
                                selecting = false
                                selection = null
                                keyView?.showIme()
                            },
                            onLongPress = { pos ->
                                val col = (pos.x / cellW).toInt().coerceIn(0, cols - 1)
                                val row = (pos.y / cellH).toInt().coerceIn(0, rows - 1)
                                val y = topRow + row
                                selection = GridSelection(col, y, col, y)
                                selecting = true
                                menuOffset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                menu = true
                            }
                        )
                    }
                    .pointerInput(cellH, snapshot.scrollbackCount, selecting) {
                        detectScrollOrSelect(
                            cellW = cellW,
                            cellH = cellH,
                            cols = cols,
                            rows = rows,
                            selecting = { selecting },
                            onScroll = { dy ->
                                val deltaRows = (dy / cellH).toInt()
                                if (deltaRows != 0) {
                                    topRow = (topRow + deltaRows).coerceIn(-snapshot.scrollbackCount, 0)
                                }
                            },
                            onSelectMove = { col, row ->
                                val cur = selection ?: return@detectScrollOrSelect
                                selection = cur.copy(
                                    x2 = col.coerceIn(0, cols - 1),
                                    y2 = (topRow + row).coerceIn(-snapshot.scrollbackCount, snapshot.rows - 1)
                                )
                            }
                        )
                    }
                    .pointerInput(fontSizeSp) {
                        detectTwoFingerPinch { zoom ->
                            if (zoom != 1f && abs(zoom - 1f) > 0.01f) onFontScale(fontSizeSp * zoom)
                        }
                    }
            ) {
                drawRect(theme.background)
                val transcript = snapshot.scrollbackLines + snapshot.lines
                val origin = snapshot.scrollbackCount
                for (screenY in 0 until rows) {
                    val extY = topRow + screenY
                    val lineIndex = origin + extY
                    if (lineIndex !in transcript.indices) continue
                    val line = transcript[lineIndex]
                    val isCursorRow = snapshot.cursorVisible &&
                        topRow == 0 &&
                        extY == snapshot.cursorY
                    drawLine(
                        line = line,
                        y = screenY,
                        cellW = cellW,
                        cellH = cellH,
                        ascent = ascent,
                        paint = paint,
                        cursorX = if (isCursorRow) snapshot.cursorX else -1,
                        selection = selection,
                        extY = extY,
                        cols = cols,
                        theme = theme
                    )
                }
            }

            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.terminal_copy)) },
                    onClick = {
                        menu = false
                        val text = selection?.let { snapshot.selectedText(it) }
                            ?: snapshot.transcriptText()
                        onCopyText(text)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.terminal_paste)) },
                    onClick = {
                        menu = false
                        selecting = false
                        selection = null
                        onPaste()
                    }
                )
            }
        }

        AndroidView(
            factory = { ctx -> TerminalKeyView(ctx) },
            update = { view ->
                if (keyView !== view) keyView = view
                view.onInput = { text ->
                    if (text.isNotEmpty()) {
                        selecting = false
                        selection = null
                        topRow = 0
                        onInput(text)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
        )
        LaunchedEffect(keyView) {
            keyView?.post { keyView?.showIme() }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLine(
    line: TerminalLine,
    y: Int,
    cellW: Float,
    cellH: Float,
    ascent: Float,
    paint: android.graphics.Paint,
    cursorX: Int,
    selection: GridSelection?,
    extY: Int,
    cols: Int,
    theme: TerminalThemeColors
) {
    val text = line.text
    val runs = if (line.runs.isEmpty()) {
        listOf(StyleRun(0, text.length, XtermColors.COLOR_DEFAULT_FG, XtermColors.COLOR_DEFAULT_BG, 0))
    } else {
        line.runs
    }
    val sel = selection?.normalized()
    for (run in runs) {
        val start = run.start.coerceIn(0, cols)
        val end = run.end.coerceIn(start, cols)
        if (start >= end) continue
        val bold = run.flags and CellFlags.BOLD != 0
        val inverse = run.flags and CellFlags.INVERSE != 0
        val invisible = run.flags and CellFlags.INVISIBLE != 0
        val fgRgb = XtermColors.toRgb(run.fg, theme.foregroundRgb, bold)
        val bgRgb = XtermColors.toRgb(run.bg, theme.backgroundRgb, false)
        val drawFg = if (inverse) bgRgb else fgRgb
        val drawBg = if (inverse) fgRgb else bgRgb
        if (drawBg != theme.backgroundRgb) {
            drawRect(
                color = packedColor(drawBg),
                topLeft = Offset(start * cellW, y * cellH),
                size = Size((end - start) * cellW, cellH)
            )
        }
        if (invisible) continue
        val sliceEnd = end.coerceAtMost(text.length)
        val sliceStart = start.coerceAtMost(text.length)
        if (sliceStart >= sliceEnd) continue
        val slice = text.substring(sliceStart, sliceEnd)
        paint.color = (0xFF000000.toInt()) or drawFg
        paint.isFakeBoldText = bold
        paint.isUnderlineText = run.flags and CellFlags.UNDERLINE != 0
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(slice, start * cellW, y * cellH - ascent, paint)
        }
    }
    if (sel != null && extY in minOf(sel.y1, sel.y2)..maxOf(sel.y1, sel.y2)) {
        val left = if (extY == sel.y1 && extY == sel.y2) {
            minOf(sel.x1, sel.x2)
        } else if (extY == minOf(sel.y1, sel.y2)) {
            if (sel.y1 <= sel.y2) sel.x1 else sel.x2
        } else if (extY == maxOf(sel.y1, sel.y2)) {
            0
        } else {
            0
        }
        val right = if (extY == sel.y1 && extY == sel.y2) {
            maxOf(sel.x1, sel.x2) + 1
        } else if (extY == minOf(sel.y1, sel.y2)) {
            cols
        } else if (extY == maxOf(sel.y1, sel.y2)) {
            (if (sel.y1 <= sel.y2) sel.x2 else sel.x1) + 1
        } else {
            cols
        }
        drawRect(
            color = theme.selection,
            topLeft = Offset(left * cellW, y * cellH),
            size = Size(((right - left).coerceAtLeast(0)) * cellW, cellH)
        )
    }
    if (cursorX in 0 until cols) {
        drawRect(
            color = theme.cursor.copy(alpha = 0.7f),
            topLeft = Offset(cursorX * cellW, y * cellH),
            size = Size(cellW, cellH)
        )
    }
}

private fun packedColor(packed: Int): Color = Color(
    red = (packed shr 16) and 0xFF,
    green = (packed shr 8) and 0xFF,
    blue = packed and 0xFF
)

private fun GridSelection.normalized(): GridSelection {
    val aFirst = y1 < y2 || (y1 == y2 && x1 <= x2)
    return if (aFirst) this else GridSelection(x2, y2, x1, y1)
}

fun TerminalSnapshot.selectedText(sel: GridSelection): String {
    val n = sel.normalized()
    val transcript = scrollbackLines + lines
    val origin = scrollbackCount
    return buildString {
        for (y in n.y1..n.y2) {
            val idx = origin + y
            if (idx !in transcript.indices) continue
            val line = transcript[idx].text
            val start = if (y == n.y1) n.x1.coerceAtLeast(0) else 0
            val end = if (y == n.y2) (n.x2 + 1).coerceAtMost(line.length) else line.length
            if (start < end && start < line.length) {
                append(line.substring(start, end.coerceAtMost(line.length)).trimEnd())
            }
            if (y != n.y2) append('\n')
        }
    }.trimEnd()
}

private suspend fun PointerInputScope.detectScrollOrSelect(
    cellW: Float,
    cellH: Float,
    cols: Int,
    rows: Int,
    selecting: () -> Boolean,
    onScroll: (Float) -> Unit,
    onSelectMove: (Int, Int) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var lastY = down.position.y
        var dragged = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed) break
            val dy = change.position.y - lastY
            if (abs(dy) > 1f || abs(change.positionChange().x) > 1f) dragged = true
            if (selecting()) {
                val col = floor(change.position.x / cellW).toInt().coerceIn(0, cols - 1)
                val row = floor(change.position.y / cellH).toInt().coerceIn(0, rows - 1)
                onSelectMove(col, row)
                change.consume()
            } else if (dragged) {
                onScroll(-dy)
                lastY = change.position.y
                change.consume()
            }
        }
    }
}

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

private fun handleHardwareKey(
    key: Key,
    ctrl: Boolean,
    onInput: (String) -> Unit,
    cursorSequence: (Char) -> String
): Boolean {
    when (key) {
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
