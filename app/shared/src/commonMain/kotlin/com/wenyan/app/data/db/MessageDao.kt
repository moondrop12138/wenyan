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
        SELECT m.sessionId AS sessionId, m.content AS firstUserText
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

    /**
     * v1.9.5 完全重答替换：原位更新内容与类型（freetext 失败行重答成功后转回 analysis）。
     * 不新增行、不改 id/顺序——Room 失效通知在语句提交后发出，界面不会出现新旧两卡并存的瞬态。
     * 返回受影响行数（0 = 该行已被删除，调用方自行回退）。
     */
    @Query("UPDATE message SET content = :content, type = :type WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, type: String): Int

    @Query("DELETE FROM message")
    suspend fun clear()
}
