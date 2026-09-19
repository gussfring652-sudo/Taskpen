package com.antakih.taskpen.notifications

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class SnoozeActivity : ComponentActivity() {

    @Inject lateinit var taskDao: com.antakih.taskpen.data.local.dao.TaskDao
    @Inject lateinit var taskAlarmScheduler: TaskAlarmScheduler
    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val taskId = intent.getStringExtra("taskId")
        if (taskId == null) {
            finish()
            return
        }

        setContent {
            SnoozeDialog(
                onDismiss = { finish() },
                onSnooze = { delayMillis ->
                    snoozeTask(taskId, delayMillis)
                }
            )
        }
    }

    private fun snoozeTask(taskId: String, delayMillis: Long) {
        val newSnoozeUntil = if (delayMillis == -1L) {
            // Caso especial: mañana a las 9:00
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else {
            System.currentTimeMillis() + delayMillis
        }

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            taskDao.updateSnoozeUntil(taskId, newSnoozeUntil)
            val updatedTask = taskDao.getTaskById(taskId)
            if (updatedTask != null) {
                taskAlarmScheduler.scheduleAlarm(updatedTask)
            }
            notificationHelper.cancelNotification(taskId)
            finish()
        }
    }
}

data class SnoozeOption(val label: String, val delayMillis: Long)

@Composable
private fun SnoozeDialog(
    onDismiss: () -> Unit,
    onSnooze: (Long) -> Unit
) {
    val options = listOf(
        SnoozeOption("+15 minutos", 15 * 60 * 1000L),
        SnoozeOption("+30 minutos", 30 * 60 * 1000L),
        SnoozeOption("+1 hora", 60 * 60 * 1000L),
        SnoozeOption("+3 horas", 3 * 60 * 60 * 1000L),
        SnoozeOption("Mañana a las 9:00", -1L) // Sentinel value
    )

    var selectedIndex by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Posponer Recordatorio") },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEachIndexed { index, option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedIndex == index,
                            onClick = null
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSnooze(options[selectedIndex].delayMillis) }) {
                Text("Posponer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
