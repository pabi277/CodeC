package com.codeci.ide.ui.editor

import com.codeci.ide.ui.viewmodels.EditorFileEntry

/**
 * Phase 16 — collapse state for the drawer tree, computed from the flat
 * entry list the ViewModel publishes. Kept pure (and host-tested) so the
 * drawer can keep using its one-pass full-tree scan: an entry is visible
 * when none of its ancestor directories is in the collapsed set.
 */
object FileTreeCollapse {

    /** A collapsed folder row stays visible (tappable); only its contents hide. */
    fun visible(entries: List<EditorFileEntry>, collapsed: Set<String>): List<EditorFileEntry> =
        if (collapsed.isEmpty()) entries else entries.filter { entry ->
            collapsed.none { dir ->
                dir != entry.relativePath && entry.relativePath.startsWith("$dir/")
            }
        }

    /** Every directory path in [entries] — what "Collapse all" stores. */
    fun allDirs(entries: List<EditorFileEntry>): Set<String> =
        entries.filter { it.isDirectory }.map { it.relativePath }.toSet()
}
