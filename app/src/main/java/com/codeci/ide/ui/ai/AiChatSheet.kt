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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                Header(state.model, full, onExpand, onMinimize)
            }
            HorizontalDivider()
            Conversation(
                state, hasSelection, question, onCancelGather,
                onProposeEdits, onToggleEditFile, onApplyEdits, onRejectProposal,
                onUndoEdits, onDismissUndoConflict,
                onApproveRun, onSkipRun, lastRunCommand, runBusy,
                Modifier.weight(1f)
            )
            HorizontalDivider()
            BottomBar(
                state, hasSelection, question, onQuestionChange,
                onExplainSelection, onExplainError, onAskProject, onProposeEdits,
                onCancelGather, onSend, onCancelPreview,
                onStop, onRetry, onClear, onDismissNotice
            )
        }
    }
}

@Composable
private fun Header(model: String, full: Boolean, onExpand: () -> Unit, onMinimize: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CodecTokens.space(Space.L)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.AutoFixHigh,
            contentDescription = null,
            modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION))
        )
        Spacer(Modifier.width(CodecTokens.space(Space.S)))
        Text(
            AiCopy.sheetTitle(model),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
    lastRunCommand: String?,
    runBusy: Boolean,
    modifier: Modifier
) {
    val scroll = rememberScrollState()
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
        val prompt = state.prompt
        if (state.phase != AiPhase.IDLE && prompt != null) {
            YouBubble(AiCopy.youLine(prompt.source, prompt.fileLabel, prompt.question))
        }
        // Phase 80 — every step of the agent task, in order, as it happens:
        // the reads it asked for, the refusals, the run it requested and the
        // run's own result. In memory and never persisted (D6).
        if (state.agentSteps.isNotEmpty()) {
            AgentActivityCard(state)
        }
        when (state.phase) {
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
                val summary = it.project
                when {
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
                        Body(AiCopy.previewHeader(state.model, it.sentChars))
                    }
                }
                if (it.unsaved && summary == null) Muted(AiCopy.UNSAVED_NOTE)
                Body(AiCopy.FREE_TIER_NOTE)
                SentText(it.systemInstruction + "\n\n" + it.userText)
            }

            AiPhase.STREAMING -> {
                if (state.answer.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.NAV)))
                } else {
                    AiBubble { Answer(state.answer) }
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
                    }
                    else -> {
                        AiBubble { Answer(state.answer) }
                    }
                }
                if (state.cutShort) Muted(AiErrors.CUT_SHORT)
                state.notice?.let { Body(it) }
                if (state.undoSummary != null && state.proposalResult !is AiProposalResult.Proposal) {
                    UndoTaskCard(state, onUndoEdits, onDismissUndoConflict)
                }
                Muted(AiCopy.WRONG_NOTE)
            }

            AiPhase.FAILED -> state.error?.let { ErrorLine(it) }
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
@Composable
private fun AgentActivityCard(state: AiUiState) {
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
            state.agentUsage?.let { usage ->
                Muted(AiCopy.agentUsageLine(usage.turns, usage.toolCalls, usage.runs))
            }
            for (step in state.agentSteps) {
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
                    if (step.detail.isNotBlank()) {
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
private fun AiBubble(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(CodecTokens.space(Space.M))) {
            Text(AiCopy.AI, style = MaterialTheme.typography.labelSmall)
            content()
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
    onSend: () -> Unit,
    onCancelPreview: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onDismissNotice: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodecTokens.space(Space.L), vertical = CodecTokens.space(Space.S)),
        verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
    ) {
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
                    // ➤ only builds the PREVIEW — nothing is sent until Send on it (D4).
                    //
                    // Phase 78 device round 1 (owner, 2026-10-01): *"The device test B
                    // part if i question anything it's saying select some code in the
                    // editor"*. The arrow was hardcoded to Explain-selection, so typing a
                    // question with nothing selected hit `fromSelection`'s blank-selection
                    // refusal (`AiContext.kt:122`) and the sheet answered "Select some code
                    // in the editor first" — a dead end wearing a send icon. Now the arrow
                    // means what the user meant: with code selected it still explains the
                    // selection (Phase 77 device-passed, unchanged); with nothing selected
                    // it asks about the project, which is what a bare question is.
                    IconButton(
                        onClick = {
                            onDismissNotice()
                            if (hasSelection) onExplainSelection(question) else onAskProject(question)
                        },
                        enabled = hasSelection || !state.gathering
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = AiCopy.SEND_QUESTION)
                    }
                }
            }

            AiPhase.PREVIEW -> Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                Button(onClick = onSend) { Text(AiCopy.SEND) }
                OutlinedButton(onClick = onCancelPreview) { Text(AiCopy.CANCEL) }
            }

            AiPhase.STREAMING -> {
                if (state.agentSteps.isNotEmpty()) {
                    Muted(AiCopy.agentWorkingLine(state.agentSteps.count { it.kind == AiAgentStepKind.TOOL }))
                }
                OutlinedButton(onClick = onStop) { Text(AiCopy.STOP) }
            }

            AiPhase.DONE -> Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                Button(onClick = { copyAnswer(context, state.answer) }) { Text(AiCopy.COPY) }
                OutlinedButton(onClick = { onQuestionChange(""); onClear() }) { Text(AiCopy.NEW_QUESTION) }
            }

            AiPhase.FAILED -> Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                if (state.keySaved) Button(onClick = onRetry) { Text(AiCopy.TRY_AGAIN) }
                OutlinedButton(onClick = onClear) { Text(AiCopy.NEW_QUESTION) }
            }
        }
    }
}
