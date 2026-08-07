package com.wenyan.app.data.repository

import com.wenyan.app.data.db.MessageDao
import com.wenyan.app.data.db.MessageEntity
import com.wenyan.app.data.db.SessionDao
import com.wenyan.app.data.db.SessionEntity
import com.wenyan.app.data.db.SessionFirstMessage
import kotlinx.coroutines.flow.Flow

/**
 * 会话/消息数据访问（AC-14 流式输出持久化）
 */
class ConversationRepository(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
) {
    /**
     * v1.7.2 新增 targetId 参数：新会话绑定当前激活档案（切档案只影响新会话）；
     * 老会话/未配置档案时 null = 未关联（注入空档案 = 现状行为）
     */
    suspend fun createSession(scenarioTag: String?, refDocs: List<String>, targetId: Long? = null): Long =
        sessionDao.insert(
            SessionEntity(
                scenarioTag = scenarioTag,
                refDocs = refDocs.toJson(),
                targetId = targetId,
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

    /** v1.3：读取/更新会话的对话状态（ConversationState JSON） */
    suspend fun getSessionState(id: Long): String = sessionDao.getById(id)?.stateJson ?: ""

    suspend fun updateSessionState(id: Long, stateJson: String) = sessionDao.updateState(id, stateJson)

    /** v1.2.1：更新会话标题（主模型拟定） */
    suspend fun updateSessionTitle(id: Long, title: String) = sessionDao.updateTitle(id, title)

    suspend fun deleteSession(id: Long) {
        messageDao.deleteBySession(id)
        sessionDao.deleteById(id)
    }

    /** v1.7.4 删档案解绑会话归属（防悬空 targetId：记忆注入静默失效 + 抽屉分组错乱） */
    suspend fun unbindSessionTarget(targetId: Long) = sessionDao.unbindTarget(targetId)

    suspend fun clearAll() {
        messageDao.clear()
        sessionDao.clear()
    }

    private fun List<String>.toJson(): String =
        org.json.JSONArray(this).toString()
}
