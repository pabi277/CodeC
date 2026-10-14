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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.codeci.ide.R
import com.codeci.ide.ui.terminal.CellFlags
import com.codeci.ide.ui.terminal.CellMetrics
import com.codeci.ide.ui.terminal.SelCell
import com.codeci.ide.ui.terminal.SelHandle
import com.codeci.ide.ui.terminal.SelectionModel
import com.codeci.ide.ui.terminal.TermSelection
import com.codeci.ide.ui.terminal.absoluteLine
import com.codeci.ide.ui.terminal.GlyphSpans
import com.codeci.ide.ui.terminal.MouseEncoding
import com.codeci.ide.ui.terminal.MouseModes
import com.codeci.ide.ui.terminal.ScrollMath
import com.codeci.ide.ui.terminal.StyleRun
import com.codeci.ide.ui.terminal.TerminalLine
import com.codeci.ide.ui.terminal.TerminalSnapshot
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
    var selection by remember { mutableStateOf<TermSelection?>(null) }
    // Overflow (⋮) menu of the selection toolbar.
    var menu by remember { mutableStateOf(false) }
    // Phase 103.1 — the handle under the finger, and the finger's view point.
    var handleGrab by remember { mutableStateOf<SelHandle?>(null) }
    var handlePoint by remember { mutableStateOf(Offset.Zero) }
    // Phase 103.2 — the planned fling, and a scroll pulse that shows the thumb.
    var fling by remember { mutableStateOf<ScrollMath.Fling?>(null) }
    var scrollEpoch by remember { mutableIntStateOf(0) }
    var thumbShown by remember { mutableStateOf(false) }
    val animatorScale = remember { animatorDurationScale(context) }

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

    // Parent toolbar copy reads the live selection text. Copy law: the parent
    // gets the selection or null, never a whole-transcript fallback.
    val selectedText = remember(selection, snapshot.generation) {
        selection?.let { SelectionModel.extract(it, snapshot) }?.takeIf { it.isNotEmpty() }
    }
    LaunchedEffect(selectedText) {
        onSelectionChanged(selectedText)
    }

    // Phase 103.2 — the fling. One plan at a time: a touch sets `fling` to
    // null (see the scroll gesture's onDown), which ends this loop at its
    // next frame. Frame-clock paced, so it is one step per display frame.
    LaunchedEffect(fling) {
        val plan = fling ?: return@LaunchedEffect
        var startNanos = -1L
        var appliedPx = 0f
        while (fling === plan) {
            val frameNanos = withFrameNanos { it }
            if (startNanos < 0L) startNanos = frameNanos
            val elapsedMs = (frameNanos - startNanos) / 1_000_000L
            val travelled = ScrollMath.flingOffset(plan, elapsedMs)
            val snap = latestSnapshot
            val r = ScrollMath.step(
                subRowPx = scrollSubPx,
                deltaPx = travelled - appliedPx,
                cellH = cellH.toFloat(),
                topRow = topRow,
                minTop = -snap.scrollbackCount
            )
            appliedPx = travelled
            scrollSubPx = r.subRowPx
            topRow = r.topRow
            scrollEpoch++
            // Stop at the rail the fling is heading for: no overshoot, no idle frames.
            val atRail = (plan.totalPx > 0f && r.topRow == 0) ||
                (plan.totalPx < 0f && r.topRow == -snap.scrollbackCount)
            if (elapsedMs >= plan.durationMs || atRail) break
        }
        val rested = ScrollMath.rest(topRow, scrollSubPx, -latestSnapshot.scrollbackCount)
        topRow = rested.topRow
        scrollSubPx = rested.subRowPx
        if (fling === plan) fling = null
    }

    // Phase 103.2 — the scrollbar thumb is visible while scrolling, then fades.
    LaunchedEffect(scrollEpoch) {
        if (scrollEpoch == 0) return@LaunchedEffect
        thumbShown = true
        delay(900)
        thumbShown = false
    }
    // The view takes no motion spec of its own: it stays off the design-system
    // motion tokens (TerminalChromeTest), and spec constructors live in the
    // theme motion file (MotionWiringTest).
    // The platform default spring fades the thumb; reduced motion (animator
    // scale 0) shows the target at once.
    val thumbTarget = if (thumbShown) 1f else 0f
    val thumbFade by animateFloatAsState(targetValue = thumbTarget, label = "terminalScrollThumb")
    val thumbAlpha = if (animatorScale > 0f) thumbFade else thumbTarget

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

        // Phase 103.1 — the handle drag. The finger's point is kept in VIEW
        // pixels and mapped to a buffer cell on every move and every
        // auto-scroll frame, so the selection follows the finger while the
        // content scrolls under it.
        val viewWPx = with(density) { maxWidth.toPx() }
        val viewHPx = with(density) { maxHeight.toPx() }
        val knobDy = with(density) { 6.dp.toPx() }
        val toolbarGap = with(density) { 8.dp.toPx() }
        val thumbWidthPx = with(density) { 4.dp.toPx() }
        val thumbMinPx = with(density) { 32.dp.toPx() }
        var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
        val dragging = handleGrab != null

        fun rowTopPx(screenRow: Int): Float = screenRow * cellH - scrollSubPx

        // A width change (or a reflow, which re-lays the history) makes any
        // buffer-absolute selection point at other text: drop it.
        LaunchedEffect(cols) {
            selection = null
            handleGrab = null
        }

        val applyHandleDrag: () -> Unit = {
            val grab = handleGrab
            val current = selection
            if (grab != null && current != null) {
                val snap = latestSnapshot
                val p = handlePoint
                val cell = SelectionModel.cellAt(
                    x = p.x,
                    y = p.y,
                    cellW = cellW,
                    cellH = cellH,
                    scrollSubPx = scrollSubPx,
                    cols = cols,
                    rows = rows,
                    topRow = topRow,
                    historyBase = snap.historyBase,
                    scrollbackCount = snap.scrollbackCount
                )
                val moved = SelectionModel.drag(current, grab, cell)
                selection = moved
                handleGrab = moved.active ?: grab
            }
        }

        // Edge auto-scroll while a handle is held in the top/bottom band.
        LaunchedEffect(dragging) {
            while (handleGrab != null) {
                withFrameNanos { }
                val step = SelectionModel.edgeScrollPx(handlePoint.y, viewHPx, cellH)
                if (step != 0f && handleGrab != null) {
                    val snap = latestSnapshot
                    val r = ScrollMath.step(
                        subRowPx = scrollSubPx,
                        deltaPx = step,
                        cellH = cellH.toFloat(),
                        topRow = topRow,
                        minTop = -snap.scrollbackCount
                    )
                    scrollSubPx = r.subRowPx
                    topRow = r.topRow
                    scrollEpoch++
                    applyHandleDrag()
                }
            }
        }

        val sel = selection
        val selectionVisible = sel != null && !snapshot.altScreen

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
                                    selection = null
                                    keyView?.showIme()
                                }
                            },
                            onLongPress = { pos ->
                                // Phase 103.1: long-press selects the word under the
                                // finger; the handles and toolbar take it from there.
                                val snap = latestSnapshot
                                val row = ((pos.y + scrollSubPx) / cellH).toInt().coerceIn(0, rows - 1)
                                val col = (pos.x / cellW).toInt().coerceIn(0, cols - 1)
                                val abs = snap.absRowOf(topRow + row)
                                val line = snap.absoluteLine(abs)
                                val (wStart, wEnd) = if (line != null) {
                                    findWordBoundaries(line.text, col)
                                } else col to col
                                selection = SelectionModel.begin(abs, wStart, wEnd, snap.altScreen)
                                menu = false
                            }
                        )
                    }
                    .pointerInput(cellW, cellH, cols, rows) {
                        detectScrollGesture(
                            onDown = {
                                // A touch stops a fling in progress.
                                fling = null
                            },
                            onScroll = { dy, x, y ->
                                val snap = latestSnapshot
                                scrollEpoch++
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
                            onRelease = { velocityPxPerS ->
                                val snap = latestSnapshot
                                if (snap.mouseMode and MouseModes.CAPTURE_MASK == 0) {
                                    // Phase 103.2: the finger has left the glass —
                                    // the sub-row remainder is discarded (whole-row
                                    // rest, Termux), then the release speed flings.
                                    // Web convention: a finger moving up is a
                                    // positive, view-newer velocity.
                                    val rested = ScrollMath.rest(topRow, scrollSubPx, -snap.scrollbackCount)
                                    topRow = rested.topRow
                                    scrollSubPx = rested.subRowPx
                                    ScrollMath.planFling(-velocityPxPerS, animatorScale)?.let { fling = it }
                                }
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
                            absRow = snapshot.absRowOf(extY),
                            cols = cols,
                            theme = theme,
                            batch = glyphBatch
                        )
                    }
                }
                // Phase 103.2 — proportional scrollbar thumb, fading after a scroll.
                if (thumbAlpha > 0f) {
                    val thumb = ScrollMath.thumb(
                        trackPx = size.height,
                        scrollbackCount = snapshot.scrollbackCount,
                        rows = rows,
                        topRow = topRow,
                        minLengthPx = thumbMinPx
                    )
                    if (thumb != null) {
                        drawRect(
                            color = theme.cursor.copy(alpha = 0.45f * thumbAlpha),
                            topLeft = Offset(size.width - thumbWidthPx, thumb.offsetPx),
                            size = Size(thumbWidthPx, thumb.lengthPx)
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

            if (sel != null && selectionVisible) {
                val startRow = SelectionModel.screenRowOf(
                    sel.start.row, snapshot.historyBase, snapshot.scrollbackCount, topRow
                )
                val endRow = SelectionModel.screenRowOf(
                    sel.end.row, snapshot.historyBase, snapshot.scrollbackCount, topRow
                )
                val startAnchorX = SelectionModel.handleAnchorX(SelHandle.START, sel.start, cellW)
                val endAnchorX = SelectionModel.handleAnchorX(SelHandle.END, sel.end, cellW)
                // Each handle's grab point sits inside the cell it marks, so the
                // first move of a drag never jumps the selection by a cell.
                val startPoint = Offset(startAnchorX, rowTopPx(startRow) + cellH / 2f)
                val endPoint = Offset(endAnchorX, rowTopPx(endRow) + cellH / 2f)
                if (startRow in 0 until rows) {
                    SelectionKnob(
                        centerX = startAnchorX,
                        centerY = rowTopPx(startRow) + cellH + knobDy,
                        color = theme.cursor,
                        onDragStart = {
                            handleGrab = SelHandle.START
                            handlePoint = startPoint
                        },
                        onDrag = { dx, dy ->
                            handlePoint = Offset(handlePoint.x + dx, handlePoint.y + dy)
                            applyHandleDrag()
                        },
                        onDragEnd = { handleGrab = null }
                    )
                }
                if (endRow in 0 until rows) {
                    SelectionKnob(
                        centerX = endAnchorX,
                        centerY = rowTopPx(endRow) + cellH + knobDy,
                        color = theme.cursor,
                        onDragStart = {
                            handleGrab = SelHandle.END
                            handlePoint = endPoint
                        },
                        onDrag = { dx, dy ->
                            handlePoint = Offset(handlePoint.x + dx, handlePoint.y + dy)
                            applyHandleDrag()
                        },
                        onDragEnd = { handleGrab = null }
                    )
                }

                // Floating toolbar: above the selection, or below it when there
                // is no room above. Hidden while a handle is being dragged.
                if (!dragging) {
                    val above = rowTopPx(startRow) - toolbarSize.height - toolbarGap
                    val below = rowTopPx(endRow) + cellH + toolbarGap
                    val rawY = if (above >= 0f) above else below
                    val tx = (startAnchorX - toolbarSize.width / 2f)
                        .coerceIn(0f, (viewWPx - toolbarSize.width).coerceAtLeast(0f))
                    val ty = rawY.coerceIn(0f, (viewHPx - toolbarSize.height).coerceAtLeast(0f))
                    Row(
                        modifier = Modifier
                            .offset { IntOffset(tx.roundToInt(), ty.roundToInt()) }
                            .onSizeChanged { toolbarSize = it }
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TerminalSelectionAction(
                            label = stringResource(R.string.terminal_copy),
                            enabled = selectedText != null
                        ) {
                            // Copy law: writes the selection, or nothing.
                            selectedText?.let(onCopyText)
                        }
                        TerminalSelectionAction(label = "Select all") {
                            selection = SelectionModel.selectAll(snapshot)
                        }
                        TerminalSelectionAction(label = stringResource(R.string.terminal_paste)) {
                            selection = null
                            onPaste()
                        }
                        Box {
                            TerminalSelectionAction(label = "More") { menu = true }
                            DropdownMenu(
                                expanded = menu,
                                onDismissRequest = { menu = false }
                            ) {
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
                            }
                        }
                    }
                }
            }
        }

        AndroidView(
            factory = { ctx -> TerminalKeyView(ctx) },
            update = { view ->
                if (keyView !== view) keyView = view
                view.onInput = { text ->
                    if (text.isNotEmpty()) {
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
    selection: TermSelection?,
    absRow: Long,
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
    // Phase 103.1: the selection is stored in buffer-absolute rows, so this
    // row's span is the same text no matter how far the output has scrolled.
    val span = selection?.let { SelectionModel.spanOnRow(it, absRow, cols) }
    if (span != null) {
        drawRect(
            color = theme.selection,
            topLeft = Offset(CellMetrics.columnX(span.first, cellW).toFloat(), CellMetrics.rowY(y, cellH).toFloat()),
            size = Size((span.last - span.first + 1) * cellW.toFloat(), cellH.toFloat())
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

private fun GridSelection.normalized(): GridSelection {
    val aFirst = y1 < y2 || (y1 == y2 && x1 <= x2)
    return if (aFirst) this else GridSelection(x2, y2, x1, y1)
}

/**
 * Copy text for a legacy extended-Y [GridSelection] (0 = top live row,
 * negative = history). Phase 103.1: delegates to [SelectionModel.extract], the
 * one extractor, so soft-wrapped rows join here exactly as they do on screen.
 */
fun TerminalSnapshot.selectedText(sel: GridSelection): String {
    val n = sel.normalized()
    val start = SelCell(absRowOf(n.y1), n.x1.coerceAtLeast(0))
    val end = SelCell(absRowOf(n.y2), n.x2.coerceAtLeast(0))
    return SelectionModel.extract(TermSelection(start, end), this)
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

/**
 * Phase 103.1/103.2 — one-finger gesture on the grid. Reports the finger's
 * vertical travel while it moves ([onScroll], web-convention delta is the
 * negated finger motion), a touch-down ([onDown]), and on lift the release
 * speed in px/s, finger-motion convention ([onRelease]). A press that never
 * moved is a tap or long-press and reports no release.
 */
private suspend fun PointerInputScope.detectScrollGesture(
    onDown: () -> Unit,
    onScroll: (Float, Float, Float) -> Unit,
    onRelease: (Float) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onDown()
        var lastY = down.position.y
        var dragged = false
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed) {
                if (dragged) {
                    tracker.addPosition(change.uptimeMillis, change.position)
                    onRelease(tracker.calculateVelocity().y)
                }
                break
            }
            val dy = change.position.y - lastY
            if (abs(dy) > 1f || abs(change.positionChange().x) > 1f) dragged = true
            if (dragged) {
                onScroll(-dy, change.position.x, change.position.y)
                lastY = change.position.y
                change.consume()
            }
            tracker.addPosition(change.uptimeMillis, change.position)
        }
    }
}

/**
 * Phase 103.1 — one selection handle. A 48 dp touch target (the Termux-style
 * grab point) around a 14 dp knob drawn just below the cell it marks.
 */
@Composable
private fun SelectionKnob(
    centerX: Float,
    centerY: Float,
    color: Color,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val density = LocalDensity.current
    val touchPx = with(density) { 48.dp.toPx() }
    val currentStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentEnd by rememberUpdatedState(onDragEnd)
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (centerX - touchPx / 2f).roundToInt(),
                    (centerY - touchPx / 2f).roundToInt()
                )
            }
            .size(48.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { currentStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        currentDrag(amount.x, amount.y)
                    },
                    onDragEnd = { currentEnd() },
                    onDragCancel = { currentEnd() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(14.dp).background(color, CircleShape))
    }
}

/** A selection-toolbar action: at least 48 dp tall, greyed when disabled. */
@Composable
private fun TerminalSelectionAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Text(label)
    }
}

/**
 * The platform's animator duration scale (0 = animations off). Read once per
 * composition; fling and the thumb fade degrade to instant when it is 0.
 */
private fun animatorDurationScale(context: Context): Float = try {
    android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
} catch (_: Exception) {
    1f
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
