package com.wenyan.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * profile DAO（MVP 单行：读取最新一行）
 */
@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): ProfileEntity?

    @Query("SELECT * FROM profile ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<ProfileEntity?>

    @Insert
    suspend fun insert(entity: ProfileEntity): Long

    @Query("DELETE FROM profile")
    suspend fun clear()
}
