package com.antakih.taskpen.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index("subjectId")]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val subjectId: String?,
    val createdAt: Long,
    val dueDate: Long?,
    val hasSpecificTime: Boolean,
    val isCompleted: Boolean,
    val calendarEventId: String?
)
