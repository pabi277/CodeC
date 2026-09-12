package com.codeci.ide.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Phase 45.2 — the coach marks' Android edge: where a control is, and what the
 * spotlight looks like. All the *decisions* live in the pure [CoachMarkPlan];
 * this file only observes layout and draws.
 *
 * Three rules the drawing must not break:
 *  - **never point at nothing** — an anchor publishes its window rect while it
 *    is laid out and withdraws it when it leaves composition, so a control that
 *    is gone (the tab bar's "Show tabs" handle, which exists only while the bar
 *    is hidden) is not "visible" and cannot be spotlit;
 *  - **never trap the user** — the scrim only draws; a tap inside the hole is
 *    left unconsumed so the real control performs its own action, and a tap
 *    anywhere else ends the mark;
 *  - **never cover the screen's own work** — the host passes
 *    [ChromeState.blockedByForeground] and the plan returns nothing while it is
 *    set (the exit survey, safe mode, an in-flight Phase 44 download).
 */

/**
 * The window rects of the controls a coach mark may spotlight, keyed by
 * [GuideAnchors] id. A process-wide bridge in the codebase's existing shape
 * (`SetupNoticeBridge`, `EditorChromeState`, `IncomingImportBridge`): screens
 * publish without a parameter being threaded through five composables, and the
 * overlay reads them from the root. Snapshot-state backed, so publishing a rect
 * recomposes the overlay.
 */
object GuideAnchorRegistry {

    private val rects: SnapshotStateMap<String, Rect> = mutableStateMapOf()

    /** Called from `onGloballyPositioned`; empty rects are ignored. */
    fun publish(id: String, rect: Rect) {
        if (rect.width <= 0f || rect.height <= 0f) return
        if (rects[id] != rect) rects[id] = rect
    }

    /** Called when the anchored control leaves composition. */
    fun withdraw(id: String) {
        rects.remove(id)
    }

    fun rect(id: String): Rect? = rects[id]

    /** The anchors currently laid out with a non-empty rect. */
    fun visibleIds(): Set<String> = rects
        .filterValues { it.width > 0f && it.height > 0f }
        .keys
        .toSet()
}

/**
 * The anchor modifier: `Modifier.guideAnchor(id)` in spirit, as a composable
 * call so the withdrawal is registered in the CALLER's scope.
 *
 * Placed LAST in a chain, it reports the tight visual box (a `padding` earlier
 * in the chain belongs to an outer layout node), which is what a spotlight wants.
 */
object GuideAnchor {

    @Composable
    fun modifier(id: String): Modifier {
        DisposableEffect(id) {
            onDispose { GuideAnchorRegistry.withdraw(id) }
        }
        return Modifier.onGloballyPositioned { coordinates ->
            GuideAnchorRegistry.publish(id, coordinates.boundsInWindow())
        }
    }
}

/**
 * The whole coach-mark surface for one arrival: which step the plan allows, the
 * per-arrival counter, and the overlay. Composed once, at the root, ABOVE the
 * scaffold — the tab bar's reveal handle lives in the scaffold's `bottomBar`, so
 * an overlay inside the content column could never spotlight it.
 *
 * @param surface the current destination's teaching surface, or null
 * @param seen step ids already shown (from `coach_marks_seen_csv`)
 * @param arrivalKey changes when the destination changes; resets the counter
 * @param onSeen called with the new seen-set; the host persists it
 */
@Composable
fun GuideCoachMarks(
    surface: GuideSurface?,
    seen: Set<String>,
    blockedByForeground: Boolean,
    arrivalKey: Any?,
    onSeen: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    // Reading the registry here is what makes the overlay follow layout: a
    // control appearing (the keyboard hiding the tab bar) recomposes this.
    val chrome = ChromeState.of(
        visibleAnchors = GuideAnchorRegistry.visibleIds(),
        blockedByForeground = blockedByForeground
    )
    var shownThisArrival by remember { mutableStateOf(0) }
    LaunchedEffect(arrivalKey) { shownThisArrival = 0 }

    val step = CoachMarkPlan.stepForArrival(surface, seen, chrome, shownThisArrival)
    LaunchedEffect(step?.id) {
        if (step != null) shownThisArrival++
    }
    if (step == null) return

    val anchorRect = GuideAnchorRegistry.rect(step.anchorId)
    val finish: () -> Unit = { onSeen(CoachMarkPlan.markSeen(seen, step.id)) }
    // Back closes the mark before anything else (Phase 49's BackRouter will own
    // this precedence; until then it is a fact, pinned by GuideWiringTest).
    BackHandler { finish() }
    if (anchorRect != null) {
        CoachMarkOverlay(
            step = step,
            anchorRect = anchorRect,
            onDismiss = finish,
            modifier = modifier
        )
    }
}

