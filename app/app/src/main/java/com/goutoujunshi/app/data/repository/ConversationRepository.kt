package com.goutoujunshi.app.data.repository

import com.goutoujunshi.app.data.db.MessageDao
import com.goutoujunshi.app.data.db.MessageEntity
import com.goutoujunshi.app.data.db.SessionDao
import com.goutoujunshi.app.data.db.SessionEntity
import com.goutoujunshi.app.data.db.SessionFirstMessage
import kotlinx.coroutines.flow.Flow

/**
 * 会话/消息数据访问（AC-14 流式输出持久化）
 */
class ConversationRepository(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
) {
    suspend fun createSession(scenarioTag: String?, refDocs: List<String>): Long =
        sessionDao.insert(
            SessionEntity(
                scenarioTag = scenarioTag,
                refDocs = refDocs.toJson(),
            )
        )

    suspend fun getSession(id: Long): SessionEntity? = sessionDao.getById(id)

    fun observeAllSessions(): Flow<List<SessionEntity>> = sessionDao.observeAll()

    /** 每个会话的首条 USER 消息（抽屉列表标题用） */
    fun observeFirstUserMessages(): Flow<List<SessionFirstMessage>> =
        messageDao.observeFirstUserMessages()

    fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> =
        messageDao.observeBySession(sessionId)

    suspend fun listMessages(sessionId: Long): List<MessageEntity> =
        messageDao.listBySession(sessionId)

    suspend fun addMessage(
        sessionId: Long,
        role: String,
        type: String,
        content: String,
    ): Long = messageDao.insert(
        MessageEntity(
            sessionId = sessionId,
            role = role,
            type = type,
            content = content,
        )
    )

    suspend fun deleteMessage(id: Long) = messageDao.deleteById(id)

    suspend fun deleteSession(id: Long) {
        messageDao.deleteBySession(id)
        sessionDao.deleteById(id)
    }

    suspend fun clearAll() {
        messageDao.clear()
        sessionDao.clear()
    }

    private fun List<String>.toJson(): String =
        org.json.JSONArray(this).toString()
}
