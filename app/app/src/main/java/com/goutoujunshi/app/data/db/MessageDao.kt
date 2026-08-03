package com.goutoujunshi.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * message DAO（按会话流式读取，写操作 suspend）
 */
@Dao
interface MessageDao {
    @Insert
    suspend fun insert(entity: MessageEntity): Long

    @Query("SELECT * FROM message WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeBySession(sessionId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun listBySession(sessionId: Long): List<MessageEntity>

    @Query("DELETE FROM message WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    @Query("DELETE FROM message")
    suspend fun clear()
}
