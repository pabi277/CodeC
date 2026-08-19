package com.example.ui.viewmodels

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManagerViewModel : ViewModel() {
    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private fun getProjectDir(context: Context): File {
        // Fallback to app-specific external storage if the public directory is inaccessible
        // due to strict scoped storage constraints (API 30+)
        var dir = File(Environment.getExternalStorageDirectory(), "CodeC/projects")
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (!dir.exists() || !dir.canWrite()) {
            dir = File(context.getExternalFilesDir(null), "CodeC/projects")
            if (!dir.exists()) dir.mkdirs()
        }
        return dir
    }

    fun loadFiles(context: Context) {
        val dir = getProjectDir(context)
        val fileList = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".c") }?.toList() ?: emptyList()
        _files.value = fileList.sortedByDescending { it.lastModified() }
    }

    fun createFile(context: Context, name: String): Result<Boolean> {
        val dir = getProjectDir(context)
        val fileName = if (name.endsWith(".c")) name else "$name.c"
        val newFile = File(dir, fileName)
        return try {
            if (newFile.exists()) {
                Result.failure(Exception("File already exists"))
            } else if (newFile.createNewFile()) {
                loadFiles(context)
                Result.success(true)
            } else {
                Result.failure(Exception("Could not create file"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun deleteFile(context: Context, file: File): Boolean {
        return try {
            if (file.delete()) {
                loadFiles(context)
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun renameFile(context: Context, oldFile: File, newName: String): Boolean {
        val fileName = if (newName.endsWith(".c")) newName else "$newName.c"
        val newFile = File(oldFile.parent, fileName)
        return try {
            if (oldFile.renameTo(newFile)) {
                loadFiles(context)
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
