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
    suspend fun insertTasks(tasks: List<TaskEntity>) // Para el insert masivo del S-Pen

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity) // Para insertar una sola tarea manualmente

    @Update
    suspend fun updateTask(task: TaskEntity)

    // Obtiene tareas pendientes, ordenadas por fecha límite. Retorna un Flow para actualizar la UI en tiempo real
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY dueDate ASC")
    fun getPendingTasks(): Flow<List<TaskEntity>>

    // Marca una tarea como completada (usando UPDATE en lugar de borrarla por si quieres ver el historial)
    @Query("UPDATE tasks SET isCompleted = 1 WHERE id = :taskId")
    suspend fun markTaskAsCompleted(taskId: String)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)
}