package com.wenyan.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息
 * role: USER / ASSISTANT；type: text / image / analysis
 * content 语义见 db-schema §2.4
 * SPEC §6 / db-schema §2.4
 */
@Entity(
    tableName = "message",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["sessionId"])],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val type: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** MessageDao.observeFirstUserMessages 投影结果（抽屉列表标题） */
data class SessionFirstMessage(
    val sessionId: Long,
    val firstUserText: String,
    val lastMessageAt: Long,
)
