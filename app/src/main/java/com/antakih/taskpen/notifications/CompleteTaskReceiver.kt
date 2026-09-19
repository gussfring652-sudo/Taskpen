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
 * Receptor para la acción "Completar" desde la notificación.
 * Marca la tarea como completada en Room, cancela la alarma
 * y limpia la notificación.
 */
@AndroidEntryPoint
class CompleteTaskReceiver : BroadcastReceiver() {

    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var taskAlarmScheduler: TaskAlarmScheduler
    @Inject lateinit var notificationHelper: NotificationHelper

    companion object {
        private const val TAG = "CompleteTaskReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("taskId") ?: run {
            Log.e(TAG, "taskId no encontrado en el Intent")
            return
        }

        Log.d(TAG, "Completando tarea desde notificación: $taskId")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                taskDao.markTaskAsCompleted(taskId)
                taskAlarmScheduler.cancelAlarm(taskId)
                notificationHelper.cancelNotification(taskId)
                Log.d(TAG, "Tarea completada exitosamente: $taskId")
            } catch (e: Exception) {
                Log.e(TAG, "Error al completar tarea: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
