package com.codeci.ide.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Space
import com.codeci.ide.ui.theme.CodecType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Phase 77.2 — the chat sheet: the whole Phase 76 flow (preview → Send →
 * stream → Stop → Copy → New question) drawn over the editor instead of in a
 * drawer that hides the code.
 *
 * Chat-shaped so multi-turn can arrive later without a redesign (owner Q1:
 * *"No i just making the ui future pruff"*), but **one exchange at a time**:
 * a question bubble, the preview card, the answer. Phase actions live in a
 * pinned bottom bar, so Send/Stop/Copy never scroll out of reach.
 *
 * HALF is a slot in the editor column at [AiSheetPolicy.halfHeight] (the
 * Output panel's own height rule); FULL is an overlay. Both are this one
 * composable, reading the SAME [AiUiState] from the one `AiViewModel` — the
 * sheet decides nothing and sends nothing by itself: the only road to the
 * network is [onSend] on a preview (D4). No apply/insert/run control exists
 * (D1): the only way out is Copy.
 */
@Composable
fun AiChatSheet(
    full: Boolean,
    state: AiUiState,
    imeVisible: Boolean,
    hasSelection: Boolean,
    question: String,
    onQuestionChange: (String) -> Unit,
    onExplainSelection: (String) -> Unit,
    onExplainError: (String) -> Unit,
    /** Phase 78 — the third source: read the project, then preview. Never sends. */
    onAskProject: (String) -> Unit,
    onCancelGather: () -> Unit,
    onSend: () -> Unit,
    onCancelPreview: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    /** Phase 90 — start a fresh conversation: the task and the transcript both clear. */
    onNewChat: () -> Unit = {},
    /** Phase 95 — the history drawer (menu icon, Pinned/Recents rows, New chat pill). */
    onOpenHistory: () -> Unit = {},
    onCloseHistory: () -> Unit = {},
    onSwitchChat: (Long) -> Unit = {},
    onToggleChatPin: (Long) -> Unit = {},
    /** Phase 95 — the welcome + agreement one-tap accept. */
    onAcceptWelcome: () -> Unit = {},
    /** Phase 91 — the simple/technical face. Display state only (D6). */
    onToggleMode: () -> Unit = {},
    /** Phase 92 — the self-check: five scripted checks, judged by the app, reported as text. */
    onSelfCheckStart: () -> Unit = {},
    /** Phase 93 — opens this app's own permission page when the notice asks for a grant. */
    onGrantAccess: () -> Unit = {},
    onSelfCheckNext: () -> Unit = {},
    /** Phase 92.1 — moves past the step that is waiting for its Send, as *not run*. */
    onSelfCheckSkip: () -> Unit = {},
    onSelfCheckStop: () -> Unit = {},
    /** The report text, built where the Android reads live (never in a composable). */
    onSelfCheckReport: () -> String = { "" },
    /** The live verdict of the step being run, computed where the reads live. */
    onSelfCheckLive: () -> AiSelfCheckVerdict? = { null },
    onDismissNotice: () -> Unit,
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
    onDragEnd: (heightFraction: Float) -> Unit,
    /** Phase 79 (Level 3) — propose reviewable multi-file edits, review diff, and undo. */
    onProposeEdits: (String) -> Unit = {},
    onToggleEditFile: (String) -> Unit = {},
    onApplyEdits: () -> Unit = {},
    onRejectProposal: () -> Unit = {},
    onUndoEdits: (Boolean) -> Unit = {},
    onDismissUndoConflict: () -> Unit = {},
    /** Phase 80 (Level 4) — the agent's run approval; nothing runs until Run. */
    onApproveRun: () -> Unit = {},
    onSkipRun: () -> Unit = {},
    /** Phase 81 — ask for the rest of an answer that was cut short (preview first, D4). */
    onContinue: () -> Unit = {},
    /** Phase 87 (Level 10, 87.6) — accept / decline the budget-extension offer. */
    onAcceptBudget: () -> Unit = {},
    onDeclineBudget: () -> Unit = {},
    /** Phase 87 (Level 10, 87.7) — accept / decline the manual backup-provider offer. */
    onAcceptBackup: () -> Unit = {},
    onDeclineBackup: () -> Unit = {},
    /** Phase 87 (Level 10, 87.8) — ask the read-only reviewer; it previews, never sends. */
    onRequestReview: () -> Unit = {},
    /** The last command the editor ran, shown on the approval card when known. */
    lastRunCommand: String? = null,
    /** True while CodeC's own runner is busy, so the Run button cannot race it. */
    runBusy: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val screenPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val baseDp = if (full) null else AiSheetPolicy.halfHeight(LocalConfiguration.current.screenHeightDp.toFloat(), imeVisible)
    val basePx = if (baseDp == null) screenPx else with(density) { baseDp.dp.toPx() }
    // Live drag (px, positive = down). Committed on release through the pure policy.
    var dragPx by remember { mutableFloatStateOf(0f) }

    val sizeModifier = if (full) {
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragPx.coerceAtLeast(0f).roundToInt()) }
    } else {
        Modifier
            .fillMaxWidth()
            .height(with(density) { (basePx - dragPx).coerceIn(0f, screenPx * AiSheetPolicy.LIVE_DRAG_MAX).toDp() })
    }

    // Phase 95 — the history drawer state. ModalNavigationDrawer owns its own
    // DrawerState, but open/close also flow through AiUiState.historyOpen so the
    // header menu button and New-chat archiving can both open it without racing.
    val drawerState = rememberDrawerState(if (state.historyOpen) DrawerValue.Open else DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.historyOpen) {
        if (state.historyOpen && drawerState.isClosed) drawerState.open()
        else if (!state.historyOpen && drawerState.isOpen) drawerState.close()
    }

    val sheetSurface: @Composable () -> Unit = {
        Surface(
            modifier = modifier.then(sizeModifier),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = if (full) RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.XS))
            else RoundedCornerShape(
                topStart = CodecTokens.radius(CodecTokens.Radius.L),
                topEnd = CodecTokens.radius(CodecTokens.Radius.L)
            ),
            shadowElevation = CodecTokens.elevation(CodecTokens.Elevation.SHEET)
        ) {
            // Phase 95 — the welcome + agreement. Until it is tapped the sheet
            // shows one card and no composer: nothing is sent, no preview is built,
            // and every action that would touch the network is still gated at
            // Send (D4). Accept is one tap, this version only.
            if (!state.welcomeAccepted) {
                WelcomeCard(onAccept = onAcceptWelcome)
            } else {
                Column(Modifier.fillMaxSize()) {
                    // Handle + header are ONE drag target (72 dp tall): drag up → FULL, down → put away.
                    Column(
                        Modifier.pointerInput(basePx, screenPx) {
                            detectVerticalDragGestures(
                                onDragStart = { dragPx = 0f },
                                onDragEnd = {
                                    val fraction = AiSheetPolicy.heightFractionAfterDrag(basePx, dragPx, screenPx)
                                    dragPx = 0f
                                    onDragEnd(fraction)
                                },
                                onDragCancel = { dragPx = 0f },
                                onVerticalDrag = { change, dy ->
                                    change.consume()
                                    dragPx += dy
                                }
                            )
                        }
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(CodecTokens.space(Space.L) + CodecTokens.space(Space.XS))
                                .semantics { contentDescription = AiCopy.DRAG_HANDLE },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .width(CodecTokens.space(Space.XXL))
                                    .height(CodecTokens.space(Space.XS))
                                    .clip(RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.XS)))
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                        Header(
                            state.provider, state.model, state.mode, full,
                            onExpand, onMinimize, onNewChat, onToggleMode,
                            onOpenHistory = {
                                onOpenHistory()
                                scope.launch { drawerState.open() }
                            }
                        )
                    }
                    HorizontalDivider()
                    Conversation(
                        state, hasSelection, question, onCancelGather,
                        onProposeEdits, onToggleEditFile, onApplyEdits, onRejectProposal,
                        onUndoEdits, onDismissUndoConflict,
                        onApproveRun, onSkipRun, onContinue, onAcceptBudget, onDeclineBudget,
                        onAcceptBackup, onDeclineBackup, onRequestReview, lastRunCommand, runBusy,
                        onSelfCheckNext = onSelfCheckNext,
                        onSelfCheckStop = onSelfCheckStop,
                        onSelfCheckReport = onSelfCheckReport,
                        onSelfCheckLive = onSelfCheckLive,
                        onGrantAccess = onGrantAccess,
                        modifier = Modifier.weight(1f)
                    )
                    HorizontalDivider()
                    BottomBar(
                        state, hasSelection, question, onQuestionChange,
                        onExplainSelection, onExplainError, onAskProject, onProposeEdits,
                        onCancelGather, onSend, onCancelPreview,
                        onStop, onRetry, onClear, onDismissNotice, onContinue,
                        onSelfCheckStart = onSelfCheckStart,
                        onGrantAccess = onGrantAccess
                    )
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = state.welcomeAccepted,
        drawerContent = {
            HistoryDrawer(
                summaries = state.history.summaries(),
                onSwitch = { id ->
                    scope.launch { drawerState.close() }
                    onCloseHistory()
                    onSwitchChat(id)
                },
                onTogglePin = onToggleChatPin,
                onNewChat = {
                    onNewChat()
                    scope.launch { drawerState.close() }
                    onCloseHistory()
                },
                onClose = {
                    scope.launch { drawerState.close() }
                    onCloseHistory()
                }
            )
        },
        content = sheetSurface
    )
}

