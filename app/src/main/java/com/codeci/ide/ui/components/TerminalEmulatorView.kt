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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import com.codeci.ide.ui.terminal.SelectionAutoScroll
import com.codeci.ide.ui.terminal.SelectionDrag
import com.codeci.ide.ui.terminal.SelectionEnd
import com.codeci.ide.ui.terminal.SelectionHandles
import com.codeci.ide.ui.terminal.SelectionModel
import com.codeci.ide.ui.terminal.SelectionPoint
import com.codeci.ide.ui.terminal.SelectionRange
import com.codeci.ide.ui.terminal.StyleRun
import com.codeci.ide.ui.terminal.TerminalLine
import com.codeci.ide.ui.terminal.TerminalScroll
import com.codeci.ide.ui.terminal.TerminalSelection
import com.codeci.ide.ui.terminal.TerminalSnapshot
import com.codeci.ide.ui.terminal.TrimMath
import com.codeci.ide.ui.terminal.XtermColors
import com.codeci.ide.ui.terminal.asSelectionText
import com.codeci.ide.ui.theme.DraculaTerminalTheme
import com.codeci.ide.ui.theme.TerminalThemeColors
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Termux-style terminal surface: a Canvas character grid whose cell size is
 * [Paint.measureText("X")] / [Paint.getFontSpacing], matching the PTY
 * cols/rows. Scrollback is [topRow] (0 = live screen, negative = history),
 * the same model as Termux's TerminalView.mTopRow.
 */
/**
 * Phase 103.1 — DEPRECATED. The one-word, popup-in-the-same-gesture model this
 * data class encoded was the owner's *"partial text copy not working"* bug; the
 * view now drags a [TerminalSelection] with two handles and copies through
 * [SelectionModel.extract]. The type stays only as a compatible input for
 * [selectedText] so nothing that already depended on it breaks silently.
 * New code must not construct one.
 */
@Deprecated("Phase 103.1: use TerminalSelection + SelectionModel.extract")
data class GridSelection(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int
)

/**
 * Phase 103.1 — the floating toolbar's three verbs, as data. The Android edge
 * performs them; this list is what the composable draws, so a verb cannot
 * exist in the UI without a case here and vice versa.
 */
enum class SelectionAction { COPY, SELECT_ALL, PASTE }

/**
 * The selection toolbar's copy button: the selection or NOTHING.
 *
 * THE LAW of Phase 103.1: there is no transcript fallback on this path. Copy
 * used to fall back to the whole transcript when the gesture produced no
 * text, which is the literal "it copied everything" symptom the owner
 * reported. An empty or blank selection disables the button instead.
 */
fun copySelectionText(snapshot: TerminalSnapshot, selection: TerminalSelection?): String? {
    val sel = selection ?: return null
    val text = SelectionModel.extract(snapshot.asSelectionText(), sel.range)
    return text.takeIf { it.isNotBlank() }
}

