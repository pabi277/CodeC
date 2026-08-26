package com.codeci.ide.ui.terminal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codeci.ide.MainActivity
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Snapshot of the Android clipboard, kept android-free so it is host-testable. */
sealed interface ClipboardContent {
    /** No clip is present. */
    data object Empty : ClipboardContent

    /** A text clip (plain text, HTML or URI list coerced to text). */
    data class Text(val value: String) : ClipboardContent

    /** A clip with no text representation (e.g. an image). */
    data object NonText : ClipboardContent
}

/**
 * Android notification operations, kept android-free so the pure core of
 * [CodecApiBridge] is host-testable.
 */
data class NotifyOps(
    val send: (title: String, body: String) -> Unit,
    val clear: () -> Unit,
    val status: () -> String
)

/**
 * Handles [CodecApiProtocol] requests emitted by terminal programs:
 *
 *  1. [handle] is called by the UI layer with the raw OSC payload;
 *  2. the payload is parsed and both file paths are confinement-checked
 *     against `$PREFIX/tmp/codec-api`;
 *  3. the capability runs (clipboard via [ClipboardManager], notifications
 *     via [NotificationManager]);
 *  4. the outcome is written atomically into the response file.
 *
 * Runtime-permission flow (Android 13+ `notify.send`): when notifications
 * are disabled the app writes `NEED_PERMISSION:...`, ensures the channel
 * (for a targetSdk ≤ 32 app, first channel creation is what surfaces the
 * system permission dialog), and asks the activity to request permission
 * via the [handle] `onPermissionRequired` callback; [resumeAfterPermission]
 * then completes the same request (OK) or fails it with an actionable
 * error.
 *
 * The app never executes anything from the payload; side effects are a
 * clipboard write, a notification, and writes inside the private API
 * directory.
 */
object CodecApiBridge {

    const val NOTIFICATION_CHANNEL_ID = "codec-terminal"
    const val NOTIFICATION_CHANNEL_NAME = "CodeC terminal"
    private const val NOTIFICATION_ID = 1001

