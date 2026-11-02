package com.codeci.ide.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.drawscope.translate
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.codeci.ide.R
import com.codeci.ide.ui.terminal.CellFlags
import com.codeci.ide.ui.terminal.CellMetrics
import com.codeci.ide.ui.terminal.GlyphSpans
import com.codeci.ide.ui.terminal.MouseEncoding
import com.codeci.ide.ui.terminal.MouseModes
import com.codeci.ide.ui.terminal.ScrollMath
import com.codeci.ide.ui.terminal.SelectionHandle
import com.codeci.ide.ui.terminal.SelectionModel
import com.codeci.ide.ui.terminal.StyleRun
import com.codeci.ide.ui.terminal.TerminalLine
import com.codeci.ide.ui.terminal.TerminalSnapshot
import com.codeci.ide.ui.terminal.TextSelection
import com.codeci.ide.ui.terminal.XtermColors
import com.codeci.ide.ui.theme.DraculaTerminalTheme
import com.codeci.ide.ui.theme.TerminalThemeColors
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
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

/** Phase 103.1: the drag handles are 48 dp touch targets (Android minimum). */
private val HandleTouchSize = 48.dp
/** Phase 103.1: the visible teardrop inside that target. */
private val HandleDotRadius = 9.dp
/** Phase 103.1: pixels of scroll per frame when a handle sits in the edge band. */
private const val EDGE_SCROLL_PX_PER_FRAME = 14f
/** Phase 103.1: the edge band, in rows, that starts auto-scroll. */
private const val EDGE_SCROLL_BAND_ROWS = 1.5f

/**
 * Legacy grid range. The view's own selection state is [TextSelection]
 * (Phase 103.1); this shape is kept because `selectedText` below is public
 * API that host tests call directly.
 */
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

/**
 * Phase 103.1: the rule now lives in [SelectionModel.wordBounds] (pure, host
 * tested) — the view only forwards, so the gesture layer and the tests cannot
 * drift apart on what "the word under the finger" means.
 */
fun findWordBoundaries(text: String, col: Int): Pair<Int, Int> =
    SelectionModel.wordBounds(text, col)

