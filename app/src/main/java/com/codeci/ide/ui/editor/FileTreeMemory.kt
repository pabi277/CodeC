package com.codeci.ide.ui.editor

import android.content.Context

/**
 * Phase 69.4 — the Files tree remembers its shape, PER PROJECT, on the device
 * (`codec_file_tree`), the same way `EditorLaunchState` remembers "open where
 * I left off".
 *
 * Owner (2026-09-28, verbatim): *"When i import a zip or repository and open
 * in editor it will in collapse state and remember what open by the use when
 * leaving and again open the editor and the project in the same position no
 * all expend or collapse"* — the folders the user opened stay open, the ones
 * they closed stay closed, and the tree comes back in exactly that position
 * after leaving the editor, switching projects or closing the app.
 *
 * The shape itself is decided in [FileTreeCollapse] (pure, host-tested):
 * this object only stores and reads a string, and every read/write is wrapped
 * so a storage failure can never take the editor down. A project that has
 * never been remembered reads back as `null` — which is what tells the tree
 * it is being opened for the first time (everything collapsed).
 */
object FileTreeMemory {

    private const val PREFS = "codec_file_tree"
    private const val PREFIX = "collapsed\u001F"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Remember [collapsed] for [projectName] (the set of CLOSED folders). */
    fun save(context: Context, projectName: String, collapsed: Set<String>) {
        runCatching {
            prefs(context).edit().putString(PREFIX + projectName, FileTreeCollapse.encode(collapsed)).apply()
        }
    }

    /**
     * The remembered closed folders for [projectName], or `null` when this
     * project's tree has never been written (its first open).
     */
    fun load(context: Context, projectName: String): Set<String>? = runCatching {
        FileTreeCollapse.decode(prefs(context).getString(PREFIX + projectName, null))
    }.getOrNull()

    /**
     * A deleted project takes its tree state with it — otherwise the store
     * grows by one entry for every project the user ever opened.
     */
    fun forget(context: Context, projectName: String) {
        runCatching { prefs(context).edit().remove(PREFIX + projectName).apply() }
    }
}