@Composable
private fun Header(
    provider: AiProviderId,
    model: String,
    mode: AiChatMode,
    full: Boolean,
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
    onNewChat: () -> Unit,
    onToggleMode: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val simple = mode == AiChatMode.SIMPLE
    // Phase 90: New chat is destructive to the conversation, so it asks first —
    // and the dialog says the whole truth: nothing here is saved anywhere.
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(AiCopy.NEW_CHAT_TITLE) },
            text = { Text(AiCopy.NEW_CHAT_BODY) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onNewChat() }) {
                    Text(AiCopy.NEW_CHAT_CONFIRM)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(AiCopy.NEW_CHAT_KEEP) }
            }
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodecTokens.space(Space.S)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Phase 95 — the drawer menu, in the corner like the ChatGPT screenshot.
        IconButton(onClick = onOpenHistory, modifier = Modifier.semantics { contentDescription = AiCopy.DRAWER_OPEN }) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = AiCopy.DRAWER_OPEN,
                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION))
            )
        }
        Text(
            // Phase 91 — the simple face names who answers, without the model id.
            if (simple) AiCopy.sheetTitleSimple(provider) else AiCopy.sheetTitle(provider, model),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Phase 91 — the face toggle, one tap, added at the owner's request:
        // "make it a toggle option to hide all technical part and make it only
        // question answer". Simple is the default.
        TextButton(
            onClick = onToggleMode,
            modifier = Modifier.semantics { contentDescription = AiCopy.MODE_TOGGLE_DESCRIPTION }
        ) {
            Text(AiCopy.modeToggleLabel(simple), style = MaterialTheme.typography.labelMedium)
        }
        IconButton(onClick = { confirming = true }) {
            Icon(
                Icons.Filled.Add,
                contentDescription = AiCopy.NEW_CHAT,
                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION))
            )
        }
        if (!full) {
            IconButton(onClick = onExpand) {
                Icon(
                    Icons.Filled.OpenInFull,
                    contentDescription = AiCopy.EXPAND,
                    modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION))
                )
            }
        }
        IconButton(onClick = onMinimize) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = AiCopy.MINIMIZE)
        }
    }
}

// ---- the exchange ----------------------------------------------------------------

