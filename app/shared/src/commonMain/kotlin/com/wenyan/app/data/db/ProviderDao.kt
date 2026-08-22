package com.wenyan.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * provider DAO
 */
@Dao
interface ProviderDao {
    @Insert
    suspend fun insert(entity: ProviderEntity): Long

    @Update
    suspend fun update(entity: ProviderEntity)

    @Query("SELECT * FROM provider ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM provider ORDER BY sortOrder ASC, id ASC")
    suspend fun listAll(): List<ProviderEntity>

    @Query("SELECT * FROM provider WHERE id = :id")
    suspend fun getById(id: Long): ProviderEntity?

    /** L23: 按名称查（预设种子幂等判据用） */
    @Query("SELECT * FROM provider WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ProviderEntity?

    @Query("DELETE FROM provider WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM provider")
    suspend fun clear()
}
