package com.codeci.ide.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.components.KeyGestureDetector
import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.editor.EditorKeySet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Phase 28.2 — CodeC Keys: the data-driven keyboard surface. It draws a
 * [KeyboardLayout] and NOTHING ELSE decides behavior: every press becomes a
 * [CapAction] through the pure [KeyboardRouter], edits go through
 * `EditorKeySet.apply`, and the caller's [onValueChange] is the strip's own
 * entry point (`viewModel.updateCode(..., isStrip = true)` at the screen —
 * the S2 path the 28.1 spike certified: programmatic edits into the VM,
 * `SoraEditorHost` replays them into the document).
 *
 * Feel law (spec §1.3): the press state changes the cap color INSIDE the
 * gesture (no ripple, no animation), haptics tick on down, hold-repeat runs
 * on 26.1's shared timers, popups/flicks follow `KeyGestureDetector`'s
 * thresholds — the same recognizer constants the strip ships, so a cap
 * behaves like its strip sibling one row up.
 */
@Composable
fun CodecKeyboard(
    layout: KeyboardLayout,
    shift: ShiftState,
    onShiftChange: (ShiftState) -> Unit,
    onLayerChange: (Int) -> Unit,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    tabSize: Int = 4,
    haptics: Boolean = true,
    /** First refusal on a resolved key (the dual-mood ghost caps, 27.1 law). */
    onInterceptKey: ((EditorKey) -> Boolean)? = null,
    onCommentToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rowHeight = (52f * layout.heightScaleClamped).dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 4.dp, vertical = 5.dp)
    ) {
        layout.rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { cap ->
                    CodecKeycap(
                        cap = cap,
                        rowHeight = rowHeight,
                        shift = shift,
                        haptics = haptics,
                        // RowScope.weight must be applied HERE, in the Row's
                        // scope, then handed down the chain.
                        weightModifier = Modifier.weight(cap.widthWeight),
                        onAction = { action ->
                            when (action) {
                                is CapAction.Edit -> {
                                    val key = action.key
                                    val consumed = onInterceptKey?.invoke(key) == true
                                    if (!consumed) {
                                        if (key is EditorKey.CommentToggle) {
                                            onCommentToggle?.invoke()
                                                ?: onValueChange(EditorKeySet.apply(key, textFieldValue, tabSize))
                                        } else {
                                            onValueChange(EditorKeySet.apply(key, textFieldValue, tabSize))
                                        }
                                    }
                                    // Shift ONCE dies with the committed
                                    // action — tap OR flick, one law.
                                    val next = KeyboardRouter.shiftAfterAction(shift, action)
                                    if (next != shift) onShiftChange(next)
                                }
                                CapAction.ToggleShift -> onShiftChange(KeyboardRouter.toggleShift(shift))
                                CapAction.ToggleLock -> onShiftChange(KeyboardRouter.toggleLock(shift))
                                is CapAction.SetLayer -> onLayerChange(action.id)
                                CapAction.Noop -> Unit
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * One keycap: instant press color, popup-on-hold, flick-up/down layers,
 * hold-repeat per the layout's [KeycapModel.repeat] — the 26.1 recognizer
 * rules expressed for grid caps. `rememberUpdatedState` keeps the actions
 * fresh INSIDE a live gesture: hold-repeat fires faster than recomposition,
 * and every 40 ms tick must see the latest buffer and the latest shift.
 */
@Composable
private fun CodecKeycap(
    cap: KeycapModel,
    rowHeight: Dp,
    shift: ShiftState,
    haptics: Boolean,
    weightModifier: Modifier,
    onAction: (CapAction) -> Unit
) {
    val def = cap.def
    val isShiftCap = def.label == KeyboardRouter.SHIFT_CAP
    val hasLongPressAction = isShiftCap || def.popup != null
    var pressed by remember(cap) { mutableStateOf(false) }
    var popupShown by remember(cap) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val swipePx = with(density) { KeyGestureDetector.SWIPE_THRESHOLD_DP.dp.toPx() }
    val freshAction by rememberUpdatedState(onAction)
    val freshShift by rememberUpdatedState(shift)
    val label = KeyboardRouter.displayLabel(def, shift)

    Box(
        modifier = weightModifier
            .height(rowHeight)
            // Rounded FILL only — no child clip: the popup bubble renders
            // outside the cap bounds (the strip clips today; we don't).
            .background(
                shape = RoundedCornerShape(9.dp),
                color = when {
                    pressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                    isShiftCap && shift != ShiftState.OFF ->
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                }
            )
            .semantics { contentDescription = label }
            .pointerInput(cap, isShiftCap) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val startX = down.position.x
                    val startY = down.position.y
                    var isLongPress = false
                    var isSwipe: String? = null
                    var repeated = false

                    val longPressJob = scope.launch {
                        delay(KeyGestureDetector.LONG_PRESS_MS)
                        if (isSwipe == null && !repeated && hasLongPressAction) {
                            isLongPress = true
                            popupShown = true // ⬆ shows the ⇪ affordance, others the popup key
                        }
                    }
                    val repeatJob: Job? = if (cap.repeat) scope.launch {
                        delay(KeyGestureDetector.HOLD_INITIAL_MS)
                        if (isSwipe == null) {
                            longPressJob.cancel()
                            repeated = true
                            while (true) {
                                freshAction(KeyboardRouter.tapAction(def, freshShift))
                                delay(KeyGestureDetector.HOLD_REPEAT_MS)
                            }
                        }
                    } else null

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val dy = change.position.y - startY
                        val dx = change.position.x - startX
                        if (isSwipe == null && abs(dy) > swipePx && abs(dy) > abs(dx)) {
                            isSwipe = when {
                                dy < 0 && def.swipeUp != null -> "up"
                                dy > 0 && def.swipeDown != null -> "down"
                                else -> "none"
                            }
                            longPressJob.cancel()
                            repeatJob?.cancel()
                            popupShown = false
                            change.consume()
                            if (isSwipe == "up" || isSwipe == "down") {
                                freshAction(KeyboardRouter.swipeAction(def, up = isSwipe == "up"))
                            }
                        }
                    }
                    longPressJob.cancel()
                    repeatJob?.cancel()
                    pressed = false
                    val showedPopup = popupShown
                    popupShown = false
                    when {
                        isSwipe != null || repeated -> Unit
                        isLongPress && showedPopup -> {
                            freshAction(
                                if (isShiftCap) CapAction.ToggleLock
                                else KeyboardRouter.popupAction(def)
                            )
                        }
                        else -> freshAction(KeyboardRouter.tapAction(def, freshShift))
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        // corner tick = this cap carries extras (26.1 affordance law)
        if (hasLongPressAction || def.swipeUp != null || def.swipeDown != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            )
        }
        if (popupShown) {
            val popupText = when {
                isShiftCap -> "⇪"
                def.popup != null -> KeyboardRouter.popupLabel(def.popup)
                else -> null
            }
            if (popupText != null) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, -with(density) { (rowHeight + 6.dp).roundToPx() }) }
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = popupText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
