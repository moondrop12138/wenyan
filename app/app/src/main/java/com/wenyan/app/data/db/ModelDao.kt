package com.wenyan.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * model DAO
 */
@Dao
interface ModelDao {
    @Insert
    suspend fun insert(entity: ModelEntity): Long

    @Update
    suspend fun update(entity: ModelEntity)

    @Query("SELECT * FROM model WHERE providerId = :providerId ORDER BY sortOrder ASC, id ASC")
    fun observeByProvider(providerId: Long): Flow<List<ModelEntity>>

    @Query("SELECT * FROM model ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM model WHERE providerId = :providerId ORDER BY sortOrder ASC, id ASC")
    suspend fun listByProvider(providerId: Long): List<ModelEntity>

    @Query("SELECT * FROM model WHERE id = :id")
    suspend fun getById(id: Long): ModelEntity?

    @Query("DELETE FROM model WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM model")
    suspend fun clear()
}
