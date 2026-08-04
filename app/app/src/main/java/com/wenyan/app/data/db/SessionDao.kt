package com.wenyan.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * session DAO
 */
@Dao
interface SessionDao {
    @Insert
    suspend fun insert(entity: SessionEntity): Long

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM session ORDER BY id DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session ORDER BY id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SessionEntity>

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM session")
    suspend fun clear()

    /** v1.3：更新会话的对话状态（ConversationState JSON） */
    @Query("UPDATE session SET stateJson = :stateJson WHERE id = :id")
    suspend fun updateState(id: Long, stateJson: String)
}
