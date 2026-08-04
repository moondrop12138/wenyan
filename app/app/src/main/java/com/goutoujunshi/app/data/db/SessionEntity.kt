package com.goutoujunshi.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 会话
 * refDocs 为引用知识文档文件名数组 JSON，如 ["实战话术编排器：从一句回复到后续分支.md"]
 * SPEC §6 / db-schema §2.3
 */
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val scenarioTag: String? = null,
    val refDocs: String = "[]",
    /** v1.3 对话状态（ConversationState JSON），连续话题跟踪/话术查重；DB v2 新增 */
    val stateJson: String = "",
)