@Composable
private fun Conversation(
    state: AiUiState,
    hasSelection: Boolean,
    question: String,
    onCancelGather: () -> Unit,
    onProposeEdits: (String) -> Unit,
    onToggleEditFile: (String) -> Unit,
    onApplyEdits: () -> Unit,
    onRejectProposal: () -> Unit,
    onUndoEdits: (Boolean) -> Unit,
    onDismissUndoConflict: () -> Unit,
    onApproveRun: () -> Unit,
    onSkipRun: () -> Unit,
    onContinue: () -> Unit,
    onAcceptBudget: () -> Unit,
    onDeclineBudget: () -> Unit,
    onAcceptBackup: () -> Unit,
    onDeclineBackup: () -> Unit,
    onRequestReview: () -> Unit,
    lastRunCommand: String?,
    runBusy: Boolean,
    /** Phase 92 — the self-check's own controls (see [SelfCheckCard]). */
    onSelfCheckNext: () -> Unit = {},
    onSelfCheckSkip: () -> Unit = {},
    onSelfCheckStop: () -> Unit = {},
    onSelfCheckReport: () -> String = { "" },
    onSelfCheckLive: () -> AiSelfCheckVerdict? = { null },
    /** Phase 93 — the permission notice's one-tap fix, drawn where the notice is. */
    onGrantAccess: () -> Unit = {},
    modifier: Modifier
) {
    val scroll = rememberScrollState()
    val simple = state.mode == AiChatMode.SIMPLE
    // Follow the stream; the user's own scrolling stops mattering once it ends.
    LaunchedEffect(state.answer.length, state.phase) {
        if (state.phase == AiPhase.STREAMING) scroll.scrollTo(scroll.maxValue)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = CodecTokens.space(Space.L), vertical = CodecTokens.space(Space.S)),
        verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.M))
    ) {
        // Phase 90 — the conversation so far. Every settled task (the ones from
        // before this one) is drawn here, in order, each answer labelled with the
        // provider and model that actually answered it (S8). In memory only (D6).
        state.session.turns.forEach { turn ->
            when (turn.role) {
                AiChatRole.YOU -> YouBubble(turn.text)
                AiChatRole.ASSISTANT -> AiBubble(
                    label = if (simple) {
                        // Phase 91 — the simple face: "AI" (still honest about a
                        // stopped turn). The technical face keeps the full S8 label.
                        AiCopy.turnLabelSimple(turn.status == AiTurnStatus.STOPPED)
                    } else {
                        AiCopy.turnLabel(
                            turn.provider, turn.model, turn.status == AiTurnStatus.STOPPED
                        )
                    }
                ) {
                    Answer(turn.text)
                    // Phase 91 — the owner asked for copy *after every reply*,
                    // so every reply carries its own. Same road as the block
                    // Copy: the clipboard and a toast, nothing else (S6).
                    TurnCopy(turn.text)
                }
            }
        }
        val prompt = state.prompt
        if (state.phase != AiPhase.IDLE && prompt != null) {
            YouBubble(AiCopy.youLine(prompt.source, prompt.fileLabel, prompt.question))
        }
        // Phase 80 — every step of the agent task, in order, as it happens:
        // the reads it asked for, the refusals, the run it requested and the
        // run's own result. In memory and never persisted (D6).
        // Phase 91 — simple mode hides the machinery, never a control: the
        // budget offer lives inside this card, so the card stays when one waits.
        if (state.agentSteps.isNotEmpty() && (!simple || state.budgetOffer != null)) {
            AgentActivityCard(state, onAcceptBudget, onDeclineBudget)
        }
        // Phase 87 (Level 10, 87.7) — the manual backup-provider offer. It is a
        // question, never an action: tapping switches the recipient and rebuilds
        // the preview, and the user still taps Send (S8, D4).
        state.backupOffer?.let { next ->
            BackupProviderCard(next, onAcceptBackup, onDeclineBackup)
        }
        // Phase 93 — the self-check's own preview, held open on purpose.
        //
        // Phase 92.1's rule is that the run carries itself to the next question
        // 900 ms after a verdict lands. For a step still WAITING, the ordinary
        // `AiPhase.PREVIEW` branch below would take over the sheet and hide the
        // card — the run's own state, its five lines and its Skip button — at the
        // exact moment the run is mid-flight. So while a check is waiting for its
        // Send, the preview keeps the card's company: the exact text that leaves
        // the phone (D4) is behind the card's own disclosure, and the Send is the
        // arrow in the bar below.
        val checking = state.selfCheck != null && state.phase == AiPhase.PREVIEW
        if (checking) {
            val p = state.prompt
            Body(AiCopy.previewSimple(state.provider, state.model))
            p?.let {
                var showSent by remember(it) { mutableStateOf(false) }
                TextButton(onClick = { showSent = !showSent }) {
                    Text(if (showSent) AiCopy.SENT_TEXT_HIDE else AiCopy.SENT_TEXT_SHOW)
                }
                if (showSent) SentText(it.systemInstruction + "\n\n" + it.userText)
            }
        }
        // Phase 92 — the self-check card. It is a control surface, so it is drawn
        // in both faces, and every line on it is a verdict the app computed.
        state.selfCheck?.let { run ->
            SelfCheckCard(
                run = run,
                live = onSelfCheckLive(),
                onNext = onSelfCheckNext,
                onSkip = onSelfCheckSkip,
                onStop = onSelfCheckStop,
                onReport = onSelfCheckReport
            )
        }
        when (state.phase) {
            // Phase 93 — a self-check preview is drawn above, next to its card.
            AiPhase.PREVIEW -> if (checking) Unit
            AiPhase.IDLE -> {
                // Phase 78 — the walk is running on IO. Nothing has been sent
                // (D4); this is a read of the user's own files.
                if (state.gathering) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.NAV))
                        )
                        Spacer(Modifier.width(CodecTokens.space(Space.M)))
                        Body(AiCopy.GATHERING)
                        Spacer(Modifier.width(CodecTokens.space(Space.M)))
                        TextButton(onClick = onCancelGather) { Text(AiCopy.CANCEL) }
                    }
                }
                if (!hasSelection) Muted(AiCopy.SELECTION_HINT)
                state.notice?.let { ErrorLine(it) }
                if (state.undoSummary != null) {
                    UndoTaskCard(state, onUndoEdits, onDismissUndoConflict)
                }
                Muted(AiCopy.NOT_SAVED_NOTE)
            }

            AiPhase.PREVIEW -> prompt?.let {
                // Phase 91 — the disclosure does not change with the face; the
                // machinery does. Simple says who answers and that Send is the
                // only door; the exact text stays one tap away below (D4).
                if (simple) {
                    Body(AiCopy.previewSimple(it.provider, it.model))
                } else {
                    Body(AiCopy.previewHeader(it.provider, it.model, it.sentChars))
                    Muted(AiCopy.answerBudgetNote(
                        it.provider, it.model,
                        if (it.continuation != null) AiContinuation.requestBudget(state.answer.length) else AiLimits.MAX_REPLY_CHARS
                    ))
                }
                // Phase 90 — the follow-up disclosure (D4): the earlier-turns
                // block that will be packed into the user message, exactly as it
                // will be sent, one tap away. Hidden when there is nothing to
                // carry, and it never replaces the two strings below.
                val earlier = it.session.render()
                if (earlier.isNotBlank()) {
                    var showEarlier by remember(it.session) { mutableStateOf(false) }
                    Muted(AiCopy.earlierTurnsLabel(it.session.turnsForBlock().size, earlier.length))
                    TextButton(onClick = { showEarlier = !showEarlier }) {
                        Text(if (showEarlier) AiCopy.EARLIER_TURNS_HIDE else AiCopy.EARLIER_TURNS_SHOW)
                    }
                    if (showEarlier) {
                        Muted(AiCopy.EARLIER_TURNS_TITLE)
                        Body(earlier.trimEnd())
                    }
                }
                val summary = it.project
                if (!simple) when {
                    // Phase 80 (Level 4) — an agent task: the map, not a five-file
                    // list, is what leaves the phone, so the preview says how many
                    // files the map names (the map's own sentence) and what the
                    // AI may then do.
                    it.agent && summary != null -> {
                        Text(AiCopy.PROJECT_PREVIEW_TITLE, style = MaterialTheme.typography.titleSmall)
                        Body(AiCopy.agentPreviewHeader(summary.projectName, it.sentChars))
                        summary.mapLine?.let { line -> Muted(line) }
                        AiPromptText.projectLeftOutLine(summary)?.let { line -> Muted(line) }
                        Muted(AiCopy.agentPreviewNote())
                    }
                    summary != null -> {
                        // Phase 78 — the file list is the point of this preview:
                        // WHICH files leave the phone, and whether one was cut.
                        Text(AiCopy.PROJECT_PREVIEW_TITLE, style = MaterialTheme.typography.titleSmall)
                        Body(
                            AiCopy.projectPreviewHeader(
                                summary.projectName, summary.files.size, it.sentChars
                            )
                        )
                        for (line in AiPromptText.projectFileLines(summary)) Muted("  ·  $line")
                        AiPromptText.projectLeftOutLine(summary)?.let { line -> Muted(line) }
                    }
                    else -> {
                        Text(AiCopy.PREVIEW_TITLE, style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (!simple) {
                    it.continuation?.let { c -> Muted(AiCopy.continuePreviewNote(c.index)) }
                    if (it.unsaved && summary == null) Muted(AiCopy.UNSAVED_NOTE)
                    Body(AiCopy.providerDataNote(it.provider))
                }
                state.notice?.let { notice -> ErrorLine(notice) }
                // Phase 91 — simple mode still shows everything that leaves the
                // phone, exactly, one tap away rather than always open.
                if (simple) {
                    var showSent by remember(it) { mutableStateOf(false) }
                    TextButton(onClick = { showSent = !showSent }) {
                        Text(if (showSent) AiCopy.SENT_TEXT_HIDE else AiCopy.SENT_TEXT_SHOW)
                    }
                    if (showSent) SentText(it.systemInstruction + "\n\n" + it.userText)
                } else {
                    SentText(it.systemInstruction + "\n\n" + it.userText)
                }
            }

            AiPhase.STREAMING -> {
                // Phase 88 (Level 11, 88.4): an agent task's countdown lives in its one
                // progress line (the bottom bar); a single-shot ask keeps it here.
                if (AiProgressPolicy.placement(state.phase, state.agentSteps.isNotEmpty()) == AiProgressPolicy.Placement.NONE) {
                    state.retryCountdown?.let { Body(AiRateLimits.countdownLine(it)) }
                }
                if (state.answer.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.NAV)))
                } else {
                    // Phase 88.3: live Markdown, re-parsed throttled and off the main thread.
                    AiBubble { Answer(state.answer, streaming = true) }
                }
                // Phase 80 — the loop is paused here: the AI asked to run the
                // project and NOTHING runs until the user taps Run.
                state.agentRun?.let { request ->
                    AgentRunCard(
                        request = request,
                        lastRunCommand = lastRunCommand,
                        running = state.agentRunRunning,
                        runBusy = runBusy,
                        onApproveRun = onApproveRun,
                        onSkipRun = onSkipRun
                    )
                }
            }

            AiPhase.DONE -> {
                when (val pr = state.proposalResult) {
                    is AiProposalResult.Proposal -> {
                        if (pr.proposal.prose.isNotBlank()) {
                            AiBubble { Answer(pr.proposal.prose) }
                        }
                        ProposalReviewCard(
                            proposal = pr.proposal,
                            state = state,
                            question = question,
                            onToggleEditFile = onToggleEditFile,
                            onApplyEdits = onApplyEdits,
                            onRejectProposal = onRejectProposal,
                            onRebuildProposal = { onProposeEdits(question) }
                        )
                    }
                    is AiProposalResult.Invalid -> {
                        if (pr.prose.isNotBlank()) {
                            AiBubble { Answer(pr.prose) }
                        }
                        ErrorLine(pr.reason)
                        // Phase 93b (finding F2, owner round 2): an invalid or
                        // incomplete block is not a dead end. This is the same
                        // one-tap rebuild the proposal card offers — the same
                        // question goes back with the reason on screen — and
                        // nothing is written either way.
                        if (question.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                                Button(
                                    onClick = { onProposeEdits(question) },
                                    enabled = !state.gathering && !state.applying
                                ) {
                                    Text(AiCopy.REBUILD_PROPOSAL)
                                }
                            }
                        }
                    }
                    else -> {
                        AiBubble { Answer(state.answer) }
                    }
                }
                if (state.cutShort) {
                    Muted(AiErrors.CUT_SHORT)
                    // Phase 81 — a cut answer either offers Continue (in the
                    // pinned bar below) or says in one sentence why it cannot.
                    val p = state.prompt
                    when {
                        p == null -> Unit
                        p.agent -> Muted(AiCopy.CONTINUE_AGENT_NOTE)
                        p.source == AiSource.PROPOSE_EDITS -> Muted(AiCopy.CONTINUE_PROPOSAL_NOTE)
                        AiContinuation.canContinue(state.continuations, state.answer.length) ->
                            Muted(AiCopy.CONTINUE_HINT)
                        else -> AiContinuation.limitNote(state.continuations, state.answer.length)
                            ?.let { Muted(it) }
                    }
                }
                // Phase 87 (Level 10, 87.8) — the read-only second opinion. The
                // button is a request, not an action: it builds a preview of a
                // request the user still has to Send (D4), and the reviewer has
                // no tools, so its reply can never become a call.
                if (!simple) {
                    if (state.review != null || state.reviewedAnswer != null) {
                        ReviewCard(state.reviewedAnswer, state.review)
                    }
                    if (state.options.reviewer == AiReviewer.ON &&
                        state.answer.isNotBlank() &&
                        state.prompt?.source != AiSource.REVIEW
                    ) {
                        Text(
                            text = AiCopy.REVIEWER_ACTION,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable(onClick = onRequestReview)
                        )
                    }
                }
                // Phase 89 (Level 12): a task with no activity card on screen (a
                // single-shot ask, or an agent task that used no tool) reports its
                // numbers here instead, so no finished task goes unmeasured on
                // screen and none is ever drawn twice.
                if (!simple && state.agentSteps.isEmpty()) MeasurementsLine(state)
                state.notice?.let { Body(it) }
                // Phase 93 — a refused Apply lands here (the proposal is still on
                // screen), so the permission fix must be reachable from DONE too.
                storageFixRow(state, onGrantAccess)
                if (state.undoSummary != null && state.proposalResult !is AiProposalResult.Proposal) {
                    UndoTaskCard(state, onUndoEdits, onDismissUndoConflict)
                }
                Muted(AiCopy.WRONG_NOTE)
            }

            AiPhase.FAILED -> {
                // A failure is a terminal state with numbers too (a failed row of
                // the Level 12 matrix is recorded, not erased). Phase 91: those
                // numbers are machinery, so only the technical face draws them.
                if (!simple && state.agentSteps.isEmpty()) MeasurementsLine(state)
                // Phase 93 — FAILED was the one face that swallowed `notice`: the
                // refusal was set, the phase terminal, and no line ever drew it.
                // (The run card lives in the STREAMING branch only, so there is no
                // second copy to avoid here — unless `agentRun` is still pending,
                // and even then the card is not on screen in this phase.)
                state.notice?.let { Body(it) }
                state.error?.let { ErrorLine(it) }
            }
        }
    }
}

