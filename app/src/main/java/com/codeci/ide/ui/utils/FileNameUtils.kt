package com.codeci.ide.ui.utils

import java.io.File

object FileNameUtils {
    private val allowedName = Regex("^[A-Za-z0-9._-]+$")

    fun sanitizeFileName(fileName: String): String? {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty()) return null
        val withoutTraversal = trimmed
            .replace("\\", "/")
            .split("/")
            .last()
            .replace("..", "")
            .replace("./", "")
        val name = withoutTraversal.trim()
        if (name.isEmpty() || name == "." || !allowedName.matches(name)) {
            return null
        }
        return name
    }

    fun resolveSafeFile(directory: File, fileName: String): File? {
        val safeName = sanitizeFileName(fileName) ?: return null
        val file = File(directory, safeName).canonicalFile
        val dir = directory.canonicalFile
        return if (file.path.startsWith(dir.path + File.separator) || file.path == dir.path) {
            file
        } else {
            null
        }
    }
}
