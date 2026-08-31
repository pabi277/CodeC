package com.codeci.ide.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.codeci.ide.R
import com.codeci.ide.ui.terminal.CellFlags
import com.codeci.ide.ui.terminal.CellMetrics
import com.codeci.ide.ui.terminal.MouseEncoding
import com.codeci.ide.ui.terminal.MouseModes
import com.codeci.ide.ui.terminal.StyleRun
import com.codeci.ide.ui.terminal.TerminalLine
import com.codeci.ide.ui.terminal.TerminalSnapshot
import com.codeci.ide.ui.terminal.XtermColors
import com.codeci.ide.ui.theme.DraculaTerminalTheme
import com.codeci.ide.ui.theme.TerminalThemeColors
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
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

fun findUrlAt(text: String, col: Int): String? {
    val regex = Regex("https?://[^\\s<>\"]+")
    for (match in regex.findAll(text)) {
        if (col in match.range.first..match.range.last) {
            return match.value
        }
    }
    return null
}

fun findWordBoundaries(text: String, col: Int): Pair<Int, Int> {
    if (text.isEmpty() || col !in text.indices) return col to col
    val isWordChar = { c: Char -> c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '/' }
    var start = col
    var end = col
    while (start > 0 && isWordChar(text[start - 1])) start--
    while (end < text.length - 1 && isWordChar(text[end + 1])) end++
    return start to end
}

