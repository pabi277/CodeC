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
 */
@Composable
fun CrashReportOverlay() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "crash-log.txt")
            report = if (file.isFile && file.length() > 0) {
                // Tail only: a corrupted or huge log must not OOM the dialog.
                val bytes = file.readBytes()
                val limit = 6000
                String(bytes, maxOf(0, bytes.size - limit), minOf(bytes.size, limit))
            } else {
                null
            }
        }
    }

    report?.let { text ->
        AlertDialog(
            onDismissRequest = { /* keep the report until acted on */ },
            title = { Text("Last crash report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "CodeC crashed previously. Please COPY ALL and paste it " +
                            "into the chat so the exact failing line can be fixed.",
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
                TextButton(onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("CodeC crash log", text))
                    Toast.makeText(context, "Crash log copied — paste it in the chat", Toast.LENGTH_LONG)
                        .show()
                }) { Text("COPY ALL") }
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
