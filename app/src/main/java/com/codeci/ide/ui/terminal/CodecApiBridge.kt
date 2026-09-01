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
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codeci.ide.MainActivity
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
 * Android "Termux:API-style" operations (Phase 5.3), kept android-free so the
 * pure core of [CodecApiBridge] is host-testable. The validation lives in the
 * core; these lambdas only perform the side effect.
 */
data class TermuxApiOps(
    val toast: (text: String) -> Unit = {},
    val shareText: (text: String) -> Unit = {},
    val openUrl: (url: String) -> Unit = {},
    val vibrate: (millis: Long) -> Unit = {}
)

/** Snapshot of the device battery, kept android-free so it is host-testable. */
data class BatterySnapshot(
    /** 0..100; null when the platform does not report a value. */
    val percentage: Int?,
    /** "charging" | "discharging" | "full" | "not-charging" | "unknown". */
    val status: String,
    /** Degrees Celsius; null when the platform does not report it. */
    val temperatureC: Double?,
    /** "good" | "overheat" | "dead" | "over-voltage" | "cold" | "unspecified-failure" | "unknown". */
    val health: String,
    /** Millivolts; null when the platform does not report it. */
    val voltageMv: Int?,
    /** "ac" | "usb" | "wireless" | "dock" | "unknown". */
    val plugged: String
)

/**
 * One sensor sample, kept android-free so it is host-testable. Exactly one of
 * the `x/y/z` triple (accelerometer, gyroscope) or [lux] (light) is set,
 * depending on [type].
 */
data class SensorReading(
    /** "accelerometer" | "gyroscope" | "light". */
    val type: String,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val lux: Double? = null
)

/**
 * Android device-capability operations (Phase 18), kept android-free so the
 * pure core of [CodecApiBridge] is host-testable. The validation and JSON
 * formatting live in the core; these lambdas only read the device / perform
 * the side effect.
 *
 * Camera capture is deliberately NOT here: `camera.capture` is a
 * runtime-permission operation parked and resumed by the activity flow
 * (like [NotifyOps]'s `notify.send`).
 */
