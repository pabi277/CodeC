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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 41.2 + follow-up — the Feedback & Support content card, now the
 * body of its own screen (owner request after device round 1: *"can it be
 * a separate page?"*). One text field, two ephemeral attachment checkboxes,
 * the channel buttons (WhatsApp-first, then the fallbacks that always
 * work), the owner's reply-to fields, and the honest three-line disclosure
 * ABOVE the checkboxes so it is on screen at the moment a box is first
 * ticked (exit 41.2.4).
 *
 * Nothing here sends anything by itself: CHAT opens WhatsApp with the
 * message typed (the user presses send), EMAIL opens a compose window, COPY
 * touches only the clipboard, GITHUB ISSUE opens a prefilled browser page.
 * If a launch fails, the content is copied — a tap never loses the report.
 *
 * @param screenLabel what the report's info line calls this surface
 *   ("Feedback" from the screen, formerly "Settings").
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
    val scope = rememberCoroutineScope()
    val feedbackStore = remember { FeedbackStore(context) }
    val gitStore = remember { GitCredentialsStore(context) }

    // Stored contacts (reactive: paste a number + SAVE and the CHAT row
    // appears with no restart — exit 41.2.1).
    val storedNumber by feedbackStore.whatsappNumberFlow.collectAsState(initial = "")
    val storedEmail by feedbackStore.contactEmailFlow.collectAsState(initial = "")

    // The section's own state. The attachment choices are deliberately NOT
    // part of any store: every report is a fresh choice (privacy law).
    var state by remember { mutableStateOf(FeedbackSectionState()) }
    var crashRecord by remember { mutableStateOf<String?>(null) }
    var crashKnown by remember { mutableStateOf(false) }
    var lastProject by remember { mutableStateOf<String?>(null) }
    var secret by remember { mutableStateOf<String?>(null) }
    var numberField by remember { mutableStateOf("") }
    var emailField by remember { mutableStateOf("") }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(storedNumber, storedEmail) {
        if (numberField.isBlank()) numberField = storedNumber
        if (emailField.isBlank()) emailField = storedEmail
    }

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
        whatsappNumberE164 = storedNumber.ifBlank { null },
        contactEmail = storedEmail.ifBlank { null }
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
                "If you write to a personal WhatsApp number, the owner can see your phone " +
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
                        val url = FeedbackDraft.whatsappUrl(storedNumber, budgeted)
                        if (url == null) {
                            copyReport("WhatsApp link could not be built — the report is copied")
                        } else if (!whatsappInstalled(context)) {
                            // Exit 41.2.2: a device without WhatsApp must not
                            // be a dead end — copy + show the number.
                            copyReport(
                                "WhatsApp is not installed — the report is copied. " +
                                    "Write to +$storedNumber"
                            )
                        } else if (!OpenInBrowser.open(context, url)) {
                            copyReport(
                                "WhatsApp did not open — the report is copied. " +
                                    "Write to +$storedNumber"
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
                    "Replies go to +$storedNumber · the message opens typed, you press send",
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
                    "WhatsApp number not set — COPY REPORT and GITHUB ISSUE reach the owner too",
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
                            storedEmail,
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Reply-to details (owner): where feedback from this app is delivered",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = numberField,
                onValueChange = { numberField = it },
                label = { Text("WhatsApp number for replies") },
                singleLine = true,
                supportingText = {
                    val t = numberField.trim()
                    when {
                        t.isEmpty() -> Text("empty = no CHAT row (clearing is an explicit off)")
                        FeedbackContacts.numberForStorage(t) != null ->
                            Text("ok — ${FeedbackContacts.numberForStorage(t)}")
                        else -> Text("that doesn't look like a WhatsApp number (with country code, e.g. +91 98765 43210)")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = emailField,
                onValueChange = { emailField = it },
                label = { Text("Contact email for replies (optional)") },
                singleLine = true,
                supportingText = {
                    val t = emailField.trim()
                    when {
                        t.isEmpty() -> Text("empty = no EMAIL button (clearing is an explicit off)")
                        FeedbackContacts.emailForStorage(t) != null -> Text("ok")
                        else -> Text("that doesn't look like an email address")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (savedMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    savedMessage ?: "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val n = feedbackStore.saveWhatsappNumber(numberField)
                        val e = feedbackStore.saveContactEmail(emailField)
                        savedMessage = buildString {
                            append(if (n) "Number saved" else "Number NOT saved — doesn't look like a WhatsApp number")
                            append(" · ")
                            append(if (e) "email saved" else "email NOT saved — doesn't look like an address")
                        }
                    }
                }) {
                    Text("SAVE")
                }
            }
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
