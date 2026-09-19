package com.antakih.taskpen.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antakih.taskpen.data.local.dao.TaskDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receptor de alarmas de recordatorio individual.
 *
 * Cuando se dispara:
 * 1. Consulta la tarea en Room
 * 2. Si es válida (no completada, no eliminada) → muestra la notificación
 * 3. Limpia snoozeUntil si existía (ya se usó)
 * 4. RE-PROGRAMA la siguiente alarma en cascada invocando scheduleAlarm()
 *    → Este es el mecanismo que mantiene viva la cadena de recordatorios
 */
@AndroidEntryPoint
class TaskAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var taskAlarmScheduler: TaskAlarmScheduler

    companion object {
        private const val TAG = "TaskAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("taskId") ?: run {
            Log.e(TAG, "TaskAlarmReceiver: taskId no encontrado en el Intent")
            return
        }

        Log.d(TAG, "Alarma disparada para taskId: $taskId")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = taskDao.getTaskById(taskId)

                if (task == null) {
                    Log.w(TAG, "Tarea no encontrada: $taskId")
                    return@launch
                }

                if (task.isCompleted || task.isDeleted) {
                    Log.d(TAG, "Tarea ya completada/eliminada, ignorando: ${task.title}")
                    return@launch
                }

                // Limpiar snoozeUntil si existía (ya se ejecutó)
                if (task.snoozeUntil != null) {
                    taskDao.updateSnoozeUntil(taskId, null)
                }

                // Mostrar la notificación visual
                notificationHelper.showTaskReminder(task)

                // RE-PROGRAMAR la siguiente alarma en cascada
                // El scheduler evaluará el nuevo tiempo restante y dejará
                // programada la siguiente alarma del cascading schedule.
                // El bucle se rompe naturalmente cuando no quedan más
                // puntos de cascada futuros.
                val rescheduledTask = task.copy(snoozeUntil = null)
                val hasNext = taskAlarmScheduler.scheduleAlarm(rescheduledTask)

                if (hasNext) {
                    Log.d(TAG, "Siguiente alarma en cascada programada para: ${task.title}")
                } else {
                    Log.d(TAG, "Última alarma de cascada para: ${task.title}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error procesando alarma: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
