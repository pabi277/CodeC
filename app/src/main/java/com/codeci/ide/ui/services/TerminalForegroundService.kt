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
import com.codeci.ide.ui.utils.AppLogger

/**
 * Keeps the app process important while one or more terminal PTYs are alive.
 *
 * The service does not own or proxy the PTY; [TerminalViewModel] still owns
 * every session. Its only job is the Android lifecycle contract that a plain
 * activity-scoped ViewModel cannot provide after the activity goes to the
 * background. Termux uses the same user-visible foreground notification
 * affordance for long-lived shells.
 */
class TerminalForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Phase 44.1 — the SAME service (and the SAME channel) carries the
        // one-time setup: while the bootstrap downloads, the notification is
        // the only system-level surface that survives the app being swiped
        // away, so it says what is happening and how far along it is. A
        // null/blank status restores the plain terminal copy.
        val status = intent?.getStringExtra(EXTRA_STATUS)?.takeIf { it.isNotBlank() }
        val percent = intent?.getIntExtra(EXTRA_PROGRESS, -1)?.takeIf { it in 0..100 }
        startForeground(NOTIFICATION_ID, buildNotification(status, percent))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun buildNotification(status: String? = null, percent: Int? = null): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(this, 0, tapIntent, pendingIntentFlags())
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // Phase 38.1 — a real status-bar silhouette (the ">_" mark).
            .setSmallIcon(R.drawable.ic_stat_codec)
            .setContentTitle(if (status == null) "CodeC terminal" else "CodeC setup")
            .setContentText(status ?: "Terminal running in the background")
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (status != null) {
            // Indeterminate while the size is unknown, determinate once the
            // server answered a length. setOnlyAlertOnce keeps it silent.
            builder.setProgress(100, percent ?: 0, percent == null)
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Terminal sessions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps active CodeC terminal sessions running"
            }
        )
    }

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    companion object {
        private const val CHANNEL_ID = "codec_terminal"
        private const val NOTIFICATION_ID = 1002
        private const val EXTRA_STATUS = "codec_terminal_status"
        private const val EXTRA_PROGRESS = "codec_terminal_progress"

        fun start(context: Context) = start(context, null, null)

        /**
         * Phase 44.1 — starts (or updates) the service with the setup status
         * line. [status] null restores the plain terminal notification. Safe
         * to call repeatedly: re-delivering onStartCommand rewrites the
         * notification without a new channel, service or permission.
         */
        fun start(context: Context, status: String?, percent: Int?) {
            val intent = Intent(context, TerminalForegroundService::class.java)
            if (status != null) intent.putExtra(EXTRA_STATUS, status)
            if (percent != null) intent.putExtra(EXTRA_PROGRESS, percent)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Android 12+ refuses a background FGS start, and a denied
                // POST_NOTIFICATIONS must never become a crash: the in-app
                // setup bar is the primary surface, the service is a bonus.
                AppLogger.w("TerminalFgs", "foreground service not started: ${e.message}")
            }
        }

        /** Rewrites the notification text/progress (starts the service if needed). */
        fun updateStatus(context: Context, status: String?, percent: Int?) =
            start(context, status, percent)

        fun stop(context: Context) {
            context.stopService(Intent(context, TerminalForegroundService::class.java))
        }
    }
}