@Composable
private fun ProposalReviewCard(
    proposal: AiEditProposal,
    state: AiUiState,
    question: String,
    onToggleEditFile: (String) -> Unit,
    onApplyEdits: () -> Unit,
    onRejectProposal: () -> Unit,
    onRebuildProposal: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CodecTokens.space(Space.M)),
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
        ) {
            Text(AiCopy.PROPOSAL_TITLE, style = MaterialTheme.typography.titleSmall)
            Muted(
                AiCopy.proposalSummaryLine(
                    selected = proposal.selectedCount,
                    total = proposal.files.size,
                    added = proposal.selectedAddedLines,
                    removed = proposal.selectedRemovedLines
                )
            )
            for (edit in proposal.files) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleEditFile(edit.path) }
                ) {
                    Checkbox(
                        checked = edit.selected,
                        onCheckedChange = { onToggleEditFile(edit.path) }
                    )
                    Text(
                        "${AiCopy.opBadge(edit.op)} · ${edit.path} (+${edit.addedLines} -${edit.removedLines})",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                SentText(edit.unifiedDiff)
            }
            if (state.applyConflictPaths.isNotEmpty()) {
                ErrorLine(AiCopy.applyConflictMessage(state.applyConflictPaths))
                Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                    if (question.isNotBlank()) {
                        Button(onClick = onRebuildProposal, enabled = !state.gathering && !state.applying) {
                            Text(AiCopy.REBUILD_PROPOSAL)
                        }
                    }
                    OutlinedButton(onClick = onRejectProposal, enabled = !state.applying) {
                        Text(AiCopy.REJECT_CHANGES)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                    Button(
                        onClick = onApplyEdits,
                        enabled = proposal.selectedCount > 0 && !state.applying
                    ) {
                        Text(if (state.applying) AiCopy.APPLYING else AiCopy.applyButtonLabel(proposal.selectedCount))
                    }
                    OutlinedButton(
                        onClick = onRejectProposal,
                        enabled = !state.applying
                    ) {
                        Text(AiCopy.REJECT_CHANGES)
                    }
                }
            }
        }
    }
}

