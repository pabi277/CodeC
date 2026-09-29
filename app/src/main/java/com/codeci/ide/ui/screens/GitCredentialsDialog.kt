package com.codeci.ide.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.codeci.ide.R
import com.codeci.ide.ui.projects.GitCredentialsStore
import com.codeci.ide.ui.projects.GitErrors
import kotlinx.coroutines.launch

/**
 * Phase 73.5 — the Git Credentials dialog, inside the Source Control
 * sheet (Spck screenshot 2: PROVIDER row, username / email / token, a
 * "Create a GitHub Token" link, Cancel / Ok). The owner's instruction was
 * to merge Settings' git config here so credentials can be set without
 * leaving the panel.
 *
 * This is a second *editor*, not a second *store*: every field reads and
 * writes the same [GitCredentialsStore] Settings uses, so whichever screen
 * saves last wins and neither can show stale data (values are reloaded
 * every time the dialog opens). The one deliberate difference from the
 * screenshot is a fourth field — the commit name — because the store keeps
 * four values and dropping one here would silently lose it.
 *
 * [onManage] jumps to Settings' GitHub Account section (the old home of
 * this editor); [onSaved] lets the sheet re-read readiness (a fresh token
 * can clear the NO_TOKEN blocker without a manual refresh).
 */
@Composable
fun GitCredentialsDialog(
    onDismiss: () -> Unit,
    onManage: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { GitCredentialsStore(context.applicationContext) }
    var username by remember { mutableStateOf("") }
    var commitName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showProviders by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val stored = store.stored()
        username = stored.username
        commitName = stored.authorName
        email = stored.authorEmail
        token = stored.token
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
                // ---- title + close --------------------------------------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.git_credentials_title),
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

                // ---- provider row -----------------------------------------
                // GitHub is the only provider this app speaks to (push,
                // publish and remote discovery are all GitHub-shaped), so
                // the dropdown honestly lists one entry — the row keeps
                // Spck's shape without inventing providers that do nothing.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.git_credentials_provider_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showProviders = true }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.git_credentials_provider_name),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showProviders,
                            onDismissRequest = { showProviders = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_credentials_provider_name)) },
                                onClick = { showProviders = false }
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.git_credentials_manage),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onManage)
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.git_credentials_username)) },
                    singleLine = true,
                    enabled = loaded && !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = commitName,
                    onValueChange = { commitName = it },
                    label = { Text(stringResource(R.string.git_credentials_commit_name)) },
                    singleLine = true,
                    enabled = loaded && !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.git_credentials_email)) },
                    singleLine = true,
                    enabled = loaded && !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.git_credentials_token)) },
                    singleLine = true,
                    enabled = loaded && !saving,
                    visualTransformation = if (tokenVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { tokenVisible = !tokenVisible }) {
                            Text(
                                stringResource(
                                    if (tokenVisible) {
                                        R.string.git_credentials_hide
                                    } else {
                                        R.string.git_credentials_show
                                    }
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // ---- "Create a GitHub Token." link --------------------------
                val createPrefix = stringResource(R.string.git_credentials_create_prefix)
                val createLink = stringResource(R.string.git_credentials_create_link)
                val createText = remember(createPrefix, createLink) {
                    buildAnnotatedString {
                        append(createPrefix)
                        pushStringAnnotation(tag = "url", annotation = GitErrors.TOKEN_HELP_URL)
                        withStyle(
                            SpanStyle(
                                color = androidx.compose.ui.graphics.Color(0xFF64B5F6),
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append(createLink)
                        }
                        pop()
                        append(".")
                    }
                }
                ClickableText(
                    text = createText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(top = 10.dp),
                    onClick = { offset ->
                        createText.getStringAnnotations(tag = "url", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                    }
                )

                // ---- Cancel / Ok --------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                store.save(token, username, commitName, email)
                                saving = false
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.git_credentials_saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                                onSaved()
                                onDismiss()
                            }
                        },
                        enabled = loaded && !saving
                    ) {
                        Text(stringResource(R.string.git_credentials_ok))
                    }
                }
            }
        }
    }
}
