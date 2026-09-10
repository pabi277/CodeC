package com.codeci.ide.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.codeci.ide.ui.theme.dataStore
import kotlinx.coroutines.flow.Flow
import com.codeci.ide.ui.theme.AccentPalette
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

        val DEV_MODE = booleanPreferencesKey("dev_mode")
        val SHOW_FILE_PATHS = booleanPreferencesKey("show_file_paths")
        val EDITOR_CUSTOM_SNIPPETS = stringPreferencesKey("editor_custom_snippets")

        // Phase 26.1 — user-editable key strip (JSON of EditorKeyDef list).
        val EDITOR_KEY_STRIP_JSON = stringPreferencesKey("editor_key_strip_json")

        // Phase 26.2 — Smart typing per-rule toggles (all ON by default
        // except python colon rule handled in logic). Phase 38.2 audit:
        // smart_typing_delete_word was deleted — nothing ever read it
        // (⌫ flick-up deletes a word — always on by design, so no
        // stored toggle exists for it).
        val SMART_TYPING_TYPE_OVER = booleanPreferencesKey("smart_typing_type_over")
        val SMART_TYPING_WRAP_SELECTION = booleanPreferencesKey("smart_typing_wrap_selection")
        val SMART_TYPING_EMPTY_PAIR = booleanPreferencesKey("smart_typing_empty_pair")
        val SMART_TYPING_AUTO_INDENT = booleanPreferencesKey("smart_typing_auto_indent")
        val SMART_TYPING_STRING_AWARE = booleanPreferencesKey("smart_typing_string_aware")

        // Phase 26.3 — IME guide dismissed flag (optional).
        val IME_GUIDE_DISMISSED = booleanPreferencesKey("ime_guide_dismissed")

        // Phase 28.2 — CodeC Keys (the dedicated code keyboard). Master is
        // ON by default (owner device round 2: "make the keyboard default,
        // user can off it") — haptics + row height are feel knobs; the JSON
        // is the dev-build layout override (spec §1.1 "edit the JSON → edit
        // the keyboard" — dev builds only).
        val CODEC_KEYS_ENABLED = booleanPreferencesKey("codec_keys_enabled")
        // Phase 35.1 — keep the dedicated code keyboard mounted while the
        // editor session is focused. The toolbar collapse remains an explicit
        // per-session override; interactive stdin still hands the IME back.
        val EDITOR_KEEP_KEYS_OPEN = booleanPreferencesKey("editor.keep_keys_open")
        val CODEC_KEYS_HAPTICS = booleanPreferencesKey("codec_keys_haptics")
        val CODEC_KEYS_HEIGHT = floatPreferencesKey("codec_keys_height")
        val CODEC_KEYS_LAYOUT_JSON = stringPreferencesKey("codec_keys_layout_json")

        // Phase 27.3 — completion law settings. Master off = feature GONE.
        val COMPLETION_MASTER = booleanPreferencesKey("completion_master")
        val COMPLETION_GHOST = booleanPreferencesKey("completion_ghost")
        val COMPLETION_STRIP = booleanPreferencesKey("completion_strip")
        val COMPLETION_PANEL = booleanPreferencesKey("completion_panel")
        val COMPLETION_DEBOUNCE_MS = intPreferencesKey("completion_debounce_ms")

        // Phase 33.1 — first-run welcome (three starter tiles). false = the
        // welcome has not been dismissed yet (fresh install); the app sets it
        // true when the user picks a starter, and Settings can clear it again
        // ("show welcome again") so testers re-trigger the first-run flow.
        val FIRST_LAUNCH_COMPLETE = booleanPreferencesKey("first_launch_complete")

        // Phase 41 follow-up (round 1) — the exit survey prompt
        // (owner-requested for the testing phase; see ui/support/ExitSurvey).
        // Default ON. This is a UI preference about a dialog, NOT attachment
        // consent — the attachment checkboxes stay never-persisted
        // (FeedbackCheckboxNotPersistedTest bans attachment-shaped keys).
        // Round 2 note: this is the ONLY feedback key left in any store —
        // the developer's number/email are hardcoded (DeveloperContact),
        // not settings.
        val FEEDBACK_EXIT_PROMPT = booleanPreferencesKey("feedback_exit_prompt_enabled")
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
    suspend fun setSmartTypingTypeOver(v: Boolean) { context.dataStore.edit { it[SMART_TYPING_TYPE_OVER] = v } }
    suspend fun setSmartTypingWrapSelection(v: Boolean) { context.dataStore.edit { it[SMART_TYPING_WRAP_SELECTION] = v } }
    suspend fun setSmartTypingEmptyPair(v: Boolean) { context.dataStore.edit { it[SMART_TYPING_EMPTY_PAIR] = v } }
    suspend fun setSmartTypingAutoIndent(v: Boolean) { context.dataStore.edit { it[SMART_TYPING_AUTO_INDENT] = v } }
    suspend fun setSmartTypingStringAware(v: Boolean) { context.dataStore.edit { it[SMART_TYPING_STRING_AWARE] = v } }

    // Phase 26.3
    val imeGuideDismissedFlow: Flow<Boolean> = context.dataStore.data.map { it[IME_GUIDE_DISMISSED] ?: false }
    suspend fun setImeGuideDismissed(v: Boolean) { context.dataStore.edit { it[IME_GUIDE_DISMISSED] = v } }

    // Phase 28.2 — CodeC Keys. DEFAULT ON (owner round 2); turning it off
    // returns the L0 strip (26/27) + the system IME exactly as before.
    val codecKeysEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[CODEC_KEYS_ENABLED] ?: true }
    // New installs keep CodeC Keys open by default. Missing legacy values
    // intentionally resolve to true so an upgrade does not change the editor
    // surface under the user's fingers.
    val editorKeepKeysOpenFlow: Flow<Boolean> = context.dataStore.data.map { it[EDITOR_KEEP_KEYS_OPEN] ?: true }
    val codecKeysHapticsFlow: Flow<Boolean> = context.dataStore.data.map { it[CODEC_KEYS_HAPTICS] ?: true }
    val codecKeysHeightFlow: Flow<Float> = context.dataStore.data.map {
        (it[CODEC_KEYS_HEIGHT] ?: 1f).coerceIn(0.7f, 1.3f)
    }
    val codecKeysLayoutJsonFlow: Flow<String> = context.dataStore.data.map { it[CODEC_KEYS_LAYOUT_JSON] ?: "" }
    suspend fun setCodecKeysEnabled(v: Boolean) { context.dataStore.edit { it[CODEC_KEYS_ENABLED] = v } }
    suspend fun setEditorKeepKeysOpen(v: Boolean) { context.dataStore.edit { it[EDITOR_KEEP_KEYS_OPEN] = v } }
    suspend fun setCodecKeysHaptics(v: Boolean) { context.dataStore.edit { it[CODEC_KEYS_HAPTICS] = v } }
    suspend fun setCodecKeysHeight(v: Float) { context.dataStore.edit { it[CODEC_KEYS_HEIGHT] = v.coerceIn(0.7f, 1.3f) } }
    suspend fun setCodecKeysLayoutJson(v: String) { context.dataStore.edit { it[CODEC_KEYS_LAYOUT_JSON] = v } }

    // Phase 27.3 — autocomplete surfaces. Defaults: ghost ON, strip ON,
    // panel on-demand (⌄ more), 120 ms beat.
    val completionMasterFlow: Flow<Boolean> = context.dataStore.data.map { it[COMPLETION_MASTER] ?: true }
    val completionGhostFlow: Flow<Boolean> = context.dataStore.data.map { it[COMPLETION_GHOST] ?: true }
    val completionStripFlow: Flow<Boolean> = context.dataStore.data.map { it[COMPLETION_STRIP] ?: true }
    val completionPanelFlow: Flow<Boolean> = context.dataStore.data.map { it[COMPLETION_PANEL] ?: true }
    val completionDebounceMsFlow: Flow<Int> = context.dataStore.data.map { it[COMPLETION_DEBOUNCE_MS] ?: 120 }
    suspend fun setCompletionMaster(v: Boolean) { context.dataStore.edit { it[COMPLETION_MASTER] = v } }
    suspend fun setCompletionGhost(v: Boolean) { context.dataStore.edit { it[COMPLETION_GHOST] = v } }
    suspend fun setCompletionStrip(v: Boolean) { context.dataStore.edit { it[COMPLETION_STRIP] = v } }
    suspend fun setCompletionPanel(v: Boolean) { context.dataStore.edit { it[COMPLETION_PANEL] = v } }
    suspend fun setCompletionDebounceMs(v: Int) { context.dataStore.edit { it[COMPLETION_DEBOUNCE_MS] = v.coerceIn(60, 500) } }

    // Phase 33.1 — first-run welcome flag (default false = welcome not yet seen).
    val firstLaunchCompleteFlow: Flow<Boolean> = context.dataStore.data.map { it[FIRST_LAUNCH_COMPLETE] ?: false }
    suspend fun setFirstLaunchComplete(v: Boolean) { context.dataStore.edit { it[FIRST_LAUNCH_COMPLETE] = v } }

    // Phase 41 follow-up — the exit survey prompt (testing-phase default ON).
    val feedbackExitPromptEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { it[FEEDBACK_EXIT_PROMPT] ?: true }
    suspend fun setFeedbackExitPromptEnabled(v: Boolean) {
        context.dataStore.edit { it[FEEDBACK_EXIT_PROMPT] = v }
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

        // Phase 40.5 — the default is CodeC's own green (AccentPalette.DEFAULT_STORAGE_HEX),
    // not the template violet. A stored value is returned as it is, so an accent the
    // user picked earlier is never rewritten.
    val accentColorFlow: Flow<String> = context.dataStore.data.map {
        AccentPalette.effectiveStoredAccent(it[ACCENT_COLOR])
    }

    val devModeUnlockedFlow: Flow<Boolean> = context.dataStore.data.map { it[DEV_MODE] ?: false }
    val showFilePathsFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_FILE_PATHS] ?: false }

    suspend fun setDevModeUnlocked(unlocked: Boolean) { context.dataStore.edit { it[DEV_MODE] = unlocked } }
    suspend fun setShowFilePaths(show: Boolean) { context.dataStore.edit { it[SHOW_FILE_PATHS] = show } }

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