@Composable
private fun UndoTaskCard(
    state: AiUiState,
    onUndoEdits: (Boolean) -> Unit,
    onDismissUndoConflict: () -> Unit
) {
    val summary = state.undoSummary ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CodecTokens.space(Space.M)),
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
        ) {
            Text(AiCopy.UNDO_TITLE, style = MaterialTheme.typography.titleSmall)
            Body(AiCopy.undoSummaryLine(summary))
            Muted(AiCopy.UNDO_SCOPE_NOTE)
            if (state.undoConflictPaths.isNotEmpty()) {
                ErrorLine(AiCopy.undoConflictMessage(state.undoConflictPaths))
                Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                    Button(
                        onClick = { onUndoEdits(true) },
                        enabled = !state.applying
                    ) {
                        Text(AiCopy.UNDO_FORCE_CONFIRM)
                    }
                    OutlinedButton(
                        onClick = onDismissUndoConflict,
                        enabled = !state.applying
                    ) {
                        Text(AiCopy.UNDO_KEEP_MINE)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { onUndoEdits(false) },
                    enabled = !state.applying
                ) {
                    Text(AiCopy.UNDO_CHANGES)
                }
            }
        }
    }
}

/**
 * Phase 80 (Level 4) — the activity timeline. One row per step, the counter of
 * the caps in use, and the detail the model actually received. Nothing here is
 * persisted (D6); it disappears with the task.
 */
/** Phase 88 (Level 11, 88.4) — the progress stage's inputs, read from state that already exists. */
private fun progressInput(state: AiUiState) = AiProgressInput(
    phase = state.phase,
    lastKind = state.agentSteps.lastOrNull()?.kind,
    answerEmpty = state.answer.isEmpty(),
    runPending = state.agentRun != null,
    runRunning = state.agentRunRunning,
    retrying = state.retryCountdown != null
)

/**
 * Phase 89 (Level 12, part 89.2) — the one numbers-only line: first-token and
 * total latency, provider-reported tokens and one boundary heap sample.
 *
 * Display state only (D6): the values live in `AiUiState.measurements`, are never
 * persisted, and the policy owns both the wording and the honesty rules — an
 * unreported value renders as "not reported" / "—", never as `0` (S3).
 */
@Composable
private fun MeasurementsLine(state: AiUiState) {
    AiMeasurePolicy.render(state.measurements)?.let { Muted(it) }
}

@Composable
private fun AgentActivityCard(
    state: AiUiState,
    onAcceptBudget: () -> Unit = {},
    onDeclineBudget: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CodecTokens.space(Space.M)),
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
        ) {
            Text(AiCopy.AGENT_ACTIVITY, style = MaterialTheme.typography.titleSmall)
            // Phase 88 (Level 11, 88.4): ONE progress line on screen. After the task
            // (DONE / FAILED) it is this card's first line; while streaming it is in
            // the bottom bar instead, so the two never both draw it. Counters render
            // against the task's own caps inside the builder (87.6).
            if (AiProgressPolicy.placement(state.phase, state.agentSteps.isNotEmpty()) == AiProgressPolicy.Placement.CARD) {
                Muted(AiProgressPolicy.line(AiProgressPolicy.stage(progressInput(state)), state.agentUsage))
            }
            // Phase 89 (Level 12): the task's own numbers, one line, under the
            // progress line — live for an agent task, whose card is on screen
            // while it runs.
            MeasurementsLine(state)
            // Phase 87 (Level 10, 87.5) — the *tool activity* control. It changes
            // ONLY what is drawn here: the same disclosed strings are behind the
            // row in both states, so the packed request text cannot depend on it
            // (**S1**), and nothing is removed, only collapsed (**S8**). The
            // pre-Send preview is a different composable and is never collapsible
            // — D4 needs the exact text visible before Send.
            val openRequests = remember { mutableStateListOf<Int>() }
            // Phase 88 (Level 11, 88.5): per-row open/closed state for result rows,
            // local to this composable like openRequests. It never reaches the
            // ViewModel, the packer or renderStep (S1).
            val openResults = remember { mutableStateMapOf<Int, Boolean>() }
            for ((index, step) in state.agentSteps.withIndex()) {
                Column(verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.XXS))) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (step.ok) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    step.sentSystemInstruction?.let { instruction ->
                        val user = step.sentUserText.orEmpty()
                        val collapsed = state.options.activity == AiActivityDisplay.COLLAPSED &&
                            !openRequests.contains(index)
                        if (collapsed) {
                            // One line: recipient plus the sizes actually sent.
                            // A tap reveals the identical full text.
                            val row = AiActivityPolicy.row(
                                step.sentProvider ?: state.provider,
                                step.sentModel ?: state.model,
                                instruction,
                                user,
                                AiActivityDisplay.COLLAPSED
                            )
                            Text(
                                text = row.summary + "  ·  tap for the full text",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openRequests.add(index) }
                            )
                        } else {
                            // D4: whole strings, not the timeline's ellipsised result preview.
                            SentText(instruction + "\n\n" + user)
                            if (state.options.activity == AiActivityDisplay.COLLAPSED) {
                                Text(
                                    text = "collapse",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clickable { openRequests.remove(index) }
                                )
                            }
                        }
                    }
                    if (AiResultRowPolicy.hasFullResult(step)) {
                        // Phase 88 (Level 11, 88.5): one line until tapped, then EXACTLY
                        // the text packed for the model (renderStep's own expression),
                        // raw and never Markdown: results are data (S3). Opening reads
                        // modelResult and writes nothing (S1).
                        val open = openResults[index] ?: AiResultRowPolicy.startsOpen(state.options.activity)
                        if (open) {
                            val full = AiResultRowPolicy.fullResult(step)
                            SentText(full)
                            Text(
                                text = AiCopy.resultCaption(full.length) + "  ·  " + AiCopy.RESULT_COLLAPSE,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openResults[index] = false }
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openResults[index] = true },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
                            ) {
                                Text(
                                    text = AiResultRowPolicy.teaser(step),
                                    fontFamily = CodecType.codeFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(AiCopy.RESULT_TAP, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else if (step.detail.isNotBlank()) {
                        // Rows without a full result keep their drawing; they are short by nature.
                        Text(
                            text = step.detail,
                            fontFamily = CodecType.codeFamily,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 12,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            // Phase 87 (Level 10, 87.6) — the budget-extension offer. It appears
            // only after the task has stopped and its answer is prose, states
            // exactly what it adds, and says out loud what it does NOT add: the
            // run count and every approval are unchanged (**S9**). Declining is
            // the default; nothing resumes without a tap.
            state.budgetOffer?.let { offer ->
                Column(verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.XXS))) {
                    Text(
                        text = AiCopy.BUDGET_OFFER_LINE,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Muted(
                        AiCopy.budgetOfferDetail(offer.extraTurns, offer.extraToolCalls)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                        Text(
                            text = AiCopy.BUDGET_OFFER_ACTION,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable(onClick = onAcceptBudget)
                        )
                        Text(
                            text = AiCopy.BUDGET_OFFER_DECLINE,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable(onClick = onDeclineBudget)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Phase 87 (Level 10, 87.7) — the manual backup-provider offer.
 *
 * It names the provider it would switch to and says out loud that accepting
 * sends nothing: the request goes back to the preview with the new recipient on
 * it, and Send stays the only road (**S8**, D4). Consent is never replayed —
 * only a provider whose own terms are current is ever offered.
 */
@Composable
private fun BackupProviderCard(
    next: AiProviderId,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CodecTokens.space(Space.M)),
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
        ) {
            Text(AiCopy.BACKUP_OFFER_PREFIX, style = MaterialTheme.typography.labelMedium)
            Muted(AiCopy.backupSwitched(next))
            Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                Text(
                    text = AiCopy.backupOfferAction(next),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable(onClick = onAccept)
                )
                Text(
                    text = AiCopy.BACKUP_OFFER_DECLINE,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable(onClick = onDecline)
                )
            }
        }
    }
}

/**
 * Phase 87 (Level 10, 87.8) — the second opinion and the answer it reviewed.
 *
 * The reviewed answer stays on screen so the two can be read together. The
 * review is **text**: if the reviewer emitted tool or edit markup, it is shown
 * verbatim rather than parsed into a call, because the reviewer was given no
 * tools and no route to one (**S3**, **S8**).
 */
@Composable
private fun ReviewCard(reviewedAnswer: String?, review: AiReviewVerdict?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CodecTokens.space(Space.M)),
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
        ) {
            Text(AiCopy.REVIEWER_TITLE, style = MaterialTheme.typography.titleSmall)
            reviewedAnswer?.takeIf { it.isNotBlank() }?.let {
                Muted(it)
            }
            when (review) {
                null -> Muted(AiCopy.REVIEWER_BUSY_NOTE)
                is AiReviewVerdict.Text -> AiBubble { Answer(review.text) }
                // Shown exactly as it arrived. It is never handed to a parser
                // that could turn it into a tool call or an edit.
                is AiReviewVerdict.MarkupShownAsText -> VerbatimText(review.text)
            }
        }
    }
}

/**
 * Phase 80 (Level 4) — the run approval card. It names the same RUN action the
 * ▶ button performs, shows the last known command when there is one, and says
 * plainly that nothing has run yet.
 */
@Composable
private fun AgentRunCard(
    request: AiAgentRunRequest,
    lastRunCommand: String?,
    running: Boolean,
    runBusy: Boolean,
    onApproveRun: () -> Unit,
    onSkipRun: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CodecTokens.space(Space.M)),
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
        ) {
            Text(AiCopy.AGENT_RUN_TITLE, style = MaterialTheme.typography.titleSmall)
            Body(AiRunDigest.approvalQuestion(request.target, lastRunCommand))
            if (running) {
                Muted(AiCopy.AGENT_RUN_RUNNING)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                    Button(onClick = onApproveRun, enabled = !runBusy) { Text(AiCopy.AGENT_RUN_APPROVE) }
                    OutlinedButton(onClick = onSkipRun) { Text(AiCopy.AGENT_RUN_SKIP) }
                }
            }
        }
    }
}

