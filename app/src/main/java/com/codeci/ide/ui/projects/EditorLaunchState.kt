package com.codeci.ide.ui.projects

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * "Open where I left off" (2026-08-31): remembers the project + file the
 * user last had open in the editor so the app can launch straight into that
 * file instead of a dashboard. Written on every successful project-file open
 * (EditorViewModel.openProjectFile) and read once at app start (MainApp's
 * start destination). Stale entries (project or file deleted since) fall
 * back to the Projects hub.
 */
object EditorLaunchState {

    private const val PREFS = "codec_editor_launch"
    private const val KEY_PROJECT = "project"
    private const val KEY_FILE = "file"
    /** Added in Phase 52; missing on older installs means "ask visibly". */
    private const val KEY_LAST_OPENED_AT = "last_opened_at"

    data class State(val projectName: String, val fileName: String)

    fun save(
        context: Context,
        projectName: String,
        fileName: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROJECT, projectName)
                .putString(KEY_FILE, fileName)
                .putLong(KEY_LAST_OPENED_AT, nowMillis)
                .apply()
        }
    }

    /** Timestamp paired with the launch state, or null for a pre-52 install. */
    fun lastOpenedAt(context: Context): Long? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAST_OPENED_AT)) return null
        return prefs.getLong(KEY_LAST_OPENED_AT, 0L).takeIf { it >= 0L }
    }

    /** Whole minutes since the saved file was opened; clock skew clamps to zero. */
    fun minutesSinceLastOpen(context: Context, nowMillis: Long = System.currentTimeMillis()): Long? {
        val saved = lastOpenedAt(context) ?: return null
        val elapsed = (nowMillis - saved).coerceAtLeast(0L)
        // Round up only after a full minute has begun: 5:00 is still inside
        // the five-minute window, while 5:01 is visibly old enough to ask.
        return if (elapsed == 0L) 0L
        else TimeUnit.MILLISECONDS.toMinutes(elapsed - 1L) + 1L
    }

    /** Last launch state, or null when stale (project/file gone) or unset. */
    fun load(context: Context): State? {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val project = p.getString(KEY_PROJECT, null) ?: return null
        val file = p.getString(KEY_FILE, null) ?: return null
        val info = runCatching { ProjectManager(context.applicationContext).project(project) }.getOrNull()
            ?: return null
        val target = runCatching { ProjectPathUtils.resolveInside(info.root, file) }.getOrNull()
            ?: return null
        return if (target.isFile) State(info.name, file) else null
    }
}
