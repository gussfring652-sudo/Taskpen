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

    @Query("UPDATE tasks SET isImportant = :isImportant WHERE id = :taskId")
    suspend fun updateTaskImportance(taskId: String, isImportant: Boolean)

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY dueDate ASC")
    fun getAllActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 1 ORDER BY dueDate ASC")
    fun getDeletedTasks(): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET isDeleted = 1 WHERE id = :taskId")
    suspend fun moveToTrash(taskId: String)

    @Query("UPDATE tasks SET isDeleted = 0 WHERE id = :taskId")
    suspend fun restoreFromTrash(taskId: String)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun permanentlyDeleteTask(taskId: String)
}