@Composable
private fun YouBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Spacer(Modifier.width(CodecTokens.space(Space.XXL)))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M))
        ) {
            Column(Modifier.padding(CodecTokens.space(Space.M))) {
                Text(AiCopy.YOU, style = MaterialTheme.typography.labelSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AiBubble(label: String = AiCopy.AI, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(CodecTokens.space(Space.M))) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            content()
        }
    }
}

/**
 * Phase 91 — the composer, shared by IDLE, DONE and FAILED so the next question
 * never needs a button pressed first (the owner's *"remove the new question"*
 * round). It only ever builds a PREVIEW: nothing is sent until Send on it (D4).
 *
 * Phase 78 device round 1 (owner, 2026-10-01): *"The device test B part if i
 * question anything it's saying select some code in the editor"*. The arrow was
 * hardcoded to Explain-selection, so typing a question with nothing selected hit
 * `fromSelection`'s blank-selection refusal (`AiContext.kt:122`) and the sheet
 * answered "Select some code in the editor first" — a dead end wearing a send
 * icon. The arrow now means what the user meant: with code selected it still
 * explains the selection; with nothing selected it asks about the project, which
 * is what a bare question is.
 */
@Composable
private fun Composer(
    state: AiUiState,
    hasSelection: Boolean,
    question: String,
    onQuestionChange: (String) -> Unit,
    onDismissNotice: () -> Unit,
    onExplainSelection: (String) -> Unit,
    onAskProject: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = question,
            onValueChange = { onQuestionChange(it.take(AiLimits.MAX_QUESTION_CHARS)) },
            placeholder = { Text(AiCopy.QUESTION_PLACEHOLDER) },
            maxLines = 3,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = CodecTokens.space(CodecTokens.MIN_TOUCH))
        )
        ArrowButton(
            enabled = hasSelection || !state.gathering,
            description = AiCopy.SEND_QUESTION,
            onTap = {
                onDismissNotice()
                if (hasSelection) onExplainSelection(question) else onAskProject(question)
            }
        )
    }
}

/**
 * Phase 93 — item 1: *"make the arrow button the final button … one arrow for the
 * whole flow."*
 *
 * One composable, so the arrow is the same control in every phase and always the
 * last thing in its row. Each caller owns what its arrow MEANS, which is how the
 * phase-78 rule survives (“with code selected explain the selection, otherwise ask
 * about the project”) and how a preview's arrow sends (the one place a request
 * goes on the wire, D4) without the two rules ever leaking into each other.
 */
@Composable
private fun ArrowButton(
    enabled: Boolean,
    description: String,
    onTap: () -> Unit
) {
    IconButton(onClick = onTap, enabled = enabled) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = description)
    }
}

/**
 * Phase 93 — item 2/3: when the notice is the storage-permission one, the sheet
 * offers the one tap that fixes it. Every other notice is a sentence and stays one.
 *
 * This asks for nothing by itself: the button calls the caller's grant action,
 * which on the editor route opens this app's own permission page — the same door
 * the storage screen and the terminal already use.
 */
@Composable
private fun storageFixRow(state: AiUiState, onGrantAccess: () -> Unit) {
    if (!state.storageProblem) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onGrantAccess) { Text(AiCopy.GRANT_ACCESS) }
    }
}

/** How long a verdict stays on the card by itself before the next question is built. */
private const val AUTO_ADVANCE_MS = 900L

/**
 * Phase 92 — the self-check card (the owner: *"I am tired of testing — give some
 * command and I will run and share what is wrong"*).
 *
 * One line per check, with the verdict the app computed — a count, a parse
 * result or a permission, never an opinion — the next check to run, and the
 * report to paste back. The report is redacted by construction: no prompt, no
 * answer, no key (**D6**).
 *
 * Phase 92.1 (his round: *"Not all test run"*): the button below is drawn for
 * **every** step that is left — a step still waiting for its Send must have a way
 * on too — and the card carries itself from verdict to verdict.
 */
