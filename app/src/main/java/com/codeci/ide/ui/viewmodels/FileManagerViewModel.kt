package com.codeci.ide.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeci.ide.R
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.stats.StatsManager
import com.codeci.ide.ui.utils.FileManager
import com.codeci.ide.ui.utils.FileNameUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileManagerViewModel : ViewModel() {
    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun consumeMessage() {
        _userMessage.value = null
    }

    fun loadFiles(context: Context) {
        viewModelScope.launch {
            val fm = FileManager(context)
            val list = withContext(Dispatchers.IO) {
                fm.listFiles().map { File(it.absolutePath) }
            }
            _files.value = list
        }
    }

    fun createFile(context: Context, name: String): Result<String> {
        val sanitized = FileNameUtils.sanitizeFileName(name)
            ?: return Result.failure(IllegalArgumentException(context.getString(R.string.invalid_file_name)))
        val fileName = if (sanitized.endsWith(".c")) sanitized else "$sanitized.c"
        val fm = FileManager(context)
        return try {
            if (fm.loadFile(fileName) != null) {
                Result.failure(IllegalStateException(context.getString(R.string.file_already_exists)))
            } else if (fm.saveFile(fileName, "#include <stdio.h>\n\nint main() {\n    return 0;\n}\n")) {
                viewModelScope.launch { StatsManager(context).incrementFilesCreated() }
                loadFiles(context)
                Result.success(fileName)
            } else {
                Result.failure(IllegalStateException(context.getString(R.string.create_failed)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteFile(context: Context, file: File): Boolean {
        val safe = FileNameUtils.sanitizeFileName(file.name) ?: return false
        val fm = FileManager(context)
        return try {
            if (fm.deleteFile(safe)) {
                loadFiles(context)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun renameFile(context: Context, oldFile: File, newName: String, onDone: (Boolean, String?) -> Unit) {
        val sanitized = FileNameUtils.sanitizeFileName(newName)
        if (sanitized == null) {
            _userMessage.value = context.getString(R.string.invalid_file_name)
            onDone(false, null)
            return
        }
        val fileName = if (sanitized.endsWith(".c")) sanitized else "$sanitized.c"
        viewModelScope.launch {
            _isBusy.value = true
            val fm = FileManager(context)
            val success = withContext(Dispatchers.IO) { fm.renameFile(oldFile.name, fileName) }
            if (success) {
                SettingsManager(context).addRecentFile(fileName)
                _userMessage.value = context.getString(R.string.rename_success)
                loadFiles(context)
                onDone(true, fileName)
            } else {
                _userMessage.value = context.getString(R.string.rename_failed)
                onDone(false, null)
            }
            _isBusy.value = false
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
