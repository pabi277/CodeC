package com.codeci.ide.ui.projects

import android.content.Context

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

    data class State(val projectName: String, val fileName: String)

    fun save(context: Context, projectName: String, fileName: String) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROJECT, projectName)
                .putString(KEY_FILE, fileName)
                .apply()
        }
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
