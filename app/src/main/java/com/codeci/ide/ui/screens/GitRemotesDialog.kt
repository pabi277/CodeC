package com.codeci.ide.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codeci.ide.R
import com.codeci.ide.ui.projects.GitManager
import com.codeci.ide.ui.projects.GitRemoteEntry

/**
 * Phase 73.3 — Spck's "Remotes": view every configured remote (not only the
 * one GitHub `origin` Publish manages), add another, or remove one. Reached
 * from the Source Control header's branch menu.
 *
 * Phase 73.7 — the owner's screenshots show this as a centered dialog, not
 * a bottom sheet: a search field with a "+" button, the remotes, and a
 * per-row menu (copy the URL / delete). Adding moved into its own New
 * Remote dialog; removing still confirms first (a name typo would
 * otherwise quietly break the next push with no obvious cause).
 * `origin` is still what [GitManager.push] uses by default.
 */
@Composable
fun GitRemotesDialog(
    remotes: List<GitRemoteEntry>,
    loading: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit,
    onRemove: (name: String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var rowMenuFor by remember { mutableStateOf<String?>(null) }
    var pendingRemove by remember { mutableStateOf<String?>(null) }

    val trimmedQuery = query.trim()
    val visible = if (trimmedQuery.isEmpty()) {
        remotes
    } else {
        remotes.filter {
            it.name.contains(trimmedQuery, ignoreCase = true) ||
                (it.url?.contains(trimmedQuery, ignoreCase = true) == true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.git_remotes_action),
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

                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.git_remotes_search_hint)) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.clear)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { showAdd = true }, enabled = !busy) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.git_remotes_add_action)
                        )
                    }
                }

                when {
                    loading -> Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                    remotes.isEmpty() -> Text(
                        text = stringResource(R.string.git_remotes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                    visible.isEmpty() -> Text(
                        text = stringResource(R.string.git_remotes_no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                    ) {
                        itemsIndexed(visible, key = { _, entry -> entry.name }) { index, entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        entry.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = entry.url ?: stringResource(R.string.git_remotes_no_url),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box {
                                    IconButton(
                                        onClick = { rowMenuFor = entry.name },
                                        enabled = !busy
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = stringResource(
                                                R.string.git_remotes_remove,
                                                entry.name
                                            )
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = rowMenuFor == entry.name,
                                        onDismissRequest = { rowMenuFor = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.git_remotes_copy_url)) },
                                            enabled = entry.url != null,
                                            onClick = {
                                                rowMenuFor = null
                                                entry.url?.let { url ->
                                                    val clipboard = context.getSystemService(
                                                        Context.CLIPBOARD_SERVICE
                                                    ) as ClipboardManager
                                                    clipboard.setPrimaryClip(
                                                        ClipData.newPlainText(entry.name, url)
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.hub_remote_url_copied),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.git_remotes_delete),
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                rowMenuFor = null
                                                pendingRemove = entry.name
                                            }
                                        )
                                    }
                                }
                            }
                            if (index < visible.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
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
            }
        }
    }

    if (showAdd) {
        NewRemoteDialog(
            busy = busy,
            existingNames = remotes.map { it.name },
            onDismiss = { showAdd = false },
            onAdd = { name, url ->
                showAdd = false
                onAdd(name, url)
            }
        )
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

/**
 * Phase 73.7 — Spck's "New Remote" dialog (the owner's screenshot 4): a
 * name, a URL, Cancel / Ok. The "+" button of [GitRemotesDialog] opens
 * it; Ok validates (same rules as the old inline form — a safe,
 * unused name and a safe URL) and hands the pair to [onAdd].
 */
@Composable
private fun NewRemoteDialog(
    busy: Boolean,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit
) {
    // Phase 73.9 — the owner's "name at origin default": the manual-first
    // no-remote flow pastes the first link here, and git convention names
    // it origin; only when origin is taken does the field start empty.
    var name by remember {
        mutableStateOf(if (existingNames.any { it.equals("origin", ignoreCase = true) }) "" else "origin")
    }
    var url by remember { mutableStateOf("") }

    val trimmedName = name.trim()
    val trimmedUrl = url.trim()
    val nameValid = trimmedName.isNotEmpty() && GitManager.isSafeRemoteName(trimmedName) &&
        existingNames.none { it.equals(trimmedName, ignoreCase = true) }
    val urlValid = trimmedUrl.isNotEmpty() && GitManager.isSafeRemoteUrl(trimmedUrl)

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
                        text = stringResource(R.string.git_remotes_new_title),
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
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onAdd(trimmedName, trimmedUrl) },
                        enabled = nameValid && urlValid && !busy
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.git_dialog_ok))
                        }
                    }
                }
            }
        }
    }
}
