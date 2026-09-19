package com.antakih.taskpen.notifications

import android.content.Context
import android.util.Log
import androidx.work.*
import com.antakih.taskpen.data.local.SettingsManager
import com.antakih.taskpen.notifications.workers.EveningSummaryWorker
import com.antakih.taskpen.notifications.workers.MorningSummaryWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "SummaryScheduler"
        private const val MORNING_WORK_TAG = "morning_summary"
        private const val EVENING_WORK_TAG = "evening_summary"
    }

    private val workManager = WorkManager.getInstance(context)

    /**
     * Programa ambos resúmenes leyendo los horarios del DataStore.
     */
    suspend fun scheduleAll() {
        val morningHour = settingsManager.morningSummaryHour.first()
        val morningMinute = settingsManager.morningSummaryMinute.first()
        val eveningHour = settingsManager.eveningSummaryHour.first()
        val eveningMinute = settingsManager.eveningSummaryMinute.first()

        scheduleMorningSummary(morningHour, morningMinute)
        scheduleEveningSummary(eveningHour, eveningMinute)
    }

    fun scheduleMorningSummary(hour: Int, minute: Int) {
        val delay = calculateInitialDelay(hour, minute)
        Log.d(TAG, "Resumen matutino programado: delay=${delay}ms (${delay / 60000}min)")

        val request = PeriodicWorkRequestBuilder<MorningSummaryWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(MORNING_WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            MORNING_WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleEveningSummary(hour: Int, minute: Int) {
        val delay = calculateInitialDelay(hour, minute)
        Log.d(TAG, "Resumen nocturno programado: delay=${delay}ms (${delay / 60000}min)")

        val request = PeriodicWorkRequestBuilder<EveningSummaryWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(EVENING_WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            EVENING_WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Calcula el delay inicial desde ahora hasta la próxima ocurrencia
     * del horario especificado. Si la hora ya pasó hoy, programa para mañana.
     */
    private fun calculateInitialDelay(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Si la hora ya pasó hoy, programar para mañana
        if (target.before(now) || target == now) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }
}
