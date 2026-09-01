package com.codeci.ide.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.R
import com.codeci.ide.ui.components.SpckIcons
import com.codeci.ide.ui.projects.DiffLine
import com.codeci.ide.ui.projects.DiffOp
import com.codeci.ide.ui.projects.GitFileChange
import com.codeci.ide.ui.projects.GitFileState
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
    viewModel: GitControlViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var commitMessage by remember { mutableStateOf("") }
    // Phase 17 — the branch chip opens the Switch Branch dialog.
    var showBranchSheet by remember { mutableStateOf(false) }

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
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
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
                        Text(
                            stringResource(R.string.git_commit_push),
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
                    val unpublished = state.status?.upstream == null &&
                        state.status?.detached != true &&
                        state.status?.branch != null
                    if (ahead > 0 || state.pushError != null || unpublished) {
                        HorizontalDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "↑",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = UnpushedAmber
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        ahead > 0 ->
                                            stringResource(R.string.git_unpushed_count, ahead)
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
                                state.pushError?.let { error ->
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = { viewModel.push(context, projectRoot) },
                                enabled = !state.busy && !state.loading,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(stringResource(R.string.git_push_action), letterSpacing = 0.8.sp)
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
            onDismiss = { showBranchSheet = false }
        )
    }
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

/** Typed icon per extension — Spck marks python/html files distinctly. */
@Composable
private fun GitFileIcon(name: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        name.endsWith(".py", ignoreCase = true) ->
            Icon(SpckIcons.PythonLogo, contentDescription = null, modifier = Modifier.size(24.dp))
        WebFileSupport.isHtml(name) ->
            Icon(SpckIcons.HtmlShield, contentDescription = null, modifier = Modifier.size(24.dp))
        else ->
            Icon(SpckIcons.FileLine, contentDescription = null, modifier = Modifier.size(24.dp), tint = muted)
    }
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
                        ConflictPurple.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
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
