package com.codeci.ide.ui.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Phase 45.2 — the tour's Android edge: where a control is, and what a box looks
 * like. All the *decisions* live in the pure [CoachMarkPlan]; this file only
 * observes layout and draws.
 *
 * Five rules the drawing must not break (all owner-given, all pinned by
 * `GuideWiringTest`):
 *  - **never point at nothing** — an anchor publishes its window rect while it
 *    is laid out and withdraws it when it leaves composition, so a control that
 *    is gone (the tab bar's reveal handle, a drawer row, the preview's Back) is
 *    not "visible" and cannot be spotlit;
 *  - **the highlighted control is the only forward button, and ONE tap is the
 *    whole answer** (round 4) — the anchor publishes its own click beside its
 *    rect, so a tap inside the hole performs that control's action and advances
 *    the tour in the same gesture, with the gesture swallowed so nothing can
 *    fire twice. Round 3 instead left the tap unconsumed and trusted Compose to
 *    deliver the rest of it to the control underneath; the owner ran that and
 *    reported *"1st click disappear the massage and i have to click 2nd time to
 *    really work but if someone don't click 2nd time it just cut off the flow of
 *    tutorial"* — the advance recomposes the host, and a click whose node is
 *    rebuilt mid-gesture never lands. See [GuideTapPolicy].
 *    A mid-tour card has NO button at all:
 *    round 2's SKIP TOUR is gone, and with it the BackHandler that ended the
 *    tour, because the owner ran that build and reported *"You add the skip
 *    option and it's not a trough guide mean it got cut. I want a full process
 *    1st to last without skip anything in this."* Back now does what Back
 *    always does — it navigates, the tour's box goes with the screen it was on,
 *    and the tour resumes at the same beat, unspent, when the user comes back;
 *  - **a tap outside does nothing** — it is swallowed. It neither dismisses the
 *    box (owner: *"even tap outside will not end that box"*) nor reaches the UI
 *    under the scrim;
 *  - **the end of the tour is the only exit** — *"At the end option to close and
 *    view again"*: [TourFinishedCard] is the one card with buttons, CLOSE and
 *    VIEW AGAIN, and it is earned only when every beat is taught or stalled;
 *  - **never cover the screen's own work** — the host passes
 *    [ChromeState.blockedByForeground] and the plan returns nothing while it is
 *    set (the exit survey, safe mode, an in-flight Phase 44 download, any editor
 *    dialog).
 *
 * The one thing this file decides for itself is the STALL GUARD: a beat the plan
 * names as waiting ([CoachMarkPlan.waitingOn]) for [CoachMarkPlan.STALL_GUARD_MS]
 * is added to an in-memory `stalled` set, which the plan then passes over for this
 * session WITHOUT marking seen. The plan only names a beat whose control could be
 * on this route and is not, so the guard cannot fire while the user is travelling
 * to a screen or watching Python install — and "every beat waits" stays safe: a
 * beat that is merely early draws nothing at all, so the app is never covered.
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

    /**
     * The click of each anchored control, published beside its rect (round 4).
     * The overlay performs THIS instead of leaving the tap to Compose's
     * pass-through, so one tap on the highlighted control does both halves —
     * the control's own action and the tour's next beat. See [GuideTapPolicy].
     */
    private val actions: SnapshotStateMap<String, () -> Unit> = mutableStateMapOf()

    /**
     * Which composition site owns an id right now. ONE id can have two
     * publishers that are never on screen together — beat 6 is the bottom bar
     * while the bar is visible and the thin "Show tabs" handle while it is
     * hidden — and a swap between them disposes one site and composes the other
     * in the SAME recomposition. A withdrawal is therefore honoured only by the
     * site that still owns the id: without that, the leaving site can wipe the
     * arriving site's rect and click, and the beat goes dark on the exact
     * keyboard transition it exists to teach.
     */
    private val owners: SnapshotStateMap<String, Any> = mutableStateMapOf()

    /** Called from `onGloballyPositioned`; empty rects are ignored. */
    fun publish(id: String, rect: Rect, owner: Any) {
        if (rect.width <= 0f || rect.height <= 0f) return
        owners[id] = owner
        if (rects[id] != rect) rects[id] = rect
    }

    /**
     * Called from the anchor's composition with ONE stable wrapper that reads
     * the latest click (see [GuideAnchor.modifier]): a process-wide map must
     * never hold a lambda that captured last frame's state.
     */
    fun publishAction(id: String, action: () -> Unit, owner: Any) {
        owners[id] = owner
        if (actions[id] !== action) actions[id] = action
    }

    /** An anchor that has no click of its own (the bar as a whole, a label). */
    fun withdrawAction(id: String, owner: Any) {
        if (owners[id] !== owner) return
        actions.remove(id)
    }

    /** Called when the anchored control leaves composition: rect AND click. */
    fun withdraw(id: String, owner: Any) {
        if (owners[id] !== owner) return
        owners.remove(id)
        rects.remove(id)
        actions.remove(id)
    }

    fun rect(id: String): Rect? = rects[id]

    /**
     * The anchored controls that have BOTH a click of their own and a rect to
     * aim at, as pure boxes — the whole input of [GuideTapPolicy.targetFor].
     */
    fun tapTargets(): List<GuideTapTarget> = actions.keys.mapNotNull { id ->
        val rect = rects[id] ?: return@mapNotNull null
        GuideTapTarget(id, GuideRect(rect.left, rect.top, rect.right, rect.bottom))
    }

    /**
     * Perform one anchored control's own click; true when there was one. Called
     * from the overlay's lift handler, so it runs on the UI thread inside the
     * gesture that asked for it — the same thread and moment a real tap on the
     * control would have used.
     */
    fun perform(id: String): Boolean {
        val action = actions[id] ?: return false
        action()
        return true
    }

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

    /**
     * @param onClick the control's OWN click, published beside the rect so one
     *   tap on the highlighted control performs it (round 4). Null for an anchor
     *   that is not itself clickable — the bottom bar as a whole (its tabs
     *   publish their own clicks, and a bar-wide hole resolves a tap to the tab
     *   under the finger) and the terminal's status chip (a label).
     */
    @Composable
    fun modifier(id: String, onClick: (() -> Unit)? = null): Modifier {
        // One stable wrapper per anchor, reading the LATEST click through this
        // state: an onClick rebuilt on every recomposition must not leave a
        // stale capture (an old project, a closed drawer) in a process-wide map.
        val current by rememberUpdatedState(onClick)
        val actionable = onClick != null
        // This site's identity in the registry, stable across recompositions and
        // unique per publisher: see [GuideAnchorRegistry]'s `owners`.
        val owner = remember(id) { Any() }
        DisposableEffect(id, actionable) {
            if (actionable) {
                GuideAnchorRegistry.publishAction(id, { current?.invoke() }, owner)
            } else {
                GuideAnchorRegistry.withdrawAction(id, owner)
            }
            onDispose { GuideAnchorRegistry.withdraw(id, owner) }
        }
        return Modifier.onGloballyPositioned { coordinates ->
            GuideAnchorRegistry.publish(id, coordinates.boundsInWindow(), owner)
        }
    }
}

