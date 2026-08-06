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
    /**
     * v1.6.3 切换模型在主页"选择模型"弹层的可见性（替代原"设为默认"单选）：
     * 模型管理里每个模型名前的开关控制是否展示
     */
    suspend fun toggleSheetVisible(id: Long)
    suspend fun setVisionFlag(id: Long, supportsVision: Boolean)
    /** v1.6.3 写入连接测试结果：保存提供商后自动测试，ok=true 绿灯 / false 红灯 */
    suspend fun markConnectionStatus(providerId: Long, ok: Boolean)

    /** 一键清除全部档案（Key/档案/会话，AC-12） */
    suspend fun wipeAll()

    val privacyAck: Flow<Boolean>
    suspend fun setPrivacyAck(ack: Boolean)

    // ===== v1.7.2 记忆档案 =====

    /** 全记忆档案 + isActive 标记（combine targetDao.observeAll + dataStore.activeTargetId） */
    val targets: Flow<List<TargetUi>>

    /** 激活记忆档案 id（null = 无激活档案） */
    val activeTargetId: Flow<Long?>

    /** 自动记忆开关（默认开） */
    val memoryAutoEnabled: Flow<Boolean>

    /** 创建档案；当前无激活档案 → 自动激活该档案 */
    suspend fun createTarget(name: String): Long

    /** 改名 + 编辑记忆正文 */
    suspend fun updateTarget(id: Long, name: String, note: String)

    /** 删除档案；删激活项 → 自动激活剩余第一个（observeAll 第一条），无剩余 → null */
    suspend fun deleteTarget(id: Long)

    /** 切换激活档案 */
    suspend fun setActiveTarget(id: Long)

    /** 自动记忆开关 */
    suspend fun setMemoryAutoEnabled(enabled: Boolean)
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
