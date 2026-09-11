package com.codeci.ide.ui.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.ide.BuildConfig
import com.codeci.ide.ui.crash.CrashLog
import com.codeci.ide.ui.projects.EditorLaunchState
import com.codeci.ide.ui.projects.GitCredentialsStore
import com.codeci.ide.ui.services.OpenInBrowser
import com.codeci.ide.ui.utils.AppLogger
import com.codeci.ide.ui.utils.DeviceDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 41.2 + follow-ups — the Feedback & Support content card, the body
 * of its own screen. One text field, two ephemeral attachment checkboxes,
 * and the channel buttons (WhatsApp-first, then the fallbacks that always
 * work), with the honest three-line disclosure ABOVE the checkboxes so it
 * is on screen at the moment a box is first ticked (exit 41.2.4).
 *
 * **Round 2 (owner, 2026-09-10): the reply-to fields are GONE** — every
 * channel points at the developer, hardcoded in [DeveloperContact]
 * (*"I want to sit as developer not some other guy … no need for the user
 * to set number"*). The user writes what happened and taps a button; the
 * app already knows who it goes to. Nothing user-configurable remains on
 * this screen except the exit-prompt switch (on the screen, not the card).
 *
 * Nothing here sends anything by itself: CHAT opens WhatsApp with the
 * message typed (the user presses send), EMAIL opens a compose window, COPY
 * touches only the clipboard, GITHUB ISSUE opens a prefilled browser page.
 * If a launch fails, the content is copied — a tap never loses the report.
 *
 * @param screenLabel what the report's info line calls this surface
 *   ("Feedback").
 * @param exitRating the star rating handed over by the exit survey
 *   (0 = none); shown as a banner so the user sees what rides along.
 */
@Composable
fun FeedbackSectionCard(
    modifier: Modifier = Modifier,
    screenLabel: String = "Feedback",
    exitRating: Int = 0
) {
    val context = LocalContext.current
    val gitStore = remember { GitCredentialsStore(context) }

    // The section's own state. The attachment choices are deliberately NOT
    // part of any store: every report is a fresh choice (privacy law).
    var state by remember { mutableStateOf(FeedbackSectionState()) }
    var crashRecord by remember { mutableStateOf<String?>(null) }
    var crashKnown by remember { mutableStateOf(false) }
    var lastProject by remember { mutableStateOf<String?>(null) }
    var secret by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // One sink, one reader list: the crash record comes from the SAME
        // newest-record read CrashReportOverlay uses (CrashLog), never a
        // second capture path.
        val loaded = withContext(Dispatchers.IO) {
            Triple(
                CrashLog.newestRecord(context.filesDir),
                EditorLaunchState.load(context)?.projectName,
                runCatching { gitStore.stored().token.ifBlank { null } }.getOrNull()
            )
        }
        crashRecord = loaded.first
        crashKnown = true
        lastProject = loaded.second
        secret = loaded.third
        // Exit 41.2.5: after a crash, the crash row is prefilled in.
        state = state.copy(
            crashRecordPresent = loaded.first != null,
            includeCrash = loaded.first != null
        )
    }

    fun buildReport(maxChars: Int): String = FeedbackDraft.build(
        FeedbackInput(
            appVersion = BuildConfig.VERSION_NAME,
            androidRelease = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            device = Build.MODEL,
            abis = DeviceDiagnostics.abiSummary(),
            project = lastProject,
            screen = screenLabel,
            exitRating = exitRating.takeIf { it in 1..5 },
            userText = state.userText,
            includeLog = state.includeLog,
            logTail = if (state.includeLog) AppLogger.logs.value else emptyList(),
            includeCrash = state.includeCrash,
            crashRecord = if (state.includeCrash) crashRecord else null,
            maxChars = maxChars,
            secretToScrub = secret,
            paths = FeedbackDraft.RedactionPaths.forApp(context.filesDir.absolutePath)
        )
    )

    fun copyReport(what: String) {
        val full = buildReport(Int.MAX_VALUE)
        val ok = writeClip(context, "CodeC feedback report", full)
        Toast.makeText(
            context,
            if (ok) what else "Copying failed — the report is on screen only",
            Toast.LENGTH_LONG
        ).show()
    }

    val section = state.copy(
        whatsappNumberE164 = DeveloperContact.WHATSAPP_E164,
        contactEmail = DeveloperContact.EMAIL
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (ExitSurvey.hasRating(exitRating)) {
                Text(
                    "Your exit rating: ${ExitSurvey.stars(exitRating)} — it goes into the report below",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = state.userText,
                onValueChange = { state = state.copy(userText = it) },
                label = { Text("What happened? (what you saw, what you tapped)") },
                minLines = 1,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // The disclosure sits ABOVE the checkboxes, so it is on screen
            // when the first box is ticked (exit 41.2.4) — three lines, no
            // legalese, each one true.
            Text(
                "CodeC sends nothing by itself. Tapping CHAT opens WhatsApp with a message " +
                    "already typed — you read it, then press send.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "The report includes: app version, Android version, device model, and whatever " +
                    "you ticked below. It never includes your files, your token, or your code.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "If you write to the developer on WhatsApp, they can see your phone " +
                    "number and profile — that's how the reply gets back to you.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = state.includeLog,
                    onCheckedChange = { state = state.copy(includeLog = it) }
                )
                Column {
                    Text("Include the last ${FeedbackDraft.MAX_LOG_LINES} log lines", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "off by default · redacted: no tokens, paths shortened",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = state.includeCrash,
                    onCheckedChange = { state = state.copy(includeCrash = it) },
                    enabled = crashKnown && crashRecord != null
                )
                Column {
                    Text("Include the last crash", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        when {
                            !crashKnown -> "checking…"
                            crashRecord == null -> "no crash recorded — nothing to attach"
                            else -> "prefilled — a crash from a previous session is ready to attach"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (section.chatAvailable) {
                Button(
                    onClick = {
                        val budgeted = buildReport(FeedbackDraft.WHATSAPP_BUDGET)
                        val url = FeedbackDraft.whatsappUrl(DeveloperContact.WHATSAPP_E164, budgeted)
                        if (url == null) {
                            copyReport("WhatsApp link could not be built — the report is copied")
                        } else if (!whatsappInstalled(context)) {
                            // Exit 41.2.2: a device without WhatsApp must not
                            // be a dead end — copy + show the number.
                            copyReport(
                                "WhatsApp is not installed — the report is copied. " +
                                    "Write to ${DeveloperContact.WHATSAPP_DISPLAY}"
                            )
                        } else if (!OpenInBrowser.open(context, url)) {
                            copyReport(
                                "WhatsApp did not open — the report is copied. " +
                                    "Write to ${DeveloperContact.WHATSAPP_DISPLAY}"
                            )
                        } else {
                            Toast.makeText(
                                context,
                                "Opening WhatsApp — nothing is sent until you press send",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = section.chatReady,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CHAT ON WHATSAPP")
                }
                Text(
                    "Replies go to ${DeveloperContact.WHATSAPP_DISPLAY} · the message opens typed, you press send",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (!section.chatReady) {
                    Text(
                        "write what happened above to enable CHAT — or use the fallbacks",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "WhatsApp is unavailable on this device — COPY REPORT and GITHUB ISSUE reach the developer too",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = { copyReport("Report copied — paste it anywhere") }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("COPY REPORT")
                }
                if (section.emailAvailable) {
                    OutlinedButton(onClick = {
                        val full = buildReport(Int.MAX_VALUE)
                        val uri = FeedbackDraft.mailto(
                            DeveloperContact.EMAIL,
                            full,
                            subject = "CodeC feedback ${BuildConfig.VERSION_NAME}"
                        )
                        val send = Intent(Intent.ACTION_SENDTO, Uri.parse(uri))
                        if (send.resolveActivity(context.packageManager) != null) {
                            context.startActivity(Intent.createChooser(send, "Send feedback"))
                        } else {
                            copyReport("No email app — the report is copied")
                        }
                    }) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EMAIL")
                    }
                }
                OutlinedButton(onClick = {
                    val full = buildReport(Int.MAX_VALUE)
                    val title = state.userText.lineSequence()
                        .firstOrNull { it.isNotBlank() }
                        ?.trim()?.take(72)
                        ?: "CodeC feedback ${BuildConfig.VERSION_NAME}"
                    val url = FeedbackDraft.gitHubIssueUrl("pabi277", "CodeC", title, full)
                    OpenInBrowser.openOrCopy(
                        context = context,
                        url = url,
                        clipboardLabel = "CodeC feedback report",
                        copyInstead = full,
                        failureMessage = "No browser — the report is copied instead"
                    )
                }) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("GITHUB ISSUE")
                }
            }

            // Round 2 (owner): no reply-to fields — the developer's contact
            // is hardcoded (DeveloperContact); the user has nothing to set.
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** WhatsApp presence (personal or Business — either handles wa.me links). */
private fun whatsappInstalled(context: Context): Boolean =
    packageInstalled(context, "com.whatsapp") || packageInstalled(context, "com.whatsapp.w4b")

private fun packageInstalled(context: Context, pkg: String): Boolean = try {
    if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(pkg, 0)
    }
    true
} catch (e: Exception) {
    false
}

private fun writeClip(context: Context, label: String, text: String): Boolean =
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    }.getOrDefault(false)
