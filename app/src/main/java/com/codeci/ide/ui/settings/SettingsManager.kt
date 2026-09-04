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
        val TERMINAL_FONT_SIZE = floatPreferencesKey("terminal_font_size")
        val TERMINAL_FONT_FAMILY = stringPreferencesKey("terminal_font_family")
        val TERMINAL_EXTRA_KEYS_MACROS = stringPreferencesKey("terminal_extra_keys_macros")

        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val RECENT_FILES_CSV = stringPreferencesKey("recent_files_csv")

        val DEV_MODE = booleanPreferencesKey("dev_mode")
        val SHOW_FILE_PATHS = booleanPreferencesKey("show_file_paths")
        val EDITOR_CUSTOM_SNIPPETS = stringPreferencesKey("editor_custom_snippets")

        // Phase 26.1 — user-editable key strip (JSON of EditorKeyDef list).
        val EDITOR_KEY_STRIP_JSON = stringPreferencesKey("editor_key_strip_json")

        // Phase 26.2 — Smart typing per-rule toggles (all ON by default except python colon rule handled in logic).
        val SMART_TYPING_TYPE_OVER = booleanPreferencesKey("smart_typing_type_over")
        val SMART_TYPING_WRAP_SELECTION = booleanPreferencesKey("smart_typing_wrap_selection")
        val SMART_TYPING_EMPTY_PAIR = booleanPreferencesKey("smart_typing_empty_pair")
        val SMART_TYPING_AUTO_INDENT = booleanPreferencesKey("smart_typing_auto_indent")
        val SMART_TYPING_STRING_AWARE = booleanPreferencesKey("smart_typing_string_aware")
        val SMART_TYPING_DELETE_WORD = booleanPreferencesKey("smart_typing_delete_word")

        // Phase 26.3 — IME guide dismissed flag (optional).
        val IME_GUIDE_DISMISSED = booleanPreferencesKey("ime_guide_dismissed")
    }

    /**
     * Phase 16 — the custom-snippet row of the editor keys (Spck "Custom
     * Snippets"), stored as `label=text` lines. The data model + rendering
     * ship now; the editing UI in Settings is a recorded follow-up.
     */
    val editorCustomSnippetsFlow: Flow<String> = context.dataStore.data.map { it[EDITOR_CUSTOM_SNIPPETS] ?: "" }
    suspend fun setEditorCustomSnippets(raw: String) {
        context.dataStore.edit { it[EDITOR_CUSTOM_SNIPPETS] = raw }
    }

    // Phase 26.1 — key strip JSON.
    val editorKeyStripJsonFlow: Flow<String> = context.dataStore.data.map { it[EDITOR_KEY_STRIP_JSON] ?: "" }
    suspend fun setEditorKeyStripJson(json: String) {
        context.dataStore.edit { it[EDITOR_KEY_STRIP_JSON] = json }
    }

    // Phase 26.2 — smart typing toggles.
    val smartTypingTypeOverFlow: Flow<Boolean> = context.dataStore.data.map { it[SMART_TYPING_TYPE_OVER] ?: true }
    val smartTypingWrapSelectionFlow: Flow<Boolean> = context.dataStore.data.map { it[SMART_TYPING_WRAP_SELECTION] ?: true }
    val smartTypingEmptyPairFlow: Flow<Boolean> = context.dataStore.data.map { it[SMART_TYPING_EMPTY_PAIR] ?: true }
    val smartTypingAutoIndentFlow: Flow<Boolean> = context.dataStore.data.map { it[SMART_TYPING_AUTO_INDENT] ?: true }
    val smartTypingStringAwareFlow: Flow<Boolean> = context.dataStore.data.map { it[SMART_TYPING_STRING_AWARE] ?: true }
    val smartTypingDeleteWordFlow: Flow<Boolean> = context.dataStore.data.map { it[SMART_TYPING_DELETE_WORD] ?: true }
    suspend fun setSmartTypingTypeOver(v: Boolean) { context.dataStore.edit { it[SMART_TYPING_TYPE_OVER] = v } }
    suspend fun setSmartTypingWrapSelection(v: Boolean) { context.dataStore.edit { it[SMART_TYPING_WRAP_SELECTION] = v } }
    suspend fun setSmartTypingEmptyPair(v: Boolean) { context.dataStore.edit { it[SMART_TYPING_EMPTY_PAIR] = v } }
    suspend fun setSmartTypingAutoIndent(v: Boolean) { context.dataStore.edit { it[SMART_TYPING_AUTO_INDENT] = v } }
    suspend fun setSmartTypingStringAware(v: Boolean) { context.dataStore.edit { it[SMART_TYPING_STRING_AWARE] = v } }
    suspend fun setSmartTypingDeleteWord(v: Boolean) { context.dataStore.edit { it[SMART_TYPING_DELETE_WORD] = v } }

    // Phase 26.3
    val imeGuideDismissedFlow: Flow<Boolean> = context.dataStore.data.map { it[IME_GUIDE_DISMISSED] ?: false }
    suspend fun setImeGuideDismissed(v: Boolean) { context.dataStore.edit { it[IME_GUIDE_DISMISSED] = v } }

    val fontSizeFlow: Flow<Float> = context.dataStore.data.map { it[FONT_SIZE] ?: 14f }
    val fontFamilyFlow: Flow<String> = context.dataStore.data.map { it[FONT_FAMILY] ?: "Monospace" }
    val tabSizeFlow: Flow<Int> = context.dataStore.data.map { it[TAB_SIZE] ?: 4 }
    val lineNumbersFlow: Flow<Boolean> = context.dataStore.data.map { it[LINE_NUMBERS] ?: true }
    val autoIndentFlow: Flow<Boolean> = context.dataStore.data.map { it[AUTO_INDENT] ?: true }
    val wordWrapFlow: Flow<Boolean> = context.dataStore.data.map { it[WORD_WRAP] ?: false }

    val cStandardFlow: Flow<String> = context.dataStore.data.map { it[C_STANDARD] ?: "C11" }
    val warningLevelFlow: Flow<String> = context.dataStore.data.map { it[WARNING_LEVEL] ?: "Standard" }
    val optimizationLevelFlow: Flow<String> = context.dataStore.data.map { it[OPTIMIZATION_LEVEL] ?: "O0" }
    val terminalFontSizeFlow: Flow<Float> = context.dataStore.data.map {
        // Phase 19.2 device round 2: 14sp gave 60x32 on the owner's phone
        // where Termux fits 71x39 — 12sp lands on ~70x37 (Termux density).
        it[TERMINAL_FONT_SIZE] ?: 12f
    }
    val terminalFontFamilyFlow: Flow<String> = context.dataStore.data.map {
        // Bundled JetBrains Mono Medium (OFL) — stock Droid Sans Mono looks
        // light and wide-tracked next to Termux's custom font.
        it[TERMINAL_FONT_FAMILY] ?: "JetBrains Mono"
    }
    val terminalExtraKeysMacrosFlow: Flow<String> = context.dataStore.data.map {
        it[TERMINAL_EXTRA_KEYS_MACROS] ?: ""
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
    suspend fun setTerminalFontSize(size: Float) {
        context.dataStore.edit { it[TERMINAL_FONT_SIZE] = size.coerceIn(8f, 32f) }
    }
    suspend fun setTerminalFontFamily(family: String) { context.dataStore.edit { it[TERMINAL_FONT_FAMILY] = family } }
    suspend fun setTerminalExtraKeysMacros(macros: String) {
        context.dataStore.edit { it[TERMINAL_EXTRA_KEYS_MACROS] = macros }
    }

    suspend fun setAccentColor(colorHex: String) { context.dataStore.edit { it[ACCENT_COLOR] = colorHex } }
}
