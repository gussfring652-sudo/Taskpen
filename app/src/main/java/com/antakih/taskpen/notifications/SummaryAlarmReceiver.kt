package com.antakih.taskpen.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antakih.taskpen.data.local.dao.SubjectDao
import com.antakih.taskpen.R
import com.antakih.taskpen.data.local.dao.TaskDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class SummaryAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var taskDao: TaskDao

    @Inject
    lateinit var subjectDao: SubjectDao

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var summaryScheduler: SummaryScheduler

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val isEvening = intent.getBooleanExtra("isEvening", false)
        val pendingResult = goAsync()

        scope.launch {
            try {
                val calendar = Calendar.getInstance()
                
                if (isEvening) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val startOfDay = calendar.timeInMillis

                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    calendar.set(Calendar.MILLISECOND, 999)
                    val endOfDay = calendar.timeInMillis

                    val rawTasks = taskDao.getSummaryTasks(startOfDay, endOfDay)
                    val tasks = rawTasks.map { t ->
                        if (t.subcategoryId != null) {
                            val subject = subjectDao.getSubjectById(t.subcategoryId)
                            if (subject != null) {
                                t.copy(title = "${t.title} [${subject.fullName}]")
                            } else t
                        } else t
                    }

                    notificationHelper.showDailySummary(
                        title = context.getString(R.string.evening_summary_title),
                        tasks = tasks,
                        notificationId = NotificationHelper.NOTIFICATION_ID_EVENING,
                        emptyMessage = context.getString(R.string.all_tasks_done)
                    )
                } else {
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val startOfDay = calendar.timeInMillis

                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    calendar.set(Calendar.MILLISECOND, 999)
                    val endOfDay = calendar.timeInMillis

                    val rawTasks = taskDao.getSummaryTasks(startOfDay, endOfDay)
                    val tasks = rawTasks.map { t ->
                        if (t.subcategoryId != null) {
                            val subject = subjectDao.getSubjectById(t.subcategoryId)
                            if (subject != null) {
                                t.copy(title = "${t.title} [${subject.fullName}]")
                            } else t
                        } else t
                    }

                    notificationHelper.showDailySummary(
                        title = context.getString(R.string.morning_summary_title),
                        tasks = tasks,
                        notificationId = NotificationHelper.NOTIFICATION_ID_MORNING,
                        emptyMessage = context.getString(R.string.no_tasks_today)
                    )
                }
                
                // Reschedule for next day
                summaryScheduler.scheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
