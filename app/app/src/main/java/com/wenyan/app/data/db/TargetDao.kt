package com.wenyan.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * target DAO（MVP 单行）
 */
@Dao
interface TargetDao {
    @Query("SELECT * FROM target ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): TargetEntity?

    @Query("SELECT * FROM target ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<TargetEntity?>

    @Insert
    suspend fun insert(entity: TargetEntity): Long

    @Query("DELETE FROM target")
    suspend fun clear()
}
