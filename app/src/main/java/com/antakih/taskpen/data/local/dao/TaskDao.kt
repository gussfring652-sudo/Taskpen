package com.antakih.taskpen.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.antakih.taskpen.data.local.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND parentTaskId IS NULL AND categoryId = :categoryId ORDER BY dueDate ASC")
    fun getTasksByCategory(categoryId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND parentTaskId IS NULL ORDER BY dueDate ASC")
    fun getPendingTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentTaskId ORDER BY createdAt ASC")
    fun getSubtasks(parentTaskId: String): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET isCompleted = 1 WHERE id = :taskId")
    suspend fun markTaskAsCompleted(taskId: String)

    @Query("UPDATE tasks SET isCompleted = 0 WHERE id = :taskId")
    suspend fun unmarkTaskAsCompleted(taskId: String)

    @Query("UPDATE tasks SET isImportant = :isImportant WHERE id = :taskId")
    suspend fun updateTaskImportance(taskId: String, isImportant: Boolean)

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND parentTaskId IS NULL ORDER BY dueDate ASC")
    fun getAllActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 1 AND parentTaskId IS NULL ORDER BY dueDate ASC")
    fun getDeletedTasks(): Flow<List<TaskEntity>>

    @Query("DELETE FROM tasks WHERE isDeleted = 1 AND deletedAt < :threshold")
    suspend fun deleteOldTrashTasks(threshold: Long)

    @Query("UPDATE tasks SET isDeleted = 1, deletedAt = :timestamp WHERE id = :taskId")
    suspend fun moveToTrash(taskId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET isDeleted = 0, deletedAt = NULL WHERE id = :taskId")
    suspend fun restoreFromTrash(taskId: String)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun permanentlyDeleteTask(taskId: String)

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND isDeleted = 0 AND dueDate IS NOT NULL")
    suspend fun getPendingTasksWithDueDate(): List<TaskEntity>

    @Query("""
        SELECT * FROM tasks 
        WHERE isCompleted = 0 AND isDeleted = 0 AND dueDate IS NOT NULL 
        AND dueDate >= :startOfDay AND dueDate < :endOfDay
        ORDER BY dueDate ASC
    """)
    suspend fun getTasksForDay(startOfDay: Long, endOfDay: Long): List<TaskEntity>

    @Query("""
        SELECT * FROM tasks 
        WHERE isCompleted = 0 AND isDeleted = 0 AND dueDate IS NOT NULL 
        AND dueDate < :endOfDay
        ORDER BY dueDate ASC
    """)
    suspend fun getOverdueAndTodayTasks(endOfDay: Long): List<TaskEntity>

    @Query("UPDATE tasks SET snoozeUntil = :snoozeUntil WHERE id = :taskId")
    suspend fun updateSnoozeUntil(taskId: String, snoozeUntil: Long?)

    @Query("UPDATE tasks SET priority = :priority, reminderOffsetMinutes = :offset WHERE id = :taskId")
    suspend fun updateReminderSettings(taskId: String, priority: Int, offset: Int?)

    @Query("UPDATE tasks SET categoryId = :newCategoryId WHERE subcategoryId = :subcategoryId")
    suspend fun updateCategoryForTasksWithSubcategory(subcategoryId: String, newCategoryId: String?)

    @Query("""
        SELECT * FROM tasks 
        WHERE isCompleted = 0 AND isDeleted = 0 
        AND ((dueDate >= :startOfDay AND dueDate < :endOfDay) OR isImportant = 1)
        ORDER BY dueDate ASC
    """)
    suspend fun getSummaryTasks(startOfDay: Long, endOfDay: Long): List<TaskEntity>
}
