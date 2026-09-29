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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.codeci.ide.R
import com.codeci.ide.ui.projects.GitCredentialsStore
import kotlinx.coroutines.launch

/**
 * Phase 73.7 — Spck's "Commit All" dialog (the owner's screenshot 6):
 * the Git Credentials row, the message, the author name + email, the
 * Stage All toggle (on, Spck's own default), Cancel / Ok. It replaces
 * the panel's old inline commit box + COMMIT & PUSH button — Spck's
 * panel has neither, and the owner asked for the ditto copy.
 *
 * The name + email are the stored commit identity, not per-commit
 * overrides: they prefill from [GitCredentialsStore] (a third editor
 * on the same store, after Settings and the Git Credentials dialog)
 * and save back on confirm when changed, so the engine — which
 * commits with the stored identity — needs no new parameter. The
 * credentials row opens the full Git Credentials dialog above this
 * one (the draft underneath is kept).
 *
 * @param canCommit true when committing is meaningful right now (the
 * panel passes "changes exist and no conflict is open"); the message
 * itself is guarded here.
 */
@Composable
fun GitCommitDialog(
    busy: Boolean,
    canCommit: Boolean,
    onDismiss: () -> Unit,
    onOpenCredentials: () -> Unit,
    onConfirm: (message: String, stageAll: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { GitCredentialsStore(context.applicationContext) }
    var message by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }
    var authorEmail by remember { mutableStateOf("") }
    var stageAll by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val stored = store.stored()
        authorName = stored.authorName
        authorEmail = stored.authorEmail
        loaded = true
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
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
                        text = stringResource(R.string.git_commit_all_action),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { if (!saving) onDismiss() }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }

                // ---- credentials row: GitHub is the only provider, and the
                // row is the door to the full editor (same store).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenCredentials)
                        .padding(vertical = 4.dp)
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

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.git_commit_dialog_message_label)) },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = authorName,
                    onValueChange = { authorName = it },
                    label = { Text(stringResource(R.string.git_credentials_commit_name)) },
                    singleLine = true,
                    enabled = loaded && !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = authorEmail,
                    onValueChange = { authorEmail = it },
                    label = { Text(stringResource(R.string.git_credentials_email)) },
                    singleLine = true,
                    enabled = loaded && !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.git_commit_stage_all),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = stageAll,
                        onCheckedChange = { stageAll = it },
                        enabled = !saving
                    )
                }
                // Phase 73.8 — one grey line for newcomers: the beginner's
                // central confusion is "I committed, why isn't it on
                // GitHub?" — answered where the commit happens.
                Text(
                    text = stringResource(R.string.git_hint_commit_local),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { if (!saving) onDismiss() },
                        enabled = !saving
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            saving = true
                            scope.launch {
                                val stored = store.stored()
                                if (authorName.trim() != stored.authorName ||
                                    authorEmail.trim() != stored.authorEmail
                                ) {
                                    store.save(stored.token, stored.username, authorName, authorEmail)
                                }
                                saving = false
                                onConfirm(message.trim(), stageAll)
                            }
                        },
                        enabled = loaded && !saving && !busy && canCommit && message.isNotBlank()
                    ) {
                        Text(stringResource(R.string.git_dialog_ok))
                    }
                }
            }
        }
    }
}
