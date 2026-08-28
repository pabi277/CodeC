package com.codeci.ide.ui.projects

import java.io.File

/**
 * Path validation for project-owned files.
 *
 * Project files are always addressed by a relative POSIX-style path. We reject
 * traversal segments instead of normalising them away, so a typo can never
 * silently select a different file. Canonical-path checks also reject symlink
 * escapes from the app-private project root.
 */
object ProjectPathUtils {
    private val safeSegment = Regex("^[A-Za-z0-9._-]+$")

    /** Project directories may preserve normal archive names such as `My App`. */
    fun sanitizeProjectName(name: String): String? =
        name.trim().takeIf { isSafeSegment(it) && it.isNotBlank() }

    /** Validates names entered through the UI (project/file/folder dialogs). */
    fun sanitizeSegment(name: String): String? {
        val value = name.trim()
        return value.takeIf { isSafeSegment(it) && safeSegment.matches(it) }
    }

    /**
     * ZIPs are user archives, not UI names. Preserve normal spaces, Unicode,
     * brackets, parentheses, and other filesystem-safe characters instead of
     * silently dropping those entries from an imported project.
     */
    fun sanitizeArchiveSegment(name: String): String? =
        name.takeIf { isSafeSegment(it) && it.isNotBlank() }

    private fun isSafeSegment(value: String): Boolean =
        value.isNotEmpty() &&
            value != "." &&
            value != ".." &&
            !value.contains('/') &&
            !value.contains('\\') &&
            !value.contains('\u0000') &&
            value.none { it.isISOControl() }

    /** Returns a safe relative path, or null for absolute/traversal paths. */
    fun sanitizeRelativePath(path: String): String? {
        val normalised = path.replace('\\', '/')
        if (normalised.isEmpty()) return ""
        if (normalised.startsWith('/') || normalised.startsWith("~")) return null
        val parts = normalised.split('/')
        if (parts.any { sanitizeArchiveSegment(it) == null }) return null
        return parts.joinToString("/")
    }

    /**
     * ZIP-relative paths use the same traversal rules but accept every normal
     * filesystem filename. The caller removes a single directory suffix
     * before calling this function.
     */
    fun sanitizeArchiveRelativePath(path: String): String? {
        if (path.isEmpty() || path.startsWith('/') || path.startsWith("~")) return null
        val parts = path.split('/')
        if (parts.any { sanitizeArchiveSegment(it) == null }) return null
        return parts.joinToString("/")
    }

    fun resolveInside(root: File, relativePath: String): File? {
        val safePath = sanitizeRelativePath(relativePath) ?: return null
        return try {
            val canonicalRoot = root.canonicalFile
            val candidate = if (safePath.isEmpty()) {
                canonicalRoot
            } else {
                File(canonicalRoot, safePath).canonicalFile
            }
            if (candidate.path == canonicalRoot.path ||
                candidate.path.startsWith(canonicalRoot.path + File.separator)
            ) candidate else null
        } catch (_: Exception) {
            null
        }
    }

    fun relativePath(root: File, file: File): String? {
        return try {
            val rootPath = root.canonicalFile.path
            val filePath = file.canonicalFile.path
            if (filePath == rootPath) return ""
            if (!filePath.startsWith(rootPath + File.separator)) return null
            sanitizeRelativePath(filePath.removePrefix(rootPath + File.separator))
        } catch (_: Exception) {
            null
        }
    }
}