fun openTerminalUrl(context: Context, url: String) {
    // Phase 37 follow-up: the ACTION_VIEW plumbing moved to the app's single
    // launcher (`OpenInBrowser`) so the share row and the terminal cannot drift
    // apart on how a URL is opened; this keeps the terminal's own failure text.
    if (!com.codeci.ide.ui.services.OpenInBrowser.open(context, url)) {
        Toast.makeText(context, "Cannot open URL: $url", Toast.LENGTH_SHORT).show()
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
    /**
     * Phase 103.1: a selection was just started by a long-press. The view
     * does not perform the haptic itself (Phase 51.4 keeps this file
     * haptic-free); the screen, which owns the terminal's chrome, does.
     */
    onSelectionStarted: () -> Unit = {},
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

    // Phase 19.2 device round 2 (2026-08-31): default family is the BUNDLED
    // JetBrains Mono (Medium weight) — Android's stock Droid Sans Mono is
    // light-stroked with wide sidebearings, which the owner saw as "thin,
    // letters far apart, stretched" next to Termux's custom font. Bold runs
    // use the real Bold face so ANSI bold stays distinct from Medium.
    val typeface = remember(fontFamily) {
        when (fontFamily) {
            "JetBrains Mono" ->
                androidx.core.content.res.ResourcesCompat.getFont(
                    context, com.codeci.ide.R.font.jetbrainsmono_medium
                ) ?: Typeface.MONOSPACE
            "Courier" -> Typeface.MONOSPACE
            "Sans Serif" -> Typeface.SANS_SERIF
            "Serif" -> Typeface.SERIF
            else -> Typeface.MONOSPACE
        }
    }
    val boldTypeface = remember(fontFamily, typeface) {
        if (fontFamily == "JetBrains Mono") {
            androidx.core.content.res.ResourcesCompat.getFont(
                context, com.codeci.ide.R.font.jetbrainsmono_bold
            ) ?: Typeface.create(typeface, Typeface.BOLD)
        } else {
            Typeface.create(typeface, Typeface.BOLD)
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
    val boldPaint = remember(paint, boldTypeface) {
        android.graphics.Paint(paint).apply {
            // `this.` — the bare name would resolve to an OUTER val, a
            // reassignment error.
            this.typeface = boldTypeface
        }
    }

    // Termux mTopRow: 0 = live screen, negative = scrolled into transcript.
    var topRow by remember { mutableIntStateOf(0) }
    var wheelRemainder by remember { mutableFloatStateOf(0f) }
    // Phase 19.2 round 3: sub-row scroll remainder (px) for pixel-smooth
    // scrolling — the grid translates by this fraction of a row.
    var scrollSubPx by remember { mutableFloatStateOf(0f) }

    // ---- Phase 103.1: selection state -------------------------------------
    // The range itself, plus two pieces of GESTURE state the old code did
    // not have: which handle (if any) the finger is holding, and whether the
    // handles + floating toolbar are on screen. The old code had only
    // `selecting`, and set it together with `menu = true` inside the
    // long-press handler — the reason a drag had nothing to extend.
    var selection by remember { mutableStateOf<TextSelection?>(null) }
    var draggingHandle by remember { mutableStateOf<SelectionHandle?>(null) }
    var showHandles by remember { mutableStateOf(false) }
    /** Latest finger position (view px) while extending or handle-dragging. */
    var dragFingerX by remember { mutableFloatStateOf(0f) }
    var dragFingerY by remember { mutableFloatStateOf(0f) }

    var menu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var selecting by remember { mutableStateOf(false) }

    var flashAlpha by remember { mutableFloatStateOf(0f) }

    // Reusable glyph batch buffer (Phase 19.2 round 3 run-batching).
    val glyphBatch = remember { StringBuilder(512) }

    // Phase 19.2 round 3: gesture handlers must read the LATEST snapshot at
    // EVENT time. The previous code keyed pointerInput on
    // snapshot.generation / scrollbackCount, which restart the detector on
    // EVERY output frame — mid-output taps were cancelled (keyboard did not
    // pop up) and drags broke when scrollback grew. Reading this state
    // instead keeps the detectors alive across frames.
    var latestSnapshot by remember { mutableStateOf(snapshot) }
    // Phase 103.1: the handle overlay hit-tests against the CURRENT handle
    // boxes. A plain local `val` captured by a keyed pointerInput freezes at
    // whatever the boxes were when the cell metrics last changed — the touch
    // would land on where a handle USED to be — so they travel as state, the
    // same discipline as `latestSnapshot`.
    var latestStartBox by remember { mutableStateOf<HandleBox?>(null) }
    var latestEndBox by remember { mutableStateOf<HandleBox?>(null) }
    SideEffect { latestSnapshot = snapshot }

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

    // Phase 103.1: hold an open selection ON ITS OWN TEXT while output
    // streams. Extended-Y coordinates are measured from the live screen, so
    // every row of new output slides the older text one step deeper into
    // history; without this shift a selection made mid-`cat` drifts and ends
    // up highlighting whatever scrolled into its place. `scrolledRows` is
    // monotonic and independent of the 2,000-row scrollback cap, which is
    // exactly what the old code had no way to observe.
    var lastScrolledRows by remember { mutableStateOf(0L) }
    LaunchedEffect(snapshot.scrolledRows) {
        val delta = snapshot.scrolledRows - lastScrolledRows
        lastScrolledRows = snapshot.scrolledRows
        if (delta > 0) {
            val cur = selection
            if (cur != null) {
                val moved = SelectionModel.shift(cur, delta.toInt(), -snapshot.scrollbackCount)
                selection = moved
                if (moved == null) showHandles = false
            }
        }
    }

    // Update active selection state for parent toolbar copy
    LaunchedEffect(selection, snapshot.generation) {
        val selectedText = selection?.let { SelectionModel.extract(snapshot, it) }?.takeIf { it.isNotEmpty() }
        onSelectionChanged(selectedText)
    }

    // Phase 19.2 round 3: REMOVED the "jump to live on every update" effect —
    // it reset topRow=0 on every output frame, so while output streamed the
    // scrollback fought the user's finger (rubber-banding). Termux semantics:
    // a scrolled-up view STAYS put during output; typed input jumps to live
    // (the onInput path below sets topRow = 0).

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

        // Phase 103.1: a WIDTH change reflows the scrollback (Phase 19.1), so
        // extended-Y coordinates stop meaning what they meant. Drop the
        // selection rather than leave a highlight on unrelated text.
        LaunchedEffect(cols) {
            selection = null
            showHandles = false
            selecting = false
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cellW, cellH, cols, rows) {
                        detectTapGestures(
                            onTap = { pos ->
                                val snap = latestSnapshot
                                // Content is translated by -scrollSubPx (see
                                // the draw loop), so map the touch back.
                                val row = ((pos.y + scrollSubPx) / cellH).toInt().coerceIn(0, rows - 1)
                                val col = (pos.x / cellW).toInt().coerceIn(0, cols - 1)
                                if (snap.mouseMode and MouseModes.CAPTURE_MASK != 0) {
                                    // Phase 19.5: the app owns the mouse —
                                    // a tap is a left click at that cell.
                                    val sgr = snap.mouseMode and MouseModes.SGR_EXT != 0
                                    MouseEncoding.press(0, col + 1, row + 1, sgr)?.let(onMouseEvent)
                                    MouseEncoding.release(0, col + 1, row + 1, sgr)?.let(onMouseEvent)
                                    return@detectTapGestures
                                }
                                val line = snap.lineAt(topRow + row)
                                val url = if (line != null) {
                                    findUrlAt(line.text, col)
                                } else null

                                if (url != null) {
                                    onUrlClick(url)
                                } else {
                                    selecting = false
                                    selection = null
                                    showHandles = false
                                    keyView?.showIme()
                                }
                            },
                            onLongPress = { pos ->
                                val snap = latestSnapshot
                                val row = ((pos.y + scrollSubPx) / cellH).toInt().coerceIn(0, rows - 1)
                                val col = (pos.x / cellW).toInt().coerceIn(0, cols - 1)
                                val y = topRow + row
                                val started = SelectionModel.beginWord(snap, col, y)
                                if (started != null) {
                                    selection = started
                                    // Phase 103.1: the finger is still down —
                                    // the SAME gesture now EXTENDS the range
                                    // (see detectScrollOrSelect). The menu no
                                    // longer opens here: opening it inside the
                                    // long-press is exactly what made a drag
                                    // impossible ("partial text copy not
                                    // working"), because there was never a
                                    // range left to extend.
                                    selecting = true
                                    draggingHandle = null
                                    showHandles = true
                                    dragFingerX = pos.x
                                    dragFingerY = pos.y
                                    menuOffset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                    // Phase 103.1: the tick is performed by
                                    // the SCREEN, not here — the emulator
                                    // view is one of the two haptics-free
                                    // zones `HapticWiringTest` guards
                                    // (Phase 51.4), so this file only says
                                    // that a selection began.
                                    onSelectionStarted()
                                }
                            }
                        )
                    }
                    .pointerInput(cellW, cellH, cols, rows) {
                        detectScrollOrSelect(
                            cellW = cellW,
                            cellH = cellH,
                            cols = cols,
                            rows = rows,
                            selecting = { selecting },
                            handleDragging = { draggingHandle != null },
                            onScroll = { dy, x, y ->
                                val snap = latestSnapshot
                                if (snap.mouseMode and MouseModes.CAPTURE_MASK != 0) {
                                    // Termux-style touch mapping: while the app
                                    // reports the mouse, swipes act as the
                                    // WHEEL (buttons 64/65), so htop/vim/tmux
                                    // scroll instead of the local scrollback.
                                    wheelRemainder += dy
                                    val units = (wheelRemainder / cellH).toInt()
                                    if (units != 0) {
                                        wheelRemainder -= units * cellH
                                        val sgr = snap.mouseMode and MouseModes.SGR_EXT != 0
                                        val col = (x / cellW).toInt().coerceIn(0, cols - 1)
                                        val row = (y / cellH).toInt().coerceIn(0, rows - 1)
                                        val dir = if (units > 0) MouseEncoding.WHEEL_DOWN else MouseEncoding.WHEEL_UP
                                        repeat(kotlin.math.abs(units)) {
                                            MouseEncoding.wheel(dir, col + 1, row + 1, sgr)?.let(onMouseEvent)
                                        }
                                    }
                                } else {
                                    // Phase 19.2 round 3: pixel-smooth local
                                    // scrolling — whole rows change topRow,
                                    // the sub-row remainder translates the
                                    // grid (was: whole-row jumps only).
                                    val r = ScrollMath.step(
                                        subRowPx = scrollSubPx,
                                        deltaPx = dy,
                                        cellH = cellH.toFloat(),
                                        topRow = topRow,
                                        minTop = -snap.scrollbackCount
                                    )
                                    scrollSubPx = r.subRowPx
                                    topRow = r.topRow
                                }
                            },
                            onSelectMove = { x, y ->
                                val snap = latestSnapshot
                                val cur = selection ?: return@detectScrollOrSelect
                                dragFingerX = x
                                dragFingerY = y
                                // Phase 103.1: the row is measured in the
                                // SAME translated space the grid is drawn in
                                // (content is shifted by -scrollSubPx); the
                                // old code divided the raw y and was up to a
                                // row off whenever the view rested between
                                // rows. The model grows the range so the
                                // anchor stays put and the edges swap when
                                // the drag passes it.
                                val row = ((y + scrollSubPx) / cellH).toInt().coerceIn(0, rows - 1)
                                val col = (x / cellW).toInt().coerceIn(0, cols - 1)
                                selection = SelectionModel.drag(
                                    selection = cur,
                                    handle = SelectionHandle.END,
                                    col = col,
                                    extY = topRow + row,
                                    cols = cols,
                                    minRow = -snap.scrollbackCount,
                                    maxRow = snap.rows - 1
                                ).selection
                            },
                            onGestureEnd = {
                                // Phase 103.1: the extending gesture is over.
                                // The range (and its handles) stay; from here
                                // a drag SCROLLS the buffer instead of
                                // rewriting the selection.
                                selecting = false
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
                // Phase 19.2 round 3: translate by the sub-row scroll
                // remainder (px) so scrolling follows the finger between
                // row boundaries. One extra row on each edge stays ready
                // under the shifted viewport; null lines just skip.
                translate(left = 0f, top = -scrollSubPx) {
                    for (screenY in -1 until rows + 1) {
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
                            theme = theme,
                            batch = glyphBatch
                        )
                    }
                }
            }

            if (flashAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = flashAlpha))
                )
            }

            // ---- Phase 103.1: handles + floating toolbar -------------------
            // Everything below is the part the owner could not do at all
            // before: a range on screen, two things to drag, and a Copy that
            // writes the range instead of the whole transcript.
            // `maxWidth`/`maxHeight` belong to the OUTER BoxWithConstraints
            // scope; inside this inner Box there is already a BoxScope
            // receiver, so the outer one has to be named.
            val viewWidthPx = with(density) { this@BoxWithConstraints.maxWidth.toPx() }
            val viewHeightPx = with(density) { this@BoxWithConstraints.maxHeight.toPx() }
            val handleTouchPx = with(density) { HandleTouchSize.toPx() }
            val handleDotPx = with(density) { HandleDotRadius.toPx() }
            val shownSel = if (showHandles) selection?.normalized() else null
            val startBox = shownSel?.let { sel ->
                handleBox(
                    anchorX = CellMetrics.columnX(sel.x1.coerceIn(0, cols - 1), cellW).toFloat(),
                    // Rows are drawn translated by -scrollSubPx, so the
                    // handle must be measured in the same space.
                    anchorY = (sel.y1 - topRow) * cellH - scrollSubPx,
                    touch = handleTouchPx,
                    viewW = viewWidthPx,
                    viewH = viewHeightPx,
                    below = false
                )
            }
            val endBox = shownSel?.let { sel ->
                handleBox(
                    anchorX = CellMetrics.columnX((sel.x2 + 1).coerceIn(0, cols), cellW).toFloat(),
                    anchorY = (sel.y2 - topRow) * cellH - scrollSubPx + cellH,
                    touch = handleTouchPx,
                    viewW = viewWidthPx,
                    viewH = viewHeightPx,
                    below = true
                )
            }
            // Publish the boxes for the gesture layer (see latestStartBox).
            SideEffect {
                latestStartBox = startBox
                latestEndBox = endBox
            }
            // The copy law: the clipboard gets the selection, or nothing.
            val selectedText = selection?.let { SelectionModel.extract(snapshot, it) } ?: ""

            if (shownSel != null && startBox != null && endBox != null) {
                Canvas(
                    modifier = Modifier
                        .offset { IntOffset(startBox.left.roundToInt(), startBox.top.roundToInt()) }
                        .size(HandleTouchSize)
                ) {
                    drawSelectionHandle(color = theme.cursor, dot = handleDotPx, below = false)
                }
                Canvas(
                    modifier = Modifier
                        .offset { IntOffset(endBox.left.roundToInt(), endBox.top.roundToInt()) }
                        .size(HandleTouchSize)
                ) {
                    drawSelectionHandle(color = theme.cursor, dot = handleDotPx, below = true)
                }
                SelectionToolbar(
                    startBox = startBox,
                    endBox = endBox,
                    touch = handleTouchPx,
                    viewWidthPx = viewWidthPx,
                    viewHeightPx = viewHeightPx,
                    density = density,
                    theme = theme,
                    canCopy = selectedText.isNotEmpty(),
                    onCopy = { onCopyText(selectedText) },
                    onSelectAll = {
                        selection = TextSelection(
                            0, -snapshot.scrollbackCount,
                            (cols - 1).coerceAtLeast(0), snapshot.rows - 1
                        )
                        showHandles = true
                    },
                    onPaste = {
                        selection = null
                        showHandles = false
                        onPaste()
                    },
                    onMore = { menu = true }
                )
            }

            // The handle drag lives on ONE overlay above the terminal instead
            // of on the handles themselves: a handle moves while it is being
            // dragged, so a gesture bound to its own box would read its
            // coordinates in a moving frame. The overlay is keyed only on the
            // cell metrics (never on topRow/scrollSubPx, which change every
            // frame — restarting a detector mid-gesture is the Phase 19.2
            // round-3 bug), and reads the live values through the state
            // holders at event time.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cellW, cellH, cols, rows) {
                        detectHandleDrag(
                            hitTest = { pos ->
                                val box = latestStartBox
                                val end = latestEndBox
                                if (box == null || end == null) {
                                    null
                                } else {
                                    when {
                                        box.hit(pos.x, pos.y, handleTouchPx) -> SelectionHandle.START
                                        end.hit(pos.x, pos.y, handleTouchPx) -> SelectionHandle.END
                                        else -> null
                                    }
                                }
                            },
                            onStart = { handle, pos ->
                                draggingHandle = handle
                                dragFingerX = pos.x
                                dragFingerY = pos.y
                            },
                            onMove = { handle, pos ->
                                val snap = latestSnapshot
                                val cur = selection
                                dragFingerX = pos.x
                                dragFingerY = pos.y
                                if (cur == null) {
                                    handle
                                } else {
                                    val row = ((pos.y + scrollSubPx) / cellH).toInt().coerceIn(0, rows - 1)
                                    val col = (pos.x / cellW).toInt().coerceIn(0, cols - 1)
                                    val moved = SelectionModel.drag(
                                        selection = cur,
                                        handle = handle,
                                        col = col,
                                        extY = topRow + row,
                                        cols = cols,
                                        minRow = -snap.scrollbackCount,
                                        maxRow = snap.rows - 1
                                    )
                                    selection = moved.selection
                                    // A drag that crosses the other edge
                                    // flips: the finger keeps the edge it
                                    // started on.
                                    moved.handle
                                }
                            },
                            onEnd = { draggingHandle = null }
                        )
                    }
            )

            // Phase 103.1: edge auto-scroll. Without it a selection can never
            // grow past one screen — the finger reaches the edge of the glass
            // and stops, no matter how much text there is.
            val gestureActive = selecting || draggingHandle != null
            LaunchedEffect(gestureActive, cellH) {
                if (!gestureActive) return@LaunchedEffect
                while (selecting || draggingHandle != null) {
                    val band = cellH * EDGE_SCROLL_BAND_ROWS
                    val dir = when {
                        dragFingerY < band -> -1f // toward older text
                        dragFingerY > viewHeightPx - band -> 1f // toward live
                        else -> 0f
                    }
                    if (dir != 0f) {
                        val snap = latestSnapshot
                        val moved = ScrollMath.step(
                            subRowPx = scrollSubPx,
                            deltaPx = dir * EDGE_SCROLL_PX_PER_FRAME,
                            cellH = cellH.toFloat(),
                            topRow = topRow,
                            minTop = -snap.scrollbackCount
                        )
                        scrollSubPx = moved.subRowPx
                        topRow = moved.topRow
                        val cur = selection
                        if (cur != null) {
                            val row = ((dragFingerY + scrollSubPx) / cellH).toInt().coerceIn(0, rows - 1)
                            val col = (dragFingerX / cellW).toInt().coerceIn(0, cols - 1)
                            val held = draggingHandle ?: SelectionHandle.END
                            val grown = SelectionModel.drag(
                                selection = cur,
                                handle = held,
                                col = col,
                                extY = topRow + row,
                                cols = cols,
                                minRow = -snap.scrollbackCount,
                                maxRow = snap.rows - 1
                            )
                            selection = grown.selection
                            if (draggingHandle != null) draggingHandle = grown.handle
                        }
                    }
                    delay(16)
                }
            }

            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                // menuOffset is in raw pixels (it is captured from a touch
                // position), and DpOffset wants dp: convert through the
                // density rather than assume a 1:1 relationship.
                offset = DpOffset(
                    x = (menuOffset.x / density.density).dp,
                    y = (menuOffset.y / density.density).dp
                )
            ) {
                // Phase 103.1: `selectedText` here is the SELECTION, never the
                // transcript. The old line was
                // `selectedText ?: snapshot.transcriptText()`, which is what
                // put the whole session on the clipboard when the gesture
                // layer had failed to produce a range — the owner's
                // "it copied everything". A Copy with nothing selected is
                // disabled, not silently upgraded to everything.
                val selectedUrl = findUrlAt(selectedText, 0)

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
                    enabled = selectedText.isNotEmpty(),
                    onClick = {
                        menu = false
                        onCopyText(selectedText)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Select All") },
                    onClick = {
                        menu = false
                        selection = TextSelection(
                            0, -snapshot.scrollbackCount,
                            (cols - 1).coerceAtLeast(0), snapshot.rows - 1
                        )
                        showHandles = true
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
                    enabled = selectedText.isNotEmpty(),
                    onClick = {
                        menu = false
                        val text = selectedText
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
                        showHandles = false
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
                        showHandles = false
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
    selection: TextSelection?,
    extY: Int,
    cols: Int,
    theme: TerminalThemeColors,
    batch: StringBuilder
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

        // Phase 19.2: glyphs sit at exact INTEGER cell origins — no drift,
        // no collisions. Phase 19.4: draws the base+marks CLUSTER when
        // present and gives a double-width lead cell a two-cell slot.
        // Phase 19.2 round 3 (owner: "terminal feels lagging"): the snapped
        // grid makes the font's advance EQUAL cellW, so consecutive PLAIN
        // columns are drawn as ONE drawText (identical positions) — a full
        // 70x37 screen drops from ~2600 measure+draw native call pairs per
        // frame to a handful per style run. Cluster, wide and non-ASCII
        // (fallback-font) cells still draw individually with the squeeze
        // guard; all-space spans draw nothing at all.
        val clusters = line.clusters
        val baseline = CellMetrics.rowY(y, cellH) - ascent
        val wideRun = run.flags and (CellFlags.WIDE_LEAD or CellFlags.WIDE_CONT) != 0
        drawIntoCanvas { canvas ->
            var batchStart = -1
            var batchInk = false
            fun flushBatch() {
                if (batchStart >= 0 && batchInk) {
                    canvas.nativeCanvas.drawText(
                        batch, 0, batch.length,
                        CellMetrics.columnX(batchStart, cellW).toFloat(), baseline, glyphPaint
                    )
                }
                batch.setLength(0)
                batchStart = -1
                batchInk = false
            }
            var charCol = sliceStart
            while (charCol < sliceEnd) {
                if (wideRun) {
                    // Homogeneous wide run — per-cell path (two-cell slots).
                    val glyph = clusters?.get(charCol) ?: text[charCol].toString()
                    if (glyph.isNotEmpty() && glyph != " ") {
                        val wide = run.flags and CellFlags.WIDE_LEAD != 0 && charCol < cols - 1
                        val slot = if (wide) cellW * 2 else cellW
                        val x = CellMetrics.columnX(charCol, cellW).toFloat()
                        val advance = glyphPaint.measureText(glyph)
                        if (advance > slot) {
                            val savedScale = glyphPaint.textScaleX
                            glyphPaint.textScaleX = savedScale * (slot / advance)
                            canvas.nativeCanvas.drawText(glyph, x, baseline, glyphPaint)
                            glyphPaint.textScaleX = savedScale
                        } else {
                            canvas.nativeCanvas.drawText(glyph, x, baseline, glyphPaint)
                        }
                    }
                    charCol++
                } else {
                    val span = GlyphSpans.spanLength(text, clusters, charCol, sliceEnd)
                    if (span > 0) {
                        if (batchStart < 0) batchStart = charCol
                        for (k in 0 until span) {
                            val c = text[charCol + k]
                            if (c != ' ') batchInk = true
                            batch.append(c)
                        }
                        charCol += span
                    } else {
                        // Special column (cluster / non-ASCII): flush first
                        // so the batch never bridges over this cell.
                        flushBatch()
                        val glyph = clusters?.get(charCol) ?: text[charCol].toString()
                        if (glyph.isNotEmpty() && glyph != " ") {
                            val x = CellMetrics.columnX(charCol, cellW).toFloat()
                            val advance = glyphPaint.measureText(glyph)
                            if (advance > cellW) {
                                val savedScale = glyphPaint.textScaleX
                                glyphPaint.textScaleX = savedScale * (cellW / advance)
                                canvas.nativeCanvas.drawText(glyph, x, baseline, glyphPaint)
                                glyphPaint.textScaleX = savedScale
                            } else {
                                canvas.nativeCanvas.drawText(glyph, x, baseline, glyphPaint)
                            }
                        }
                        charCol++
                    }
                }
            }
            flushBatch()
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
        cellH = CellMetrics.cellHeightPx(paint.fontSpacing, CellMetrics.TERMINAL_LINE_FACTOR),
        ascent = paint.fontMetrics.ascent
    )
}

private fun packedColor(packed: Int): Color = Color(
    red = (packed shr 16) and 0xFF,
    green = (packed shr 8) and 0xFF,
    blue = packed and 0xFF
)

// Phase 103.1: normalization moved into TextSelection.normalized() alongside
// the rest of the selection model; the view no longer keeps a second copy.

/**
 * Phase 103.1: the extraction rule — including the soft-wrap join that was
 * missing — lives in [SelectionModel.extract]. This keeps the public shape
 * (used by `TerminalUxTest` / `TerminalBufferTest`) while making every caller
 * copy wrapped lines correctly.
 */
fun TerminalSnapshot.selectedText(sel: GridSelection): String =
    SelectionModel.extract(this, TextSelection(sel.x1, sel.y1, sel.x2, sel.y2))

/**
 * Phase 103.1: `lineAt` is now a member of [TerminalSnapshot] (one
 * definition of the extended-Y coordinate, next to the buffer that defines
 * it) — see `TerminalBuffer.kt`.
 */

/**
 * One gesture, three jobs, decided per MOVE (Phase 103.1 added the first
 * branch): a handle being dragged owns the gesture and the buffer must not
 * scroll under it; an open selection being dragged extends; anything else
 * scrolls.
 *
 * [cellW]/[cellH]/[cols]/[rows] stay parameters because the caller keys its
 * pointerInput on them; everything that changes per frame (topRow,
 * scrollSubPx, the selection) is read by the CALLBACKS, which capture the
 * Compose state holders and therefore see live values.
 */
/**
 * Phase 103.1: the on-screen box of one selection handle — a 48 dp touch
 * target (Android's minimum) clamped into the view, so a selection whose row
 * has scrolled off the top or bottom is still grabbable instead of
 * unreachable.
 */
private class HandleBox(
    val left: Float,
    val top: Float,
    /** Cell corner the handle points at (used to place the toolbar). */
    val anchorX: Float
)

/**
 * The touch box for a handle at ([anchorX], [anchorY]): the START handle sits
 * ABOVE its row (its tail touching the row's top), the END handle BELOW it.
 */
private fun handleBox(
    anchorX: Float,
    anchorY: Float,
    touch: Float,
    viewW: Float,
    viewH: Float,
    below: Boolean
): HandleBox {
    val top = if (below) anchorY else anchorY - touch
    return HandleBox(
        left = (anchorX - touch / 2f).coerceIn(0f, (viewW - touch).coerceAtLeast(0f)),
        top = top.coerceIn(0f, (viewH - touch).coerceAtLeast(0f)),
        anchorX = anchorX
    )
}

private fun HandleBox.hit(x: Float, y: Float, touch: Float): Boolean =
    x >= left && x <= left + touch && y >= top && y <= top + touch

/** Phase 103.1: the teardrop — a dot with a stem pointing at the cell. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionHandle(
    color: Color,
    dot: Float,
    below: Boolean
) {
    val cx = size.width / 2f
    val cy = if (below) dot + 2f else size.height - dot - 2f
    val stemEnd = if (below) 2f else size.height - 2f
    drawLine(
        color = color,
        start = Offset(cx, cy),
        end = Offset(cx, stemEnd),
        strokeWidth = dot * 0.4f
    )
    drawCircle(color = color, radius = dot, center = Offset(cx, cy))
}

/**
 * Phase 103.1: the floating toolbar. Termux-style — Copy / Select all /
 * Paste / More — placed above the start handle when there is room and below
 * the end handle when there is not, so it never sits under the finger that
 * is dragging. Colours come from the terminal theme's own roles (Phase 40.5
 * colour law): no new palette entry for a chrome element.
 */
@Composable
private fun SelectionToolbar(
    startBox: HandleBox,
    endBox: HandleBox,
    touch: Float,
    viewWidthPx: Float,
    viewHeightPx: Float,
    density: Density,
    theme: TerminalThemeColors,
    canCopy: Boolean,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onPaste: () -> Unit,
    onMore: () -> Unit
) {
    val heightPx = with(density) { 48.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val widthPx = with(density) { 240.dp.toPx() }
    val y = if (startBox.top - heightPx - gapPx >= 0f) {
        startBox.top - heightPx - gapPx
    } else {
        (endBox.top + touch + gapPx).coerceAtMost((viewHeightPx - heightPx).coerceAtLeast(0f))
    }
    val x = (startBox.anchorX - with(density) { 12.dp.toPx() })
        .coerceIn(0f, (viewWidthPx - widthPx).coerceAtLeast(0f))
    Surface(
        // The geometry above is in pixels (it is measured against cell
        // metrics); `offset` is in dp, so convert through the density.
        modifier = Modifier.offset(
            x = (x / density.density).dp,
            y = (y / density.density).dp
        ),
        shape = RoundedCornerShape(8.dp),
        color = theme.topBarBackground,
        // Phase 51.3 keeps the app's token system out of this file (Phase 36
        // owns the emulator's rendering, and the terminal draws from its own
        // theme), so the lift is a plain dp like the rest of the view's chrome.
        shadowElevation = 4.dp
    ) {
        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            ToolbarAction(stringResource(R.string.terminal_copy), canCopy, theme, onCopy)
            ToolbarAction("Select all", true, theme, onSelectAll)
            ToolbarAction(stringResource(R.string.terminal_paste), true, theme, onPaste)
            ToolbarAction("More", true, theme, onMore)
        }
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    enabled: Boolean,
    theme: TerminalThemeColors,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = theme.foreground)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Phase 103.1: drag one of the two handles.
 *
 * Consumes from the DOWN, not from the first move: the terminal's own
 * scroll/tap detector deliberately accepts already-consumed downs
 * (`requireUnconsumed = false`), so a handle that only consumed after touch
 * slop would ALSO scroll the buffer — or, on a tap, clear the very selection
 * the user was reaching for.
 *
 * [onMove] returns the handle the finger is now holding, because a drag that
 * crosses the other edge flips the range.
 */
private suspend fun PointerInputScope.detectHandleDrag(
    hitTest: (Offset) -> SelectionHandle?,
    onStart: (SelectionHandle, Offset) -> Unit,
    onMove: (SelectionHandle, Offset) -> SelectionHandle,
    onEnd: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Not on a handle: leave the event alone so the terminal below sees
        // it exactly as it did before this overlay existed.
        val handle = hitTest(down.position) ?: return@awaitEachGesture
        down.consume()
        var current: SelectionHandle = handle
        onStart(current, down.position)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed) break
            change.consume()
            current = onMove(current, change.position)
        }
        onEnd()
    }
}

private suspend fun PointerInputScope.detectScrollOrSelect(
    cellW: Int,
    cellH: Int,
    cols: Int,
    rows: Int,
    selecting: () -> Boolean,
    handleDragging: () -> Boolean,
    onScroll: (Float, Float, Float) -> Unit,
    onSelectMove: (Float, Float) -> Unit,
    onGestureEnd: () -> Unit
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
            if (handleDragging()) {
                // Phase 103.1: the handle overlay above us owns this pointer.
                // Consume so nothing else in the stack acts on it, but do
                // NOT scroll — the handle drag has its own edge auto-scroll.
                change.consume()
            } else if (selecting()) {
                onSelectMove(change.position.x, change.position.y)
                change.consume()
            } else if (dragged) {
                onScroll(-dy, change.position.x, change.position.y)
                lastY = change.position.y
                change.consume()
            }
        }
        onGestureEnd()
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
