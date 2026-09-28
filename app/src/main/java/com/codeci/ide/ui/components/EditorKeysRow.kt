package com.codeci.ide.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.editor.EditorKeyDef
import com.codeci.ide.ui.editor.EditorKeySet
import com.codeci.ide.ui.editor.RunKey
import com.codeci.ide.ui.editor.RunKeyDef
import com.codeci.ide.ui.editor.RunKeySet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Phase 16 — the snippet / extra-keys row docked directly above the status
 * bar (Spck's signature row, mockup-exact): flat keycaps — 40dp tall, 10dp
 * radius, a slightly lighter fill, NO border — on the editor's own
 * background, horizontally scrollable. Data-driven from [EditorKeySet]: the
 * caller supplies the precomputed [keys] (Phase 23.2 decides which set via
 * `keysForContext`), and a tap applies the key to the buffer.
 *
 * Phase 26.1 — key strip 2.0: long-press popup, swipe layers, hold-repeat.
 * Caps under 48dp, corner tick for caps with extras, pure gesture state
 * machine (host-testable via KeyGestureDetector), hold-repeat for arrows.
 * FIX 2026-09-05: horizontal drag to scroll the strip no longer inserts a key
 * (touchSlop vs swipe), long-press tolerates tiny jitter, swipe consumes.
 *
 * Phase 69.1 — where the row is scrolled to is the OWNER's business, not this
 * composable's: the caps a phone thumb reaches for most (`;`, `/`, `=`, then
 * the arrows) live in the row's right half, so the row gets scrolled — and a
 * `rememberScrollState()` in here lives exactly as long as the row does. The
 * row is composed at TWO call sites (keyboard down / keyboard up) and in three
 * branches of the one strip (Keys | Suggestions | Run), so every keyboard
 * toggle and every chip appearance handed back a fresh state and snapped the
 * row back to its left edge. [scrollState] is passed in and owned above, one
 * state for the Keys variant.
 */
@Composable
fun EditorKeysRow(
    keys: List<EditorKeyDef>,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    tabSize: Int = 4,
    onCommentToggle: (() -> Unit)? = null,
    /** Phase 69.1 — the row's horizontal position, owned by the caller. */
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
    /**
     * Phase 27.1 — first refusal on a resolved key (the dual-mood ghost caps).
     * Return true = consumed; return false/null = [EditorKeySet.apply] as usual.
     */
    onInterceptKey: ((EditorKey) -> Boolean)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { def ->
            EditorKeyCap(
                def = def,
                onKey = { key ->
                    if (onInterceptKey?.invoke(key) == true) {
                        // consumed upstream (ghost accept) — buffer untouched
                    } else if (key is EditorKey.CommentToggle) {
                        onCommentToggle?.invoke() ?: onValueChange(EditorKeySet.apply(key, textFieldValue, tabSize))
                    } else {
                        onValueChange(EditorKeySet.apply(key, textFieldValue, tabSize))
                    }
                }
            )
        }
    }
}

/**
 * Phase 23.2 — the interactive-run keys. Shown instead of [EditorKeysRow]
 * while an interactive program is waiting for stdin: submit the line, send
 * SIGINT, append a tab, and history arrows (no-op stubs for now). Actions go
 * through [onKeyAction] so the screen routes them to the ViewModel.
 *
 * Phase 26.1 — run keys gain their own popups (HOME/END/PGUP/PGDN etc).
 */
@Composable
fun RunKeysRow(
    onKeyAction: (RunKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RunKeySet.KEYS.forEach { def ->
            RunKeyCap(def = def, onKeyAction = onKeyAction)
        }
    }
}

/** Phase 26.1 — pure gesture classification for host tests. */
object KeyGestureDetector {
    const val LONG_PRESS_MS = 300L
    const val SWIPE_THRESHOLD_DP = 28f
    const val HOLD_INITIAL_MS = 150L
    const val HOLD_REPEAT_MS = 40L

    enum class Result { TAP, POPUP, SWIPE_UP, SWIPE_DOWN, HOLD_REPEAT, NONE }

    /**
     * Phase 69.3 — is this displacement the row SCROLLING (and therefore never
     * a key)?
     *
     * The owner (2026-09-28, verbatim): *"The quick keys are sensitive even i
     * want to drag for other keys it's types which ever i am scrolling"*. The
     * row starts scrolling at the platform's own touch slop
     * ([androidx.compose.ui.platform.ViewConfiguration.touchSlop], in pixels —
     * Android's `scaledTouchSlop`, 8 dp on a stock phone); until 69.3 the cap
     * only gave up the tap past **20 dp**, so every drag between the two — the
     * short flick a thumb actually uses to reach the caps on the right —
     * scrolled the row *and* typed the cap the finger started on.
     *
     * The rule is one number and one owner: past the slop, with the horizontal
     * travel at least the vertical, the gesture belongs to the row. The caller
     * must not consume the change, so `horizontalScroll` still gets it.
     */
    fun isScrollDx(dxPx: Float, dyPx: Float, slopPx: Float): Boolean {
        val absDx = abs(dxPx)
        return absDx > slopPx && absDx >= abs(dyPx)
    }

