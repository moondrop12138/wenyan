package com.wenyan.app.data.db

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

    /** 取每个会话首条 USER 消息（抽屉列表当标题用）；无消息的会话不返回 */
    @Query(
        """
        SELECT m.sessionId AS sessionId, m.content AS firstUserText, m.createdAt AS lastMessageAt
        FROM message m
        INNER JOIN (
            SELECT sessionId, MIN(id) AS firstUserId
            FROM message
            WHERE role = 'USER'
            GROUP BY sessionId
        ) firsts ON firsts.firstUserId = m.id
        """,
    )
    fun observeFirstUserMessages(): Flow<List<SessionFirstMessage>>

    /** O3: 会话全文检索（LIKE 起步，命中消息原文，限 50 条） */
    @Query("SELECT * FROM message WHERE content LIKE '%' || :query || '%' ORDER BY id DESC LIMIT 50")
    suspend fun search(query: String): List<MessageEntity>

    @Query("DELETE FROM message WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    @Query("DELETE FROM message WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM message")
    suspend fun clear()
}
