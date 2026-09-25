package com.antakih.taskpen.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.antakih.taskpen.data.local.dao.TaskDao
import com.antakih.taskpen.data.local.entities.TaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor de alarmas exactas con sistema de Recordatorios en Cascada.
 *
 * En lugar de una sola alarma estática, el sistema evalúa dinámicamente
 * el tiempo restante hasta el dueDate y programa la siguiente alarma
 * del cascading schedule. Cuando la alarma se dispara, el receptor
 * vuelve a invocar schedule() para programar el siguiente nivel.
 *
 * Cascada por prioridad:
 *
 * Alta (priority=2):
 *   >7d antes → alarma a dueDate-7d
 *   1-7d antes → alarma a dueDate-3d
 *   <24h antes → alarma a dueDate-12h
 *
 * Media (priority=1):
 *   >7d antes → alarma a dueDate-2d
 *   1-7d antes → alarma a dueDate-1d
 *   <24h antes → alarma a dueDate-4h
 *
 * Baja (priority=0):
 *   >7d antes → alarma a dueDate-1d
 *   1-7d antes → alarma a dueDate-12h
 *   <24h antes → alarma a dueDate-1h
 */
import com.antakih.taskpen.data.local.SettingsManager

@Singleton
class TaskAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "TaskAlarmScheduler"
        private const val ACTION_TASK_ALARM = "com.antakih.taskpen.TASK_ALARM"

        // Constantes de tiempo en milisegundos
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Programa la siguiente alarma en cascada para una tarea.
     * Si la tarea tiene snoozeUntil activo, usa ese timestamp en su lugar.
     * Retorna true si se programó una alarma, false si no aplica.
     */
    fun scheduleAlarm(task: TaskEntity): Boolean {
        // No programar si la tarea está completada, eliminada o sin fecha
        if (task.isCompleted || task.isDeleted || task.dueDate == null) {
            cancelAlarm(task.id)
            return false
        }

        val triggerTime = calculateTriggerTime(task) ?: run {
            Log.d(TAG, "No hay alarma futura para tarea: ${task.title}")
            return false
        }

        val pendingIntent = createPendingIntent(task.id)

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.d(TAG, "Alarma programada para '${task.title}' en ${triggerTime - System.currentTimeMillis()}ms (${(triggerTime - System.currentTimeMillis()) / MINUTE} min)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Sin permiso para alarmas exactas: ${e.message}")
            return false
        }

        return true
    }

    /**
     * Cancela la alarma programada para una tarea.
     */
    fun cancelAlarm(taskId: String) {
        val pendingIntent = createPendingIntent(taskId)
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Alarma cancelada para taskId: $taskId")
    }

    /**
     * Reprograma todas las alarmas pendientes.
     * Usado después de un reinicio del dispositivo.
     */
    suspend fun rescheduleAllAlarms() {
        val tasks = taskDao.getPendingTasksWithDueDate()
        var scheduled = 0
        tasks.forEach { task ->
            if (scheduleAlarm(task)) scheduled++
        }
        Log.d(TAG, "Reprogramadas $scheduled alarmas de ${tasks.size} tareas pendientes")
    }

    /**
     * Calcula el tiempo de disparo para la siguiente alarma en cascada.
     *
     * 1. Si snoozeUntil existe y es futuro → usar snoozeUntil
     * 2. Si el usuario definió un reminderOffsetMinutes manual → usar dueDate - offset
     * 3. Si no → calcular el siguiente punto de cascada dinámico
     */
    private fun calculateTriggerTime(task: TaskEntity): Long? {
        val now = System.currentTimeMillis()

        // Prioridad 1: Snooze activo
        task.snoozeUntil?.let { snooze ->
            if (snooze > now) return snooze
        }

        val dueDate = task.dueDate ?: return null

        if (task.reminderMode == 0) { // Exact
            val offsetMillis = (task.reminderOffsetMinutes ?: 0) * 60_000L
            val offsetTime = dueDate - offsetMillis
            return if (now < offsetTime) {
                offsetTime // Return offset time
            } else if (now < dueDate) {
                dueDate // Return exact time if offset already passed
            } else {
                null
            }
        } else { // Cascade
            task.customCascadeIntervalMinutes?.let { interval ->
                val nextPoint = now + (interval * 60_000L)
                return if (nextPoint < dueDate) nextPoint else dueDate
            }
            return calculateNextCascadePoint(task.priority, dueDate, now)
        }
    }

    /**
     * Determina el siguiente punto de alarma en cascada basándose en
     * la prioridad y el tiempo restante hasta el dueDate.
     *
     * Cada prioridad define 3 puntos de alarma (offsets desde dueDate).
     * Se devuelve el primer punto que aún está en el futuro, empezando
     * desde el más lejano al más cercano.
     */
    private fun calculateNextCascadePoint(priority: Int, dueDate: Long, now: Long): Long? {
        val cascadeOffsets = getCascadeOffsets(priority)

        // Los offsets están ordenados de mayor a menor (más lejano primero)
        // Buscamos el SIGUIENTE punto futuro más cercano
        for (offset in cascadeOffsets) {
            val alarmTime = dueDate - offset
            if (alarmTime > now) {
                return alarmTime
            }
        }

        return null // Todos los puntos ya pasaron
    }

    /**
     * Retorna los offsets de cascada para una prioridad dada,
     * ordenados de mayor a menor (más lejano primero).
     *
     * Cada offset representa cuánto tiempo ANTES del dueDate se debe
     * disparar la alarma.
     */
    private fun getCascadeOffsets(priority: Int): List<Long> {
        val intervalsStr = when (priority) {
            2 -> settingsManager.cascadeHigh.value
            1 -> settingsManager.cascadeMedium.value
            else -> settingsManager.cascadeLow.value
        }
        
        return intervalsStr.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseInterval(it) }
            .sortedDescending()
    }

    private fun parseInterval(interval: String): Long? {
        val value = interval.dropLast(1).toLongOrNull() ?: return null
        val unit = interval.last()
        return when (unit) {
            'd', 'D' -> value * DAY
            'h', 'H' -> value * HOUR
            'm', 'M' -> value * MINUTE
            else -> null
        }
    }

    private fun createPendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION_TASK_ALARM
            putExtra("taskId", taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
