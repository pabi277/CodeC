package com.codeci.ide.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.R
import com.codeci.ide.ui.components.SpckIcons
import com.codeci.ide.ui.projects.BranchTarget
import com.codeci.ide.ui.projects.BranchTargetKind
import com.codeci.ide.ui.projects.GitBranch
import com.codeci.ide.ui.projects.ProjectsHub
import com.codeci.ide.ui.viewmodels.GitControlViewModel
import java.io.File

/**
 * Phase 17 §2.3 — the Switch Branch dialog (Spck's "Switch Branch" flow),
 * reachable from the Source Control branch chip, the editor drawer footer and
 * the Projects card ⋮.
 *
 * It lists local branches, offers remote-only branches (checked out as new
 * local tracking branches) and a bonus "New branch…" row, and — when the tree
 * is dirty — stashes the work before switching and restores it when the user
 * comes back (Spck's promise). Every git call goes through
 * [GitControlViewModel], so nothing runs on the UI thread.
 *
 * Presentation only: all state comes from the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchSwitchSheet(
    projectRoot: File,
    onDismiss: () -> Unit,
    viewModel: GitControlViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var selected by remember { mutableStateOf<BranchTarget?>(null) }
    var stashChanges by remember { mutableStateOf(true) }
    var newBranchName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(projectRoot) {
        // A previous visit's outcome must not greet the user on reopen.
        viewModel.clearBranchResult()
        viewModel.loadBranches(context, projectRoot)
    }

    // Preselect the checked-out branch the moment the list arrives.
    LaunchedEffect(state.branches) {
        if (selected == null) {
            selected = state.branches?.current?.let { BranchTarget(it, BranchTargetKind.LOCAL) }
        }
    }

    val changeCount = state.status?.files?.size ?: 0
    val newNameValid = ProjectsHub.isValidBranchName(newBranchName.trim())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // ---- header ------------------------------------------------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.branch_switch_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = projectRoot.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                }
                HorizontalDivider()

                when {
                    // ---- finished: show the outcome ------------------------
                    state.branchResult != null -> {
                        Text(
                            text = state.branchResult!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                viewModel.clearBranchResult()
                                onDismiss()
                            }) { Text(stringResource(R.string.close_action)) }
                        }
                    }

                    state.branchesLoading && state.branches == null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
                    }

                    else -> {
                        // ---- stash promise ---------------------------------
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clickable { stashChanges = !stashChanges }
                        ) {
                            Checkbox(
                                checked = stashChanges,
                                onCheckedChange = { stashChanges = it }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.branch_switch_stash),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = if (changeCount == 0) {
                                        stringResource(R.string.branch_switch_clean)
                                    } else {
                                        stringResource(R.string.branch_switch_dirty, changeCount)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider()

                        // ---- branch list -----------------------------------
                        val branches = state.branches
                        if (branches == null || branches.branches.isEmpty()) {
                            Text(
                                text = state.branchError
                                    ?: stringResource(R.string.branch_switch_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                            ) {
                                item {
                                    SectionHeader(stringResource(R.string.branch_switch_local_header))
                                }
                                items(branches.local, key = { "local:${it.name}" }) { branch ->
                                    BranchRow(
                                        branch = branch,
                                        selected = selected?.name == branch.name &&
                                            selected?.kind == BranchTargetKind.LOCAL,
                                        onClick = {
                                            creating = false
                                            selected = BranchTarget(branch.name, BranchTargetKind.LOCAL)
                                        }
                                    )
                                }
                                if (branches.remote.isNotEmpty()) {
                                    item {
                                        SectionHeader(stringResource(R.string.branch_switch_remote_header))
                                    }
                                    items(branches.remote, key = { "remote:${it.name}" }) { branch ->
                                        BranchRow(
                                            branch = branch,
                                            selected = selected?.name == branch.name &&
                                                selected?.kind == BranchTargetKind.REMOTE,
                                            onClick = {
                                                creating = false
                                                selected = BranchTarget(branch.name, BranchTargetKind.REMOTE)
                                            }
                                        )
                                    }
                                }
                                item {
                                    HorizontalDivider()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { creating = true }
                                            .padding(vertical = 12.dp, horizontal = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(R.string.branch_switch_new),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            if (creating) {
                                OutlinedTextField(
                                    value = newBranchName,
                                    onValueChange = { newBranchName = it },
                                    placeholder = {
                                        Text(stringResource(R.string.branch_switch_new_hint))
                                    },
                                    isError = newBranchName.isNotBlank() && !newNameValid,
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 6.dp)
                                )
                                if (newBranchName.isNotBlank() && !newNameValid) {
                                    Text(
                                        text = stringResource(R.string.branch_switch_invalid_name),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        state.branchError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            )
                        }

                        // ---- actions ---------------------------------------
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            if (state.branchBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.branch_switch_working),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                enabled = !state.branchBusy && when {
                                    creating -> newNameValid
                                    else -> selected != null
                                },
                                onClick = {
                                    val target = if (creating) {
                                        BranchTarget(newBranchName.trim(), BranchTargetKind.NEW)
                                    } else {
                                        selected ?: return@TextButton
                                    }
                                    viewModel.switchBranch(context, projectRoot, target, stashChanges)
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.branch_switch_action),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 10.dp)
    )
}

@Composable
private fun BranchRow(
    branch: GitBranch,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected) {
                    Modifier
                        .background(accent.copy(alpha = 0.12f))
                        .border(
                            width = 1.dp,
                            color = accent.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp)
                        )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Icon(
            SpckIcons.GitBranch,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (branch.isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = branch.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (branch.isCurrent) accent else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (branch.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        when {
            branch.isCurrent -> {
                Text(
                    text = stringResource(R.string.branch_switch_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
                Spacer(Modifier.width(8.dp))
            }
            branch.isRemote -> {
                Text(
                    text = stringResource(R.string.branch_switch_remote_tag),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = accent
            )
        } else {
            Spacer(Modifier.size(18.dp))
        }
    }
}
