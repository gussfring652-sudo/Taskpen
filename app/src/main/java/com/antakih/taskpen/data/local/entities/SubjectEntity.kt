package com.antakih.taskpen.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subjects",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId")]
)
data class SubjectEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "NULL") val categoryId: String?,
    val fullName: String,
    val aliases: List<String>,
    val semester: Int? = null
)