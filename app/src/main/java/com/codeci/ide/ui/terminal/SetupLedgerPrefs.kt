package com.codeci.ide.ui.terminal

import android.content.Context
import android.content.SharedPreferences

/**
 * Phase 44.2 — the Android edge for [SetupLedger]: three cells in a
 * SharedPreferences, written with **`commit()`, not `apply()`**, because the
 * marker has to be durable BEFORE the risky step it describes (the same reason
 * `MainActivity`'s crash-loop ledger commits — a kill between the two renames
 * of `swapPrefix` is exactly the event the marker exists to survive).
 *
 * Not DataStore: DataStore needs a coroutine and cannot be read synchronously
 * in the first instructions of a process, and the boot repair runs on a plain
 * daemon thread before any Compose exists.
 *
 * Kept in its own file so [SetupLedger] stays Android-free and host-testable.
 */
object SetupLedgerPrefs {

    const val PREFS_NAME = "codec-setup"
    const val KEY_PHASE = "phase"
    const val KEY_RELEASE = "release"
    const val KEY_STARTED_AT = "startedAt"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun ledger(context: Context): SetupLedger = SetupLedger(store(prefs(context)))

    fun store(prefs: SharedPreferences): SetupLedger.Store = object : SetupLedger.Store {
        override fun readPhase(): String? = prefs.getString(KEY_PHASE, null)
        override fun readRelease(): String? = prefs.getString(KEY_RELEASE, null)
        override fun readStartedAt(): Long = prefs.getLong(KEY_STARTED_AT, 0L)

        override fun write(phase: String, release: String, startedAt: Long) {
            prefs.edit()
                .putString(KEY_PHASE, phase)
                .putString(KEY_RELEASE, release)
                .putLong(KEY_STARTED_AT, startedAt)
                .commit()
        }

        override fun clear() {
            prefs.edit().clear().commit()
        }
    }
}
