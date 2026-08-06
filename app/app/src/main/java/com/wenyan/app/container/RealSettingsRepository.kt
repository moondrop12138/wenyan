package com.wenyan.app.container

import com.wenyan.app.data.datastore.SettingsRepository as DataStoreSettings
import com.wenyan.app.data.repository.ConversationRepository
import com.wenyan.app.data.repository.ProfileRepository
import com.wenyan.app.data.repository.ProviderRepository
import com.wenyan.app.llm.ChatRequest
import com.wenyan.app.llm.LlmClient
import com.wenyan.app.llm.LlmErrorCode
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

    /**
     * v1.6.3 测试连接：挨个测试该提供商模型列表里的**所有**模型，任一模型成功（无 Failed）→ 返回 null（绿灯）；
     * 全部失败 → 返回最后一个错误（红灯）。无模型/无 API Key 显式返回错误（不再误判为成功）。
     */
    override suspend fun testConnection(providerId: Long): LlmError? {
        val provider = providerRepository.getProvider(providerId)
            ?: return LlmError("no_provider", "提供商不存在", false)
        val apiKey = providerRepository.decryptApiKey(providerId)
            ?: return UiMappers.toLlmError(LlmErrorCode.UNAUTHORIZED)
        val models = providerRepository.listModels(providerId)
        if (models.isEmpty()) return LlmError("no_model", "该提供商还没有模型，请先添加模型", false)
        return testAllModels(models.map { it.name }) { name ->
            val events = LlmClient(provider.baseUrl, apiKey).stream(
                ChatRequest(
                    model = name,
                    system = "你好",
                    userText = "ping",
                )
            ).toList()
            events.filterIsInstance<LlmEvent.Failed>().firstOrNull()?.let {
                UiMappers.toLlmError(it.error)
            }
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

    /**
     * v1.6.3 切换模型在主页"选择模型"弹层的可见性（替代原"设为默认"单选）
     */
    override suspend fun toggleSheetVisible(id: Long) {
        val model = providerRepository.getModel(id) ?: return
        providerRepository.updateModel(model.copy(showInSheet = !model.showInSheet))
    }

    /** v1.6.3 写入连接测试结果（保存提供商后自动测试） */
    override suspend fun markConnectionStatus(providerId: Long, ok: Boolean) {
        providerRepository.updateConnectionStatus(providerId, ok)
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

/**
 * v1.6.3 遍历所有模型测试连接（纯函数，可单测）：
 * 任一模型返回 null（成功）→ 整体 null（绿灯，提前返回）；全部失败 → 返回最后一个错误。
 */
internal suspend fun testAllModels(
    models: List<String>,
    testOne: suspend (String) -> LlmError?,
): LlmError? {
    var last: LlmError? = null
    for (m in models) {
        last = testOne(m) ?: return null
    }
    return last
}
