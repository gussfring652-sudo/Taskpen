package com.antakih.taskpen.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "taskpen_settings")

@Singleton
class SettingsManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private object Keys {
        val CASE_SENSITIVE_TAGS = booleanPreferencesKey("case_sensitive_tags")
        val MORNING_SUMMARY_HOUR = intPreferencesKey("morning_summary_hour")
        val MORNING_SUMMARY_MINUTE = intPreferencesKey("morning_summary_minute")
        val EVENING_SUMMARY_HOUR = intPreferencesKey("evening_summary_hour")
        val EVENING_SUMMARY_MINUTE = intPreferencesKey("evening_summary_minute")
        val DEFAULT_TASK_PRIORITY = intPreferencesKey("default_task_priority")
        val DAILY_REPORT_MODE = intPreferencesKey("daily_report_mode")
        val CONFIRM_TRASH_DELETE = booleanPreferencesKey("confirm_trash_delete")
    }

    val isCaseSensitiveTags: StateFlow<Boolean> = context.dataStore.data
        .map { it[Keys.CASE_SENSITIVE_TAGS] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val morningSummaryHour: Flow<Int> = context.dataStore.data.map { it[Keys.MORNING_SUMMARY_HOUR] ?: 8 }
    val morningSummaryMinute: Flow<Int> = context.dataStore.data.map { it[Keys.MORNING_SUMMARY_MINUTE] ?: 0 }
    val eveningSummaryHour: Flow<Int> = context.dataStore.data.map { it[Keys.EVENING_SUMMARY_HOUR] ?: 21 }
    val eveningSummaryMinute: Flow<Int> = context.dataStore.data.map { it[Keys.EVENING_SUMMARY_MINUTE] ?: 0 }
    val defaultTaskPriority: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_TASK_PRIORITY] ?: 1 }
    val dailyReportMode: Flow<Int> = context.dataStore.data.map { it[Keys.DAILY_REPORT_MODE] ?: 0 }
    val confirmTrashDelete: Flow<Boolean> = context.dataStore.data.map { it[Keys.CONFIRM_TRASH_DELETE] ?: true }

    fun setCaseSensitiveTags(value: Boolean) {
        scope.launch {
            context.dataStore.edit { it[Keys.CASE_SENSITIVE_TAGS] = value }
        }
    }

    suspend fun setMorningSummaryTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.MORNING_SUMMARY_HOUR] = hour
            it[Keys.MORNING_SUMMARY_MINUTE] = minute
        }
    }

    suspend fun setEveningSummaryTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.EVENING_SUMMARY_HOUR] = hour
            it[Keys.EVENING_SUMMARY_MINUTE] = minute
        }
    }

    suspend fun setDefaultTaskPriority(priority: Int) {
        context.dataStore.edit {
            it[Keys.DEFAULT_TASK_PRIORITY] = priority
        }
    }

    suspend fun setDailyReportMode(mode: Int) {
        context.dataStore.edit {
            it[Keys.DAILY_REPORT_MODE] = mode
        }
    }

    suspend fun setConfirmTrashDelete(confirm: Boolean) {
        context.dataStore.edit {
            it[Keys.CONFIRM_TRASH_DELETE] = confirm
        }
    }
}
