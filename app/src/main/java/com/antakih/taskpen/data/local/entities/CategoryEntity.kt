package com.antakih.taskpen.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    @ColumnInfo(defaultValue = "0") val lastUsed: Long = System.currentTimeMillis()
)