data class DeviceApiOps(
    /** Snapshot of the device battery; null when the battery service is unavailable. */
    val batteryStatus: () -> BatterySnapshot? = { null },
    /** One sample of the named sensor; null when that sensor is unavailable. */
    val sensorRead: (type: String) -> SensorReading? = { null },
    /** Speaks [text]; returns `OK` or an `ERR:`-prefixed reason. */
    val ttsSpeak: (text: String) -> String = {
        "${CodecApiProtocol.ERR_PREFIX}no TextToSpeech engine available"
    },
    /** Dispatches the (already validated) intent; returns `OK` or an `ERR:`-prefixed reason. */
    val intentSend: (action: String, data: String) -> String = { _, _ ->
        "${CodecApiProtocol.ERR_PREFIX}intent dispatch unavailable"
    }
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
 * Phase 18 extends the same park/resume pattern to `camera.capture`
 * (runtime CAMERA): [handle] parks the request with
 * `NEED_PERMISSION:android.permission.CAMERA`, the activity shows the
 * runtime dialog, [resumeAfterPermission] writes the interim `CAPTURING:`
 * marker once the user grants, and the activity's `TakePicture` contract
 * then writes the final `OK:<path>` / `ERR:` through
 * [completeCameraCapture].
 *
 * The app never executes anything from the payload; side effects are a
 * clipboard write, a notification, a camera photo, a TTS utterance, an
 * intent, and writes inside the private API directory.
 */
object CodecApiBridge {

    const val NOTIFICATION_CHANNEL_ID = "codec-terminal"
    const val NOTIFICATION_CHANNEL_NAME = "CodeC terminal"
    private const val NOTIFICATION_ID = 1001

    /** Sub-directory of [CodecApiProtocol.API_DIR_NAME] where camera photos land. */
    const val CAMERA_DIR_NAME = "camera"

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

        if (request.op == CodecApiProtocol.Op.CAMERA_CAPTURE) {
            // Runtime CAMERA: park the request exactly like notify.send.
            // The CLI keeps polling on NEED_PERMISSION (and later CAPTURING)
            // until the activity writes the final OK:<path> / ERR:.
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            deliver(
                request,
                apiDir,
                if (granted) CodecApiProtocol.capturePending() else
                    CodecApiProtocol.permissionNotice(Manifest.permission.CAMERA)
            )
            onPermissionRequired?.invoke(request, Manifest.permission.CAMERA)
            return@withContext true
        }

        val response = when {
            request.op.isNotifyOperation -> {
                val notifyOps = androidNotifyOps(context)
                if (notifyOps == null) {
                    "${CodecApiProtocol.ERR_PREFIX}notification service unavailable"
                } else {
                    execute(request, apiDir, { ClipboardContent.Empty }, {}, notifyOps)
                }
            }
            request.op.isTermuxApiOperation -> {
                execute(
                    request = request,
                    apiDir = apiDir,
                    readClipboard = { ClipboardContent.Empty },
                    writeClipboard = {},
                    termuxApi = androidTermuxApiOps(context)
                )
            }
            request.op.isDeviceApiOperation -> {
                execute(
                    request = request,
                    apiDir = apiDir,
                    readClipboard = { ClipboardContent.Empty },
                    writeClipboard = {},
                    deviceApi = androidDeviceApiOps(context)
                )
            }
            else -> {
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
        }
        deliver(request, apiDir, response)
        true
    }

    /**
     * Completes a request that was parked by the permission flow. Called by
     * the activity after the user answers the notification permission
     * dialog; the response replaces the earlier `NEED_PERMISSION:` file
     * content (atomic rename), which is what lets the CLI stop waiting.
     *
     * Phase 18: for `camera.capture` a grant writes the interim `CAPTURING:`
     * marker (the activity is about to launch the photo capture); the final
     * `OK:<path>` / `ERR:` is written by [completeCameraCapture]. A denial
     * writes an actionable error.
     */
    suspend fun resumeAfterPermission(
        context: Context,
        request: CodecApiProtocol.Request,
        apiDir: File,
        granted: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val response = if (request.op == CodecApiProtocol.Op.CAMERA_CAPTURE) {
            if (granted) {
                CodecApiProtocol.capturePending(cameraTargetName(request, apiDir) ?: "")
            } else {
                "${CodecApiProtocol.ERR_PREFIX}camera permission denied — " +
                    "grant it in Android Settings > Apps > CodeC > Permissions"
            }
        } else {
            resumeResponse(request, apiDir, granted, androidNotifyOps(context))
        }
        deliver(request, apiDir, response)
        true
    }

    /**
     * Pure, host-testable permission-resume logic: granted → run the
     * capability; denied → actionable error (no notification is posted).
     * For `camera.capture` the granted branch only validates the target and
     * returns the interim `CAPTURING:` marker (the actual photo is taken by
     * the activity); use [cameraTargetName] to obtain the validated name.
     */
    fun resumeResponse(
        request: CodecApiProtocol.Request,
        apiDir: File,
        granted: Boolean,
        notify: NotifyOps?
    ): String =
        when {
            request.op == CodecApiProtocol.Op.CAMERA_CAPTURE && !granted ->
                "${CodecApiProtocol.ERR_PREFIX}camera permission denied — " +
                    "grant it in Android Settings > Apps > CodeC > Permissions"
            request.op == CodecApiProtocol.Op.CAMERA_CAPTURE ->
                CodecApiProtocol.capturePending(cameraTargetName(request, apiDir) ?: "")
            granted ->
                execute(request, apiDir, { ClipboardContent.Empty }, {}, notify)
            else ->
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
     *  - `battery.status`   → battery JSON;
     *  - `sensor.read`      → sensor JSON (`{x,y,z}` or `{lux}`);
     *  - `tts.speak`        → `OK` (payload = text) — validated here;
     *  - `camera.capture`   → `CAPTURING:<name>` after target validation
     *    (final response is written by [completeCameraCapture]);
     *  - `intent.send`      → `OK` (payload = first line action, rest data);
     *  - any failure → `ERR:<message>`.
     */
    fun execute(
        request: CodecApiProtocol.Request,
        apiDir: File,
        readClipboard: () -> ClipboardContent,
        writeClipboard: (String) -> Unit,
        notify: NotifyOps? = null,
        termuxApi: TermuxApiOps = TermuxApiOps(),
        deviceApi: DeviceApiOps = DeviceApiOps()
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

            CodecApiProtocol.Op.TOAST_SHOW -> {
                if (requestFile == null) {
                    "${CodecApiProtocol.ERR_PREFIX}missing request file"
                } else {
                    val file = File(requestFile)
                    if (file.length() > CodecApiProtocol.MAX_TOAST_BYTES.toLong()) {
                        "${CodecApiProtocol.ERR_PREFIX}toast text too large " +
                            "(max ${CodecApiProtocol.MAX_TOAST_BYTES} bytes)"
                    } else {
                        val text = try {
                            file.readText(Charsets.UTF_8)
                        } catch (e: Exception) {
                            AppLogger.e("CodecApiBridge", "cannot read toast request", e)
                            return "${CodecApiProtocol.ERR_PREFIX}cannot read request file"
                        }
                        if (text.isBlank()) {
                            "${CodecApiProtocol.ERR_PREFIX}toast text is empty"
                        } else {
                            termuxApi.toast(text)
                            "OK"
                        }
                    }
                }
            }

            CodecApiProtocol.Op.SHARE_TEXT -> {
                if (requestFile == null) {
                    "${CodecApiProtocol.ERR_PREFIX}missing request file"
                } else {
                    val file = File(requestFile)
                    if (file.length() > CodecApiProtocol.MAX_SHARE_BYTES.toLong()) {
                        "${CodecApiProtocol.ERR_PREFIX}share text too large " +
                            "(max ${CodecApiProtocol.MAX_SHARE_BYTES} bytes)"
                    } else {
                        val text = try {
                            file.readText(Charsets.UTF_8)
                        } catch (e: Exception) {
                            AppLogger.e("CodecApiBridge", "cannot read share request", e)
                            return "${CodecApiProtocol.ERR_PREFIX}cannot read request file"
                        }
                        if (text.isBlank()) {
                            "${CodecApiProtocol.ERR_PREFIX}share text is empty"
                        } else {
                            termuxApi.shareText(text)
                            "OK"
                        }
                    }
                }
            }

            CodecApiProtocol.Op.OPEN_URL -> {
                if (requestFile == null) {
                    "${CodecApiProtocol.ERR_PREFIX}missing request file"
                } else {
                    val file = File(requestFile)
                    if (file.length() > CodecApiProtocol.MAX_URL_BYTES.toLong()) {
                        "${CodecApiProtocol.ERR_PREFIX}url too large " +
                            "(max ${CodecApiProtocol.MAX_URL_BYTES} bytes)"
                    } else {
                        val url = try {
                            file.readText(Charsets.UTF_8).trim()
                        } catch (e: Exception) {
                            AppLogger.e("CodecApiBridge", "cannot read url request", e)
                            return "${CodecApiProtocol.ERR_PREFIX}cannot read request file"
                        }
                        if (!url.startsWith("http://", ignoreCase = true) &&
                            !url.startsWith("https://", ignoreCase = true)
                        ) {
                            "${CodecApiProtocol.ERR_PREFIX}only http(s) URLs can be opened"
                        } else {
                            termuxApi.openUrl(url)
                            "OK"
                        }
                    }
                }
            }

            CodecApiProtocol.Op.VIBRATE -> {
                if (requestFile == null) {
                    "${CodecApiProtocol.ERR_PREFIX}missing request file"
                } else {
                    val file = File(requestFile)
                    val raw = try {
                        file.readText(Charsets.UTF_8).trim()
                    } catch (e: Exception) {
                        AppLogger.e("CodecApiBridge", "cannot read vibrate request", e)
                        return "${CodecApiProtocol.ERR_PREFIX}cannot read request file"
                    }
                    val millis = if (raw.isEmpty()) {
                        CodecApiProtocol.DEFAULT_VIBRATE_MS
                    } else {
                        raw.toLongOrNull()
                    }
                    if (millis == null) {
                        "${CodecApiProtocol.ERR_PREFIX}vibrate duration must be a number of milliseconds"
                    } else {
                        termuxApi.vibrate(millis.coerceIn(1L, CodecApiProtocol.MAX_VIBRATE_MS))
                        "OK"
                    }
                }
            }

            CodecApiProtocol.Op.BATTERY_STATUS ->
                batteryResponse(deviceApi)

            CodecApiProtocol.Op.SENSOR_READ ->
                sensorResponse(request, apiDir, deviceApi)

            CodecApiProtocol.Op.TTS_SPEAK -> {
                val (text, error) = readText(request, apiDir, CodecApiProtocol.MAX_TTS_BYTES) {
                    "${CodecApiProtocol.ERR_PREFIX}tts text too large " +
                        "(max ${CodecApiProtocol.MAX_TTS_BYTES} bytes)"
                }
                when {
                    error != null -> error
                    text.isBlank() -> "${CodecApiProtocol.ERR_PREFIX}tts text is empty"
                    else -> deviceApi.ttsSpeak(text)
                }
            }

            CodecApiProtocol.Op.CAMERA_CAPTURE ->
                cameraResponse(request, apiDir)

            CodecApiProtocol.Op.INTENT_SEND ->
                intentResponse(request, apiDir, deviceApi)
        }
    }

    /** `battery.status`: format the adapter snapshot as JSON. */
    fun batteryResponse(deviceApi: DeviceApiOps): String {
        val b = deviceApi.batteryStatus()
            ?: return "${CodecApiProtocol.ERR_PREFIX}battery service unavailable"
        return "{\"percentage\":${b.percentage ?: "null"}," +
            "\"status\":\"${b.status}\"," +
            "\"temperature\":${formatDouble(b.temperatureC)}," +
            "\"health\":\"${b.health}\"," +
            "\"voltage\":${b.voltageMv ?: "null"}," +
            "\"plugged\":\"${b.plugged}\"}"
    }

    /** `sensor.read`: validate the requested type, then format the sample as JSON. */
    fun sensorResponse(
        request: CodecApiProtocol.Request,
        apiDir: File,
        deviceApi: DeviceApiOps
    ): String {
        val (type, missing) = readSingleLine(
            request, apiDir, CodecApiProtocol.MAX_SENSOR_TYPE_BYTES
        )
        if (missing != null) return missing
        if (type !in SENSOR_TYPES) {
            return "${CodecApiProtocol.ERR_PREFIX}unknown sensor type '${type}' " +
                "(choose: ${SENSOR_TYPES.joinToString(", ")})"
        }
        val reading = deviceApi.sensorRead(type)
            ?: return "${CodecApiProtocol.ERR_PREFIX}${type} sensor unavailable"
        val sample = if (type == "light") {
            reading.lux?.let { "{\"type\":\"light\",\"lux\":${formatDouble(it)}}" }
                ?: "${CodecApiProtocol.ERR_PREFIX}light sensor returned no value"
        } else {
            val x = reading.x?.let { formatDouble(it) }
                ?: return "${CodecApiProtocol.ERR_PREFIX}${type} sensor returned no value"
            val y = reading.y?.let { formatDouble(it) }
                ?: return "${CodecApiProtocol.ERR_PREFIX}${type} sensor returned no value"
            val z = reading.z?.let { formatDouble(it) }
                ?: return "${CodecApiProtocol.ERR_PREFIX}${type} sensor returned no value"
            "{\"type\":\"$type\",\"x\":$x,\"y\":$y,\"z\":$z}"
        }
        return sample
    }

    /** `camera.capture`: validate the requested output file name. */
    fun cameraResponse(
        request: CodecApiProtocol.Request,
        apiDir: File
    ): String {
        val (name, missing) = readSingleLine(request, apiDir, CodecApiProtocol.MAX_CAMERA_NAME_BYTES)
        if (missing != null) return missing
        if (!CAMERA_FILE_NAME.matches(name)) {
            return "${CodecApiProtocol.ERR_PREFIX}unsafe camera file name '${name}' " +
                "(letters, digits, dots, dashes, underscores; .jpg/.jpeg/.png only)"
        }
        return CodecApiProtocol.capturePending(name)
    }

    /**
     * The validated output file name of a `camera.capture` request (the
     * request file's single line), or null when absent/unsafe. Used by the
     * activity to build the FileProvider URI.
     */
    fun cameraTargetName(
        request: CodecApiProtocol.Request,
        apiDir: File
    ): String? {
        val (name, _) = readSingleLine(request, apiDir, CodecApiProtocol.MAX_CAMERA_NAME_BYTES)
        return name.takeIf { CAMERA_FILE_NAME.matches(it) }
    }

    /**
     * `intent.send`: payload = first line action, remainder data. Only the
     * implicit actions `view`, `dial` and `send` are allowed (never an
     * explicit component — the terminal must not target a private activity
     * of another app), and `view`/`dial` data must use an allow-listed URI
     * scheme.
     */
    fun intentResponse(
        request: CodecApiProtocol.Request,
        apiDir: File,
        deviceApi: DeviceApiOps
    ): String {
        val (body, missing) = readText(request, apiDir, CodecApiProtocol.MAX_INTENT_BYTES) {
            "${CodecApiProtocol.ERR_PREFIX}intent payload too large " +
                "(max ${CodecApiProtocol.MAX_INTENT_BYTES} bytes)"
        }
        if (missing != null) return missing
        val newline = body.indexOf('\n')
        val action = (if (newline < 0) body else body.substring(0, newline)).trim().lowercase()
        val data = if (newline < 0) "" else body.substring(newline + 1)
        if (action !in INTENT_ACTIONS) {
            return "${CodecApiProtocol.ERR_PREFIX}unknown intent action '${action}' " +
                "(allowed: ${INTENT_ACTIONS.joinToString(", ")})"
        }
        if (data.isBlank()) {
            return "${CodecApiProtocol.ERR_PREFIX}missing intent data for '${action}'"
        }
        if (action != "send") {
            val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):").find(data.trim())?.groupValues?.get(1)
                ?.lowercase()
            if (scheme == null || scheme !in INTENT_SCHEMES) {
                return "${CodecApiProtocol.ERR_PREFIX}intent '${action}' allows only " +
                    "${INTENT_SCHEMES.joinToString(", ")} URIs"
            }
        }
        return deviceApi.intentSend(action, data)
    }

    /**
     * Writes the FINAL `camera.capture` outcome. Called by the activity after
     * the `TakePicture` contract returns; replaces the interim `CAPTURING:`
     * marker (atomic rename) so the CLI can stop waiting.
     */
    suspend fun completeCameraCapture(
        request: CodecApiProtocol.Request,
        apiDir: File,
        success: Boolean,
        output: File?
    ): Boolean = withContext(Dispatchers.IO) {
        val response = when {
            success && output != null -> "OK:${output.absolutePath}"
            success -> "${CodecApiProtocol.ERR_PREFIX}photo could not be saved"
            else -> "${CodecApiProtocol.ERR_PREFIX}photo capture cancelled"
        }
        deliver(request, apiDir, response)
        true
    }

    private fun readSingleLine(
        request: CodecApiProtocol.Request,
        apiDir: File,
        maxBytes: Int
    ): Pair<String, String?> {
        val (body, missing) = readText(request, apiDir, maxBytes) { missingRequestFile() }
        if (missing != null) return "" to missing
        return body.trim() to null
    }

    /** Reads the (confined) request payload; `null` + error for missing/oversized/unreadable. */
    private fun readText(
        request: CodecApiProtocol.Request,
        apiDir: File,
        maxBytes: Int,
        tooLarge: () -> String
    ): Pair<String, String?> {
        val requestFile = request.requestFile
        if (requestFile == null) return "" to missingRequestFile()
        if (requestFile.isNotEmpty() &&
            !CodecApiProtocol.isConfinedDirectChild(requestFile, apiDir)
        ) {
            return "" to "${CodecApiProtocol.ERR_PREFIX}request path escapes the CodeC API directory"
        }
        val file = File(requestFile)
        return try {
            if (file.length() > maxBytes.toLong()) {
                "" to tooLarge()
            } else {
                file.readText(Charsets.UTF_8) to null
            }
        } catch (e: Exception) {
            AppLogger.e("CodecApiBridge", "cannot read request file", e)
            "" to "${CodecApiProtocol.ERR_PREFIX}cannot read request file"
        }
    }

    private fun missingRequestFile(): String =
        "${CodecApiProtocol.ERR_PREFIX}missing request file"

    /** Formats a double like the JSON examples (never more than 3 decimals, no trailing zeros). */
    fun formatDouble(value: Double?): String {
        if (value == null) return "null"
        val fixed = String.format(java.util.Locale.ROOT, "%.3f", value)
        val trimmed = fixed.trimEnd('0').trimEnd('.')
        return when (trimmed) {
            "", "-0" -> "0"
            else -> trimmed
        }
    }

    internal val SENSOR_TYPES = listOf("accelerometer", "gyroscope", "light")
    internal val INTENT_ACTIONS = listOf("view", "dial", "send")
    internal val INTENT_SCHEMES =
        listOf("http", "https", "geo", "mailto", "tel", "sms", "market")
    internal val CAMERA_FILE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*\\.(jpg|jpeg|png)$")

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

    /**
     * Android side of the Termux:API-style operations (Phase 5.3). All four
     * are permission-light: toast/share/open-URL need nothing, and `VIBRATE`
     * is a normal (install-time) permission declared in the manifest. The
     * bridge runs on `Dispatchers.IO` with the Application context, so:
     *  - toast posts to the main looper (Toast requires it), and
     *  - share/open-URL launch their intents with `FLAG_ACTIVITY_NEW_TASK`.
     */
    private fun androidTermuxApiOps(context: Context): TermuxApiOps = TermuxApiOps(
        toast = { text ->
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
            }
        },
        shareText = { text ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(send, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        },
        openUrl = { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        },
        vibrate = { millis ->
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator == null || !vibrator.hasVibrator()) return@TermuxApiOps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(millis)
            }
        }
    )

    /**
     * Android side of the Phase 18 device capabilities. Battery, sensors,
     * TTS and implicit intents are permission-light (no manifest permission
     * for battery/sensors; TTS is an engine service; intents just launch).
     * `camera.capture` is handled separately (see the activity park flow).
     */
    private fun androidDeviceApiOps(context: Context): DeviceApiOps = DeviceApiOps(
        batteryStatus = { readBattery(context.applicationContext) },
        sensorRead = { type -> readSensor(context.applicationContext, type) },
        ttsSpeak = { text -> speakTts(context.applicationContext, text) },
        intentSend = { action, data -> dispatchIntent(context.applicationContext, action, data) }
    )

    /** Sticky `ACTION_BATTERY_CHANGED` broadcast — no permission needed. */
    private fun readBattery(context: Context): BatterySnapshot? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = if (level >= 0 && scale > 0) level * 100 / scale else null
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val temperature = if (tenths >= 0) tenths / 10.0 else null
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it >= 0 }
        return BatterySnapshot(
            percentage = percentage,
            status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not-charging"
                else -> "unknown"
            },
            temperatureC = temperature,
            health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over-voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "unspecified-failure"
                else -> "unknown"
            },
            voltageMv = voltage,
            plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                BatteryManager.BATTERY_PLUGGED_DOCK -> "dock"
                else -> "unknown"
            }
        )
    }

    /** Registers for one sample with a bounded wait; no permission needed. */
    private fun readSensor(context: Context, type: String): SensorReading? {
        val manager = context.getSystemService(SensorManager::class.java) ?: return null
        val sensor = when (type) {
            "accelerometer" -> manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            "gyroscope" -> manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            "light" -> manager.getDefaultSensor(Sensor.TYPE_LIGHT)
            else -> null
        } ?: return null
        val latch = CountDownLatch(1)
        val values = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                for (i in values.indices) {
                    if (i < event.values.size) values[i] = event.values[i]
                }
                latch.countDown()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        @Suppress("DEPRECATION")
        val registered = manager.registerListener(
            listener, sensor, SensorManager.SENSOR_DELAY_UI, Handler(Looper.getMainLooper())
        )
        if (!registered) return null
        latch.await(1500L, TimeUnit.MILLISECONDS)
        manager.unregisterListener(listener)
        return if (type == "light") {
            SensorReading(type = type, lux = values[0].toDouble())
        } else {
            SensorReading(
                type = type,
                x = values[0].toDouble(),
                y = values[1].toDouble(),
                z = values[2].toDouble()
            )
        }
    }

    /**
     * One app-lifetime [TextToSpeech] instance: the engine is bound through
     * the TTS object, so a local instance would be released (and speech
     * cut off) as soon as the request handler returns.
     */
    @Volatile
    private var ttsInstance: TextToSpeech? = null
    @Volatile
    private var ttsReady: Boolean = false
    private val ttsLock = Any()

    private fun speakTts(context: Context, text: String): String {
        val engine = textToSpeech(context)
            ?: return "${CodecApiProtocol.ERR_PREFIX}no TextToSpeech engine available"
        if (!ttsReady) {
            return "${CodecApiProtocol.ERR_PREFIX}TextToSpeech engine failed to initialise"
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "codec-tts")
        return if (result == TextToSpeech.SUCCESS) {
            "OK"
        } else {
            "${CodecApiProtocol.ERR_PREFIX}TextToSpeech engine refused the request (code $result)"
        }
    }

    private fun textToSpeech(context: Context): TextToSpeech? {
        ttsInstance?.let { return it }
        synchronized(ttsLock) {
            ttsInstance?.let { return it }
            val latch = CountDownLatch(1)
            val engine = try {
                TextToSpeech(context.applicationContext) { status ->
                    ttsReady = status == TextToSpeech.SUCCESS
                    latch.countDown()
                }
            } catch (e: Exception) {
                AppLogger.e("CodecApiBridge", "TextToSpeech init failed", e)
                null
            }
            if (engine == null) return null
            ttsInstance = engine
            latch.await(3000L, TimeUnit.MILLISECONDS)
            return engine
        }
    }

    /**
     * Dispatches an already-validated implicit intent. The core enforces the
     * action and URI-scheme allow-lists before this runs; only the launch and
     * its outcome are handled here.
     */
    private fun dispatchIntent(context: Context, action: String, data: String): String {
        val intent = when (action) {
            "view" -> Intent(Intent.ACTION_VIEW, Uri.parse(data.trim()))
            "dial" -> Intent(Intent.ACTION_DIAL, Uri.parse(data.trim()))
            "send" -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, data)
            }
            else -> return "${CodecApiProtocol.ERR_PREFIX}unknown intent action '$action'"
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "OK"
        } catch (e: Exception) {
            AppLogger.e("CodecApiBridge", "intent dispatch failed", e)
            "${CodecApiProtocol.ERR_PREFIX}no app can handle $action ${data.take(80)}"
        }
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