/**
 * The whole tour surface: which step the plan allows right now, and the overlay.
 * Composed once, at the root, ABOVE the scaffold — the tab bar and its reveal
 * handle live in the scaffold's `bottomBar`, so an overlay inside the content
 * column could never spotlight them.
 *
 * There is no per-arrival counter any more (the owner's *"you didn't add all"*),
 * and no skip: one tour, first beat to last, and a close at the end.
 *
 * @param seen step ids already taught (from `coach_marks_seen_csv`)
 * @param blockedByForeground a dialog, the exit survey, safe mode or a moving
 *   Phase 44 download owns the screen: no box at all
 * @param drawerOpen the editor's ☰ drawer is open, so only its own two beats may
 *   show and every other beat waits for it to close
 * @param route the navigation destination, which decides only whether the stall
 *   guard may time a beat out ([CoachMarkPlan.anchorsPossibleOn])
 * @param onSeen called with the new seen-set; the host persists it
 * @param onReplay **VIEW AGAIN** on the finish card: the host clears the seen set
 *   (and takes the user to the editor, where beat 1 lives)
 */
@Composable
fun GuideCoachMarks(
    seen: Set<String>,
    blockedByForeground: Boolean,
    drawerOpen: Boolean,
    route: String?,
    onSeen: (Set<String>) -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Reading the registry here is what makes the overlay follow layout: a
    // control appearing (the drawer opening, the keyboard hiding the tab bar)
    // recomposes this and the tour takes its next step.
    val chrome = ChromeState.of(
        visibleAnchors = GuideAnchorRegistry.visibleIds(),
        blockedByForeground = blockedByForeground,
        drawerOpen = drawerOpen,
        route = route
    )

    // ---- the stall guard (this file's one decision) ------------------------
    // Beats the tour is waiting for, timed. In-memory only: a stalled beat is
    // never written to the seen set, so it is taught on the next pass (the next
    // launch, or VIEW AGAIN, which clears this set too).
    var stalled by remember { mutableStateOf(setOf<String>()) }
    val waiting = CoachMarkPlan.waitingOn(seen, chrome, stalled)
    LaunchedEffect(waiting?.id) {
        val id = waiting?.id ?: return@LaunchedEffect
        delay(CoachMarkPlan.STALL_GUARD_MS)
        stalled = stalled + id
    }

    // ---- the finish card, earned only by a tour that really ran ------------
    // `ranThisSession` is what keeps an install that STARTS complete (all ten
    // beats taught, or the owner's phone after round 2) from being greeted by a
    // "that's the whole tour" card on every launch: the card is the end of a
    // tour this composition watched happen, not a state of the preference.
    val finished = CoachMarkPlan.isFinished(seen, stalled)
    var ranThisSession by remember { mutableStateOf(!finished) }
    var finishClosed by remember { mutableStateOf(false) }
    LaunchedEffect(finished) { if (!finished) ranThisSession = true }

    val step = CoachMarkPlan.nextStep(seen, chrome, stalled)
    if (step == null) {
        if (finished && ranThisSession && !finishClosed && !blockedByForeground) {
            TourFinishedCard(
                stepCount = CoachMarkPlan.steps.size,
                onClose = { finishClosed = true },
                onViewAgain = {
                    finishClosed = false
                    stalled = emptySet()
                    onReplay()
                },
                modifier = modifier
            )
        }
        return
    }
    val anchorRect = GuideAnchorRegistry.rect(step.anchorId) ?: return

    CoachMarkOverlay(
        step = step,
        stepNumber = CoachMarkPlan.steps.indexOf(step) + 1,
        stepCount = CoachMarkPlan.steps.size,
        anchorRect = anchorRect,
        onAdvance = { onSeen(CoachMarkPlan.markSeen(seen, step.id)) },
        modifier = modifier
    )
}

