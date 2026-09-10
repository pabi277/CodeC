package com.codeci.ide.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.R
import com.codeci.ide.ui.components.SpckIcons
import com.codeci.ide.ui.components.FileIconView
import com.codeci.ide.ui.projects.DiffLine
import com.codeci.ide.ui.projects.DiffOp
import com.codeci.ide.ui.projects.GitBlocker
import com.codeci.ide.ui.projects.GitErrors
import com.codeci.ide.ui.projects.GitFileChange
import com.codeci.ide.ui.projects.GitFileState
import com.codeci.ide.ui.projects.GitOp
import com.codeci.ide.ui.projects.GitHubPublish
import com.codeci.ide.ui.projects.PushOutcome
import com.codeci.ide.ui.utils.WebFileSupport
import com.codeci.ide.ui.viewmodels.GitControlViewModel
import java.io.File

/**
 * Phase 13 Source Control sheet, re-skinned mockup-exact (design:
 * mockups/source-control.png, Phase 17 spec §2.1): "Source Control" title
 * with an outlined `⌥ branch ▾` chip, a multiline commit-message box, the
 * full-width filled COMMIT & PUSH button, a "Changes N" list where each row
 * carries a typed file icon, its folder path, the porcelain letter and a
 * per-file +/− stage toggle, and the PULL / REFRESH outlined button pair.
 * Engine and diff viewer are the unchanged Phase 13 `GitManager`/`DiffEngine`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitControlSheet(
    projectRoot: File,
    onDismiss: () -> Unit,
    viewModel: GitControlViewModel = viewModel(),
    /** Phase 39 device follow-up — see [BranchSwitchSheet.onBeforeSwitch]. */
    onBeforeBranchSwitch: (() -> Unit)? = null,
    /** Phase 39 device follow-up — see [BranchSwitchSheet.onAfterSwitch]. */
    onAfterBranchSwitch: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var commitMessage by remember { mutableStateOf("") }
    // Phase 17 — the branch chip opens the Switch Branch dialog.
    var showBranchSheet by remember { mutableStateOf(false) }
    // Phase 40.3 — the Publish-to-GitHub dialog (create the remote there isn't one).
    var showPublishSheet by remember { mutableStateOf(false) }

    LaunchedEffect(projectRoot) {
        viewModel.refresh(context, projectRoot)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // ---- header: title + branch chip -----------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.source_control_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                state.status?.branch?.let { branch ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showBranchSheet = true }
                            .border(
                                width = 1.dp,
                                // Phase 40.5 — 0.55 alpha measured 2.25:1 (needs
                                // 3:1 for a control boundary); opaque is 4.56:1.
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                SpckIcons.GitBranch,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = branch,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = stringResource(R.string.editor_drawer_switch_branch),
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            when {
                state.loading || state.busy -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
                !state.gitInstalled -> {
                    SheetGuidance(stringResource(R.string.git_not_installed_message))
                }
                !state.isRepo -> {
                    SheetGuidance(stringResource(R.string.git_not_a_repo_message))
                }
                else -> {
                    // ---- Phase 40.1: readiness before the attempt ----------
                    // The owner's symptom was an error rendered where he was
                    // not looking ("it shows error in the background i can't
                    // see it"). Readiness answers *before* the tap: what is
                    // missing, in one sentence, with the remedy on the same row.
                    val pushBlocker = state.readiness?.blocker(GitOp.PUSH)
                    val pushReadiness = state.readiness?.message(GitOp.PUSH)
                    when {
                        pushBlocker != null && pushReadiness != null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        ) {
                            Text(
                                text = "!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = UnpushedAmber
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pushReadiness,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = UnpushedAmber
                                )
                                if (pushBlocker == GitBlocker.NO_TOKEN) {
                                    GitHelpLink(GitErrors.TOKEN_HELP_URL)
                                }
                            }
                            if (pushBlocker == GitBlocker.NO_REMOTE) {
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { showPublishSheet = true },
                                    enabled = !state.busy && !state.publishBusy,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(42.dp)
                                ) {
                                    Text("PUBLISH", letterSpacing = 0.8.sp)
                                }
                            }
                        }
                        state.readiness?.isReady(GitOp.PUSH) == true -> Text(
                            text = "✓ GitHub ready",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                    // Phase 17 §2.5 — conflicts get their own group and block
                    // the commit; everything else stays in "Changes".
                    val files = state.status?.files.orEmpty()
                    val conflicts = files.filter { it.isConflict }
                    val others = files.filterNot { it.isConflict }

                    // ---- commit message + COMMIT & PUSH --------------------
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        placeholder = {
                            Text(stringResource(R.string.git_commit_message_placeholder))
                        },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    // Phase 39.2 — "what will be committed" list + hygiene note.
                    // Makes the ignore policy verifiable by a human instead of
                    // by faith, and answers "why didn't my file push?".
                    state.hygieneNote?.let { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                    state.commitPreview?.let { preview ->
                        if (preview.total > 0) {
                            Text(
                                text = stringResource(R.string.git_commit_preview_header, preview.total),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                            preview.staged.forEach { entry ->
                                Text(
                                    text = "  ${entry.status}  ${entry.path}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (preview.truncated) {
                                Text(
                                    text = stringResource(
                                        R.string.git_commit_preview_more,
                                        (preview.total - preview.staged.size).coerceAtLeast(0)
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                )
                            }
                        }
                    }
                    // Mockup-exact: light-lavender fill with dark text
                    // (not the default primary/white button).
                    Button(
                        onClick = {
                            viewModel.commitAndPush(context, projectRoot, commitMessage)
                            commitMessage = ""
                        },
                        enabled = !state.busy && !state.loading &&
                            state.isRepo && commitMessage.isNotBlank() &&
                            !state.status?.files.isNullOrEmpty() &&
                            // Phase 17 §2.5 — Spck blocks commits while a
                            // merge conflict is open.
                            conflicts.isEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC3A1F5),
                            contentColor = Color(0xFF221A3E),
                            disabledContainerColor = Color(0xFFC3A1F5).copy(alpha = 0.4f),
                            disabledContentColor = Color(0xFF221A3E).copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(top = 10.dp)
                    ) {
                        // Phase 39 device follow-up — name the branch so a
                        // push from test-1 never looks like "push to main".
                        val pushBranch = state.status?.branch
                        Text(
                            text = if (!pushBranch.isNullOrBlank()) {
                                stringResource(R.string.git_commit_push_to, pushBranch)
                            } else {
                                stringResource(R.string.git_commit_push)
                            },
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Phase 17 §2.5 — say WHY the button is dead (Spck rule:
                    // no commit while a conflict is open).
                    if (conflicts.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.git_commit_blocked),
                            style = MaterialTheme.typography.labelSmall,
                            color = ConflictPurple,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(top = 14.dp))

                    // ---- conflicts (Phase 17 §2.5) -------------------------
                    if (conflicts.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.git_conflicts_header),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ConflictPurple
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(ConflictPurple.copy(alpha = 0.18f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    conflicts.size.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ConflictPurple
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.git_conflict_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            conflicts.forEachIndexed { index, change ->
                                GitChangeRow(
                                    change = change,
                                    projectFolderName = projectRoot.name,
                                    onOpenDiff = {
                                        viewModel.openDiff(context, projectRoot, change.path)
                                    },
                                    onToggleStage = {
                                        viewModel.markResolved(context, projectRoot, change)
                                    },
                                    markResolvedMode = true
                                )
                                if (index < conflicts.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }

                    // ---- changes list --------------------------------------
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.git_changes_header),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(others.size.toString(), style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (files.isEmpty()) {
                        Text(
                            text = stringResource(R.string.git_working_tree_clean),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                        )
                    } else if (others.isEmpty()) {
                        Text(
                            text = stringResource(R.string.git_no_other_changes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .padding(vertical = 2.dp)
                        ) {
                            itemsIndexed(others, key = { _, change -> change.path }) { index, change ->
                                GitChangeRow(
                                    change = change,
                                    projectFolderName = projectRoot.name,
                                    onOpenDiff = {
                                        viewModel.openDiff(context, projectRoot, change.path)
                                    },
                                    onToggleStage = {
                                        viewModel.toggleStage(context, projectRoot, change)
                                    }
                                )
                                // Mockup: a hairline between every change row.
                                if (index < others.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // ---- PULL / REFRESH -------------------------------------
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.pull(context, projectRoot) },
                            enabled = !state.busy && !state.loading,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            // Mockup: the pull mark is the download arrow (↓ over a line).
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.git_pull), letterSpacing = 0.8.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = { viewModel.refresh(context, projectRoot) },
                            enabled = !state.busy && !state.loading,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.refresh), letterSpacing = 0.8.sp)
                        }
                    }

                    // ---- honest push state (Phase 17 device fix) -----------
                    // A commit clears the change list, so a FAILED push used
                    // to look exactly like a successful one. Whenever the
                    // branch is ahead of its remote (or a push failed), say so
                    // and offer a retry.
                    val ahead = state.status?.ahead ?: 0
                    // A branch that tracks nothing has no "ahead" figure at
                    // all — it simply is not published yet, which is exactly
                    // the case the owner hit with a freshly created branch.
                    val unpublished = state.status?.unpublished == true
                    // Phase 40.2 — a push result is *state*, not a toast: it
                    // stays until it is dismissed or the user refreshes.
                    val pushOk = state.lastResult?.ok == true
                    if (ahead > 0 || state.pushError != null || unpublished ||
                        state.lastResult != null
                    ) {
                        HorizontalDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = if (pushOk) "✓" else "↑",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (pushOk) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    UnpushedAmber
                                }
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (!pushOk) {
                                    Text(
                                        text = when {
                                            ahead > 0 -> {
                                                val b = state.status?.branch
                                                if (!b.isNullOrBlank()) {
                                                    stringResource(
                                                        R.string.git_unpushed_count_branch,
                                                        ahead,
                                                        b
                                                    )
                                                } else {
                                                    stringResource(R.string.git_unpushed_count, ahead)
                                                }
                                            }
                                            state.pushError != null ->
                                                stringResource(R.string.git_unpushed_unknown)
                                            else -> stringResource(
                                                R.string.git_unpushed_new_branch,
                                                state.status?.branch ?: ""
                                            )
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = UnpushedAmber
                                    )
                                }
                                // Phase 40.2 — what actually happened to the last
                                // push, named branch and all.
                                state.lastResult?.let { result ->
                                    PushResultCard(
                                        result = result,
                                        onDismiss = { viewModel.dismissPushResult() },
                                        onPublish = { showPublishSheet = true }
                                    )
                                }
                                state.pushError?.let { error ->
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                // Phase 17 follow-up — a tappable help link
                                // (the GitHub token page) when the failure has
                                // one, so "no token" is one tap from the fix.
                                state.pushHelpUrl?.let { url ->
                                    GitHelpLink(url)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = { viewModel.push(context, projectRoot) },
                                enabled = !state.busy && !state.loading,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                val pushBranch = state.status?.branch
                                Text(
                                    text = if (!pushBranch.isNullOrBlank()) {
                                        stringResource(R.string.git_push_to, pushBranch)
                                    } else {
                                        stringResource(R.string.git_push_action)
                                    },
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (state.diffPath != null) {
        GitDiffDialog(
            path = state.diffPath!!,
            loading = state.diffLoading,
            lines = state.diffLines,
            onClose = { viewModel.closeDiff() }
        )
    }

    // Phase 17 — Switch Branch, opened from the branch chip.
    if (showBranchSheet) {
        BranchSwitchSheet(
            projectRoot = projectRoot,
            onDismiss = { showBranchSheet = false },
            onBeforeSwitch = onBeforeBranchSwitch,
            onAfterSwitch = onAfterBranchSwitch
        )
    }

    // Phase 40.3 — Publish to GitHub (create the remote when there isn't one).
    if (showPublishSheet) {
        PublishToGitHubDialog(
            defaultName = projectRoot.name,
            busy = state.publishBusy,
            error = state.publishError,
            note = state.publishNote,
            needsPermission = state.publishNeedsPermission,
            onDismiss = {
                showPublishSheet = false
                viewModel.dismissPublish()
            },
            onPublish = { name, description, isPrivate ->
                viewModel.publishToGitHub(
                    context = context,
                    projectRoot = projectRoot,
                    repoName = name,
                    description = description.takeIf { it.isNotBlank() },
                    isPrivate = isPrivate
                )
            },
            onAttach = { url -> viewModel.attachRemoteToGitHub(context, projectRoot, url) }
        )
    }
}

/**
 * Phase 40.2 — the result card. Deliberately a projection of git's own bytes
 * (never a green tick we invented): the branch it refers to is always named,
 * because "push stayed local" was exactly the case where the UI did not say
 * which branch it was talking about.
 */
@Composable
private fun PushResultCard(
    result: PushOutcome,
    onDismiss: () -> Unit,
    onPublish: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        when (result) {
            is PushOutcome.Pushed -> {
                Text(
                    text = "✓ Pushed ${result.branch} → ${shortRemote(result.remoteUrl)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                val detail = when {
                    result.newBranch -> "new branch on GitHub"
                    result.from != null -> "${result.from}..${result.to}"
                    else -> null
                }
                if (detail != null) {
                    Text(
                        text = "  $detail",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is PushOutcome.UpToDate -> Text(
                text = "↑ Everything up-to-date — nothing was pushed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is PushOutcome.Rejected -> Text(
                text = "✗ Push rejected — ${result.hint}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            is PushOutcome.NoRemote -> {
                Text(
                    text = "↑ Still local — ${result.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = UnpushedAmber
                )
                TextButton(onClick = onPublish) { Text("PUBLISH TO GITHUB") }
            }
            is PushOutcome.Auth -> Text(
                text = "✗ ${result.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            is PushOutcome.Failed -> {
                Text(
                    text = "✗ ${result.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                result.detail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text(
            text = "Dismiss",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onDismiss() }
                .padding(vertical = 2.dp, horizontal = 2.dp)
        )
    }
}

/**
 * Phase 40.3 — the Publish dialog.
 *
 * Two hard rules from the spec are visible here: **private by default** (a
 * phone IDE pushing a folder called `taxes` to a public repository is a data
 * leak — GitHub's own default is public, so `private` is sent explicitly), and
 * **the failure message lives in the window the user is looking at** (the
 * Phase 40.1 lesson: never a snackbar alone).
 */
@Composable
private fun PublishToGitHubDialog(
    defaultName: String,
    busy: Boolean,
    error: String?,
    note: String?,
    needsPermission: String?,
    onDismiss: () -> Unit,
    onPublish: (name: String, description: String, isPrivate: Boolean) -> Unit,
    onAttach: (url: String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(GitHubPublish.nameFor(defaultName)) }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }
    var existingUrl by remember { mutableStateOf("") }

    // Success closes the dialog — the sheet keeps the result card and note.
    LaunchedEffect(note) {
        if (!note.isNullOrBlank()) onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy
        ),
        title = {
            Text(
                text = "Publish to GitHub",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Creates the repository on GitHub, adds it as origin, and " +
                        "pushes this branch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    enabled = !busy,
                    label = { Text("Repository name") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    singleLine = true,
                    enabled = !busy,
                    label = { Text("Description (optional)") }
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        enabled = !busy
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Private repository", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = "GitHub's own default is public — this stays private unless " +
                        "you turn it off.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                needsPermission?.let { needs ->
                    Text(
                        text = "GitHub asked for: $needs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (error != null) {
                    Text(
                        text = "Open github.com/new ↗",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(GitHubPublish.NEW_REPO_URL))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                            .padding(vertical = 4.dp)
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text(
                    text = "Already created it in the browser?",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = existingUrl,
                    onValueChange = { existingUrl = it },
                    singleLine = true,
                    enabled = !busy,
                    placeholder = { Text("https://github.com/user/repo.git") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(name, description, isPrivate) },
                enabled = !busy && name.isNotBlank()
            ) {
                Text(if (busy) "PUBLISHING…" else "PUBLISH")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onAttach(existingUrl.trim()) },
                    enabled = !busy && existingUrl.isNotBlank()
                ) {
                    Text("ATTACH")
                }
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

/** `https://github.com/u/r.git` → `github.com/u/r` for one-line result cards. */
private fun shortRemote(url: String?): String {
    if (url.isNullOrBlank()) return "GitHub"
    return url
        .removePrefix("https://")
        .removePrefix("http://")
        .removeSuffix(".git")
        .trimEnd('/')
}

@Composable
private fun SheetGuidance(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
    )
}

/**
 * A tappable "Create a GitHub token ↗" link, opened in the browser via an
 * ACTION_VIEW intent (the app has no in-app browser; this is the same
 * approach the Settings screen uses for its GitHub links).
 */
@Composable
private fun GitHelpLink(url: String) {
    val context = LocalContext.current
    Text(
        text = "Create a GitHub token ↗",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            .padding(vertical = 2.dp)
    )
}

/** Typed icon per extension — Spck marks python/html files distinctly. */
@Composable
private fun GitFileIcon(name: String) {
    FileIconView(
        name = name,
        isDirectory = false,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * One change row. The trailing button is the Phase 16 +/− stage toggle, or —
 * for a conflicted file ([markResolvedMode]) — Spck's ✓ "Mark Resolved".
 */
@Composable
private fun GitChangeRow(
    change: GitFileChange,
    projectFolderName: String,
    onOpenDiff: () -> Unit,
    onToggleStage: () -> Unit,
    markResolvedMode: Boolean = false
) {
    val accent = badgeColor(change.state)
    val staged = change.x != ' '
    val fileName = change.path.substringAfterLast('/')
    val parent = change.path.substringBeforeLast('/', "")
    val folderPath = if (parent.isEmpty()) "/$projectFolderName" else "/$projectFolderName/$parent"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDiff)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .padding(end = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            GitFileIcon(fileName)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = folderPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            change.oldPath?.let { old ->
                Text(
                    text = stringResource(R.string.git_renamed_from, old),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = change.badge,
            color = accent,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 12.dp)
        )
        // Per-file stage/unstage toggle (+/−), mockup-exact outlined square —
        // or the Phase 17 ✓ "Mark Resolved" for a conflicted path.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = if (markResolvedMode) {
                        // Phase 40.5 — 0.6 alpha measured 2.55:1; opaque conflict
                        // purple is 4.81:1 (an inactive control is exempt, but the
                        // boundary still identifies the button, so it uses outline).
                        ConflictPurple
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable(onClick = onToggleStage),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (markResolvedMode) Icons.Default.Check else SpckIcons.PlusMinus,
                contentDescription = stringResource(
                    when {
                        markResolvedMode -> R.string.git_mark_resolved
                        staged -> R.string.git_unstage
                        else -> R.string.git_stage
                    }
                ),
                modifier = Modifier.size(20.dp),
                tint = if (markResolvedMode) {
                    ConflictPurple
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun GitDiffDialog(
    path: String,
    loading: Boolean,
    lines: List<DiffLine>,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.git_diff_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = path,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                }
                HorizontalDivider()
                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .padding(vertical = 8.dp)
                    ) {
                        items(lines) { line ->
                            val color = when (line.op) {
                                DiffOp.ADD -> DiffAddColor
                                DiffOp.REMOVE -> DiffRemoveColor
                                DiffOp.CONTEXT -> MaterialTheme.colorScheme.onSurface
                            }
                            val marker = when (line.op) {
                                DiffOp.ADD -> "+"
                                DiffOp.REMOVE -> "-"
                                DiffOp.CONTEXT -> " "
                            }
                            Text(
                                text = "$marker${line.text}",
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (lines.isEmpty()) {
                        Text(
                            text = stringResource(R.string.git_diff_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

private val DiffAddColor = Color(0xFF66BB6A)
private val DiffRemoveColor = Color(0xFFEF5350)

/** Spck marks merge conflicts purple (Phase 17 §2.5). */
private val ConflictPurple = Color(0xFFBA68C8)

/** "Not pushed yet" — amber, so it reads as a warning, not an error. */
private val UnpushedAmber = Color(0xFFE6B33C)

private fun badgeColor(state: GitFileState): Color = when (state) {
    GitFileState.MODIFIED -> Color(0xFFE6B33C)
    GitFileState.ADDED -> DiffAddColor
    GitFileState.DELETED -> DiffRemoveColor
    GitFileState.UNTRACKED -> Color(0xFF9E9E9E)
    GitFileState.RENAMED -> Color(0xFF64B5F6)
    GitFileState.UNMERGED -> Color(0xFFBA68C8)
}
