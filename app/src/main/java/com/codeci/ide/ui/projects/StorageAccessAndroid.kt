package com.codeci.ide.ui.projects

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Phase 93 — the Android half of [StorageAccessPolicy]: the two reads, and the
 * Settings page that grants the modern one.
 *
 * The audit found the same logic copied into `MainActivity`'s runtime request,
 * the Settings screen's "Set up storage" row and the terminal's gate — three
 * copies, three chances to drift, and one of them still launched the legacy
 * dialog on Android 13+ where it can never be granted. There is one reader now;
 * [StorageAccessPolicy] still owns every decision (what "granted" means, which
 * switch to name, what the report says), so this file stays a thin adapter.
 */
object StorageAccessAndroid {

    /** The phone's storage facts, read once. */
    fun read(context: Context): StorageFacts = StorageAccessPolicy.facts(
        sdkInt = Build.VERSION.SDK_INT,
        allFiles = allFilesGranted(),
        legacyRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED,
        legacyWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    )

    /**
     * The API-30 call, behind an early-return guard: `Build.VERSION_CODES.R` is
     * [StorageApi.ALL_FILES_FROM], and below it there is no such switch to read.
     */
    private fun allFilesGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return Environment.isExternalStorageManager()
    }

    /** True when this phone holds whichever storage route it has. */
    fun granted(context: Context): Boolean = read(context).granted

    /**
     * Opens this app's own all-files page, with the global list as a fallback for
     * a launcher that does not answer the per-app intent.
     */
    fun openAllFilesSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {
                // Nothing left to try; the caller's UI already told the user where
                // the switch is (StorageAccessPolicy.fixSteps).
            }
        }
    }
}
