package com.codeci.ide.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.R
import com.codeci.ide.ui.projects.DiffLine
import com.codeci.ide.ui.projects.DiffOp
import com.codeci.ide.ui.projects.GitFileChange
import com.codeci.ide.ui.projects.GitFileState
import com.codeci.ide.ui.viewmodels.GitControlViewModel
import java.io.File

/**
 * Phase 13 — the Source Control bottom sheet for an open project: branch
 * badge, change list with porcelain status letters, inline diff viewer,
 * pull, and one-tap COMMIT & PUSH against the packaged `git` binary.
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

    LaunchedEffect(projectRoot) {
        viewModel.refresh(context, projectRoot)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.source_control_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.refresh(context, projectRoot) },
                    enabled = !state.busy && !state.loading
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.git_refresh))
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
                        horizontalArrangement = Arrangement.Center
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
                    val status = state.status
                    if (status != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = status.branch ?: stringResource(R.string.git_detached_head),
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            if (status.ahead > 0 || status.behind > 0) {
                                Text(
                                    text = buildString {
                                        if (status.ahead > 0) append("↑${status.ahead} ")
                                        if (status.behind > 0) append("↓${status.behind}")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            TextButton(
                                onClick = { viewModel.pull(context, projectRoot) },
                                enabled = !state.busy
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.git_pull))
                            }
                        }
                    }

                    HorizontalDivider()

                    val files = state.status?.files.orEmpty()
                    if (files.isEmpty()) {
                        Text(
                            text = stringResource(R.string.git_working_tree_clean),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp)
                                .padding(vertical = 4.dp)
                        ) {
                            items(files, key = { it.path }) { change ->
                                GitChangeRow(change) {
                                    viewModel.openDiff(context, projectRoot, change.path)
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text(stringResource(R.string.git_commit_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                viewModel.commitAndPush(context, projectRoot, commitMessage)
                                commitMessage = ""
                            },
                            enabled = !state.busy && !state.loading &&
                                state.isRepo && commitMessage.isNotBlank() &&
                                !state.status?.files.isNullOrEmpty()
                        ) {
                            Text(stringResource(R.string.git_commit_push))
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

@Composable
private fun GitChangeRow(change: GitFileChange, onClick: () -> Unit) {
    val accent = badgeColor(change.state)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .background(accent.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = change.badge,
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = change.path,
                style = MaterialTheme.typography.bodyMedium,
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
                        horizontalArrangement = Arrangement.Center
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

private fun badgeColor(state: GitFileState): Color = when (state) {
    GitFileState.MODIFIED -> Color(0xFFFFB74D)
    GitFileState.ADDED -> DiffAddColor
    GitFileState.DELETED -> DiffRemoveColor
    GitFileState.UNTRACKED -> Color(0xFF9E9E9E)
    GitFileState.RENAMED -> Color(0xFF64B5F6)
    GitFileState.UNMERGED -> Color(0xFFBA68C8)
}