@Composable
private fun SelfCheckCard(
    run: AiSelfCheck.Run,
    live: AiSelfCheckVerdict?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onReport: () -> String
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CodecTokens.space(Space.M)),
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.XS))
        ) {
            Text(
                text = if (AiSelfCheck.isFinished(run)) {
                    AiCopy.SELF_CHECK_TITLE + " — " + AiCopy.SELF_CHECK_DONE
                } else {
                    AiCopy.SELF_CHECK_TITLE + " — " + AiSelfCheck.progressLabel(run)
                },
                style = MaterialTheme.typography.titleSmall
            )
            Muted(AiCopy.SELF_CHECK_NOTE)
            AiSelfCheck.STEPS.forEachIndexed { index, step ->
                val verdict = AiSelfCheck.verdictAt(run, index, live)
                val mark = when (verdict?.outcome) {
                    AiSelfCheckOutcome.PASS -> "✔"
                    AiSelfCheckOutcome.FAIL -> "✘"
                    AiSelfCheckOutcome.PENDING -> "…"
                    null -> "·"
                }
                val color = when (verdict?.outcome) {
                    AiSelfCheckOutcome.PASS -> MaterialTheme.colorScheme.primary
                    AiSelfCheckOutcome.FAIL -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = "$mark  ${step.title} — ${verdict?.detail ?: "not run"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
            }
            val current = AiSelfCheck.stepAt(run)
            val waiting = current != null && current.door != null && live == null
            if (waiting) Muted(AiCopy.SELF_CHECK_SEND_HINT)
            // Phase 92.1 — the check carries itself to the next question the moment
            // a verdict lands (the owner's round: *"not all test run"*), so the only
            // tap left in the whole run is the Send of a preview (**D4**). The
            // button below is still there, and it is never hidden: waiting for a
            // Send used to leave the card with nothing to press at all.
            LaunchedEffect(run.stepIndex, live?.detail) {
                if (live != null && !AiSelfCheck.isFinished(run)) {
                    delay(AUTO_ADVANCE_MS)
                    onNext()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                if (current != null) {
                    OutlinedButton(onClick = if (waiting) onSkip else onNext) {
                        Text(if (waiting) AiCopy.SELF_CHECK_SKIP else AiCopy.SELF_CHECK_NEXT)
                    }
                }
                TextButton(onClick = { copyAnswer(context, onReport()) }) { Text(AiCopy.SELF_CHECK_REPORT) }
                TextButton(onClick = onStop) { Text(AiCopy.SELF_CHECK_STOP) }
            }
        }
    }
}

/**
 * Phase 91 — every reply carries its own Copy (the owner: *"set the copy part
 * after every reply default"*). Same road as the code-block Copy: clipboard and
 * a toast, nothing else — no insert, no apply, no run (**S6**).
 */
@Composable
private fun TurnCopy(text: String) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = { copyAnswer(context, text) }) {
            Text(AiCopy.COPY_THIS, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ---- the pinned bottom bar: the input while idle, the phase's actions otherwise -----

@Composable
private fun BottomBar(
    state: AiUiState,
    hasSelection: Boolean,
    question: String,
    onQuestionChange: (String) -> Unit,
    onExplainSelection: (String) -> Unit,
    onExplainError: (String) -> Unit,
    onAskProject: (String) -> Unit,
    onProposeEdits: (String) -> Unit,
    onCancelGather: () -> Unit,
    /** Phase 93 — the composer's arrow inside a preview. It SENDS (and only it does). */
    onSend: () -> Unit,
    onCancelPreview: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onDismissNotice: () -> Unit,
    /** Phase 81 — the door to the continuation preview (the button sends nothing). */
    onContinue: () -> Unit,
    /** Phase 92 — starts the scripted check from the idle bar. */
    onSelfCheckStart: () -> Unit = {},
    /**
     * Phase 93 — the preflight notice's one-tap fix (see [storageFixRow]): opens
     * this app's own permission page. Display plumbing only; the request itself is
     * `MainActivity.requestStoragePermissions` (the storage screen's own door).
     */
    onGrantAccess: () -> Unit = {}
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodecTokens.space(Space.L), vertical = CodecTokens.space(Space.S)),
        verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
    ) {
        // Phase 93 fix — the refusal, drawn where the tap happened.
        //
        // The self-check's own round said *"Not all test run"* and its fix printed
        // `SELF_CHECK_BUSY` into `state.notice` … which the conversation body drew
        // in IDLE and PREVIEW only — never while the answer the refusal was about
        // was STREAMING, so the button looked dead. The bar is pinned under the
        // conversation and exists in every phase; STREAMING is the one the body
        // does not cover, and FAILED has its own copy there, so this stays exactly
        // one sentence per phase.
        if (state.phase == AiPhase.STREAMING) {
            state.notice?.let { ErrorLine(it) }
        }
        // The one tap that answers a permission sentence. It sits with the notice
        // and outside the phase branches, because the preflight raises it in IDLE
        // and a refused Apply raises it in DONE — neither of which was PREVIEW.
        if (state.phase != AiPhase.DONE) storageFixRow(state, onGrantAccess)
        when (state.phase) {
            AiPhase.IDLE -> {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
                ) {
                    val explainSelection = { onDismissNotice(); onExplainSelection(question) }
                    if (hasSelection) {
                        Button(onClick = explainSelection) { Text(AiCopy.EXPLAIN_SELECTION) }
                    } else {
                        OutlinedButton(onClick = explainSelection) { Text(AiCopy.EXPLAIN_SELECTION) }
                    }
                    OutlinedButton(onClick = { onDismissNotice(); onExplainError(question) }) {
                        Text(AiCopy.EXPLAIN_ERROR)
                    }
                    // Phase 78 — reads the project, then lands on a preview like
                    // the other two. Disabled while a walk is running so two
                    // walks cannot race for the same sheet.
                    OutlinedButton(
                        onClick = { onDismissNotice(); onAskProject(question) },
                        enabled = !state.gathering
                    ) {
                        Text(AiCopy.ASK_PROJECT)
                    }
                    // Phase 79 (Level 3) — reads the project and builds an
                    // edit-proposal preview. Still requires Send on the preview (D4).
                    OutlinedButton(
                        onClick = { onDismissNotice(); onProposeEdits(question) },
                        enabled = !state.gathering
                    ) {
                        Text(AiCopy.PROPOSE_EDITS)
                    }
                }
                Composer(
                    state, hasSelection, question, onQuestionChange,
                    onDismissNotice, onExplainSelection, onAskProject
                )
                // Phase 92 — the one command: five checks the app judges itself,
                // then a report to share. It starts nothing but the first preview.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSelfCheckStart) { Text(AiCopy.SELF_CHECK_START) }
                    Spacer(Modifier.width(CodecTokens.space(Space.S)))
                    Muted(AiCopy.SELF_CHECK_HINT)
                }
            }

            AiPhase.PREVIEW -> {
                // Phase 93 — item 1: *one arrow for the whole flow.* The bar used
                // to end in [Send] [Cancel]; now it is Cancel and the same ➤ arrow
                // the other phases use, in the same place at the end of the row.
                // Send is still the ONLY thing that sends (D4) — there is one way
                // to do it, there and nowhere else.
                if (state.testing) {
                    OutlinedButton(onClick = onStop) { Text(AiCopy.STOP) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!state.testing && !state.configuring) {
                        OutlinedButton(onClick = onCancelPreview) { Text(AiCopy.CANCEL) }
                    }
                    Spacer(Modifier.width(CodecTokens.space(Space.S)))
                    Text(
                        text = AiCopy.SEND_ARROW_HINT,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    ArrowButton(
                        enabled = !state.gathering && !state.applying,
                        description = AiCopy.SEND_PREVIEW,
                        // A preview's arrow is Send, unconditionally: the preview
                        // is already the answer to "what will be sent", and an
                        // editor selection must not turn this tap into another ask.
                        onTap = {
                            onDismissNotice()
                            onSend()
                        }
                    )
                }
            }

            AiPhase.STREAMING -> {
                // Phase 88 (Level 11, 88.4): an agent task's one progress line names the
                // stage ("steps" = model turns); a rate-limit countdown replaces the stage
                // words. A single-shot ask keeps just its countdown, as before.
                if (AiProgressPolicy.placement(state.phase, state.agentSteps.isNotEmpty()) == AiProgressPolicy.Placement.BOTTOM_BAR) {
                    Muted(
                        AiProgressPolicy.line(
                            AiProgressPolicy.stage(progressInput(state)),
                            state.agentUsage,
                            state.retryCountdown?.let { AiRateLimits.countdownLine(it) }
                        )
                    )
                } else {
                    // Phase 91 — the simple face shows no counters; when there is
                    // nothing else on screen it says it is working, nothing more.
                    if (state.mode == AiChatMode.SIMPLE) {
                        if (state.answer.isEmpty() && state.retryCountdown == null) Muted(AiCopy.WORKING)
                    }
                    state.retryCountdown?.let { Muted(AiRateLimits.countdownLine(it)) }
                }
                // Phase 93 — item 1: the arrow is the last control of the row in
                // every phase, so "the final button" never moves. Mid-stream it
                // asks the project, exactly as phase 91's composer did, and it
                // never sends anything (that is Send's job alone).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onStop) { Text(AiCopy.STOP) }
                    Spacer(Modifier.width(CodecTokens.space(Space.S)))
                    Spacer(Modifier.weight(1f))
                    ArrowButton(
                        enabled = !state.gathering && !state.applying,
                        description = AiCopy.SEND_QUESTION,
                        onTap = {
                            onDismissNotice()
                            if (hasSelection) onExplainSelection(question) else onAskProject(question)
                        }
                    )
                }
            }

            AiPhase.DONE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                    Button(onClick = { copyAnswer(context, state.answer) }) { Text(AiCopy.COPY) }
                    // Phase 81 — only for a cut-off prose answer with room left, and
                    // only as a door to the preview: it sends nothing by itself (D4).
                    val p = state.prompt
                    val canContinue = state.cutShort && p != null && !p.agent &&
                        p.source != AiSource.PROPOSE_EDITS &&
                        AiContinuation.canContinue(state.continuations, state.answer.length)
                    if (canContinue) {
                        OutlinedButton(onClick = onContinue) { Text(AiCopy.CONTINUE) }
                    }
                }
                // Phase 91 — the owner: *"remove the new question it also default
                // no need to click the new question part"*. There is nothing to
                // press first: the composer is here, and typing the next question
                // is the whole gesture. The finished task joins the conversation
                // when that new preview is built, so nothing is lost.
                Composer(
                    state, hasSelection, question, onQuestionChange,
                    onDismissNotice, onExplainSelection, onAskProject
                )
            }

            AiPhase.FAILED -> {
                Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                    if (state.keySaved) Button(onClick = onRetry) { Text(AiCopy.TRY_AGAIN) }
                    // Not "New question" any more (Phase 91): this only puts the
                    // failed task away. The composer below starts the next one.
                    OutlinedButton(onClick = onClear) { Text(AiCopy.CLEAR_TASK) }
                }
                Composer(
                    state, hasSelection, question, onQuestionChange,
                    onDismissNotice, onExplainSelection, onAskProject
                )
            }
        }
    }
}

