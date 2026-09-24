package com.antakih.taskpen.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.antakih.taskpen.MainActivity
import com.antakih.taskpen.R
import com.antakih.taskpen.data.local.entities.TaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_REMINDERS = "taskpen_reminders"
        const val CHANNEL_SUMMARIES = "taskpen_summaries"
        const val NOTIFICATION_ID_MORNING = 9001
        const val NOTIFICATION_ID_EVENING = 9002

        // Usamos el hashCode del taskId para generar IDs de notificación únicos
        fun notificationIdForTask(taskId: String): Int = taskId.hashCode()
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        val remindersChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
            enableVibration(true)
        }

        val summariesChannel = NotificationChannel(
            CHANNEL_SUMMARIES,
            context.getString(R.string.channel_summaries),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_summaries_desc)
        }

        notificationManager.createNotificationChannel(remindersChannel)
        notificationManager.createNotificationChannel(summariesChannel)
    }

    /**
     * Muestra una notificación de recordatorio individual para una tarea.
     * Incluye acciones de Completar y Posponer.
     */
    fun showTaskReminder(task: TaskEntity) {
        val notificationId = notificationIdForTask(task.id)

        // Intent para abrir la app al tocar la notificación
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("taskId", task.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción: Completar
        val completeIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1000,
            Intent(context, CompleteTaskReceiver::class.java).apply {
                putExtra("taskId", task.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción: Posponer
        val snoozeIntent = PendingIntent.getActivity(
            context,
            notificationId + 2000,
            Intent(context, SnoozeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("taskId", task.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dateFormat = SimpleDateFormat("EEE dd MMM, hh:mm a", Locale.getDefault())
        val dueDateText = task.dueDate?.let { "Vence: ${dateFormat.format(Date(it))}" } ?: ""

        val priorityEmoji = when (task.priority) {
            2 -> "🔴"
            1 -> "🟡"
            else -> "🟢"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$priorityEmoji ${task.title}")
            .setContentText(dueDateText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_menu_send,
                context.getString(R.string.action_complete),
                completeIntent
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                context.getString(R.string.action_snooze),
                snoozeIntent
            )
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Muestra una notificación de resumen diario con estilo InboxStyle.
     */
    fun showDailySummary(
        title: String,
        tasks: List<TaskEntity>,
        notificationId: Int,
        emptyMessage: String
    ) {
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("showDailyReport", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (tasks.isEmpty()) {
            val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARIES)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(title)
                .setContentText(emptyMessage)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

            notificationManager.notify(notificationId, notification)
            return
        }

        val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(context.getString(R.string.tasks_remaining, tasks.size))

        tasks.take(6).forEach { task ->
            val priorityDot = when (task.priority) {
                2 -> "🔴"
                1 -> "🟡"
                else -> "🟢"
            }
            val time = task.dueDate?.let { dateFormat.format(Date(it)) } ?: ""
            inboxStyle.addLine("$priorityDot ${task.title}  $time")
        }

        if (tasks.size > 6) {
            inboxStyle.addLine("...y ${tasks.size - 6} más")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARIES)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.tasks_remaining, tasks.size))
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Cancela una notificación específica.
     */
    fun cancelNotification(taskId: String) {
        notificationManager.cancel(notificationIdForTask(taskId))
    }
}
