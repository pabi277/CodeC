package com.codeci.ide.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.codeci.ide.R

/**
 * Phase 73.6 — the shared "Git is not installed, do you want to install
 * it?" question (the owner's words). Asked from two doors that both end in
 * the same background install: the editor drawer's Repository panel (its
 * Initialize flow, when no git is found) and the Source Control sheet's
 * INSTALL GIT button (which asked nothing before starting in 73.2–73.5).
 *
 * Confirming starts the real `pkg install` in the shared terminal session
 * underneath while a status bar tracks it in the panel — the install
 * itself never was and still is not a redirect to Terminal.
 */
@Composable
fun GitInstallPromptDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_install_prompt_title)) },
        text = { Text(stringResource(R.string.git_install_prompt_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.git_install_prompt_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
