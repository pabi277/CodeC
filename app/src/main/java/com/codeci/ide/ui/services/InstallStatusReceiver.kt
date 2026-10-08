package com.codeci.ide.ui.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.codeci.ide.ui.utils.AppLogger

/**
 * Phase 102 — the status callback of the PackageInstaller SESSION the
 * verified updater commits ([ApkUpdateManager.installViaSession]). The
 * session hands the bytes to the system installer over a private pipe — no
 * FileProvider URI grant leaves the app on that path — and reports back here:
 * pending user action (the system's own confirmation screen, which we must
 * launch), success, or a named failure. Declared `exported="false"` in the
 * manifest; only the system installer ever sends these.
 */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    AppLogger.e("Update", "install session pending user action but no intent")
                }
            }
            PackageInstaller.STATUS_SUCCESS ->
                AppLogger.i("Update", "install session: success")
            Int.MIN_VALUE ->
                AppLogger.e("Update", "install session callback without a status extra")
            else ->
                AppLogger.e("Update", "install session failed (status $status): $message")
        }
    }
}
