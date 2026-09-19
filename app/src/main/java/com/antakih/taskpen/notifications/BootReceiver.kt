package com.antakih.taskpen.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receptor para BOOT_COMPLETED.
 * Restaura silenciosamente todas las alarmas exactas y resúmenes diarios
 * después de un reinicio del dispositivo.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var taskAlarmScheduler: TaskAlarmScheduler
    @Inject lateinit var summaryScheduler: SummaryScheduler

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "BOOT_COMPLETED recibido — restaurando alarmas y resúmenes")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                taskAlarmScheduler.rescheduleAllAlarms()
                summaryScheduler.scheduleAll()
                Log.d(TAG, "Restauración post-reinicio completada")
            } catch (e: Exception) {
                Log.e(TAG, "Error en restauración post-reinicio: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
