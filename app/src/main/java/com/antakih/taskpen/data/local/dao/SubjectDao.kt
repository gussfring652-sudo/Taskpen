package com.antakih.taskpen.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.antakih.taskpen.data.local.entities.SubjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubject(subject: SubjectEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>)

    @Query("SELECT * FROM subjects WHERE categoryId = :categoryId ORDER BY fullName ASC")
    fun getSubjectsByCategory(categoryId: String): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE categoryId = :categoryId ORDER BY fullName ASC")
    suspend fun getSubjectsByCategoryOnce(categoryId: String): List<SubjectEntity>

    @Query("SELECT * FROM subjects ORDER BY fullName ASC")
    suspend fun getAllSubjectsOnce(): List<SubjectEntity>

    @Query("SELECT * FROM subjects ORDER BY fullName ASC")
    fun getAllSubjectsOnceFlow(): Flow<List<SubjectEntity>>

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteSubject(id: String)
}
