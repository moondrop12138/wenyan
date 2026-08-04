package com.wenyan.app.ui.contract

import kotlinx.coroutines.flow.Flow

/**
 * 设置项 Repository（DataStore + Room provider/model 表）。后端实现。
 */
interface SettingsRepository {
    val providers: Flow<List<ProviderInfo>>
    val models: Flow<List<ModelInfo>>
    val currentModelId: Flow<Long?>
    val visionModelId: Flow<Long?>
    val themeMode: Flow<String>

    suspend fun setCurrentModel(id: Long)
    suspend fun setVisionModel(id: Long)
    suspend fun setThemeMode(mode: String)

    /** 测试连接：成功返回 null，失败返回归一错误 */
    suspend fun testConnection(providerId: Long): LlmError?

    suspend fun saveProvider(name: String, baseUrl: String, apiKey: String, isPreset: Boolean): Long
    suspend fun updateProvider(id: Long, name: String, baseUrl: String, apiKey: String?)
    suspend fun deleteProvider(id: Long)
    suspend fun addModel(providerId: Long, name: String, supportsVision: Boolean)
    suspend fun deleteModel(id: Long)
    suspend fun setDefaultModel(id: Long)
    suspend fun setVisionFlag(id: Long, supportsVision: Boolean)

    /** 一键清除全部档案（Key/档案/会话，AC-12） */
    suspend fun wipeAll()

    val privacyAck: Flow<Boolean>
    suspend fun setPrivacyAck(ack: Boolean)
}

/**
 * 问卷 Repository（profile/target 表）。后端实现。
 */
interface OnboardingRepository {
    val onboardingCompleted: Flow<Boolean>

    /** 提交问卷建档；返回成功后置 onboardingCompleted=true */
    suspend fun submit(draft: OnboardingDraft)

    /** 跳过问卷（AC-02 二次确认后调用），档案稍后可补录 */
    suspend fun skip()
}

/**
 * 依赖注入容器（联调占位：后端提供真实 AppContainer 后替换 Stub）。
 */
interface AppContainer {
    val chatRepository: ChatRepository
    val settingsRepository: SettingsRepository
    val onboardingRepository: OnboardingRepository
}
