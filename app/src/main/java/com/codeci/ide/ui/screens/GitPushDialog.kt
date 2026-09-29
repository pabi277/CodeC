package com.codeci.ide.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.codeci.ide.R

/**
 * Phase 73.7 — Spck's "Push" dialog (the owner's screenshot 8): the
 * Remotes dropdown, the Branches dropdown, the Git Credentials row,
 * Cancel / Ok. CodeC used to push straight to the first remote and the
 * current branch with no questions; the dialog names both before the
 * attempt (the 40.2 result card still reports what happened after).
 *
 * The lists arrive loaded (the panel loads remotes + branches when the
 * dialog opens) and the selections follow them; with no remote at all
 * there is nothing to choose, so the dialog says so and offers the
 * Remotes screen instead of a dead Ok.
 */
@Composable
fun GitPushDialog(
    remotes: List<String>,
    branches: List<String>,
    currentBranch: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onAddRemote: () -> Unit,
    onOpenCredentials: () -> Unit,
    onConfirm: (remote: String, branch: String) -> Unit
) {
    var remote by remember(remotes) { mutableStateOf(remotes.firstOrNull().orEmpty()) }
    var branch by remember(branches, currentBranch) {
        mutableStateOf(
            currentBranch?.takeIf { branches.contains(it) }
                ?: branches.firstOrNull().orEmpty()
        )
    }
    var showRemotes by remember { mutableStateOf(false) }
    var showBranches by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.git_push_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }

                if (remotes.isEmpty()) {
                    Text(
                        text = stringResource(R.string.git_push_no_remote),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    OutlinedButton(
                        onClick = onAddRemote,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text(stringResource(R.string.git_remotes_add_action))
                    }
                } else {
                    // ---- Remotes dropdown (the clone dialog's shape) ------
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = remote,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.git_push_dialog_remotes)) },
                            trailingIcon = {
                                IconButton(onClick = { showRemotes = true }) {
                                    Icon(Icons.Default.ExpandMore, contentDescription = null)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = showRemotes,
                            onDismissRequest = { showRemotes = false }
                        ) {
                            remotes.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name, maxLines = 1) },
                                    onClick = {
                                        remote = name
                                        showRemotes = false
                                    }
                                )
                            }
                        }
                    }
                    // ---- Branches dropdown --------------------------------
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = branch,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.git_push_dialog_branches)) },
                            trailingIcon = {
                                IconButton(onClick = { showBranches = true }) {
                                    Icon(Icons.Default.ExpandMore, contentDescription = null)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = showBranches,
                            onDismissRequest = { showBranches = false }
                        ) {
                            branches.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name, maxLines = 1) },
                                    onClick = {
                                        branch = name
                                        showBranches = false
                                    }
                                )
                            }
                        }
                    }
                }

                // ---- credentials row (same door as the Commit dialog) ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenCredentials)
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.git_credentials_action),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.git_credentials_provider_name),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Phase 73.8 — one grey line for newcomers: what Push does
                // with the commits (the other half of the commit hint).
                Text(
                    text = stringResource(R.string.git_hint_push),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(remote, branch) },
                        enabled = remote.isNotBlank() && branch.isNotBlank() && !busy
                    ) {
                        Text(stringResource(R.string.git_dialog_ok))
                    }
                }
            }
        }
    }
}
