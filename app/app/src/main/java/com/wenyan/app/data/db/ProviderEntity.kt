package com.wenyan.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 提供商（BYOK 多供应商）
 * apiKeyEncrypted 为 Keystore AES-GCM 密文（Base64），可空（自定义可后补）
 * SPEC §6 / db-schema §2.5
 */
@Entity(tableName = "provider")
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val apiKeyEncrypted: String? = null,
    val isPreset: Boolean = false,
    val sortOrder: Int = 0,
)
