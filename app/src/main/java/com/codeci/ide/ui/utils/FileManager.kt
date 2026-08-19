package com.codeci.ide.ui.utils

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

    fun getProjectDir(): File {
        var dir = File(Environment.getExternalStorageDirectory(), "CodeC/projects")
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Could not create public projects dir", e)
        }

        if (!dir.exists() || !dir.canWrite()) {
            dir = File(context.getExternalFilesDir(null), "CodeC/projects")
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
        return dir
    }

    fun createDirectory(): Boolean {
        val dir = getProjectDir()
        return dir.exists() && dir.canWrite()
    }

    fun saveFile(fileName: String, content: String): Boolean {
        return try {
            val file = FileNameUtils.resolveSafeFile(getProjectDir(), fileName) ?: return false
            file.writeText(content)
            AppLogger.d("FileManager", "Saved file: ${file.name} (${content.length} chars)")
            true
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Failed to save file: $fileName", e)
            false
        }
    }

    fun loadFile(fileName: String): String? {
        return try {
            val file = FileNameUtils.resolveSafeFile(getProjectDir(), fileName) ?: return null
            if (file.exists() && file.canRead()) {
                AppLogger.d("FileManager", "Loaded file: ${file.name}")
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

    fun deleteFile(fileName: String): Boolean {
        return try {
            val file = FileNameUtils.resolveSafeFile(getProjectDir(), fileName) ?: return false
            file.exists() && file.delete()
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Failed to delete file: $fileName", e)
            false
        }
    }

    fun renameFile(oldName: String, newName: String): Boolean {
        return try {
            val dir = getProjectDir()
            val oldFile = FileNameUtils.resolveSafeFile(dir, oldName) ?: return false
            val newFile = FileNameUtils.resolveSafeFile(dir, newName) ?: return false
            if (oldFile.exists() && !newFile.exists()) {
                oldFile.renameTo(newFile)
            } else {
                false
            }
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Failed to rename $oldName to $newName", e)
            false
        }
    }

    fun listFiles(): List<FileInfo> {
        return try {
            val dir = getProjectDir()
            val files = dir.listFiles() ?: return emptyList()
            files.filter { it.isFile && it.name.endsWith(".c") }
                .map { FileInfo(it.name, it.length(), it.lastModified(), it.absolutePath) }
                .sortedByDescending { it.lastModified }
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Failed to list files", e)
            emptyList()
        }
    }
}
