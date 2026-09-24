package com.antakih.taskpen.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antakih.taskpen.data.local.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "SummaryScheduler"
        private const val MORNING_REQUEST_CODE = 8001
        private const val EVENING_REQUEST_CODE = 8002
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Programa ambos resúmenes leyendo los horarios del DataStore.
     */
    suspend fun scheduleAll() {
        val mode = settingsManager.dailyReportMode.first()
        val morningHour = settingsManager.morningSummaryHour.first()
        val morningMinute = settingsManager.morningSummaryMinute.first()
        val eveningHour = settingsManager.eveningSummaryHour.first()
        val eveningMinute = settingsManager.eveningSummaryMinute.first()

        // 0 = Both, 1 = Morning, 2 = Evening
        if (mode == 0 || mode == 1) {
            scheduleMorningSummary(morningHour, morningMinute)
        } else {
            cancelMorningSummary()
        }

        if (mode == 0 || mode == 2) {
            scheduleEveningSummary(eveningHour, eveningMinute)
        } else {
            cancelEveningSummary()
        }
    }

    private fun scheduleMorningSummary(hour: Int, minute: Int) {
        val targetTime = calculateNextOccurrence(hour, minute)
        Log.d(TAG, "Resumen matutino programado para: $targetTime")

        val intent = Intent(context, SummaryAlarmReceiver::class.java).apply {
            putExtra("isEvening", false)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MORNING_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            targetTime,
            pendingIntent
        )
    }

    private fun scheduleEveningSummary(hour: Int, minute: Int) {
        val targetTime = calculateNextOccurrence(hour, minute)
        Log.d(TAG, "Resumen nocturno programado para: $targetTime")

        val intent = Intent(context, SummaryAlarmReceiver::class.java).apply {
            putExtra("isEvening", true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            EVENING_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            targetTime,
            pendingIntent
        )
    }

    private fun cancelMorningSummary() {
        val intent = Intent(context, SummaryAlarmReceiver::class.java).apply {
            putExtra("isEvening", false)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MORNING_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun cancelEveningSummary() {
        val intent = Intent(context, SummaryAlarmReceiver::class.java).apply {
            putExtra("isEvening", true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            EVENING_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Calcula la próxima ocurrencia del horario especificado en ms.
     * Si la hora ya pasó hoy, programa para mañana.
     */
    private fun calculateNextOccurrence(hour: Int, minute: Int): Long {
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

        return target.timeInMillis
    }
}
