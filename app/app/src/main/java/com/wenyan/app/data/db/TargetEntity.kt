package com.wenyan.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 对象档案（MVP 单行）
 * timeline 为 org.json 数组字符串，如 [{"time":"2026-07","event":"..."}]
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
    val createdAt: Long = System.currentTimeMillis(),
)