/**
 * The end of the tour — and the only card with buttons, because the owner asked
 * for exactly that: *"At the end option to close and view again."*
 *
 * Earned by [CoachMarkPlan.isFinished]: every beat taught, or passed by the stall
 * guard because its control never appeared. A stalled beat is still unseen, so
 * **CLOSE** does not spend it — it comes back on the next pass — while **VIEW
 * AGAIN** clears the seen set and starts all ten beats from the first.
 *
 * Not an `AlertDialog` (that is a window of its own, and this card belongs to the
 * same layer as the tour) and not a hole in a scrim: there is no control to point
 * at, so the backdrop is dim and swallows taps exactly like the tour's does.
 */
@Composable
fun TourFinishedCard(
    stepCount: Int,
    onClose: () -> Unit,
    onViewAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = FINISH_SCRIM_ALPHA))
        }
        // The backdrop swallows every gesture: no dismiss by tapping outside (the
        // tour's own rule), no tap reaching the UI underneath.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                }
        )
        Card(
            modifier = Modifier
                .padding(SCREEN_MARGIN_DP)
                .width(CARD_WIDTH_DP),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tour \u00B7 $stepCount of $stepCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "That is the whole tour",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = FINISH_BODY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onViewAgain) { Text("VIEW AGAIN") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onClose) { Text("CLOSE") }
                }
            }
        }
    }
}

