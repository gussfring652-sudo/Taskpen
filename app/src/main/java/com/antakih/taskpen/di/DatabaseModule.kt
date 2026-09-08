package com.antakih.taskpen.di

import android.content.Context
import androidx.room.Room
import com.antakih.taskpen.data.local.AppDatabase
import com.antakih.taskpen.data.local.dao.CategoryDao
import com.antakih.taskpen.data.local.dao.SubjectDao
import com.antakih.taskpen.data.local.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN isImportant INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE categories ADD COLUMN lastUsed INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Task entity: add deletedAt
                database.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                
                // Subject entity: make categoryId nullable
                database.execSQL("CREATE TABLE IF NOT EXISTS `subjects_new` (`id` TEXT NOT NULL, `categoryId` TEXT DEFAULT NULL, `fullName` TEXT NOT NULL, `aliases` TEXT NOT NULL, `semester` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                database.execSQL("INSERT INTO `subjects_new` (`id`, `categoryId`, `fullName`, `aliases`, `semester`) SELECT `id`, `categoryId`, `fullName`, `aliases`, `semester` FROM `subjects`")
                database.execSQL("DROP TABLE `subjects`")
                database.execSQL("ALTER TABLE `subjects_new` RENAME TO `subjects`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_subjects_categoryId` ON `subjects` (`categoryId`)")
            }
        }

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "taskpen_database"
        )
        .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideSubjectDao(database: AppDatabase): SubjectDao = database.subjectDao()
}
