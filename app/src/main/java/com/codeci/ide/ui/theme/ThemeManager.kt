package com.codeci.ide.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ThemeManager(private val context: Context) {

    companion object {
        val APP_THEME_KEY = stringPreferencesKey("app_theme")
        val EDITOR_THEME_KEY = stringPreferencesKey("editor_theme")
        val TERMINAL_THEME_KEY = stringPreferencesKey("terminal_theme")
    }

    val appThemeFlow: Flow<AppThemeMode> = context.dataStore.data.map { preferences ->
        val themeString = preferences[APP_THEME_KEY] ?: AppThemeMode.SYSTEM.name
        try {
            AppThemeMode.valueOf(themeString)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    val editorThemeFlow: Flow<EditorThemeType> = context.dataStore.data.map { preferences ->
        val themeString = preferences[EDITOR_THEME_KEY] ?: EditorThemeType.DRACULA.name
        try {
            EditorThemeType.valueOf(themeString)
        } catch (e: Exception) {
            EditorThemeType.DRACULA
        }
    }

    val terminalThemeFlow: Flow<TerminalThemeType> = context.dataStore.data.map { preferences ->
        val themeString = preferences[TERMINAL_THEME_KEY] ?: TerminalThemeType.DRACULA.name
        try {
            TerminalThemeType.valueOf(themeString)
        } catch (e: Exception) {
            TerminalThemeType.DRACULA
        }
    }

    suspend fun setAppTheme(theme: AppThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME_KEY] = theme.name
        }
    }

    suspend fun setEditorTheme(theme: EditorThemeType) {
        context.dataStore.edit { preferences ->
            preferences[EDITOR_THEME_KEY] = theme.name
        }
    }

    suspend fun setTerminalTheme(theme: TerminalThemeType) {
        context.dataStore.edit { preferences ->
            preferences[TERMINAL_THEME_KEY] = theme.name
        }
    }
}