/**
 * One box: a scrim with a hole, a card beside it, and the tour's single forward
 * gesture — tap the highlighted control, which performs its own action and
 * moves the tour on in the SAME tap (round 4).
 *
 * [stepNumber]/[stepCount] are on the card so the tour reads as one flow instead
 * of ten unrelated popups (the owner's *"not consistent with flow"*), in the same
 * "Guide · 1 of 5" shape the slides use.
 *
 * The card carries NO button: not a NEXT (round 1's "remove the next option") and
 * not a SKIP (round 3's *"it's not a trough guide mean it got cut"*). The only
 * card with buttons is the one at the end, [TourFinishedCard].
 */
@Composable
fun CoachMarkOverlay(
    step: CoachStep,
    stepNumber: Int,
    stepCount: Int,
    anchorRect: Rect,
    onAdvance: () -> Unit,
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
        //  - inside the hole → ONE tap does both halves: the anchored control's
        //    own click (performed from the registry, not left to Compose's
        //    pass-through) and then the tour's next beat. The gesture is
        //    swallowed while we own it, so the control cannot fire a second
        //    time. Where no anchored click lives under the finger (a tab the
        //    tour does not use, inside beat 6's bar-wide hole) the gesture is
        //    left ALONE so the real control still performs it — and the advance
        //    happens on the LIFT either way, never on the press, because
        //    advancing on the press is what let round 3 recompose the host
        //    mid-gesture and lose the click ("i have to click 2nd time");
        //  - a drag that travels and lifts outside the hole is a scroll, not a
        //    tap: it does nothing at all, so the box stays and no beat is spent;
        //  - outside → the whole gesture is swallowed. No dismiss, no pass-through.
        val touchSlop = LocalViewConfiguration.current.touchSlop
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(hole, step.id, touchSlop) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val holeRect = GuideRect(hole.left, hole.top, hole.right, hole.bottom)
                        if (!hole.contains(down.position)) {
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                            return@awaitEachGesture
                        }
                        // Which control the finger is on, decided at the PRESS
                        // (before anything can move): the pure policy picks the
                        // most specific anchored click under it.
                        val target = GuideTapPolicy.targetFor(
                            tapX = down.position.x,
                            tapY = down.position.y,
                            hole = holeRect,
                            targets = GuideAnchorRegistry.tapTargets()
                        )
                        if (target != null) down.consume()
                        var lift = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            if (target != null) event.changes.forEach { it.consume() }
                            event.changes.firstOrNull()?.let { lift = it.position }
                            if (event.changes.none { it.pressed }) break
                        }
                        if (!GuideTapPolicy.isTap(
                                downX = down.position.x,
                                downY = down.position.y,
                                upX = lift.x,
                                upY = lift.y,
                                hole = holeRect,
                                touchSlop = touchSlop
                            )
                        ) {
                            return@awaitEachGesture
                        }
                        // Action first, then the beat: the click runs against the
                        // state the user is looking at, and the advance's
                        // recomposition comes after it, never through it.
                        if (target != null) GuideAnchorRegistry.perform(target.id)
                        onAdvance()
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
                // No button row. The way on is the highlighted control; the way
                // out is the end of the tour.
            }
        }
    }
}

/** The finish card's copy: one line per surface the tour taught. */
private const val FINISH_BODY =
    "\u2630 holds your files, RUN \u25B6 builds and runs, Packages adds a language " +
        "once, Terminal is a real Linux shell."

private val HOLE_PADDING_DP = 6.dp
private val HOLE_CORNER_DP = 12.dp
private val HOLE_STROKE_DP = 2.dp
private const val SCRIM_ALPHA = 0.6f
private const val FINISH_SCRIM_ALPHA = 0.55f
private const val HIGHLIGHT_ALPHA = 0.9f
private val CARD_WIDTH_DP = 260.dp

/** First-frame seed only; replaced by the measured height after layout. */
private val CARD_HEIGHT_DP = 150.dp
private val CARD_GAP_DP = 12.dp
private val SCREEN_MARGIN_DP = 16.dp