// ---- Phase 95: Welcome + agreement ----------------------------------------

/**
 * The welcome screen that shows when the owner opens the AI for the first time
 * after install (or after the agreement copy is bumped). One tap to accept,
 * one tap to close the sheet. Nothing leaves the device before Accept and
 * before Send (D4), and the composer is not drawn until after Accept — so the
 * agreement cannot be scrolled past, and it cannot be confused with a permission.
 */
@Composable
private fun WelcomeCard(onAccept: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(CodecTokens.space(Space.L)),
        verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.M))
    ) {
        // Round sparkle icon, same brand mark as the header but bigger and in a
        // soft circle — the ChatGPT screenshot's hero treatment.
        Box(
            Modifier
                .size(CodecTokens.space(Space.XXL))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoFixHigh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.NAV))
            )
        }
        Text(AiCopy.WELCOME_TITLE, style = MaterialTheme.typography.headlineSmall)
        Text(AiCopy.WELCOME_SUBTITLE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider()

        Text(AiCopy.WELCOME_AGREEMENT_TITLE, style = MaterialTheme.typography.titleMedium)
        AiCopy.WELCOME_AGREEMENT_BODY.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = CodecTokens.space(Space.XXS))
                        .size(CodecTokens.icon(CodecTokens.Icon.ACTION))
                )
                Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(CodecTokens.space(Space.S)))
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text(AiCopy.WELCOME_AGREE) }
    }
}

// ---- Phase 95: the history drawer -----------------------------------------

/**
 * Pinned / Recents rows and a New-chat pill, in the same shape the owner
 * screenshot'd. The rows carry titles only — never message text (a drawer row
 * is a title, not a transcript), and a tap replaces the conversation whole.
 */
@Composable
private fun HistoryDrawer(
    summaries: List<AiChatSummary>,
    onSwitch: (Long) -> Unit,
    onTogglePin: (Long) -> Unit,
    onNewChat: () -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header: title + close + new-chat pill.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CodecTokens.space(Space.M)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    AiCopy.HISTORY,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = AiCopy.DRAWER_CLOSE)
                }
            }
            // The Chat-shaped "New chat" pill: white outline, always at the top,
            // exactly one tap.
            OutlinedButton(
                onClick = onNewChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodecTokens.space(Space.M))
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION)))
                Spacer(Modifier.width(CodecTokens.space(Space.S)))
                Text(AiCopy.NEW_CHAT)
            }
            Spacer(Modifier.height(CodecTokens.space(Space.M)))

            if (summaries.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(CodecTokens.space(Space.L)), contentAlignment = Alignment.Center) {
                    Text(
                        AiCopy.HISTORY_EMPTY,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val pinned = summaries.filter { it.pinned }
                val recents = summaries.filterNot { it.pinned }
                LazyColumn(Modifier.weight(1f)) {
                    if (pinned.isNotEmpty()) {
                        item {
                            SectionLabel(AiCopy.PINNED)
                        }
                        items(pinned, key = { it.id }) { row -> HistoryRow(row, onSwitch, onTogglePin) }
                    }
                    if (recents.isNotEmpty()) {
                        item {
                            SectionLabel(AiCopy.RECENTS)
                        }
                        items(recents, key = { it.id }) { row -> HistoryRow(row, onSwitch, onTogglePin) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = CodecTokens.space(Space.L),
            end = CodecTokens.space(Space.L),
            top = CodecTokens.space(Space.M),
            bottom = CodecTokens.space(Space.S)
        )
    )
}

@Composable
private fun HistoryRow(
    row: AiChatSummary,
    onSwitch: (Long) -> Unit,
    onTogglePin: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSwitch(row.id) }
            .padding(horizontal = CodecTokens.space(Space.S)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            row.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = CodecTokens.space(Space.S))
        )
        IconButton(onClick = { onTogglePin(row.id) }) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = if (row.pinned) AiCopy.HISTORY_UNPIN else AiCopy.HISTORY_PIN,
                tint = if (row.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION))
            )
        }
    }
}