    /**
     * Classifies gesture from duration and displacement.
     * [durationMs] time finger was down, [dyPx] vertical displacement (negative = up),
     * [dxPx] horizontal, [hasPopup]/[hasSwipe] whether cap carries those.
     * Pure for CI.
     */
    fun classify(
        durationMs: Long,
        dyPx: Float,
        dxPx: Float,
        hasPopup: Boolean,
        hasSwipeUp: Boolean,
        hasSwipeDown: Boolean,
        isArrow: Boolean
    ): Result {
        val absDy = abs(dyPx)
        val absDx = abs(dxPx)
        // Horizontal scroll dominates -> not a key tap at all
        if (absDx > 20 * 3 && absDx > absDy) {
            return Result.NONE
        }
        // Swipe takes precedence if vertical drag dominates.
        if (absDy > absDx && absDy > SWIPE_THRESHOLD_DP * 3) { // approx 28dp * density ~3
            return if (dyPx < 0 && hasSwipeUp) Result.SWIPE_UP
            else if (dyPx > 0 && hasSwipeDown) Result.SWIPE_DOWN
            else Result.NONE
        }
        if (isArrow && durationMs >= HOLD_INITIAL_MS) {
            return Result.HOLD_REPEAT
        }
        if (durationMs >= LONG_PRESS_MS && hasPopup) {
            return Result.POPUP
        }
        return Result.TAP
    }
}