/** The spotlight: a scrim with a hole, a card beside it, one tap to finish. */
@Composable
fun CoachMarkOverlay(
    step: CoachStep,
    anchorRect: Rect,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screen = GuideSize(
            width = with(density) { maxWidth.toPx() },
            height = with(density) { maxHeight.toPx() }
        )
        val padPx = with(density) { HOLE_PADDING_DP.toPx() }
        val hole = Rect(
            left = anchorRect.left - padPx,
            top = anchorRect.top - padPx,
            right = anchorRect.right + padPx,
            bottom = anchorRect.bottom + padPx
        )
        val placement = TooltipPlacement.place(
            anchor = GuideRect(hole.left, hole.top, hole.right, hole.bottom),
            screen = screen,
            tooltip = GuideSize(
                width = with(density) { CARD_WIDTH_DP.toPx() },
                // An estimate, not a measurement: measuring the card and then
                // placing it is a layout feedback loop for a gap that reads the
                // same either way (PART_45_2 records the simplification).
                height = with(density) { CARD_HEIGHT_DP.toPx() }
            ),
            gap = with(density) { CARD_GAP_DP.toPx() },
            margin = with(density) { SCREEN_MARGIN_DP.toPx() }
        )

        // Drawing only: a Canvas takes no pointer input, so the control under
        // the hole keeps working while it is spotlit.
        Canvas(Modifier.fillMaxSize()) {
            val scrim = Color.Black.copy(alpha = SCRIM_ALPHA)
            if (hole.top > 0f) {
                drawRect(scrim, Offset.Zero, Size(size.width, hole.top))
            }
            if (hole.bottom < size.height) {
                drawRect(
                    scrim,
                    Offset(0f, hole.bottom),
                    Size(size.width, (size.height - hole.bottom).coerceAtLeast(0f))
                )
            }
            if (hole.left > 0f) {
                drawRect(
                    scrim,
                    Offset(0f, hole.top.coerceAtLeast(0f)),
                    Size(hole.left, hole.height.coerceAtLeast(0f))
                )
            }
            if (hole.right < size.width) {
                drawRect(
                    scrim,
                    Offset(hole.right, hole.top.coerceAtLeast(0f)),
                    Size(
                        (size.width - hole.right).coerceAtLeast(0f),
                        hole.height.coerceAtLeast(0f)
                    )
                )
            }
            drawRoundRect(
                color = Color.White.copy(alpha = HIGHLIGHT_ALPHA),
                topLeft = Offset(hole.left.coerceAtLeast(0f), hole.top.coerceAtLeast(0f)),
                size = Size(
                    hole.width.coerceAtLeast(0f),
                    hole.height.coerceAtLeast(0f)
                ),
                cornerRadius = CornerRadius(HOLE_CORNER_DP.toPx()),
                style = Stroke(width = HOLE_STROKE_DP.toPx())
            )
        }

        // Tap handling, above the scrim and below the card: inside the hole the
        // tap is NOT consumed (the real control performs its own action, and the
        // lesson is over); anywhere else it is consumed and simply ends.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(hole) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onDismiss()
                        if (!hole.contains(down.position)) down.consume()
                    }
                }
        )

        Card(
            modifier = Modifier
                .offset { IntOffset(placement.left.roundToInt(), placement.top.roundToInt()) }
                .width(CARD_WIDTH_DP),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = step.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("GOT IT")
                    }
                }
            }
        }
    }
}

private val HOLE_PADDING_DP = 6.dp
private val HOLE_CORNER_DP = 12.dp
private val HOLE_STROKE_DP = 2.dp
private const val SCRIM_ALPHA = 0.6f
private const val HIGHLIGHT_ALPHA = 0.9f
private val CARD_WIDTH_DP = 260.dp
private val CARD_HEIGHT_DP = 150.dp
private val CARD_GAP_DP = 12.dp
private val SCREEN_MARGIN_DP = 16.dp
