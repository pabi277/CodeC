package com.codeci.ide.ui.editor

import com.codeci.ide.ui.projects.ProjectPathUtils
import java.io.File

/** New-file input is a relative path, never an absolute path or a traversal. */
object NewFilePath {
    fun resolve(parent: String?, input: String): String? {
        val value = input.trim()
        if (value.isEmpty() || value.contains('\\')) return null
        val segments = value.split('/')
        if (segments.any { ProjectPathUtils.sanitizeSegment(it) != it }) return null
        val prefix = parent?.takeIf { it.isNotEmpty() }
        return ProjectPathUtils.sanitizeRelativePath(if (prefix == null) value else "$prefix/$value")
    }

    /** Exclusive creation: never truncate an existing file, even if it appears after validation. */
    fun create(root: File, parent: String?, input: String): Result<String> = runCatching {
        val relative = resolve(parent, input) ?: error("Enter a relative file path, for example css/subjects.css")
        require(root.isDirectory) { "The destination folder is no longer available" }
        val target = ProjectPathUtils.resolveInside(root, relative) ?: error("Invalid file path")
        require(!target.exists()) { "A file or folder with that name already exists" }
        val directory = target.parentFile ?: error("Invalid folder path")
        require(directory.isDirectory || directory.mkdirs()) { "Could not create the parent folders" }
        // Recheck containment after creating parents (also rejects escaping symlinks).
        val checked = ProjectPathUtils.resolveInside(root, relative) ?: error("Invalid file path")
        require(checked.createNewFile()) { "A file or folder with that name already exists" }
        relative
    }
}
