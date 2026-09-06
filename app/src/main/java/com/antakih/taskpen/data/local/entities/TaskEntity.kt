package com.antakih.taskpen.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index("subcategoryId"), Index("categoryId"), Index("parentTaskId")]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    // La categoría principal (ej. "Universidad", "General")
    @ColumnInfo(defaultValue = "NULL") val categoryId: String?,
    // La subcategoría / materia (ej. "Robótica", "Ética")
    @ColumnInfo(name = "subcategoryId", defaultValue = "NULL") val subcategoryId: String?,
    // Si esta tarea es hija de otra (para subtareas)
    @ColumnInfo(defaultValue = "NULL") val parentTaskId: String?,
    val createdAt: Long,
    val dueDate: Long?,
    val hasSpecificTime: Boolean,
    val isCompleted: Boolean,
    @ColumnInfo(defaultValue = "0") val isImportant: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    val calendarEventId: String?
)
