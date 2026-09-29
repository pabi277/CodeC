package com.codeci.ide.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.codeci.ide.R
import com.codeci.ide.ui.projects.GitManager
import com.codeci.ide.ui.projects.GitRemoteEntry

/**
 * Phase 73.3 — Spck's "Remotes": view every configured remote (not only the
 * one GitHub `origin` Publish manages), add another, or remove one. Reached
 * from the Source Control header's overflow menu.
 *
 * `origin` is still what [GitManager.push] uses by default, so removing it
 * (or the only remote) is the one action here that confirms first — a name
 * typo here would otherwise quietly break the next push with no obvious
 * cause.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitRemotesSheet(
    remotes: List<GitRemoteEntry>,
    loading: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit,
    onRemove: (name: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var pendingRemove by remember { mutableStateOf<String?>(null) }

    val trimmedName = name.trim()
    val trimmedUrl = url.trim()
    val nameValid = trimmedName.isNotEmpty() && GitManager.isSafeRemoteName(trimmedName) &&
        remotes.none { it.name.equals(trimmedName, ignoreCase = true) }
    val urlValid = trimmedUrl.isNotEmpty() && GitManager.isSafeRemoteUrl(trimmedUrl)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.git_remotes_action),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
            )

            when {
                loading -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
                remotes.isEmpty() -> Text(
                    text = stringResource(R.string.git_remotes_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                ) {
                    itemsIndexed(remotes, key = { _, entry -> entry.name }) { index, entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = entry.url ?: stringResource(R.string.git_remotes_no_url),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { pendingRemove = entry.name },
                                enabled = !busy
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.git_remotes_remove, entry.name)
                                )
                            }
                        }
                        if (index < remotes.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        }
                    }
                }
            }

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = stringResource(R.string.git_remotes_add_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.git_remotes_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.git_remotes_url_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Button(
                    onClick = {
                        onAdd(trimmedName, trimmedUrl)
                        name = ""
                        url = ""
                    },
                    enabled = nameValid && urlValid && !busy,
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.git_remotes_add_action))
                    }
                }
            }
        }
    }

    pendingRemove?.let { remoteName ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.git_remotes_remove_title)) },
            text = { Text(stringResource(R.string.git_remotes_remove_confirm, remoteName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemove = null
                        onRemove(remoteName)
                    }
                ) {
                    Text(
                        stringResource(R.string.git_remotes_remove_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    }
}
