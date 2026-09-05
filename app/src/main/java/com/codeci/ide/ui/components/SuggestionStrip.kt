package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.editor.EditorKeySet
import com.codeci.ide.ui.editor.SuggestionChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Phase 27.2 — the suggestion strip: while an identifier offers multiple
 * candidates, the bar above the keyboard stops being key caps and becomes
 * completion CHIPS (Spck-bar / iPad shortcut-bar pattern): thumb-reachable
 * ≥44 dp targets that can never occlude the code.
 *
 *  - S2: tap = full accept at the caret (same VM path as a ghost accept);
 *        LONG-PRESS shows the kind/detail tooltip.
 *  - S3: the pinned LEFT "⌨" cap (and any unmatched keystroke) restores the
 *        key set instantly — suggestions never imprison the keys.
 *  - S4: swipe DOWN anywhere on the strip dismisses it for the current
 *        identifier (per-identifier dismissal lives in the VM model).
 *  - S5: the pinned RIGHT "⌄ more" cap opens sora's floating panel as
 *        explicit browse mode.
 *  - S8: identical row geometry to the keys row (44-high caps, 6 dp vertical
 *        padding) so the context swap never makes the IME jump.
 *
 * Chip ordering/ranking is computed by the pure pipeline
 * (`StripContext.kt`, `SuggestionStripModelTest`); this composable is a dumb
 * renderer over it.
 */
@Composable
fun SuggestionStrip(
    chips: List<SuggestionChip>,
    showMore: Boolean,
    onAccept: (SuggestionChip) -> Unit,
    onDismissIdentifier: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 28.dp.toPx() }
    var dragDy = 0f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                // S4 — dismiss-by-swipe-down. Vertical drags only; the chips
                // row keeps horizontal scrolling and taps pass through under
                // touch slop.
                detectVerticalDragGestures(
                    onDragStart = { dragDy = 0f },
                    onDragEnd = { dragDy = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        dragDy += dragAmount
                        if (dragDy > dismissThresholdPx) {
                            change.consume()
                            onDismissIdentifier()
                        }
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // S3 — pinned LEFT: back to keys for this identifier.
            StripPinCap(
                label = "⌨",
                contentDescription = "Hide suggestions, show keys",
                onTap = onDismissIdentifier
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                chips.forEach { chip ->
                    SuggestionChipCap(chip = chip, onAccept = onAccept)
                }
            }
            if (showMore) {
                // S5 — pinned RIGHT: the floating panel as explicit browse mode.
                StripPinCap(
                    label = "⌄ more",
                    contentDescription = "Open full completion panel",
                    wide = true,
                    onTap = onMore
                )
            }
        }
    }
}

@Composable
private fun StripPinCap(
    label: String,
    contentDescription: String,
    wide: Boolean = false,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = if (wide) 56.dp else 44.dp, minHeight = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(Unit) {
                detectTapLike(onTap)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

/** A completion chip: tap accepts; long-press shows the kind/detail tooltip. */
@Composable
private fun SuggestionChipCap(
    chip: SuggestionChip,
    onAccept: (SuggestionChip) -> Unit
) {
    val scope = rememberCoroutineScope()
    var tooltip by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                // S7 — the ghost-backed chip is the "selected" one: filled accent.
                if (chip.ghostBacked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .semantics { contentDescription = "accept ${chip.label}" }
            .pointerInput(chip) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var isScroll = false
                    var isLong = false
                    val startX = down.position.x
                    val longJob = scope.launch {
                        delay(EditorKeySet.LONG_PRESS_MS)
                        if (!isScroll) {
                            isLong = true
                            tooltip = true
                        }
                    }
                    var finished = false
                    while (!finished) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            finished = true
                            break
                        }
                        if (abs(change.position.x - startX) > with(density) { 20.dp.toPx() }) {
                            isScroll = true
                            longJob.cancel()
                            tooltip = false
                        }
                    }
                    longJob.cancel()
                    val accepted = !isScroll && !isLong
                    tooltip = false
                    if (accepted) onAccept(chip)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = chip.glyph,
                style = MaterialTheme.typography.labelSmall,
                color = if (chip.ghostBacked) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Text(
                text = chip.displayLabel,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                color = if (chip.ghostBacked) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        if (tooltip) {
            // S2 — kind + detail tooltip above the chip while held.
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, -with(density) { 46.dp.roundToPx() }) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = buildString {
                        append(
                            when (chip.kind) {
                                com.codeci.ide.ui.editor.CompletionKind.SNIPPET -> "snippet"
                                com.codeci.ide.ui.editor.CompletionKind.KEYWORD -> "keyword"
                                com.codeci.ide.ui.editor.CompletionKind.IDENTIFIER -> "identifier"
                            }
                        )
                        chip.detail?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}

/** Tap-only detector for the pinned caps (no long-press/swipe semantics). */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapLike(onTap: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val startX = down.position.x
        var moved = false
        var finished = false
        while (!finished) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull()
            if (change == null || !change.pressed) {
                finished = true
                break
            }
            if (abs(change.position.x - startX) > 24f) moved = true
        }
        if (!moved) onTap()
    }
}
