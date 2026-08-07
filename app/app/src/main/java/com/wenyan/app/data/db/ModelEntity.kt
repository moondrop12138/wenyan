package com.wenyan.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 模型
 * supportsVision 标注多模态能力（设置页可改）；isDefault 为该提供商默认模型
 * showInSheet 控制该模型是否出现在主页"选择模型"弹层（v1.6.3，模型管理里切换）
 * SPEC §6 / db-schema §2.6
 */
@Entity(
    tableName = "model",
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["providerId"])],
)
data class ModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val name: String,
    val supportsVision: Boolean = false,
    val isDefault: Boolean = false,
    val showInSheet: Boolean = true,
    val sortOrder: Int = 0,
)
