package com.antakih.taskpen.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antakih.taskpen.data.local.dao.TaskDao
import com.antakih.taskpen.data.local.dao.CategoryDao
import com.antakih.taskpen.data.local.dao.SubjectDao
import com.antakih.taskpen.data.local.entities.CategoryEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
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
    @Inject lateinit var subjectDao: SubjectDao
    @Inject lateinit var categoryDao: CategoryDao
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var taskAlarmScheduler: TaskAlarmScheduler

    companion object {
        private const val TAG = "TaskAlarmReceiver"
        const val ACTION_SNOOZE = "ACTION_SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("taskId") ?: run {
            Log.e(TAG, "TaskAlarmReceiver: taskId no encontrado en el Intent")
            return
        }

        val action = intent.action
        Log.d(TAG, "Alarma disparada para taskId: $taskId, action: $action")

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

                if (action == ACTION_SNOOZE) {
                    val newSnoozeUntil = intent.getLongExtra("snoozeUntil", System.currentTimeMillis() + 15 * 60 * 1000L)
                    val updatedTask = task.copy(
                        isPostponed = true,
                        snoozeUntil = newSnoozeUntil
                    )
                    taskDao.insertTask(updatedTask)
                    taskAlarmScheduler.scheduleAlarm(updatedTask)
                    notificationHelper.cancelNotification(taskId)
                } else {
                    // Limpiar snoozeUntil si existía (ya se ejecutó)
                    if (task.snoozeUntil != null) {
                        taskDao.updateSnoozeUntil(taskId, null)
                    }

                    // Append subject name to task title if present
                    var taskToShow = task
                    if (task.subcategoryId != null) {
                        val subject = subjectDao.getSubjectById(task.subcategoryId)
                        if (subject != null) {
                            taskToShow = task.copy(title = "${task.title} [${subject.fullName}]")
                        }
                    }

                    // Mostrar la notificación visual
                    notificationHelper.showTaskReminder(taskToShow)

                    // RE-PROGRAMAR la siguiente alarma en cascada
                    val rescheduledTask = task.copy(snoozeUntil = null)
                    val hasNext = taskAlarmScheduler.scheduleAlarm(rescheduledTask)
                    if (hasNext) {
                        Log.d(TAG, "Siguiente alarma programada para: ${task.title}")
                    } else {
                        Log.d(TAG, "Última alarma para: ${task.title}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando alarma: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
