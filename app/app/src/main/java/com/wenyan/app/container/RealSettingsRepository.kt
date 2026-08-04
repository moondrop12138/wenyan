package com.wenyan.app.container

import com.wenyan.app.data.datastore.SettingsRepository as DataStoreSettings
import com.wenyan.app.data.repository.ConversationRepository
import com.wenyan.app.data.repository.ProfileRepository
import com.wenyan.app.data.repository.ProviderRepository
import com.wenyan.app.llm.ChatRequest
import com.wenyan.app.llm.LlmClient
import com.wenyan.app.llm.LlmEvent
import com.wenyan.app.log.AppLogger
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.contract.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

/**
 * 设置项/提供商/模型 Repository 真实实现（AC-09/10/11/12/18）
 * 装配：Room provider/model + DataStore 设置 + Keystore 加密 + LLM 测试连接。
 */
class RealSettingsRepository(
    private val dataStore: DataStoreSettings,
    private val providerRepository: ProviderRepository,
    private val profileRepository: ProfileRepository,
    private val conversationRepository: ConversationRepository,
) : SettingsRepository {

    override val providers: Flow<List<ProviderInfo>> =
        providerRepository.observeProviders().map { list -> list.map { UiMappers.toProviderInfo(it) } }

    override val models: Flow<List<ModelInfo>> =
        combine(providerRepository.observeProviders(), providerRepository.observeAllModels()) { providers, models ->
            val nameById = providers.associate { it.id to it.name }
            models.map { UiMappers.toModelInfo(it, nameById[it.providerId] ?: "") }
        }

    override val currentModelId: Flow<Long?> = dataStore.currentModelId

    override val visionModelId: Flow<Long?> = dataStore.visionModelId

    override val themeMode: Flow<String> = dataStore.theme

    override val privacyAck: Flow<Boolean> = dataStore.privacyAck

    override suspend fun setCurrentModel(id: Long) {
        dataStore.setCurrentModelId(id)
    }

    override suspend fun setVisionModel(id: Long) {
        dataStore.setVisionModelId(id)
    }

    override suspend fun setThemeMode(mode: String) {
        dataStore.setTheme(mode)
    }

    override suspend fun setPrivacyAck(ack: Boolean) {
        dataStore.setPrivacyAck(ack)
        // 隐私操作埋点：只记录事件，不记录 Key/档案内容
        if (ack) AppLogger.i("privacy_ack_confirm")
    }

    override suspend fun testConnection(providerId: Long): LlmError? {
        val provider = providerRepository.getProvider(providerId) ?: return null
        val apiKey = providerRepository.decryptApiKey(providerId) ?: return null
        val model = providerRepository.listModels(providerId).firstOrNull() ?: return null
        val client = LlmClient(provider.baseUrl, apiKey)
        val events = client.stream(
            ChatRequest(
                model = model.name,
                system = "你好",
                userText = "ping",
            )
        ).toList()
        return events.filterIsInstance<LlmEvent.Failed>().firstOrNull()?.let {
            UiMappers.toLlmError(it.error)
        }
    }

    override suspend fun saveProvider(
        name: String,
        baseUrl: String,
        apiKey: String,
        isPreset: Boolean,
    ): Long = providerRepository.addProvider(name, baseUrl, apiKey, isPreset)

    override suspend fun updateProvider(id: Long, name: String, baseUrl: String, apiKey: String?) {
        val entity = providerRepository.getProvider(id) ?: return
        providerRepository.updateProvider(entity.copy(name = name, baseUrl = baseUrl))
        if (!apiKey.isNullOrBlank()) {
            providerRepository.updateProviderApiKey(id, apiKey)
        }
    }

    override suspend fun deleteProvider(id: Long) {
        providerRepository.deleteProvider(id)
    }

    override suspend fun addModel(providerId: Long, name: String, supportsVision: Boolean) {
        providerRepository.addModel(providerId, name, supportsVision)
    }

    override suspend fun deleteModel(id: Long) {
        providerRepository.deleteModel(id)
    }

    override suspend fun setDefaultModel(id: Long) {
        val model = providerRepository.getModel(id) ?: return
        providerRepository.updateModel(model.copy(isDefault = true))
    }

    override suspend fun setVisionFlag(id: Long, supportsVision: Boolean) {
        val model = providerRepository.getModel(id) ?: return
        providerRepository.updateModel(model.copy(supportsVision = supportsVision))
    }

    override suspend fun wipeAll() {
        // AC-12：删除全部本地数据（档案/会话/提供商/模型 + Key + 设置）
        profileRepository.clearAll()
        conversationRepository.clearAll()
        providerRepository.clearAll()
        dataStore.clearAll()
        AppLogger.i("privacy_wipe_all")
    }
}
