package com.antakih.taskpen.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SettingsManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("taskpen_settings", Context.MODE_PRIVATE)

    private val _isCaseSensitiveTags = MutableStateFlow(prefs.getBoolean("case_sensitive_tags", false))
    val isCaseSensitiveTags: StateFlow<Boolean> = _isCaseSensitiveTags.asStateFlow()

    fun setCaseSensitiveTags(value: Boolean) {
        prefs.edit().putBoolean("case_sensitive_tags", value).apply()
        _isCaseSensitiveTags.value = value
    }
}
