package com.codeci.ide.ui.stats

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.codeci.ide.ui.theme.dataStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StatsManager(private val context: Context) {

    companion object {
        val TOTAL_RUNS = intPreferencesKey("stats_total_runs")
        val TOTAL_FILES_CREATED = intPreferencesKey("stats_total_files_created")
        val LAST_RUN_DATE = stringPreferencesKey("stats_last_run_date")
        val CURRENT_STREAK = intPreferencesKey("stats_current_streak")

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun today(): String = dateFormat.format(Date())

        fun yesterday(): String {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            return dateFormat.format(calendar.time)
        }
    }

    val totalRunsFlow: Flow<Int> = context.dataStore.data.map { it[TOTAL_RUNS] ?: 0 }
    val totalFilesCreatedFlow: Flow<Int> = context.dataStore.data.map { it[TOTAL_FILES_CREATED] ?: 0 }
    val lastRunDateFlow: Flow<String?> = context.dataStore.data.map { it[LAST_RUN_DATE] }
    val currentStreakFlow: Flow<Int> = context.dataStore.data.map { it[CURRENT_STREAK] ?: 0 }

    suspend fun incrementRuns() {
        context.dataStore.edit { preferences ->
            val today = today()
            val last = preferences[LAST_RUN_DATE]
            val streak = preferences[CURRENT_STREAK] ?: 0
            preferences[TOTAL_RUNS] = (preferences[TOTAL_RUNS] ?: 0) + 1
            preferences[CURRENT_STREAK] = when {
                last == null -> 1
                last == today -> streak.coerceAtLeast(1)
                last == yesterday() -> streak + 1
                else -> 1
            }
            preferences[LAST_RUN_DATE] = today
        }
    }

    suspend fun incrementFilesCreated() {
        context.dataStore.edit { preferences ->
            preferences[TOTAL_FILES_CREATED] = (preferences[TOTAL_FILES_CREATED] ?: 0) + 1
        }
    }
}
