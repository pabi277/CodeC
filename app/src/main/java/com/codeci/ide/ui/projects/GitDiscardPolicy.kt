package com.codeci.ide.ui.projects

/** The owner chose unstaged-only restore, not a reset to HEAD or file deletion. */
object GitDiscardPolicy {
    fun canDiscard(change: GitFileChange): Boolean =
        !change.isConflict && change.oldPath == null &&
            change.x in listOf(' ', 'M') && change.y in listOf('M', 'D') &&
            safePath(change.path) != null

    fun safePath(path: String): String? {
        // Never normalise a name into a different file for a destructive action.
        if ('\\' in path || path.isEmpty() || "***" in path) return null
        val safe = ProjectPathUtils.sanitizeRelativePath(path) ?: return null
        if (safe != path || safe.split('/').any { it.equals(".git", ignoreCase = true) }) return null
        return safe
    }
}