/** What the toolbar's Copy button shows: enabled iff there is selected text. */
fun selectionCopyEnabled(snapshot: TerminalSnapshot, selection: TerminalSelection?): Boolean =
    copySelectionText(snapshot, selection) != null

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
    resizeKey: Any? = null,
    /**
     * Phase 103.2 — the platform's animator scale (`ValueAnimator.getDurationScale()`).
     * Zero means the phone's "remove animations" switch is on: the fling then
     * degrades to one instant jump instead of moving, exactly as the app's
     * shared motion vocabulary treats decoration. Passed in (rather than read
     * here) so the rule is pure.
     */
    animatorScale: Float = 1f
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
    // Phase 103.2: this is a DRAG-time quantity only. The finger-lift path
    // (`TerminalScroll.rest`) snaps it to 0, which is the actual fix for the
    // owner's first-open blank band: before 103 a resting terminal could stay
    // translated by up to a row, drawing an empty strip above row 0.
    var scrollSubPx by remember { mutableFloatStateOf(0f) }

    // ---- Phase 103.1: the selection and its two handles -------------------
    var selection by remember { mutableStateOf<TerminalSelection?>(null) }
    var activeEnd by remember { mutableStateOf(SelectionEnd.CURSOR) }
    var selecting by remember { mutableStateOf(false) }
    /** The floating Copy / Select all / Paste toolbar, over the grid. */
    var toolbar by remember { mutableStateOf(false) }
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

    // Phase 103.2 — the finger-lift snap, shared by the drag path and the
    // selection path so the two cannot disagree about where "rest" is. This is
    // the whole fix for the first-open blank band: after this call
    // `scrollSubPx == 0`, so a resting grid is translated by exactly zero.
    val settleScroll = {
        val r = TerminalScroll.rest(scrollSubPx, cellH.toFloat(), topRow, minTop)
        scrollSubPx = 0f
        topRow = r.topRow
    }

    // Phase 103.1 — the selection follows its TEXT, never the view.
    //
    // A row pushed off the live screen moves every surviving cell down the
    // extY axis (output scrolling under a selection made mid-`cat`), while a
    // trim of the oldest history row moves nothing — the two are easy to
    // confuse, so both are read here and the derivation lives in exactly one
    // place (`TrimMath.contentShift`). A dropped row that took the selection
    // with it answers `lineAt` → null, and `remembers` then refuses the
    // selection instead of letting it paint over a new row.
    // Phase 103.2 — the scrollbar fades in while the view moves and out when
    // it rests; the fling job is the one ticker, paced by the frame pump's
    // interval, and is cancelled by the next touch.
    var scrollbarAlpha by remember { mutableFloatStateOf(0f) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    var lastScrollbackGeneration by remember { mutableStateOf<Long?>(snapshot.scrollbackGeneration) }
    var lastScrollbackCount by remember { mutableStateOf<Int?>(snapshot.scrollbackCount) }
    val contentShift = TrimMath.contentShift(
        prevCount = lastScrollbackCount ?: snapshot.scrollbackCount,
        nowCount = snapshot.scrollbackCount,
        prevTrims = lastScrollbackGeneration ?: snapshot.scrollbackGeneration,
        nowTrims = snapshot.scrollbackGeneration
    )
    SideEffect {
        lastScrollbackGeneration = snapshot.scrollbackGeneration
        lastScrollbackCount = snapshot.scrollbackCount
        if (contentShift != 0) {
            val moved = selection?.shifted(contentShift)
            if (moved != null && SelectionModel.remembers(moved, snapshot)) {
                selection = moved
            } else {
                // Its text is gone (or was overwritten): drop it silently
                // rather than leave a selection painting other people's cells.
                selection = null
                toolbar = false
                selecting = false
            }
        }
    }

    // The toolbar Copy reads the selection, never the transcript (103.1's law).
    val selectionCopy = copySelectionText(snapshot, selection)

    // Phase 103.1 — a selection is anchored to its ROWS (TrimMath), and the
    // fade effect that used to sit here is now the scrollbar's (103.2).

    // Update active selection state for parent toolbar copy
    LaunchedEffect(selection, snapshot.generation) {
        onSelectionChanged(selectionCopy)
    }

    // Phase 103.2 — the thumb appears only while the view is moving, then
    // fades. Keyed on the offset, not on the clock: a resting terminal draws
    // no scrollbar at all.
    LaunchedEffect(topRow, scrollSubPx) {
        if (snapshot.scrollbackCount > 0) {
            scrollbarAlpha = 1f
            delay(SCROLLBAR_LINGER_MS)
            scrollbarAlpha = 0f
        }
    }

    // Phase 103.1 — extending a selection by one line of edge scroll. The
    // handle travels with the text, and the scroll moves the viewport the same
    // number of rows so the finger stays on the cell it is holding. Returning
    // through the pure model keeps this identical to a drag frame.
    val scrollSelection: (TerminalSelection, SelectionEnd, Int, TerminalSnapshot) -> TerminalSelection =
        { sel, end, lines, snap ->
            val minTop = -snap.scrollbackCount
            val nextTop = (topRow + lines).coerceIn(minTop, 0)
            val applied = nextTop - topRow
            if (applied != 0) {
                topRow = nextTop
            }
            if (applied == 0) sel else SelectionModel.growByScroll(sel, end, applied)
        }

    // Phase 103.2 — a fling runs on the frame pump's own interval and the next
    // touch cancels it; every step goes through the same ScrollMath as a drag.
    val startFling: (Float, Float) -> Unit = { velocity, maxPx ->
        flingJob?.cancel()
        val v = velocity.coerceIn(-TerminalScroll.MAX_VELOCITY, TerminalScroll.MAX_VELOCITY)
        if (TerminalScroll.flingPx(v, maxPx).isNotEmpty()) {
            flingJob = scope.launch {
                var remaining = maxPx
                for (step in com.codeci.ide.ui.terminal.ScrollMotionPolicy.flingSteps(v, maxPx, animatorScale)) {
                    val snap = latestSnapshot
                    val moved = ScrollMath.step(
                        subRowPx = scrollSubPx,
                        deltaPx = step,
                        cellH = cellH.toFloat(),
                        topRow = topRow,
                        minTop = -snap.scrollbackCount
                    )
                    val consumed = abs(moved.topRow - topRow) * cellH + abs(moved.subRowPx - scrollSubPx)
                    scrollSubPx = moved.subRowPx
                    topRow = moved.topRow
                    remaining -= consumed
                    if (remaining <= 0f || abs(step) < 1f) break
                    delay(TerminalScroll.FRAME_MS)
                }
                // A fling ends at rest too — same snap, no band.
                val r = TerminalScroll.rest(scrollSubPx, cellH.toFloat(), topRow, -latestSnapshot.scrollbackCount)
                scrollSubPx = 0f
                topRow = r.topRow
            }
        }
    }

    // Phase 19.2 round 3: REMOVED the "jump to live on every update" effect —
    // it reset topRow=0 on every output frame, so while output streamed the
    // scrollback fought the user's finger (rubber-banding). Termux semantics:
    // a scrolled-up view STAYS put during output; typed input jumps to live
    // (the onInput path below sets topRow = 0).

    // Phase 103.1 — handle geometry in px, derived once, used by BOTH the
    // drawing code and the hit test (so what you see is what you can grab).
    val handleRadiusPx = with(density) { SELECTION_HANDLE_RADIUS_DP.dp.toPx() }
    val handleStemPx = with(density) { SELECTION_HANDLE_STEM_DP.dp.toPx() }
    val handleGapPx = with(density) { SELECTION_HANDLE_GAP_DP.dp.toPx() }
    val handleTouchPx = with(density) { 48.dp.toPx() }

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
                    // Phase 103.1/103.2 — ONE gesture authority. The two old
                    // detectors (tap/long-press, then scroll-or-select) could
                    // both claim the same finger, which is how the popup
                    // opened before a drag could start. Now a single
                    // `awaitEachGesture` loop decides what a press means and
                    // carries it to the lift:
                    //   down on a handle  → drag that handle (edge auto-scroll)
                    //   down elsewhere    → tap = clear + keyboard;
                    //                       long-press = word selection;
                    //                       drag before the long-press = scroll
                    // The lift is always a `TerminalScroll.rest` snap (103.2).
                    .pointerInput(cellW, cellH, cols, rows) {
                        detectTerminalGestures(
                            cellW = cellW,
                            cellH = cellH,
                            cols = cols,
                            rows = rows,
                            latest = { latestSnapshot },
                            subPxNow = { scrollSubPx },
                            selectionNow = { selection },
                            handleHit = { x, y ->
                                hitHandle(
                                    selection = selection,
                                    topRow = topRow,
                                    subRowPx = scrollSubPx,
                                    cellW = cellW,
                                    cellH = cellH,
                                    radiusPx = handleRadiusPx,
                                    stemPx = handleStemPx,
                                    gapPx = handleGapPx,
                                    touchPx = handleTouchPx,
                                    x = x,
                                    y = y
                                )
                            },
                            onBeginHandleDrag = { end ->
                                activeEnd = end
                                selecting = true
                                toolbar = false
                            },
                            onMoveHandle = { end, x, y ->
                                val snap = latestSnapshot
                                val cur = selection ?: return@detectTerminalGestures
                                val drag = SelectionAutoScroll.dragFor(
                                    selection = cur,
                                    end = end,
                                    x = x,
                                    y = y,
                                    cellW = cellW,
                                    cellH = cellH,
                                    cols = cols,
                                    rows = rows,
                                    xScroll = scrollSubPx,
                                    rowCap = topRow
                                )
                                val (move, scroll) = when (drag) {
                                    is SelectionDrag.Move -> drag.to to 0
                                    is SelectionDrag.AutoScroll -> null to 0
                                    is SelectionDrag.MoveAndScroll -> drag.to to drag.lines
                                }
                                if (move != null) {
                                    var sel = SelectionModel.drag(cur, end, move)
                                    if (scroll != 0) sel = scrollSelection(sel, end, scroll, snap)
                                    selection = sel
                                }
                            },
                            onTap = { x, y ->
                                val snap = latestSnapshot
                                // Content is translated by -scrollSubPx (see
                                // the draw loop), so map the touch back.
                                val row = ((y + scrollSubPx) / cellH).toInt().coerceIn(0, rows - 1)
                                val col = (x / cellW).toInt().coerceIn(0, cols - 1)
                                if (snap.mouseMode and MouseModes.CAPTURE_MASK != 0) {
                                    // Phase 19.5: the app owns the mouse —
                                    // a tap is a left click at that cell.
                                    val sgr = snap.mouseMode and MouseModes.SGR_EXT != 0
                                    MouseEncoding.press(0, col + 1, row + 1, sgr)?.let(onMouseEvent)
                                    MouseEncoding.release(0, col + 1, row + 1, sgr)?.let(onMouseEvent)
                                    return@detectTerminalGestures
                                }
                                if (selection != null) {
                                    // A tap while a selection is up dismisses it
                                    // (and the toolbar with it) — the same
                                    // one-tap exit every native text surface has.
                                    selection = null
                                    toolbar = false
                                    selecting = false
                                    onSelectionChanged(null)
                                    return@detectTerminalGestures
                                }
                                val line = snap.lineAt(topRow + row)
                                val url = if (line != null) findUrlAt(line.text, col) else null
                                if (url != null) onUrlClick(url) else keyView?.showIme()
                            },
                            onLongPress = { x, y ->
                                val snap = latestSnapshot
                                val row = ((y + scrollSubPx) / cellH).toInt().coerceIn(0, rows - 1)
                                val col = (x / cellW).toInt().coerceIn(0, cols - 1)
                                val absRow = (topRow + row).coerceIn(-snap.scrollbackCount, snap.rows - 1)
                                val line = snap.lineAt(absRow)
                                // Phase 103.1: word-grow, selection handles,
                                // floating toolbar — and NO popup in the same
                                // gesture, so a drag has something to extend.
                                val (start, end) = if (line != null) {
                                    SelectionModel.wordAt(line.text, col)
                                } else col to col
                                selection = TerminalSelection.word(
                                    absRow, start, end, line?.text.orEmpty()
                                )
                                activeEnd = SelectionEnd.CURSOR
                                selecting = true
                                toolbar = true
                                // NO haptic here on purpose. The terminal
                                // surface is one of the app's two animation-free
                                // zones (Phase 36/48) and HapticWiringTest pins
                                // it haptic-free as well (Phase 51.4): the
                                // long-press already answers by growing a word
                                // under the finger, and a buzz on a terminal
                                // that is also the source of BEL is noise.
                            },
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
                            // Phase 103.2 — the finger-lift snap is unconditional
                            // on BOTH paths (plain scroll and a drag that ended
                            // in a selection): it is the fix for the first-open
                            // blank band. A selection is not "moved" by it —
                            // it already holds buffer-absolute rows, so the snap
                            // only changes what the canvas shows, never which
                            // cells are selected.
                            onRest = { settleScroll() },
                            onFling = { velocity, aboutToScroll ->
                                val snap = latestSnapshot
                                if (snap.mouseMode and MouseModes.CAPTURE_MASK == 0) {
                                    startFling(velocity, aboutToScroll)
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
                            selection = selection?.range,
                            extY = extY,
                            cols = cols,
                            theme = theme,
                            batch = glyphBatch
                        )
                    }
                }
                // Phase 103.1 — the two handles ride above the grid, drawn
                // from the pure geometry (SelectionHandles), never measured
                // by eye. Each is a 20 dp circle on a short stem tab; the
                // touch target is the 48 dp box the gesture path hit-tests.
                val handleMetrics = SelectionHandles.Metrics(
                    radius = handleRadiusPx,
                    stem = handleStemPx,
                    gap = handleGapPx
                )
                val sel = selection
                if (sel != null && !sel.range.isCaret()) {
                    for ((end, cell) in listOf(
                        SelectionEnd.ANCHOR to sel.anchorEnd,
                        SelectionEnd.CURSOR to sel.cursorEnd
                    )) {
                        val viewRow = cell.row - topRow
                        if (viewRow < -1 || viewRow > rows) continue
                        val h = SelectionHandles.forCell(
                            col = cell.col,
                            row = viewRow,
                            cellW = cellW,
                            cellH = cellH,
                            end = end == SelectionEnd.CURSOR,
                            xScroll = scrollSubPx,
                            metrics = handleMetrics
                        )
                        // Stem: a filled tab from the cell edge to the circle.
                        drawRect(
                            color = theme.cursor,
                            topLeft = Offset(h.tabX - h.tabW, minOf(h.tabY, h.circleY)),
                            size = Size(h.tabW, kotlin.math.abs(h.circleY - h.tabY).coerceAtLeast(h.tabH))
                        )
                        // The dot itself: hollow ring, so it never hides text
                        // under it — Termux's handle, drawn with the terminal's
                        // own cursor colour (Phase 40.5: existing roles only).
                        drawCircle(
                            color = theme.cursor,
                            radius = handleMetrics.radius,
                            center = Offset(h.circleX, h.circleY),
                            style = Stroke(width = with(density) { 2.dp.toPx() })
                        )
                    }
                }
                // Phase 103.2 — the scrollbar: proportional to the scrollback,
                // only while there is something to scroll. Fades out when idle
                // so a resting terminal is a clean sheet (the owner's look).
                if (scrollbarAlpha > 0f) {
                    val bar = TerminalScroll.scrollbar(
                        topRow = topRow,
                        lines = rows,
                        scrollback = snapshot.scrollbackCount,
                        viewH = size.height
                    )
                    if (bar.hasTravel) {
                        drawRoundRect(
                            color = theme.foreground.copy(alpha = 0.25f * scrollbarAlpha),
                            topLeft = Offset(size.width - with(density) { 6.dp.toPx() }, 0f),
                            size = Size(with(density) { 3.dp.toPx() }, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(with(density) { 2.dp.toPx() })
                        )
                        drawRoundRect(
                            color = theme.cursor.copy(alpha = 0.55f * scrollbarAlpha),
                            topLeft = Offset(
                                size.width - with(density) { 6.dp.toPx() },
                                bar.offsetPx
                            ),
                            size = Size(
                                with(density) { 3.dp.toPx() },
                                bar.lengthPx.coerceAtMost(size.height)
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(with(density) { 2.dp.toPx() })
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

            // Phase 103.1 — the floating toolbar replaces the popup menu:
            // Copy / Select all / Paste are the one-tap verbs, the rest
            // (Open URL, Copy all, Share, Reset) is the ⋮ overflow. Copy is
            // DISABLED when the selection yields no text — it never falls
            // back to the transcript.
            if (toolbar && selection != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    SelectionToolbar(
                        theme = theme,
                        hasSelection = selectionCopy != null,
                        onCopy = {
                            val text = copySelectionText(snapshot, selection)
                            if (text != null) onCopyText(text)
                            // Copy ends the interaction, like every native
                            // text surface: the selection clears, the grid
                            // is clean, and the toolbar goes with it.
                            selection = null
                            toolbar = false
                            selecting = false
                            onSelectionChanged(null)
                        },
                        onSelectAll = {
                            selection = SelectionModel.selectAll(latestSnapshot)
                            activeEnd = SelectionEnd.CURSOR
                            selecting = true
                        },
                        onPaste = {
                            toolbar = false
                            selection = null
                            selecting = false
                            onPaste()
                        },
                        more = { close ->
                            val selectedText = copySelectionText(snapshot, selection)
                            val selectedUrl = selectedText?.let { findUrlAt(it, 0) }
                            if (selectedUrl != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.terminal_open_url)) },
                                    onClick = {
                                        close()
                                        toolbar = false
                                        selection = null
                                        onUrlClick(selectedUrl)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.terminal_copy_all)) },
                                onClick = {
                                    close()
                                    toolbar = false
                                    selection = null
                                    onCopyText(snapshot.transcriptText())
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.terminal_share)) },
                                onClick = {
                                    close()
                                    val text = selectedText ?: snapshot.transcriptText()
                                    toolbar = false
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
                                text = { Text(stringResource(R.string.terminal_reset)) },
                                onClick = {
                                    close()
                                    toolbar = false
                                    selection = null
                                    onReset()
                                }
                            )
                        }
                    )
                }
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

/**
 * Phase 103.1 — the floating selection toolbar, anchored inside the terminal
 * (never over the keyboard), Termux-shaped: Copy · Select all · Paste, with a
 * ⋮ for the verbs that are not one-tap (Open URL, Copy all, Share, Reset).
 *
 * Copy is DISABLED, not silently lenient, when the selection yields nothing:
 * the pre-103 fallback copied the whole transcript, which is the exact
 * "partial copy copied everything" report. Each button keeps a ≥ 48 dp target
 * (FillHeight + min size) and the bar is drawn on the terminal's own
 * background with the cursor colour for the primary verb, so it needs no
 * Material role inside this file (Phase 36/Phase 40.5 laws).
 */
@Composable
private fun SelectionToolbar(
    theme: TerminalThemeColors,
    hasSelection: Boolean,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onPaste: () -> Unit,
    more: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    var moreOpen by remember { mutableStateOf(false) }
    // The bar is drawn on the terminal's own dark surface, so the buttons take
    // the terminal's foreground role instead of the Material primary ink —
    // existing roles only, no new literal (Phase 40.5), and readable on every
    // terminal theme rather than only the light ones.
    val colors = ButtonDefaults.textButtonColors(
        contentColor = theme.foreground,
        disabledContentColor = theme.foreground.copy(alpha = 0.38f)
    )
    Row(
        modifier = Modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            // Phase 40.5: the theme's own top-bar role, never a new literal —
            // a toolbar floating over the terminal is chrome, not text.
            .background(theme.topBarBackground)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCopy, enabled = hasSelection, colors = colors) {
            Text(stringResource(R.string.terminal_copy))
        }
        TextButton(onClick = onSelectAll, colors = colors) {
            Text(stringResource(R.string.terminal_select_all))
        }
        TextButton(onClick = onPaste, colors = colors) {
            Text(stringResource(R.string.terminal_paste))
        }
        Box(modifier = Modifier.widthIn(min = 48.dp)) {
            TextButton(onClick = { moreOpen = true }, colors = colors) {
                Text(stringResource(R.string.terminal_more))
            }
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                // Each row closes this menu before doing its work.
                val close: () -> Unit = { moreOpen = false }
                more(close)
            }
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
    selection: SelectionRange?,
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
    val sel = selection
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
    if (sel != null && extY in sel.start.row..sel.end.row) {
        // Phase 103.1 — the band is computed from the ordered range
        // (SelectionRange is normalized at construction), so a drag through
        // itself and a right-to-left selection draw identically.
        val left = if (extY == sel.start.row) sel.start.col else 0
        val right = if (extY == sel.end.row) sel.end.col + 1 else cols
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


/**
 * Phase 103.1 — the OLD one-word extractor, kept only as a thin adapter for
 * callers that still hold a [GridSelection]. The semantics are the new ones
 * (soft wraps joined); it is here so the existing suite's rectangle case keeps
 * measuring the same thing. New code uses [SelectionModel.extract].
 */
@Suppress("DEPRECATION")
fun TerminalSnapshot.selectedText(sel: GridSelection): String {
    val a = SelectionPoint(sel.y1, sel.x1)
    val b = SelectionPoint(sel.y2, sel.x2)
    val range = SelectionRange(minOf(a, b), maxOf(a, b))
    return SelectionModel.extract(asSelectionText(), range)
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

// ---------------------------------------------------------------------------
// Phase 103.1 / 103.2 — the gesture authority and its Android edge.
//
// Everything numerically interesting about these gestures is a pure function
// in `ui/terminal/TerminalSelection.kt` (`SelectionModel`,
// `SelectionAutoScroll`, `SelectionHandles`) and
// `ui/terminal/TerminalScroll.kt` (`TerminalScroll.rest`, `flingPx`,
// `scrollbar`). What is left here is only what needs a pointer, a clock or a
// canvas: which arm a press takes, the frame loop, the haptic, the constants.
// ---------------------------------------------------------------------------

/** The handles' visible size (dp) — the touch target is the 48 dp box below. */
internal const val SELECTION_HANDLE_RADIUS_DP = 10f
internal const val SELECTION_HANDLE_STEM_DP = 10f
internal const val SELECTION_HANDLE_GAP_DP = 2f

/** A thumb stays visible this long after the last movement, then fades. */
private const val SCROLLBAR_LINGER_MS = 700L

/**
 * ONE detector for the whole surface — the structural fix behind the owner's
 * drag bug. Before 103 the tap/long-press detector and the scroll-or-select
 * detector were two `pointerInput` blocks racing for the same finger, and the
 * long-press opened a popup in the same gesture that claimed the drag.
 */
private suspend fun PointerInputScope.detectTerminalGestures(
    cellW: Int,
    cellH: Int,
    cols: Int,
    rows: Int,
    latest: () -> TerminalSnapshot,
    subPxNow: () -> Float,
    selectionNow: () -> TerminalSelection?,
    handleHit: (Float, Float) -> SelectionEnd?,
    onBeginHandleDrag: (SelectionEnd) -> Unit,
    onMoveHandle: (SelectionEnd, Float, Float) -> Unit,
    onTap: (Float, Float) -> Unit,
    onLongPress: (Float, Float) -> Unit,
    onScroll: (Float, Float, Float) -> Unit,
    onRest: () -> Unit,
    onFling: (Float, Float) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var mode: Mode = Mode.UNDECIDED
        val grabbed = handleHit(down.position.x, down.position.y)
        if (grabbed != null) {
            mode = Mode.HANDLE
            onBeginHandleDrag(grabbed)
        }
        val downAt = down.uptimeMillis
        // Where the press landed. A long-press must ESTABLISH its word and stop
        // there: the first version fell into SELECTING and emitted a move on
        // the very next frame, which clamped the freshly-grabbed word to the
        // finger's own cell and made every long-press a one-cell caret. The
        // selection only starts extending once the finger has actually moved.
        val press = down.position
        var lastY = down.position.y
        var lastX = down.position.x
        var lastT = down.uptimeMillis
        var velocity = 0f
        var moved = false
        var longPressed = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
                ?: event.changes.firstOrNull()
                ?: break
            if (!change.pressed) {
                // ---- lift: the ONE exit every path shares -------------------
                onRest()
                if (mode == Mode.SCROLL) {
                    val snap = latest()
                    val rangePx = snap.scrollbackCount * cellH.toFloat()
                    if (rangePx > 0f) onFling(velocity, rangePx)
                }
                if (mode == Mode.UNDECIDED && !moved && !longPressed) {
                    onTap(down.position.x, down.position.y)
                }
                change.consume()
                break
            }

            val dy = change.position.y - lastY
            val dx = change.position.x - lastX
            if (!moved && (abs(change.position.y - press.y) > TOUCH_SLOP_PX ||
                    abs(change.position.x - press.x) > TOUCH_SLOP_PX)
            ) moved = true
            val dt = (change.uptimeMillis - lastT).coerceAtLeast(1L)
            velocity = (dy / dt).let { 0.8f * velocity + 0.2f * it }

            when (mode) {
                Mode.UNDECIDED -> {
                    if (moved) {
                        // A drag that never became a long-press is a scroll.
                        mode = Mode.SCROLL
                        onScroll(-dy, change.position.x, change.position.y)
                    } else if (!longPressed &&
                        change.uptimeMillis - downAt >= LONG_PRESS_MS
                    ) {
                        longPressed = true
                        mode = Mode.SELECTING
                        onLongPress(change.position.x, change.position.y)
                    }
                }
                Mode.SCROLL -> {
                    onScroll(-dy, change.position.x, change.position.y)
                }
                Mode.HANDLE -> {
                    // A grab inside the 48 dp touch box is not a drag: the
                    // handle must stay on its cell until the finger actually
                    // travels, or it jumps up to a thumb-width on touch-down.
                    if (moved) {
                        onMoveHandle(grabbed ?: SelectionEnd.CURSOR, change.position.x, change.position.y)
                        change.consume()
                    }
                }
                Mode.SELECTING -> {
                    // The word from the long-press stands until the finger
                    // leaves the press point; then it extends like a handle.
                    val travel = abs(change.position.y - press.y) + abs(change.position.x - press.x)
                    if (travel > TOUCH_SLOP_PX) {
                        onMoveHandle(grabbed ?: SelectionEnd.CURSOR, change.position.x, change.position.y)
                        change.consume()
                    }
                }
            }
            lastY = change.position.y
            lastX = change.position.x
            lastT = change.uptimeMillis
        }
    }
}

private enum class Mode { UNDECIDED, SCROLL, SELECTING, HANDLE }

/** The platform's own slop, expressed once (Compose defaults to 8 px). */
private const val TOUCH_SLOP_PX = 8f
private const val LONG_PRESS_MS = 500L

/** Hit-tests the two handles from the SAME geometry the canvas draws them with. */
private fun hitHandle(
    selection: TerminalSelection?,
    topRow: Int,
    subRowPx: Float,
    cellW: Int,
    cellH: Int,
    radiusPx: Float,
    stemPx: Float,
    gapPx: Float,
    touchPx: Float,
    x: Float,
    y: Float
): SelectionEnd? {
    val sel = selection ?: return null
    if (sel.range.isCaret()) return null
    val metrics = SelectionHandles.Metrics(radius = radiusPx, stem = stemPx, gap = gapPx)
    for (end in listOf(SelectionEnd.ANCHOR, SelectionEnd.CURSOR)) {
        // The LOGICAL ends, not the normalized pair: a drag that crossed the
        // other end leaves each handle on its own cell, and grabbing the one
        // under the finger must move that same end.
        val cell = if (end == SelectionEnd.ANCHOR) sel.anchorEnd else sel.cursorEnd
        val viewRow = cell.row - topRow
        if (viewRow < -1 || viewRow > rows) continue
        val h = SelectionHandles.forCell(
            col = cell.col,
            row = viewRow,
            cellW = cellW,
            cellH = cellH,
            end = end == SelectionEnd.CURSOR,
            xScroll = subRowPx,
            metrics = metrics
        )
        if (SelectionHandles.hits(h, x, y, touchPx)) return end
    }
    return null
}
