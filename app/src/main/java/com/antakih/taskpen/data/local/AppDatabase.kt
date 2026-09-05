package com.antakih.taskpen.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.antakih.taskpen.data.local.dao.TaskDao
import com.antakih.taskpen.data.local.entities.CategoryEntity
import com.antakih.taskpen.data.local.entities.SubjectEntity
import com.antakih.taskpen.data.local.entities.TaskEntity

@Database(
    entities = [CategoryEntity::class, SubjectEntity::class, TaskEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

}
