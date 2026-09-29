package com.codeci.ide.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.codeci.ide.R
import com.codeci.ide.ui.projects.GitCommitEntry

/** Log History (browse only) vs. Checkout Commit (each row offers a checkout). */
enum class GitLogSheetMode { VIEW, CHECKOUT }

/**
 * Phase 73.3 — Spck's "Log History" and "Checkout Commit" share one commit
 * list (both are `git log`); [mode] only changes whether a row's checkout
 * action is offered. Reached from the Source Control header's overflow menu.
 *
 * Checking out an older commit detaches HEAD — a state most beginners have
 * never heard of — so a row's checkout always confirms first, in plain
 * words, before [onCheckout] runs; [GitControlView]'s branch chip and result
 * message then make the "not on a branch now" state visible afterward.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitLogSheet(
    mode: GitLogSheetMode,
    commits: List<GitCommitEntry>,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCheckout: (GitCommitEntry) -> Unit
) {
    var pendingCheckout by remember { mutableStateOf<GitCommitEntry?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(
                    if (mode == GitLogSheetMode.CHECKOUT) {
                        R.string.git_checkout_commit_action
                    } else {
                        R.string.git_log_history_action
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
            )
            if (mode == GitLogSheetMode.CHECKOUT) {
                Text(
                    text = stringResource(R.string.git_checkout_commit_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            when {
                loading -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
                error != null -> Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
                commits.isEmpty() -> Text(
                    text = stringResource(R.string.git_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                ) {
                    itemsIndexed(commits, key = { _, entry -> entry.sha }) { index, entry ->
                        GitCommitRow(
                            entry = entry,
                            checkoutMode = mode == GitLogSheetMode.CHECKOUT,
                            onCheckout = { pendingCheckout = entry }
                        )
                        if (index < commits.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                    }
                }
            }
        }
    }

    pendingCheckout?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingCheckout = null },
            title = { Text(stringResource(R.string.git_checkout_commit_title)) },
            text = { Text(stringResource(R.string.git_checkout_commit_confirm, entry.shortSha)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCheckout = null
                        onCheckout(entry)
                    }
                ) {
                    Text(stringResource(R.string.git_checkout_commit_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCheckout = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    }
}

@Composable
private fun GitCommitRow(
    entry: GitCommitEntry,
    checkoutMode: Boolean,
    onCheckout: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.subject,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${entry.shortSha} · ${entry.author} · ${commitDateOnly(entry.date)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (checkoutMode) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onCheckout,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    stringResource(R.string.git_checkout_commit_row_action),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * The date portion of an ISO-8601 timestamp (`--date=iso-strict` always
 * includes a literal `T`): `2026-09-29T12:00:00+05:30` → `2026-09-29`. A
 * beginner's history list needs "when", not a timezone offset; anything
 * that does not look like the expected shape is shown as-is rather than
 * risking a wrong-looking reformat.
 */
private fun commitDateOnly(iso: String): String =
    iso.substringBefore('T').ifBlank { iso }
