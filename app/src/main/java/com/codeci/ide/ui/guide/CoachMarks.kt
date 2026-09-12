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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Phase 45.2 — the tour's Android edge: where a control is, and what a box looks
 * like. All the *decisions* live in the pure [CoachMarkPlan]; this file only
 * observes layout and draws.
 *
 * Four rules the drawing must not break (all owner-given, all pinned by
 * `GuideWiringTest`):
 *  - **never point at nothing** — an anchor publishes its window rect while it
 *    is laid out and withdraws it when it leaves composition, so a control that
 *    is gone (the tab bar's reveal handle, a drawer row, the preview's Back) is
 *    not "visible" and cannot be spotlit;
 *  - **the highlighted control is the only forward button** — a tap inside the
 *    hole is left unconsumed so the real control performs its own action, and
 *    that same tap advances the tour. The card has no NEXT/GOT IT;
 *  - **a tap outside does nothing** — it is swallowed. It neither dismisses the
 *    box (owner: *"even tap outside will not end that box"*) nor reaches the UI
 *    under the scrim. The exits are SKIP TOUR, one tap, and Back;
 *  - **never cover the screen's own work** — the host passes
 *    [ChromeState.blockedByForeground] and the plan returns nothing while it is
 *    set (the exit survey, safe mode, an in-flight Phase 44 download).
 */

/**
 * The window rects of the controls a box may spotlight, keyed by [GuideAnchors]
 * id. A process-wide bridge in the codebase's existing shape (`SetupNoticeBridge`,
 * `EditorChromeState`, `IncomingImportBridge`): screens publish without a
 * parameter being threaded through five composables, and the overlay reads them
 * from the root. Snapshot-state backed, so publishing a rect recomposes the
 * overlay — which is what makes the tour follow the user (tap ☰ → the drawer's
 * rows publish → the next box is already positioned).
 *
 * Dialogs are the one thing this cannot see: an `AlertDialog` is its own window,
 * so a rect measured inside one is dialog-relative and would put the hole in the
 * wrong place (PART_45_2, deviation 9).
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
 * The whole tour surface: which step the plan allows right now, and the overlay.
 * Composed once, at the root, ABOVE the scaffold — the tab bar and its reveal
 * handle live in the scaffold's `bottomBar`, so an overlay inside the content
 * column could never spotlight them.
 *
 * There is no per-arrival counter any more (the owner's *"you didn't add all"*):
 * one tour, in order, as far as the screen allows.
 *
 * @param seen step ids already taught (from `coach_marks_seen_csv`)
 * @param blockedByForeground a dialog, the exit survey, safe mode or a moving
 *   Phase 44 download owns the screen: no box at all
 * @param drawerOpen the editor's ☰ drawer is open, so only its own two beats may
 *   show and every other beat waits for it to close
 * @param onSeen called with the new seen-set; the host persists it
 */
@Composable
fun GuideCoachMarks(
    seen: Set<String>,
    blockedByForeground: Boolean,
    drawerOpen: Boolean,
    onSeen: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    // Reading the registry here is what makes the overlay follow layout: a
    // control appearing (the drawer opening, the keyboard hiding the tab bar)
    // recomposes this and the tour takes its next step.
    val chrome = ChromeState.of(
        visibleAnchors = GuideAnchorRegistry.visibleIds(),
        blockedByForeground = blockedByForeground,
        drawerOpen = drawerOpen
    )
    val step = CoachMarkPlan.nextStep(seen, chrome) ?: return
    val anchorRect = GuideAnchorRegistry.rect(step.anchorId) ?: return

    // Back ends the TOUR, not just this box: a box that Back closes and the plan
    // immediately returns would be a loop, and the no-nag law wants one exit that
    // really exits. Settings → Reset tips is the door back.
    val endTour: () -> Unit = { onSeen(CoachMarkPlan.markAllSeen(seen)) }
    BackHandler { endTour() }

    CoachMarkOverlay(
        step = step,
        stepNumber = CoachMarkPlan.steps.indexOf(step) + 1,
        stepCount = CoachMarkPlan.steps.size,
        anchorRect = anchorRect,
        onAdvance = { onSeen(CoachMarkPlan.markSeen(seen, step.id)) },
        onSkip = endTour,
        modifier = modifier
    )
}

/**
 * One box: a scrim with a hole, a card beside it, and the tour's single forward
 * gesture — tap the highlighted control.
 *
 * [stepNumber]/[stepCount] are on the card so the tour reads as one flow instead
 * of ten unrelated popups (the owner's *"not consistent with flow"*), in the same
 * "Guide · 1 of 5" shape the slides use.
 */
@Composable
fun CoachMarkOverlay(
    step: CoachStep,
    stepNumber: Int,
    stepCount: Int,
    anchorRect: Rect,
    onAdvance: () -> Unit,
    onSkip: () -> Unit,
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
        // The card's height is MEASURED, not estimated: the owner's *"Not showing
        // the full box guide at one"* was a 150dp guess placing a taller card, so
        // the clamp branch pushed it over its own hole. The estimate only seeds
        // the first frame; `onSizeChanged` below replaces it, and the card's size
        // does not depend on its offset, so there is no layout feedback loop.
        var cardHeightPx by remember(step.id) {
            mutableStateOf(with(density) { CARD_HEIGHT_DP.toPx() })
        }
        val placement = TooltipPlacement.place(
            anchor = GuideRect(hole.left, hole.top, hole.right, hole.bottom),
            screen = screen,
            tooltip = GuideSize(
                width = with(density) { CARD_WIDTH_DP.toPx() },
                height = cardHeightPx
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

        // Tap handling, above the scrim and below the card:
        //  - inside the hole → NOT consumed, so the real control performs its own
        //    action, and the tour advances (this is the only forward gesture);
        //  - outside → the whole gesture is swallowed. No dismiss, no pass-through.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(hole, step.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (hole.contains(down.position)) {
                            onAdvance()
                        } else {
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                    }
                }
        )

        Card(
            modifier = Modifier
                .offset { IntOffset(placement.left.roundToInt(), placement.top.roundToInt()) }
                .width(CARD_WIDTH_DP)
                .onSizeChanged { cardHeightPx = it.height.toFloat() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tour \u00B7 $stepNumber of $stepCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
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
                    // The only button on the card, and it is an exit, not a "next".
                    TextButton(onClick = onSkip) {
                        Text("SKIP TOUR")
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

/** First-frame seed only; replaced by the measured height after layout. */
private val CARD_HEIGHT_DP = 150.dp
private val CARD_GAP_DP = 12.dp
private val SCREEN_MARGIN_DP = 16.dp
