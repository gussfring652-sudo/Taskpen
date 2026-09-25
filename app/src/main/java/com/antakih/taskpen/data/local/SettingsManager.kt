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
        val DEFAULT_TASK_TIME_MODE = intPreferencesKey("default_task_time_mode")
        val DEFAULT_TASK_TIME_HOUR = intPreferencesKey("default_task_time_hour")
        val DEFAULT_TASK_TIME_MINUTE = intPreferencesKey("default_task_time_minute")
        val CASCADE_LOW = stringPreferencesKey("cascade_low")
        val CASCADE_MEDIUM = stringPreferencesKey("cascade_medium")
        val CASCADE_HIGH = stringPreferencesKey("cascade_high")
        val USE_CUSTOM_CASCADES = booleanPreferencesKey("use_custom_cascades")
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
    val defaultTaskTimeMode: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_TASK_TIME_MODE] ?: 0 }
    val defaultTaskTimeHour: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_TASK_TIME_HOUR] ?: 9 }
    val defaultTaskTimeMinute: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_TASK_TIME_MINUTE] ?: 0 }

    val cascadeLow: StateFlow<String> = context.dataStore.data
        .map { it[Keys.CASCADE_LOW] ?: "3d,2d,1d" }
        .stateIn(scope, SharingStarted.Eagerly, "3d,2d,1d")

    val cascadeMedium: StateFlow<String> = context.dataStore.data
        .map { it[Keys.CASCADE_MEDIUM] ?: "3d,2d,1d,12h,6h" }
        .stateIn(scope, SharingStarted.Eagerly, "3d,2d,1d,12h,6h")

    val cascadeHigh: StateFlow<String> = context.dataStore.data
        .map { it[Keys.CASCADE_HIGH] ?: "2d,1d,12h,6h,3h,1h" }
        .stateIn(scope, SharingStarted.Eagerly, "2d,1d,12h,6h,3h,1h")

    val useCustomCascades: StateFlow<Boolean> = context.dataStore.data
        .map { it[Keys.USE_CUSTOM_CASCADES] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

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

    suspend fun setDefaultTaskTime(mode: Int, hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.DEFAULT_TASK_TIME_MODE] = mode
            it[Keys.DEFAULT_TASK_TIME_HOUR] = hour
            it[Keys.DEFAULT_TASK_TIME_MINUTE] = minute
        }
    }

    suspend fun setCascadeIntervals(priority: Int, intervalsStr: String) {
        context.dataStore.edit {
            when (priority) {
                0 -> it[Keys.CASCADE_LOW] = intervalsStr
                1 -> it[Keys.CASCADE_MEDIUM] = intervalsStr
                2 -> it[Keys.CASCADE_HIGH] = intervalsStr
            }
        }
    }

    suspend fun setUseCustomCascades(value: Boolean) {
        context.dataStore.edit {
            it[Keys.USE_CUSTOM_CASCADES] = value
        }
    }
}
