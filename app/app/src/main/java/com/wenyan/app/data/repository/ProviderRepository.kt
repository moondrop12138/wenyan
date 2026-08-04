package com.wenyan.app.data.repository

import com.wenyan.app.data.db.ModelDao
import com.wenyan.app.data.db.ModelEntity
import com.wenyan.app.data.db.ProviderDao
import com.wenyan.app.data.db.ProviderEntity
import com.wenyan.app.data.security.KeystoreAesGcmCipher
import kotlinx.coroutines.flow.Flow

/**
 * 提供商/模型数据访问（AC-09 / AC-10）
 * Repository 层只做数据读写与加解密适配，不含业务规则。
 * API Key 落库前加密，读取时解密（AC-11）。
 */
class ProviderRepository(
    private val providerDao: ProviderDao,
    private val modelDao: ModelDao,
    private val cipher: KeystoreAesGcmCipher,
) {
    fun observeProviders(): Flow<List<ProviderEntity>> = providerDao.observeAll()

    fun observeModels(providerId: Long): Flow<List<ModelEntity>> =
        modelDao.observeByProvider(providerId)

    /** 全量模型流（跨提供商，供模型切换器聚合展示） */
    fun observeAllModels(): Flow<List<ModelEntity>> = modelDao.observeAll()

    suspend fun listProviders(): List<ProviderEntity> = providerDao.listAll()

    suspend fun listModels(providerId: Long): List<ModelEntity> =
        modelDao.listByProvider(providerId)

    suspend fun getProvider(id: Long): ProviderEntity? = providerDao.getById(id)

    suspend fun getModel(id: Long): ModelEntity? = modelDao.getById(id)

    /** 新增提供商；apiKey 明文传入，内部加密存储 */
    suspend fun addProvider(
        name: String,
        baseUrl: String,
        apiKey: String?,
        isPreset: Boolean = false,
        sortOrder: Int = 0,
    ): Long {
        val encrypted = apiKey?.takeIf { it.isNotBlank() }?.let { cipher.encrypt(it) }
        return providerDao.insert(
            ProviderEntity(
                name = name,
                baseUrl = baseUrl,
                apiKeyEncrypted = encrypted,
                isPreset = isPreset,
                sortOrder = sortOrder,
            )
        )
    }

    suspend fun updateProvider(entity: ProviderEntity) = providerDao.update(entity)

    /** 仅更新 API Key（重新加密） */
    suspend fun updateProviderApiKey(providerId: Long, apiKey: String) {
        val current = providerDao.getById(providerId) ?: return
        providerDao.update(current.copy(apiKeyEncrypted = cipher.encrypt(apiKey)))
    }

    suspend fun deleteProvider(id: Long) = providerDao.deleteById(id)

    /** 解密后的明文 API Key（仅供 LLM Client 出网时使用，不落 UI 状态） */
    suspend fun decryptApiKey(providerId: Long): String? {
        val entity = providerDao.getById(providerId) ?: return null
        val encrypted = entity.apiKeyEncrypted ?: return null
        return try {
            cipher.decrypt(encrypted)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun addModel(
        providerId: Long,
        name: String,
        supportsVision: Boolean,
        isDefault: Boolean = false,
        sortOrder: Int = 0,
    ): Long = modelDao.insert(
        ModelEntity(
            providerId = providerId,
            name = name,
            supportsVision = supportsVision,
            isDefault = isDefault,
            sortOrder = sortOrder,
        )
    )

    suspend fun updateModel(entity: ModelEntity) = modelDao.update(entity)

    suspend fun deleteModel(id: Long) = modelDao.deleteById(id)

    suspend fun clearAll() {
        providerDao.clear()
        modelDao.clear()
    }
}