@Composable
private fun EditorKeyCap(
    def: EditorKeyDef,
    onKey: (EditorKey) -> Unit
) {
    val hasPopup = def.popup != null
    val hasSwipe = def.swipeUp != null || def.swipeDown != null
    val isArrow = def.key is EditorKey.Caret && (def.key as EditorKey.Caret).move in setOf(
        EditorKey.Caret.Move.LEFT, EditorKey.Caret.Move.RIGHT,
        EditorKey.Caret.Move.UP, EditorKey.Caret.Move.DOWN
    )
    var showPopup by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 28.dp.toPx() }
    // Phase 69.3 — the distance the row itself starts scrolling at, not a
    // number invented here: `ViewConfiguration.touchSlop` is the platform's
    // own slop (Android's `scaledTouchSlop`, 8 dp on a stock device, already
    // in PIXELS) and it is the same value `horizontalScroll` uses to claim a
    // drag — so the cap gives up the tap at exactly the moment the row takes
    // the scroll (owner: *"even i want to drag for other keys it's types which
    // ever i am scrolling"*).
    val scrollSlopPx = LocalViewConfiguration.current.touchSlop

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = if (def.wide) 56.dp else 44.dp, minHeight = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .pointerInput(def) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var isLongPress = false
                    var isSwipe: String? = null
                    var isScroll = false
                    val startY = down.position.y
                    val startX = down.position.x
                    val longPressJob = scope.launch {
                        delay(EditorKeySet.LONG_PRESS_MS)
                        if (isSwipe == null && !isScroll && hasPopup) {
                            isLongPress = true
                            showPopup = true
                        }
                    }
                    val hrJob = if (isArrow) scope.launch {
                        delay(EditorKeySet.HOLD_INITIAL_DELAY_MS)
                        held = true
                        while (true) {
                            // Phase 69.3 — a hold that turned into a scroll
                            // never repeats, not even the first step: the
                            // arrows are the caps a thumb drags from.
                            if (!isScroll) onKey(def.key)
                            delay(EditorKeySet.HOLD_REPEAT_INTERVAL_MS)
                        }
                    } else null
                    holdJob = hrJob

                    // Track moves until up
                    var finished = false
                    while (!finished) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change == null) {
                            finished = true
                            break
                        }
                        // Phase 69.3 — the LAST change counts too. A flick can
                        // lift the finger where no move event was ever
                        // delivered, and that up is where the gesture really
                        // ended: measure it before calling the touch finished.
                        val dy = change.position.y - startY
                        val dx = change.position.x - startX
                        if (!change.pressed) finished = true
                        // Horizontal scroll dominates -> suppress tap, let Row scroll
                        if (!isScroll && KeyGestureDetector.isScrollDx(dx, dy, scrollSlopPx)) {
                            isScroll = true
                            longPressJob.cancel()
                            hrJob?.cancel()
                            showPopup = false
                            // don't consume, let horizontalScroll handle it
                        } else if (isSwipe == null && !isScroll && abs(dy) > swipeThresholdPx && abs(dy) > abs(dx)) {
                            if (dy < 0 && def.swipeUp != null) {
                                isSwipe = "up"
                                longPressJob.cancel()
                                hrJob?.cancel()
                                showPopup = false
                                change.consume()
                            } else if (dy > 0 && def.swipeDown != null) {
                                isSwipe = "down"
                                longPressJob.cancel()
                                hrJob?.cancel()
                                showPopup = false
                                change.consume()
                            } else if (abs(dy) > swipeThresholdPx) {
                                // vertical drag but key has no swipe in that direction -> cancel popup, still treat as scroll/no-op
                                longPressJob.cancel()
                                hrJob?.cancel()
                                showPopup = false
                            }
                        }
                    }
                    longPressJob.cancel()
                    hrJob?.cancel()
                    holdJob = null
                    val wasHeld = held
                    held = false
                    showPopup = false

                    when {
                        isScroll -> Unit // horizontal scroll, no key
                        isSwipe == "up" && def.swipeUp != null -> {
                            onKey(def.swipeUp)
                        }
                        isSwipe == "down" && def.swipeDown != null -> {
                            onKey(def.swipeDown)
                        }
                        wasHeld -> {
                            Unit
                        }
                        isLongPress && def.popup != null -> {
                            onKey(def.popup)
                        }
                        isSwipe != null -> {
                            // swipe detected but no corresponding action -> no-op (don't fall through to tap)
                            Unit
                        }
                        !isScroll -> {
                            onKey(def.key)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = def.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        if (hasPopup || hasSwipe) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    // Phase 40.5 — onPrimary on a 70%-alpha primary measured
                    // 3.46:1; the badge uses the opaque primary (5.06:1).
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            ) {
                Text(
                    text = when {
                        hasPopup && hasSwipe -> "•"
                        hasPopup -> "⌃"
                        else -> "↕"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 1.dp)
                )
            }
        }
        if (showPopup && def.popup != null) {
            val popupLabel = when (val p = def.popup) {
                is EditorKey.Insert -> p.text
                is EditorKey.Pair -> p.open + p.close
                is EditorKey.Caret -> when (p.move) {
                    EditorKey.Caret.Move.LINE_START -> "Home"
                    EditorKey.Caret.Move.LINE_END -> "End"
                    EditorKey.Caret.Move.PAGE_UP -> "PgUp"
                    EditorKey.Caret.Move.PAGE_DOWN -> "PgDn"
                    else -> "•"
                }
                EditorKey.Tab -> "TAB"
                EditorKey.DeleteWord -> "⌫W"
                EditorKey.CommentToggle -> "//"
                else -> "•"
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, -with(density) { 44.dp.roundToPx() }) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = popupLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun RunKeyCap(
    def: RunKeyDef,
    onKeyAction: (RunKey) -> Unit
) {
    val hasPopup = def.popup != null
    var showPopup by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 28.dp.toPx() }
    // Phase 69.3 — the distance the row itself starts scrolling at, not a
    // number invented here: `ViewConfiguration.touchSlop` is the platform's
    // own slop (Android's `scaledTouchSlop`, 8 dp on a stock device, already
    // in PIXELS) and it is the same value `horizontalScroll` uses to claim a
    // drag — so the cap gives up the tap at exactly the moment the row takes
    // the scroll (owner: *"even i want to drag for other keys it's types which
    // ever i am scrolling"*).
    val scrollSlopPx = LocalViewConfiguration.current.touchSlop

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = if (def.wide) 56.dp else 44.dp, minHeight = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .pointerInput(def) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var isLongPress = false
                    var isScroll = false
                    val startY = down.position.y
                    val startX = down.position.x
                    val job = scope.launch {
                        delay(EditorKeySet.LONG_PRESS_MS)
                        if (!isScroll && hasPopup) {
                            isLongPress = true
                            showPopup = true
                        }
                    }
                    var finished = false
                    while (!finished) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change == null) {
                            finished = true
                            break
                        }
                        // Phase 69.3 — the LAST change counts too. A flick can
                        // lift the finger where no move event was ever
                        // delivered, and that up is where the gesture really
                        // ended: measure it before calling the touch finished.
                        val dy = change.position.y - startY
                        val dx = change.position.x - startX
                        if (!change.pressed) finished = true
                        if (!isScroll && KeyGestureDetector.isScrollDx(dx, dy, scrollSlopPx)) {
                            isScroll = true
                            job.cancel()
                            showPopup = false
                        } else if (abs(dy) > swipeThresholdPx) {
                            job.cancel()
                            showPopup = false
                            // no swipe for run keys, just cancel popup
                        }
                    }
                    job.cancel()
                    val wasPopup = showPopup
                    showPopup = false
                    if (isScroll) {
                        Unit
                    } else if (wasPopup && def.popup != null && isLongPress) {
                        onKeyAction(def.popup)
                    } else if (!isScroll) {
                        onKeyAction(def.action)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = def.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        if (hasPopup) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    // Phase 40.5 — onPrimary on a 70%-alpha primary measured
                    // 3.46:1; the badge uses the opaque primary (5.06:1).
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "⌃",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        if (showPopup && def.popupLabel != null) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, -with(density) { 44.dp.roundToPx() }) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = def.popupLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
