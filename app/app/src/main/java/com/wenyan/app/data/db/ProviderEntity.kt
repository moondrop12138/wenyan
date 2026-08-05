package com.wenyan.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 提供商（BYOK 多供应商）
 * apiKeyEncrypted 为 Keystore AES-GCM 密文（Base64），可空（自定义可后补）
 * connectionStatus 连接状态（v1.6.3）："" = 未测试/失败（红灯），"ok" = 测试成功（绿灯）；
 * 保存提供商后立即自动测试并写入
 * SPEC §6 / db-schema §2.5
 */
@Entity(tableName = "provider")
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val apiKeyEncrypted: String? = null,
    val isPreset: Boolean = false,
    val connectionStatus: String = "",
    val sortOrder: Int = 0,
)
