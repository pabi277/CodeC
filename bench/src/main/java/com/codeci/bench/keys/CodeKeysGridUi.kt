package com.codeci.bench.keys

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

/**
 * Phase 28.1 — the spike key grid: 3 letter rows + TAB/DEL/⏎/space, rendered
 * flat with INSTANT press state (spec: "key down → visual press state in the
 * SAME frame — ripple too slow"). Deliberately NOT the 28.2 engine: no
 * popups, no swipes, no JSON. What IS here is the interaction law 28.2 will
 * inherit: every press routes through [SpikeSession.press], and DEL repeats
 * on 26.1's 150 ms / 40 ms shared timer cadence.
 */
@Composable
fun CodeKeysGrid(session: SpikeSession, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(session) {
        session.hapticTick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    val rows = remember { CodecKeyGrid.rows() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for ((rowIndex, row) in rows.withIndex()) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { cap ->
                    KeycapCell(
                        cap = cap,
                        session = session,
                        wide = cap.wide || (rowIndex == rows.lastIndex && row.size <= 4),
                        modifier = Modifier.weight(if (cap.wide) 5f else if (cap.backspace) 2.2f else 1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeycapCell(
    cap: GridKeycap,
    session: SpikeSession,
    wide: Boolean,
    modifier: Modifier = Modifier
) {
    var pressed by remember(cap) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var holdJob by remember(cap) { mutableStateOf<Job?>(null) }
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            // Instant color swap — no ripple, no animation (feel law).
            .background(
                if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (wide) 0.5f else 0.7f)
            )
            .pointerInput(cap) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    // DOWN → press → commit, all synchronous in the handler:
                    // the same shape a production keycap must keep.
                    session.press(cap, System.nanoTime())
                    if (cap.holdRepeat) {
                        holdJob = scope.launch {
                            delay(KeysSpikeScripts.HOLD_INITIAL_MS)
                            while (pressed) {
                                session.press(cap, System.nanoTime())
                                delay(KeysSpikeScripts.HOLD_REPEAT_MS)
                            }
                        }
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                    }
                    pressed = false
                    holdJob?.cancel()
                    holdJob = null
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = cap.label,
            fontSize = if (cap.label.length > 1) 12.sp else 16.sp,
            fontFamily = FontFamily.Monospace,
            color = if (pressed) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}