fun openTerminalUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open URL: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun triggerTerminalBellFeedback(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        }
    } catch (_: Exception) {}
}

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
    modifier: Modifier = Modifier,
    bellTrigger: Long = 0L,
    onSelectionChanged: (String?) -> Unit = {},
    onUrlClick: (String) -> Unit = {},
    /** Phase 19.5: raw mouse-reporting bytes for the running program. */
    onMouseEvent: (String) -> Unit = {},
    /** Phase 19.5: terminal RESET (RIS) from the context menu. */
    onReset: () -> Unit = {},
    /** Re-fires [onResize] when this key changes (e.g. active session id, Phase 7). */
    resizeKey: Any? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var keyView by remember { mutableStateOf<TerminalKeyView?>(null) }

    // Reactive local font size for instant smooth pinch-to-zoom
    var activeFontSizeSp by remember(fontSizeSp) { mutableFloatStateOf(fontSizeSp) }

    val typeface = remember(fontFamily) {
        when (fontFamily) {
            "Courier" -> Typeface.MONOSPACE
            "Sans Serif" -> Typeface.SANS_SERIF
            "Serif" -> Typeface.SERIF
            else -> Typeface.MONOSPACE
        }
    }

    // Settled paint for PTY rows/cols sizing (avoids resizing PTY repeatedly
    // during pinch). Phase 19.2: cells are INTEGER pixels — a fractional
    // cellW made (col * cellW) drift across a row and glyphs collide.
    // Device-round fix (2026-08-31): the size is additionally FITTED to the
    // grid (CellMetrics.fitSizeToGrid) so the cell EQUALS the font advance —
    // plain ceil() left up to 1px of slack per letter, which the owner saw
    // as "a noticeable gap between them".
    val settledPaint = remember(fontSizeSp, density, typeface) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = with(density) { fontSizeSp.sp.toPx() }
            configureGridPaint()
        }
    }
    val settledGrid = remember(settledPaint) { fitGridPaint(settledPaint) }
    val settledCellW = settledGrid.cellW
    val settledCellH = settledGrid.cellH

    // Active visual paint for 60fps instant pinch rendering (fitted to the
    // pixel grid exactly like the settled paint).
    val paint = remember(activeFontSizeSp, density, typeface) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = with(density) { activeFontSizeSp.sp.toPx() }
            configureGridPaint()
        }
    }
    // Fit FIRST: fitGridPaint mutates paint.textSize, and boldPaint below
    // copies the fitted paint so bold shares the snapped size and grid.
    val grid = remember(paint) { fitGridPaint(paint) }
    val cellW = grid.cellW
    val cellH = grid.cellH
    val ascent = grid.ascent

    // Real bold face (keeps the monospace advance when available) instead of
    // isFakeBoldText, whose stroke thickening pushes past the cell edge.
    val boldPaint = remember(paint, typeface) {
        android.graphics.Paint(paint).apply {
            // `this.` — the bare name would resolve to the OUTER val
            // typeface (the family face local), a reassignment error.
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
        }
    }

    // Termux mTopRow: 0 = live screen, negative = scrolled into transcript.
    var topRow by remember { mutableIntStateOf(0) }
    var wheelRemainder by remember { mutableFloatStateOf(0f) }
    var selection by remember { mutableStateOf<GridSelection?>(null) }
    var menu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var selecting by remember { mutableStateOf(false) }

    var flashAlpha by remember { mutableFloatStateOf(0f) }

    // BEL visual flash + vibration
    LaunchedEffect(bellTrigger) {
        if (bellTrigger > 0L) {
            triggerTerminalBellFeedback(context)
            flashAlpha = 0.35f
            delay(120)
            flashAlpha = 0f
        }
    }

    val minTop = -snapshot.scrollbackCount
    if (topRow < minTop) topRow = minTop
    if (topRow > 0) topRow = 0

    // Update active selection state for parent toolbar copy
    LaunchedEffect(selection, snapshot.generation) {
        val selectedText = selection?.let { snapshot.selectedText(it) }?.takeIf { it.isNotEmpty() }
        onSelectionChanged(selectedText)
    }

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
        val ptyCols = CellMetrics.columnsForWidth(with(density) { maxWidth.toPx() }, settledCellW)
        val ptyRows = CellMetrics.rowsForHeight(with(density) { maxHeight.toPx() }, settledCellH)
        // resizeKey (Phase 7): re-apply the grid to the PTY when the *bound
        // session* changes even if the view's own size did not — otherwise a
        // freshly created session keeps the emulator-default 80x24 grid and
        // the cursor drifts (the exact bug class Phase 6.1 closed).
        LaunchedEffect(ptyCols, ptyRows, resizeKey) { onResize(ptyCols, ptyRows) }

        val cols = CellMetrics.columnsForWidth(with(density) { maxWidth.toPx() }, cellW)
        val rows = CellMetrics.rowsForHeight(with(density) { maxHeight.toPx() }, cellH)

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cellW, cellH, snapshot.generation, snapshot.scrollbackCount) {
                        detectTapGestures(
                            onTap = { pos ->
                                val col = (pos.x / cellW).toInt().coerceIn(0, cols - 1)
                                val row = (pos.y / cellH).toInt().coerceIn(0, rows - 1)
                                if (snapshot.mouseMode and MouseModes.CAPTURE_MASK != 0) {
                                    // Phase 19.5: the app owns the mouse —
                                    // a tap is a left click at that cell.
                                    val sgr = snapshot.mouseMode and MouseModes.SGR_EXT != 0
                                    MouseEncoding.press(0, col + 1, row + 1, sgr)?.let(onMouseEvent)
                                    MouseEncoding.release(0, col + 1, row + 1, sgr)?.let(onMouseEvent)
                                    return@detectTapGestures
                                }
                                val line = snapshot.lineAt(topRow + row)
                                val url = if (line != null) {
                                    findUrlAt(line.text, col)
                                } else null

                                if (url != null) {
                                    onUrlClick(url)
                                } else {
                                    selecting = false
                                    selection = null
                                    keyView?.showIme()
                                }
                            },
                            onLongPress = { pos ->
                                val col = (pos.x / cellW).toInt().coerceIn(0, cols - 1)
                                val row = (pos.y / cellH).toInt().coerceIn(0, rows - 1)
                                val y = topRow + row
                                val line = snapshot.lineAt(y)
                                val (wStart, wEnd) = if (line != null) {
                                    findWordBoundaries(line.text, col)
                                } else col to col
                                selection = GridSelection(wStart, y, wEnd, y)
                                selecting = true
                                menuOffset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                menu = true
                            }
                        )
                    }
                    .pointerInput(
                        cellW,
                        cellH,
                        snapshot.scrollbackCount,
                        snapshot.mouseMode,
                        selecting
                    ) {
                        detectScrollOrSelect(
                            cellW = cellW,
                            cellH = cellH,
                            cols = cols,
                            rows = rows,
                            selecting = { selecting },
                            onScroll = { dy, x, y ->
                                if (snapshot.mouseMode and MouseModes.CAPTURE_MASK != 0) {
                                    // Termux-style touch mapping: while the app
                                    // reports the mouse, swipes act as the
                                    // WHEEL (buttons 64/65), so htop/vim/tmux
                                    // scroll instead of the local scrollback.
                                    wheelRemainder += dy
                                    val units = (wheelRemainder / cellH).toInt()
                                    if (units != 0) {
                                        wheelRemainder -= units * cellH
                                        val sgr = snapshot.mouseMode and MouseModes.SGR_EXT != 0
                                        val col = (x / cellW).toInt().coerceIn(0, cols - 1)
                                        val row = (y / cellH).toInt().coerceIn(0, rows - 1)
                                        val dir = if (units > 0) MouseEncoding.WHEEL_DOWN else MouseEncoding.WHEEL_UP
                                        repeat(kotlin.math.abs(units)) {
                                            MouseEncoding.wheel(dir, col + 1, row + 1, sgr)?.let(onMouseEvent)
                                        }
                                    }
                                } else {
                                    wheelRemainder = 0f
                                    val deltaRows = (dy / cellH).toInt()
                                    if (deltaRows != 0) {
                                        topRow = (topRow + deltaRows).coerceIn(-snapshot.scrollbackCount, 0)
                                    }
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
                    .pointerInput(Unit) {
                        detectSmoothPinch(
                            onZoomChange = { factor ->
                                activeFontSizeSp = (activeFontSizeSp * factor).coerceIn(8f, 32f)
                            },
                            onZoomEnd = {
                                onFontScale(activeFontSizeSp)
                            }
                        )
                    }
            ) {
                drawRect(theme.background)
                for (screenY in 0 until rows) {
                    val extY = topRow + screenY
                    val line = snapshot.lineAt(extY) ?: continue
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
                        boldPaint = boldPaint,
                        cursorX = if (isCursorRow) snapshot.cursorX else -1,
                        selection = selection,
                        extY = extY,
                        cols = cols,
                        theme = theme
                    )
                }
            }

            if (flashAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = flashAlpha))
                )
            }

            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false }
            ) {
                val selectedText = selection?.let { snapshot.selectedText(it) }
                val selectedUrl = selectedText?.let { findUrlAt(it, 0) }

                if (selectedUrl != null) {
                    DropdownMenuItem(
                        text = { Text("Open URL") },
                        onClick = {
                            menu = false
                            onUrlClick(selectedUrl)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.terminal_copy)) },
                    onClick = {
                        menu = false
                        val text = selectedText ?: snapshot.transcriptText()
                        onCopyText(text)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Select All") },
                    onClick = {
                        menu = false
                        selection = GridSelection(
                            0, -snapshot.scrollbackCount,
                            cols - 1, snapshot.rows - 1
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy All") },
                    onClick = {
                        menu = false
                        onCopyText(snapshot.transcriptText())
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        menu = false
                        val text = selectedText ?: snapshot.transcriptText()
                        runCatching {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Share terminal text")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Reset") },
                    onClick = {
                        menu = false
                        onReset()
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
    cellW: Int,
    cellH: Int,
    ascent: Float,
    paint: android.graphics.Paint,
    boldPaint: android.graphics.Paint,
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
                topLeft = Offset(CellMetrics.columnX(start, cellW).toFloat(), CellMetrics.rowY(y, cellH).toFloat()),
                size = Size(((end - start) * cellW).toFloat(), cellH.toFloat())
            )
        }
        if (invisible) continue
        val sliceEnd = end.coerceAtMost(text.length)
        val sliceStart = start.coerceAtMost(text.length)
        if (sliceStart >= sliceEnd) continue
        val glyphPaint = if (bold) boldPaint else paint
        glyphPaint.color = (0xFF000000.toInt()) or drawFg
        glyphPaint.isUnderlineText = run.flags and CellFlags.UNDERLINE != 0

        // Phase 19.2: character-by-character drawing at exact INTEGER cell
        // origins — (start + i) * cellW is a whole pixel, so there is no
        // accumulated rounding drift and glyphs can never collide.
        // Phase 19.4: draws the base+marks CLUSTER when present (Indic and
        // combining marks render as one glyph run) and gives a double-width
        // lead cell a two-cell slot; continuation cells are blanks and draw
        // nothing here.
        val clusters = line.clusters
        drawIntoCanvas { canvas ->
            for (i in 0 until (sliceEnd - sliceStart)) {
                val charCol = sliceStart + i
                val glyph = clusters?.get(charCol) ?: text[charCol].toString()
                if (glyph.isNotEmpty() && glyph != " ") {
                    val wide = run.flags and CellFlags.WIDE_LEAD != 0 && charCol < cols - 1
                    val slot = if (wide) cellW * 2 else cellW
                    val x = CellMetrics.columnX(start + i, cellW).toFloat()
                    val baseline = CellMetrics.rowY(y, cellH) - ascent
                    val advance = glyphPaint.measureText(glyph)
                    if (advance > slot) {
                        // Fallback-font glyph or cluster wider than its slot:
                        // squeeze it in so it cannot bleed into the neighbour.
                        val savedScale = glyphPaint.textScaleX
                        glyphPaint.textScaleX = savedScale * (slot / advance)
                        canvas.nativeCanvas.drawText(glyph, x, baseline, glyphPaint)
                        glyphPaint.textScaleX = savedScale
                    } else {
                        canvas.nativeCanvas.drawText(glyph, x, baseline, glyphPaint)
                    }
                }
            }
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
            topLeft = Offset(CellMetrics.columnX(left, cellW).toFloat(), CellMetrics.rowY(y, cellH).toFloat()),
            size = Size(((right - left).coerceAtLeast(0)) * cellW.toFloat(), cellH.toFloat())
        )
    }
    if (cursorX in 0 until cols) {
        drawRect(
            color = theme.cursor.copy(alpha = 0.7f),
            topLeft = Offset(CellMetrics.columnX(cursorX, cellW).toFloat(), CellMetrics.rowY(y, cellH).toFloat()),
            size = Size(cellW.toFloat(), cellH.toFloat())
        )
    }
}

/**
 * Phase 19.2 grid paint hygiene: no letter spacing or horizontal scaling of
 * the font's own advances (they must equal the integer cell), subpixel
 * positioning for crisp small text.
 */
private fun android.graphics.Paint.configureGridPaint() {
    letterSpacing = 0f
    textScaleX = 1f
    isSubpixelText = true
}

/** Integer grid metrics of an already-configured paint (Phase 19.2). */
private class GridFit(val cellW: Int, val cellH: Int, val ascent: Float)

/**
 * Phase 19.2 device-round fix: snap [paint]'s textSize so its monospace
 * advance is a whole pixel (CellMetrics.fitSizeToGrid), then derive the
 * integer grid from it. Afterwards cellW EQUALS the font's own advance —
 * the previous ceil() added up to a full pixel of tracking per letter
 * ("letters have a noticeable gap between them", owner device report
 * 2026-08-31).
 */
private fun fitGridPaint(paint: android.graphics.Paint): GridFit {
    val fit = CellMetrics.fitSizeToGrid(paint.textSize) { size ->
        val saved = paint.textSize
        paint.textSize = size
        val advance = paint.measureText("MMMMMMMMMM") / 10f
        paint.textSize = saved
        advance
    }
    paint.textSize = fit.textSizePx
    return GridFit(
        cellW = fit.cellWidthPx,
        cellH = CellMetrics.cellHeightPx(paint.fontSpacing),
        ascent = paint.fontMetrics.ascent
    )
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
    return buildString {
        for (y in n.y1..n.y2) {
            val line = lineAt(y) ?: continue
            val text = line.text
            val start = if (y == n.y1) n.x1.coerceAtLeast(0) else 0
            val end = if (y == n.y2) (n.x2 + 1).coerceAtMost(text.length) else text.length
            if (start < end && start < text.length) {
                val sb = StringBuilder()
                for (col in start until end.coerceAtMost(text.length)) {
                    // Phase 19.4: join wide pairs and expand clusters.
                    if (line.columnIsContinuation(col)) continue
                    val cluster = line.clusters?.get(col)
                    if (cluster != null) sb.append(cluster) else sb.append(text[col])
                }
                append(sb.toString().trimEnd())
            }
            if (y != n.y2) append('\n')
        }
    }.trimEnd()
}

/**
 * Transcript row for an extended Y coordinate (0 = top live row, negative =
 * scrolled into history) without concatenating the two lists.
 */
fun TerminalSnapshot.lineAt(extY: Int): TerminalLine? {
    val idx = scrollbackCount + extY
    if (idx < 0) return null
    return if (idx < scrollbackLines.size) {
        scrollbackLines[idx]
    } else {
        val live = idx - scrollbackLines.size
        if (live in lines.indices) lines[live] else null
    }
}

private suspend fun PointerInputScope.detectScrollOrSelect(
    cellW: Int,
    cellH: Int,
    cols: Int,
    rows: Int,
    selecting: () -> Boolean,
    onScroll: (Float, Float, Float) -> Unit,
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
                onScroll(-dy, change.position.x, change.position.y)
                lastY = change.position.y
                change.consume()
            }
        }
    }
}

private suspend fun PointerInputScope.detectSmoothPinch(
    onZoomChange: (Float) -> Unit,
    onZoomEnd: () -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var lastDistance = 0f
        var didPinch = false
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                if (didPinch) {
                    onZoomEnd()
                }
                break
            }
            if (pressed.size >= 2) {
                val distance = (pressed[0].position - pressed[1].position).getDistance()
                if (lastDistance > 0f && distance > 0f) {
                    val factor = distance / lastDistance
                    if (factor in 0.6f..1.4f) {
                        onZoomChange(factor)
                        didPinch = true
                    }
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
            onInput(if (ctrl) "\u001b[1;5A" else cursorSequence('A'))
            return true
        }
        Key.DirectionDown -> {
            onInput(if (ctrl) "\u001b[1;5B" else cursorSequence('B'))
            return true
        }
        Key.DirectionRight -> {
            onInput(if (ctrl) "\u001b[1;5C" else cursorSequence('C'))
            return true
        }
        Key.DirectionLeft -> {
            onInput(if (ctrl) "\u001b[1;5D" else cursorSequence('D'))
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
