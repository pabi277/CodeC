package com.codeci.ide.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.codeci.ide.ui.theme.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsManager(private val context: Context) {

    companion object {
        val FONT_SIZE = floatPreferencesKey("font_size")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val TAB_SIZE = intPreferencesKey("tab_size")
        val LINE_NUMBERS = booleanPreferencesKey("line_numbers")
        val AUTO_INDENT = booleanPreferencesKey("auto_indent")
        val WORD_WRAP = booleanPreferencesKey("word_wrap")

        val C_STANDARD = stringPreferencesKey("c_standard")
        val WARNING_LEVEL = stringPreferencesKey("warning_level")
        val OPTIMIZATION_LEVEL = stringPreferencesKey("optimization_level")
        val COMPILER_BACKEND = stringPreferencesKey("compiler_backend")
        val TERMINAL_FONT_SIZE = floatPreferencesKey("terminal_font_size")

        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val RECENT_FILES_CSV = stringPreferencesKey("recent_files_csv")

        val DEV_MODE = booleanPreferencesKey("dev_mode")
        val SHOW_FILE_PATHS = booleanPreferencesKey("show_file_paths")
    }

    val fontSizeFlow: Flow<Float> = context.dataStore.data.map { it[FONT_SIZE] ?: 14f }
    val fontFamilyFlow: Flow<String> = context.dataStore.data.map { it[FONT_FAMILY] ?: "Monospace" }
    val tabSizeFlow: Flow<Int> = context.dataStore.data.map { it[TAB_SIZE] ?: 4 }
    val lineNumbersFlow: Flow<Boolean> = context.dataStore.data.map { it[LINE_NUMBERS] ?: true }
    val autoIndentFlow: Flow<Boolean> = context.dataStore.data.map { it[AUTO_INDENT] ?: true }
    val wordWrapFlow: Flow<Boolean> = context.dataStore.data.map { it[WORD_WRAP] ?: false }

    val cStandardFlow: Flow<String> = context.dataStore.data.map { it[C_STANDARD] ?: "C11" }
    val warningLevelFlow: Flow<String> = context.dataStore.data.map { it[WARNING_LEVEL] ?: "Standard" }
    val optimizationLevelFlow: Flow<String> = context.dataStore.data.map { it[OPTIMIZATION_LEVEL] ?: "O0" }
    val compilerBackendFlow: Flow<String> = context.dataStore.data.map {
        it[COMPILER_BACKEND] ?: "auto"
    }
    val terminalFontSizeFlow: Flow<Float> = context.dataStore.data.map {
        it[TERMINAL_FONT_SIZE] ?: 14f
    }

    val accentColorFlow: Flow<String> = context.dataStore.data.map { it[ACCENT_COLOR] ?: "#FF6200EE" }

    val recentFilesOrderedFlow: Flow<List<String>> = context.dataStore.data.map {
        val csv = it[RECENT_FILES_CSV] ?: ""
        if (csv.isEmpty()) emptyList() else csv.split(",")
    }

    val devModeUnlockedFlow: Flow<Boolean> = context.dataStore.data.map { it[DEV_MODE] ?: false }
    val showFilePathsFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_FILE_PATHS] ?: false }

    suspend fun setDevModeUnlocked(unlocked: Boolean) { context.dataStore.edit { it[DEV_MODE] = unlocked } }
    suspend fun setShowFilePaths(show: Boolean) { context.dataStore.edit { it[SHOW_FILE_PATHS] = show } }

    suspend fun addRecentFile(fileName: String) {
        context.dataStore.edit { preferences ->
            val currentCsv = preferences[RECENT_FILES_CSV] ?: ""
            val currentList = if (currentCsv.isEmpty()) emptyList() else currentCsv.split(",")
            val newList = mutableListOf(fileName)
            newList.addAll(currentList.filter { it != fileName })
            preferences[RECENT_FILES_CSV] = newList.take(10).joinToString(",")
        }
    }

    suspend fun replaceRecentFile(oldName: String, newName: String) {
        context.dataStore.edit { preferences ->
            val currentCsv = preferences[RECENT_FILES_CSV] ?: ""
            val currentList = if (currentCsv.isEmpty()) emptyList() else currentCsv.split(",")
            val newList = mutableListOf(newName)
            newList.addAll(currentList.filter { it != oldName && it != newName })
            preferences[RECENT_FILES_CSV] = newList.take(10).joinToString(",")
        }
    }

    suspend fun setFontSize(size: Float) { context.dataStore.edit { it[FONT_SIZE] = size } }
    suspend fun setFontFamily(family: String) { context.dataStore.edit { it[FONT_FAMILY] = family } }
    suspend fun setTabSize(size: Int) { context.dataStore.edit { it[TAB_SIZE] = size } }
    suspend fun setLineNumbers(enabled: Boolean) { context.dataStore.edit { it[LINE_NUMBERS] = enabled } }
    suspend fun setAutoIndent(enabled: Boolean) { context.dataStore.edit { it[AUTO_INDENT] = enabled } }
    suspend fun setWordWrap(enabled: Boolean) { context.dataStore.edit { it[WORD_WRAP] = enabled } }

    suspend fun setCStandard(standard: String) { context.dataStore.edit { it[C_STANDARD] = standard } }
    suspend fun setWarningLevel(level: String) { context.dataStore.edit { it[WARNING_LEVEL] = level } }
    suspend fun setOptimizationLevel(level: String) { context.dataStore.edit { it[OPTIMIZATION_LEVEL] = level } }
    suspend fun setCompilerBackend(backend: String) { context.dataStore.edit { it[COMPILER_BACKEND] = backend } }
    suspend fun setTerminalFontSize(size: Float) {
        context.dataStore.edit { it[TERMINAL_FONT_SIZE] = size.coerceIn(8f, 32f) }
    }

    suspend fun setAccentColor(colorHex: String) { context.dataStore.edit { it[ACCENT_COLOR] = colorHex } }
}
