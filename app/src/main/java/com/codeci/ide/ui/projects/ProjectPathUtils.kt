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

    fun sanitizeProjectName(name: String): String? = sanitizeSegment(name)

    fun sanitizeSegment(name: String): String? {
        val value = name.trim()
        return value.takeIf {
            it.isNotEmpty() &&
                it != "." &&
                it != ".." &&
                !it.contains('/') &&
                !it.contains('\\') &&
                !it.contains('\u0000') &&
                safeSegment.matches(it)
        }
    }

    /** Returns a safe relative path, or null for absolute/traversal paths. */
    fun sanitizeRelativePath(path: String): String? {
        val normalised = path.trim().replace('\\', '/')
        if (normalised.isEmpty()) return ""
        if (normalised.startsWith('/') || normalised.startsWith("~")) return null
        val parts = normalised.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        if (parts.any { sanitizeSegment(it) == null }) return null
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
