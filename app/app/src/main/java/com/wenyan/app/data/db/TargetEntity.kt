package com.wenyan.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 对象档案（v1.7.2 起多档案：每行 = 一个记忆档案）
 * timeline 为 org.json 数组字符串，如 [{"time":"2026-07","event":"..."}]
 * note 为跨会话记忆正文（v1.7.2 新增，默认空串；DB v5）
 * SPEC §6 / db-schema §2.2
 */
@Entity(tableName = "target")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codeName: String,
    val mbti: String? = null,
    val score: Int? = null,
    val relationStatus: String? = null,
    val timeline: String = "[]",
    /** v1.7.2 记忆正文（跨会话记忆内容，空串默认；mergeNote 上限 2000 字；DB v5 新增） */
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
