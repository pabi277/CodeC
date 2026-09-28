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

    /** Filename/path filtering keeps matching rows and their ancestors, even below collapsed folders. */
    fun search(entries: List<EditorFileEntry>, query: String): List<EditorFileEntry> {
        val needle = query.trim()
        if (needle.isEmpty()) return entries
        val paths = mutableSetOf<String>()
        entries.filter { it.relativePath.contains(needle, ignoreCase = true) }.forEach { entry ->
            var path = entry.relativePath
            while (path.isNotEmpty()) {
                paths += path
                path = path.substringBeforeLast('/', "")
            }
        }
        return entries.filter { it.relativePath in paths }
    }

    /** Every directory path in [entries] — what "Collapse all" stores. */
    fun allDirs(entries: List<EditorFileEntry>): Set<String> =
        entries.filter { it.isDirectory }.map { it.relativePath }.toSet()

    // ---- Phase 69.4 — the tree remembers its shape, per project ----------

    /** Paths are joined on a control character no filename carries. */
    private const val SEP = "\u001F"

    /**
     * [collapsed] as one string, sorted so the same tree always writes the
     * same bytes (and a diff in storage means a real change).
     */
    fun encode(collapsed: Set<String>): String = collapsed.sorted().joinToString(SEP)

    /**
     * The inverse of [encode]: `null` means the project's tree has NEVER been
     * remembered (its first open), which is deliberately different from an
     * empty set — an empty set is "the user opened every folder".
     */
    fun decode(raw: String?): Set<String>? {
        if (raw == null) return null
        return raw.split(SEP).filterTo(linkedSetOf()) { it.isNotBlank() }
    }

    /**
     * What the tree looks like when the project's session starts: the
     * remembered set when there is one, otherwise **every folder closed**
     * (owner, 2026-09-28: *"...open in editor it will in collapse state"* —
     * a freshly imported ZIP or clone is a wall of folders on a phone, and
     * today it opens with all of them expanded).
     */
    fun initialTree(allDirs: Set<String>, remembered: Set<String>?): Set<String> = remembered ?: allDirs

    /**
     * Drop folders that are gone (renamed, moved, deleted since). Callers must
     * only ask this with a KNOWN directory list — pruning against an empty one
     * would throw away everything the user remembers.
     */
    fun prune(collapsed: Set<String>, existingDirs: Set<String>): Set<String> =
        collapsed.filterTo(linkedSetOf()) { it in existingDirs }
}
