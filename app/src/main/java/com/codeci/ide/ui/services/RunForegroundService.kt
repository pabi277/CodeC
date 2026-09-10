package com.codeci.ide.ui.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.codeci.ide.MainActivity
import com.codeci.ide.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 24.2 — foreground notification for long-running programs.
 *
 * When a RUN ▶ (or server) has been running for > 5 s, [EditorViewModel]
 * promotes the app process to a foreground service with a tappable
 * notification ("Running: <file>" + elapsed) and a Stop action. Short
 * hello-world runs stay silent. The service does not own the child process —
 * the run lives in the same app process — but `startForeground` raises the
 * process priority so Android is far less likely to kill it while the user is
 * in another app. The Stop action is delivered back to the ViewModel through
 * [stopCallback].
 *
 * Phase 37.2 extends the same service to the LAN server: [startServing] keeps
 * the process alive behind a `Serving <project> on <ip>:<port>` title with the
 * identical Stop action, so a phone server survives the user switching apps
 * without the app growing a second foreground-service type.
 */
class RunForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastTitle: String = "CodeC run"

    /** Phase 37.2 — a server keeps one steady text instead of an elapsed clock. */
    private var servingBody: String? = null
    private var tickerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                runCatching { stopCallback?.invoke() }
                tickerJob?.cancel()
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                lastTitle = intent?.getStringExtra(EXTRA_TITLE) ?: "CodeC run"
                servingBody = intent?.getStringExtra(EXTRA_BODY)
                startForeground(NOTIFICATION_ID, buildNotification(0))
                startTicker()
                return START_NOT_STICKY
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        tickerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startTicker() {
        // A server notification says "Serving … on ip:port"; re-notifying it
        // once a second for a running clock would be pure battery burn.
        if (tickerJob != null || servingBody != null) return
        val start = System.currentTimeMillis()
        tickerJob = scope.launch {
            while (true) {
                delay(1_000)
                val elapsed = ((System.currentTimeMillis() - start) / 1_000).coerceAtLeast(0)
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotification(elapsed))
            }
        }
    }

    private fun buildNotification(elapsedSeconds: Long): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPending = PendingIntent.getActivity(this, 0, tapIntent, pendingIntentFlags())
        val stopPending = PendingIntent.getService(
            this, 1,
            Intent(this, RunForegroundService::class.java).setAction(ACTION_STOP),
            pendingIntentFlags()
        )
        val serving = servingBody
        val title = if (serving != null) lastTitle else getString(R.string.foreground_run_title, lastTitle)
        val body = when {
            serving != null -> serving
            elapsedSeconds > 0 -> getString(R.string.foreground_run_body, elapsedSeconds)
            else -> getString(R.string.foreground_run_starting)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            // Phase 38.1 — a real status-bar silhouette (the ">_" mark),
            // not the full-colour adaptive foreground (white-box bug).
            .setSmallIcon(R.drawable.ic_stat_codec)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tapPending)
            .addAction(0, getString(R.string.foreground_run_stop), stopPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.foreground_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.foreground_channel_description)
        manager.createNotificationChannel(channel)
    }

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    companion object {
        const val ACTION_STOP = "com.codeci.ide.action.STOP_FOREGROUND_RUN"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val CHANNEL_ID = "codec_runs"
        private const val NOTIFICATION_ID = 1001

        private val onStopRequest = AtomicReference<(() -> Unit)?>(null)

        /** Called whenever the service's Stop action is tapped. */
        var stopCallback: (() -> Unit)?
            get() = onStopRequest.get()
            set(value) { onStopRequest.set(value) }

        /** Start the foreground run notification for [title]. */
        fun start(context: Context, title: String) = startInternal(context, title, null)

        /**
         * Phase 37.2 — keep-alive for a LAN server: same service, same Stop
         * action, but the title is the composed `Serving <project> on
         * <ip>:<port>` sentence and the body says what sharing means. No second
         * service type (the spec's explicit rule).
         */
        fun startServing(context: Context, title: String, body: String) =
            startInternal(context, title, body)

        private fun startInternal(context: Context, title: String, body: String?) {
            val intent = Intent(context, RunForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                body?.let { putExtra(EXTRA_BODY, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Cancel the foreground run notification. */
        fun stop(context: Context) {
            stopCallback = null
            runCatching {
                context.startService(
                    Intent(context, RunForegroundService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
