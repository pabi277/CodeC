package com.codeci.ide.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 25.2 device-round instrumentation. The owner's device is not rooted
 * and Android 11+ hides `Android/data` from file managers, so a crash log
 * written to disk is unreachable — unless the APP shows it. On launch, if
 * [MainActivity]'s uncaught-exception handler has appended a crash to
 * `filesDir/crash-log.txt`, this overlay opens BEFORE anything else with
 * the last report and Copy / Share / Clear buttons. No permissions needed.
 *
 * The dialog only appears when a NEW crash exists (the file is deleted on
 * Clear, so a normal session never sees it again).
 *
 * Phase 42.3 — this same overlay is the crash-LOOP guard's single surface
 * (extended, never a second modal — a stacked dialog is how a safety
 * feature becomes the thing people screenshot and complain about):
 *
 *  - [loopDetected] adds the sentence *"CodeC failed again while starting.
 *    Nothing has been changed or deleted."* and one more button,
 *    [TRY WITHOUT MY SETTINGS] — the safe-mode door (PART_42_3 §2);
 *  - [SEND REPORT] hands the crash record to Phase 41's feedback screen
 *    with both attachments pre-ticked (via [CrashHandOffBridge]), replacing
 *    today's manual "copy this and find me in a chat" with a tap — the one
 *    moment a user is motivated to tell you is right after you failed them.
 */
@Composable
fun CrashReportOverlay(
    /** True when the startup ledger counted two interrupted starts in a row. */
    loopDetected: Boolean = false,
    /** [TRY WITHOUT MY SETTINGS]: activate safe mode for the session + restart. */
    onStartWithoutSettings: () -> Unit = {},
    /** [SEND REPORT]: hand the newest record to the feedback screen. */
    onSendReport: () -> Unit = {}
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // The NEWEST record, from its header — NOT a byte-tail of the
            // whole file. Records accumulate, and a tail window cuts off
            // the record's first lines — the exception type and message,
            // i.e. the diagnosis — exactly when it is needed most
            // (Phase 29 device round, 2026-09-06: three pasted reports
            // in a row were generic tail-only stacks). The writer caps
            // each record and bounds the file, so this read stays small.
            //
            // Phase 41.2: the read itself lives in `CrashLog` now — the
            // feedback section needs the same bytes, and one sink must
            // have one reader list so the two can never drift apart.
            report = CrashLog.newestRecord(context.filesDir)
        }
    }

    report?.let { text ->
        // The exception line (first line after the ==== header) in the TITLE:
        // even a screenshot of the dialog then carries the diagnosis. The
        // body can scroll out of view; the title cannot.
        val exceptionLine = text.lineSequence()
            .dropWhile { it.startsWith("====") }
            .firstOrNull { it.isNotBlank() }
            ?.take(90)
        AlertDialog(
            onDismissRequest = { /* keep the report until acted on */ },
            title = {
                Text(if (exceptionLine != null) "Crash: $exceptionLine" else "Last crash report")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (loopDetected) {
                        // Phase 42.3 §2 — the loop sentence, worded so the
                        // user trusts the [TRY WITHOUT MY SETTINGS] door:
                        // nothing was reset, nothing was deleted, projects
                        // and files are untouched.
                        Text(
                            "CodeC failed again while starting. Nothing has been changed or deleted.",
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        "CodeC crashed previously. Please SEND REPORT (or COPY ALL and paste " +
                            "into the chat) so the exact failing line can be fixed.",
                        fontSize = 13.sp
                    )
                    Text(
                        text,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("CodeC crash log", text))
                        Toast.makeText(context, "Crash log copied — paste it in the chat", Toast.LENGTH_LONG)
                            .show()
                    }) { Text("COPY ALL") }
                    // Phase 42.3 — one tap hands the SAME record to the
                    // feedback screen (both attachments pre-ticked); nothing
                    // is ever uploaded by the app itself.
                    TextButton(onClick = onSendReport) { Text("SEND REPORT") }
                    if (loopDetected) {
                        TextButton(onClick = onStartWithoutSettings) {
                            Text("TRY WITHOUT MY SETTINGS")
                        }
                    }
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, "CodeC crash log")
                                    .putExtra(Intent.EXTRA_TEXT, text),
                                "Share crash log"
                            )
                        )
                    }) { Text("SHARE") }
                    TextButton(onClick = {
                        runCatching { File(context.filesDir, "crash-log.txt").delete() }
                        report = null
                    }) { Text("CLEAR") }
                }
            }
        )
    }
}
