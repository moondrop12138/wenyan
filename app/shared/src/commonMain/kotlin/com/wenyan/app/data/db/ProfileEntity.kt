package com.wenyan.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户档案（MVP 单行）
 * SPEC §6 / db-schema §2.1
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mbti: String? = null,
    val score: Int? = null,
    val strengths: String? = null,
    val weaknesses: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
