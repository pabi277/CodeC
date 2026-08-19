package com.example.ui.utils

import android.content.Context
import android.os.Environment
import java.io.File

data class FileInfo(
    val name: String,
    val size: Long,
    val lastModified: Long,
    val absolutePath: String
)

class FileManager(private val context: Context) {

    /**
     * Helper to get the correct directory with scoped storage fallback.
     * Attempts to use the public CodeC/projects directory. If inaccessible 
     * (e.g. Android 10+ scoped storage restrictions without MANAGE_EXTERNAL_STORAGE), 
     * it falls back to the app-specific external storage.
     */
    private fun getProjectDir(): File {
        var dir = File(Environment.getExternalStorageDirectory(), "CodeC/projects")
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback for Android 10+ if public directory is not writable
        if (!dir.exists() || !dir.canWrite()) {
            dir = File(context.getExternalFilesDir(null), "CodeC/projects")
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
        return dir
    }

    /**
     * Creates the CodeC/projects directory if missing
     */
    fun createDirectory(): Boolean {
        val dir = getProjectDir()
        return dir.exists() && dir.canWrite()
    }

    /**
     * Creates file if doesn't exist, overwrites if exists
     */
    fun saveFile(fileName: String, content: String): Boolean {
        return try {
            val dir = getProjectDir()
            val file = File(dir, fileName)
            file.writeText(content)
            AppLogger.d("FileManager", "Saved file: $fileName (${content.length} chars)")
            true
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Failed to save file: $fileName", e)
            false
        }
    }

    /**
     * Reads file content, returns null if error
     */
    fun loadFile(fileName: String): String? {
        return try {
            val dir = getProjectDir()
            val file = File(dir, fileName)
            if (file.exists() && file.canRead()) {
                AppLogger.d("FileManager", "Loaded file: $fileName")
                file.readText()
            } else {
                AppLogger.e("FileManager", "Cannot read file: $fileName")
                null
            }
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Failed to load file: $fileName", e)
            null
        }
    }

    /**
     * Deletes the specified file
     */
    fun deleteFile(fileName: String): Boolean {
        return try {
            val dir = getProjectDir()
            val file = File(dir, fileName)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Renames a file
     */
    fun renameFile(oldName: String, newName: String): Boolean {
        return try {
            val dir = getProjectDir()
            val oldFile = File(dir, oldName)
            val newFile = File(dir, newName)
            if (oldFile.exists() && !newFile.exists()) {
                oldFile.renameTo(newFile)
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Lists all .c files in the project directory
     */
    fun listFiles(): List<FileInfo> {
        return try {
            val dir = getProjectDir()
            val files = dir.listFiles() ?: return emptyList()
            files.filter { it.isFile && it.name.endsWith(".c") }
                .map { FileInfo(it.name, it.length(), it.lastModified(), it.absolutePath) }
                .sortedByDescending { it.lastModified }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
