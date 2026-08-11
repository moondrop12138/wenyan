package com.wenyan.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 已记住事实（v1.7.3 单条管理，DB v6 新增表；v1.9.0 DB v7 加 kind 分层）
 * targetId FK → target.id ON DELETE CASCADE：删档案自动删其全部事实。
 * text 单条事实（提炼约束 ≤40 字，手工编辑不强制截断）；createdAt 毫秒 epoch。
 * kind：fact=用户明确陈述/可核验客观事实；hypothesis=模型推断/暂定解释（注入时标注"推测待验证"）。
 * 每档案 ≤50 条（超出静默丢弃新事实，见 RealChatRepository.extractMemoryOnce）。
 * SPEC §6 / db-schema §2.2（v6 增补，v7 增 kind）
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
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val KIND_FACT = "fact"
        const val KIND_HYPOTHESIS = "hypothesis"
    }
}
