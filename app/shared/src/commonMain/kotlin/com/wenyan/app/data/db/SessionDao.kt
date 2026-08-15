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

    /** v1.2.1：更新会话标题（主模型拟定） */
    @Query("UPDATE session SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    /** v1.7.4 删档案时解绑其全部会话（session.targetId 无 FK，防悬空引用：记忆注入静默失效 + 抽屉分组错乱） */
    @Query("UPDATE session SET targetId = NULL WHERE targetId = :id")
    suspend fun unbindTarget(id: Long)

    /** 桌面版：更新引用知识文档列表（refDocs JSON 数组） */
    @Query("UPDATE session SET refDocs = :refDocs WHERE id = :id")
    suspend fun updateRefDocs(id: Long, refDocs: String)

    /** 桌面版：会话绑定/换绑档案（设置页"关联档案"下拉） */
    @Query("UPDATE session SET targetId = :targetId WHERE id = :id")
    suspend fun bindTarget(id: Long, targetId: Long?)
}