    /**
     * @return true when the payload was a recognized CodeCApi request
     * (regardless of whether the operation itself succeeded), false when it
     * should be silently ignored.
     */
    suspend fun handle(
        context: Context,
        payload: String,
        apiDir: File,
        onPermissionRequired: ((CodecApiProtocol.Request, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val request = CodecApiProtocol.parse(payload) ?: return@withContext false

        if (request.op == CodecApiProtocol.Op.NOTIFY_SEND && !notificationsEnabled(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+: notifications are off by default even for
                // targetSdk-28 apps; for targetSdk <= 32 the system shows the
                // permission dialog on FIRST channel creation while the
                // activity is started. Ensure the channel so the dialog can
                // appear, tell the CLI to wait, then ask the activity.
                ensureNotificationChannel(context)
                deliver(
                    request,
                    apiDir,
                    CodecApiProtocol.permissionNotice(Manifest.permission.POST_NOTIFICATIONS)
                )
                onPermissionRequired?.invoke(request, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                deliver(
                    request,
                    apiDir,
                    "${CodecApiProtocol.ERR_PREFIX}notifications are disabled — " +
                        "enable them in Android Settings > CodeC > Notifications"
                )
            }
            return@withContext true
        }

        val response = if (request.op.isNotifyOperation) {
            val notifyOps = androidNotifyOps(context)
            if (notifyOps == null) {
                "${CodecApiProtocol.ERR_PREFIX}notification service unavailable"
            } else {
                execute(request, apiDir, { ClipboardContent.Empty }, {}, notifyOps)
            }
        } else {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboardManager == null) {
                "${CodecApiProtocol.ERR_PREFIX}clipboard service unavailable"
            } else {
                execute(
                    request = request,
                    apiDir = apiDir,
                    readClipboard = { readClipboard(clipboardManager, context) },
                    writeClipboard = { text ->
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText("CodeC clipboard", text)
                        )
                    }
                )
            }
        }
        deliver(request, apiDir, response)
        true
    }

    /**
     * Completes a request that was parked by the permission flow. Called by
     * the activity after the user answers the notification permission
     * dialog; the response replaces the earlier `NEED_PERMISSION:` file
     * content (atomic rename), which is what lets the CLI stop waiting.
     */
    suspend fun resumeAfterPermission(
        context: Context,
        request: CodecApiProtocol.Request,
        apiDir: File,
        granted: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val response = resumeResponse(request, apiDir, granted, androidNotifyOps(context))
        deliver(request, apiDir, response)
        true
    }

    /**
     * Pure, host-testable permission-resume logic: granted → run the
     * capability; denied → actionable error (no notification is posted).
     */
    fun resumeResponse(
        request: CodecApiProtocol.Request,
        apiDir: File,
        granted: Boolean,
        notify: NotifyOps?
    ): String =
        if (granted) {
            execute(request, apiDir, { ClipboardContent.Empty }, {}, notify)
        } else {
            "${CodecApiProtocol.ERR_PREFIX}notification permission denied — " +
                "enable notifications in Android Settings > CodeC > Notifications"
        }

    /**
     * Pure, host-testable core: validates paths, runs the capability through
     * the injected operations and returns the response body.
     *
     * Response conventions:
     *  - `clipboard.get`    → raw clipboard text ("" when empty);
     *  - `clipboard.set`/`clear` → `OK`;
     *  - `clipboard.status` → `clipboard: text|empty|non-text` + `length: N`;
     *  - `notify.send`/`clear` → `OK`;
     *  - `notify.status`    → the adapter's status lines;
     *  - `notify.send` payload: first line = title, remainder = body;
     *  - any failure → `ERR:<message>`.
     */
    fun execute(
        request: CodecApiProtocol.Request,
        apiDir: File,
        readClipboard: () -> ClipboardContent,
        writeClipboard: (String) -> Unit,
        notify: NotifyOps? = null
    ): String {
        if (!CodecApiProtocol.isConfinedDirectChild(request.responseFile, apiDir)) {
            return "${CodecApiProtocol.ERR_PREFIX}response path escapes the CodeC API directory"
        }
        val requestFile = request.requestFile
        if (requestFile != null && !CodecApiProtocol.isConfinedDirectChild(requestFile, apiDir)) {
            return "${CodecApiProtocol.ERR_PREFIX}request path escapes the CodeC API directory"
        }

        return when (request.op) {
            CodecApiProtocol.Op.CLIPBOARD_GET -> when (val clip = readClipboard()) {
                is ClipboardContent.Empty -> ""
                is ClipboardContent.Text -> clip.value
                ClipboardContent.NonText ->
                    "${CodecApiProtocol.ERR_PREFIX}clipboard does not contain text"
            }

            CodecApiProtocol.Op.CLIPBOARD_SET -> {
                if (requestFile == null) {
                    "${CodecApiProtocol.ERR_PREFIX}missing request file"
                } else {
                    val file = File(requestFile)
                    if (file.length() > CodecApiProtocol.MAX_SET_BYTES.toLong()) {
                        "${CodecApiProtocol.ERR_PREFIX}clipboard content too large " +
                            "(max ${CodecApiProtocol.MAX_SET_BYTES} bytes)"
                    } else {
                        val text = try {
                            file.readText(Charsets.UTF_8)
                        } catch (e: Exception) {
                            AppLogger.e("CodecApiBridge", "cannot read clipboard request", e)
                            return "${CodecApiProtocol.ERR_PREFIX}cannot read request file"
                        }
                        writeClipboard(text)
                        "OK"
                    }
                }
            }

            CodecApiProtocol.Op.CLIPBOARD_CLEAR -> {
                writeClipboard("")
                "OK"
            }

            CodecApiProtocol.Op.CLIPBOARD_STATUS -> when (val clip = readClipboard()) {
                is ClipboardContent.Empty -> "clipboard: empty\nlength: 0"
                is ClipboardContent.Text -> "clipboard: text\nlength: ${clip.value.length}"
                ClipboardContent.NonText -> "clipboard: non-text\nlength: 0"
            }

            CodecApiProtocol.Op.NOTIFY_SEND -> {
                val ops = notify ?: return "${CodecApiProtocol.ERR_PREFIX}notification service unavailable"
                if (requestFile == null) {
                    "${CodecApiProtocol.ERR_PREFIX}missing request file"
                } else {
                    val file = File(requestFile)
                    if (file.length() > CodecApiProtocol.MAX_NOTIFY_BYTES.toLong()) {
                        "${CodecApiProtocol.ERR_PREFIX}notification content too large " +
                            "(max ${CodecApiProtocol.MAX_NOTIFY_BYTES} bytes)"
                    } else {
                        val content = try {
                            file.readText(Charsets.UTF_8)
                        } catch (e: Exception) {
                            AppLogger.e("CodecApiBridge", "cannot read notify request", e)
                            return "${CodecApiProtocol.ERR_PREFIX}cannot read request file"
                        }
                        val newline = content.indexOf('\n')
                        val title = (if (newline < 0) content else content.substring(0, newline)).trim()
                        val body = if (newline < 0) "" else content.substring(newline + 1)
                        if (title.isEmpty()) {
                            "${CodecApiProtocol.ERR_PREFIX}notification title is empty"
                        } else {
                            ops.send(title, body)
                            "OK"
                        }
                    }
                }
            }

            CodecApiProtocol.Op.NOTIFY_CLEAR -> {
                val ops = notify ?: return "${CodecApiProtocol.ERR_PREFIX}notification service unavailable"
                ops.clear()
                "OK"
            }

            CodecApiProtocol.Op.NOTIFY_STATUS ->
                (notify ?: return "${CodecApiProtocol.ERR_PREFIX}notification service unavailable").status()
        }
    }

    /** Atomically writes [response] into the validated response file. */
    private fun deliver(
        request: CodecApiProtocol.Request,
        apiDir: File,
        response: String
    ) {
        if (!CodecApiProtocol.isConfinedDirectChild(request.responseFile, apiDir)) {
            AppLogger.w(
                "CodecApiBridge",
                "refusing to write outside ${CodecApiProtocol.API_DIR_NAME}: ${request.responseFile}"
            )
            return
        }
        val target = File(request.responseFile)
        val partial = File(target.parentFile, "${target.name}.partial")
        try {
            partial.writeText(response)
            if (!partial.renameTo(target)) {
                // Some filesystems refuse rename over an existing file.
                target.writeText(response)
                partial.delete()
            }
        } catch (e: Exception) {
            AppLogger.e("CodecApiBridge", "cannot write clipboard response", e)
        }
    }

    /** True when Android says this app may post notifications. */
    private fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Notifications from CodeC terminal commands"
            manager.createNotificationChannel(channel)
        }
    }

    private fun androidNotifyOps(context: Context): NotifyOps? {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return null
        return NotifyOps(
            send = { title, body ->
                ensureNotificationChannel(context)
                val openApp = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                if (body.isNotEmpty()) {
                    builder.setContentText(body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                }
                manager.notify(NOTIFICATION_ID, builder.build())
            },
            clear = { manager.cancel(NOTIFICATION_ID) },
            status = {
                val enabled = notificationsEnabled(context)
                val channelExists = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null
                } else {
                    true
                }
                "notification permission: ${if (enabled) "enabled" else "disabled"}\n" +
                    "channel: $NOTIFICATION_CHANNEL_ID (${if (channelExists) "ready" else "not created"})"
            }
        )
    }

    private fun readClipboard(clipboardManager: ClipboardManager, context: Context): ClipboardContent {
        val clip = clipboardManager.primaryClip ?: return ClipboardContent.Empty
        if (clip.itemCount == 0) return ClipboardContent.Empty
        val description = clip.description
        val isText = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) ||
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)
        if (!isText) return ClipboardContent.NonText
        val text = try {
            clip.getItemAt(0).coerceToText(context)?.toString()
        } catch (e: Exception) {
            AppLogger.e("CodecApiBridge", "cannot coerce clipboard item", e)
            null
        }
        return if (text == null) ClipboardContent.Empty else ClipboardContent.Text(text)
    }
}
