package com.wenyan.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 已记住事实（v1.7.3 单条管理，DB v6 新增表；v1.9.0 DB v7 加 kind 分层；v1.9.1 DB v8 加 expiresAt/source）
 * targetId FK → target.id ON DELETE CASCADE：删档案自动删其全部事实。
 * text 单条事实（提炼约束 ≤40 字，手工编辑不强制截断）；createdAt 毫秒 epoch。
 * kind：fact=用户明确陈述/可核验客观事实；hypothesis=模型推断/暂定解释（注入时标注"推测待验证"）。
 * expiresAt：可空毫秒 epoch，非空=临时事实（如"今天/本周"时效信息），到期后不再注入（UI 可转永久）；null=永久。
 * source：事实素材来源——paste=粘贴聊天记录 / transcription=截图转述 / chat=口述输入 / manual=手工添加（老数据默认）。
 * 每档案 ≤50 条（超出静默丢弃新事实，见 RealChatRepository.extractMemoryOnce）。
 * SPEC §6 / db-schema §2.2（v6 增补，v7 增 kind，v8 增 expiresAt/source）
 */
@Entity(
    tableName = "memory_fact",
    foreignKeys = [
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("targetId")],
)
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetId: Long,
    val text: String,
    /** v1.9.0：fact=事实 / hypothesis=推断（老数据迁移默认 fact） */
    val kind: String = "fact",
    /** v1.9.1：临时事实到期时间（毫秒 epoch；null=永久），到期后注入过滤 */
    val expiresAt: Long? = null,
    /** v1.9.1：素材来源（paste/transcription/chat/manual，老数据迁移默认 manual） */
    val source: String = SOURCE_MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val KIND_FACT = "fact"
        const val KIND_HYPOTHESIS = "hypothesis"

        /** v1.9.1 素材来源 */
        const val SOURCE_PASTE = "paste"
        const val SOURCE_TRANSCRIPTION = "transcription"
        const val SOURCE_CHAT = "chat"
        const val SOURCE_MANUAL = "manual"
    }
}
