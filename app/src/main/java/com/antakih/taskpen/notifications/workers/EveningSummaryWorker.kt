package com.antakih.taskpen.notifications.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.antakih.taskpen.R
import com.antakih.taskpen.data.local.dao.TaskDao
import com.antakih.taskpen.notifications.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class EveningSummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskDao: TaskDao,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        val tasks = taskDao.getOverdueAndTodayTasks(endOfDay)

        notificationHelper.showDailySummary(
            title = applicationContext.getString(R.string.evening_summary_title),
            tasks = tasks,
            notificationId = NotificationHelper.NOTIFICATION_ID_EVENING,
            emptyMessage = applicationContext.getString(R.string.all_tasks_done)
        )

        return Result.success()
    }
}
