package com.wenyan.app.container

import android.content.Context
import android.net.Uri
import com.wenyan.app.data.datastore.SettingsRepository as DataStoreSettings
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.data.repository.ConversationRepository
import com.wenyan.app.data.repository.ProfileRepository
import com.wenyan.app.data.repository.ProviderRepository
import com.wenyan.app.data.update.UpdateCheckResult
import com.wenyan.app.data.update.UpdateChecker
import com.wenyan.app.llm.ChatRequest
import com.wenyan.app.llm.LlmClient
import com.wenyan.app.llm.LlmErrorCode
import com.wenyan.app.llm.LlmEvent
import com.wenyan.app.log.AppLogger
import com.wenyan.app.log.CrashLogStore
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.MemoryFactUi
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.contract.SettingsRepository
import com.wenyan.app.ui.contract.TargetUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

/**
 * 设置项/提供商/模型 Repository 真实实现（AC-09/10/11/12/18）
 * 装配：Room provider/model + DataStore 设置 + Keystore 加密 + LLM 测试连接。
 * v1.7.3：事实单条管理 + 档案详情全字段编辑 + 崩溃日志导出 + 更新检查。
 */
class RealSettingsRepository(
    private val context: Context,
    private val dataStore: DataStoreSettings,
    private val providerRepository: ProviderRepository,
    private val profileRepository: ProfileRepository,
    private val conversationRepository: ConversationRepository,
    private val crashLogStore: CrashLogStore,
    private val updateChecker: UpdateChecker,
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

    // ===== v1.7.2 记忆档案 =====

    /** v1.7.3-fix：第三路全档案事实计数 → 档案行 caption 展示「已记住 N 条」 */
    override val targets: Flow<List<TargetUi>> =
        combine(
            profileRepository.observeTargets(),
            dataStore.activeTargetId,
            profileRepository.observeFactCounts(),
        ) { list, activeId, factCounts ->
            list.map { UiMappers.toTargetUi(it, isActive = it.id == activeId, factCount = factCounts[it.id] ?: 0) }
        }

    override val activeTargetId: Flow<Long?> = dataStore.activeTargetId

    override val memoryAutoEnabled: Flow<Boolean> = dataStore.memoryAutoEnabled

    /** v1.7.2 创建档案；当前无激活档案 → 自动激活该档案（空白名称防御返回 -1） */
    override suspend fun createTarget(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        val id = profileRepository.saveTarget(TargetEntity(codeName = trimmed))
        if (dataStore.getActiveTargetId() == null) dataStore.setActiveTargetId(id)
        return id
    }

    /**
     * v1.7.2 改名兼容入口（v1.7.3 起 note 代码层废弃：只改名称，不再写入 note——防旧数据污染）；
     * 档案详情页编辑走 updateTargetDetails（全字段）。
     */
    override suspend fun updateTarget(id: Long, name: String, note: String) {
        val e = profileRepository.getTarget(id) ?: return
        profileRepository.updateTarget(e.copy(codeName = name.trim()))
    }

    /**
     * v1.7.2 删除档案；删激活项 → 自动激活剩余第一个（observeAll 第一条，id DESC=最新）；无剩余 → null
     * v1.7.4：删前先解绑其全部会话的 targetId（session 无 FK，防悬空引用）
     */
    override suspend fun deleteTarget(id: Long) {
        profileRepository.deleteTarget(id)
        conversationRepository.unbindSessionTarget(id)
        if (dataStore.getActiveTargetId() == id) {
            dataStore.setActiveTargetId(profileRepository.observeTargets().first().firstOrNull()?.id)
        }
    }

    /** v1.7.4 档案详情页打开前搬移老 note（merge 幂等；MemoryEditViewModel init 调用） */
    override suspend fun ensureMigrated(targetId: Long) = profileRepository.migrateNoteToFactsOnce(targetId)

    override suspend fun setActiveTarget(id: Long) = dataStore.setActiveTargetId(id)

    override suspend fun setMemoryAutoEnabled(enabled: Boolean) = dataStore.setMemoryAutoEnabled(enabled)

    // ===== v1.7.3 事实单条管理 + 档案详情编辑 =====

    override fun observeFacts(targetId: Long): Flow<List<MemoryFactUi>> =
        profileRepository.observeFacts(targetId).map { list -> list.map { UiMappers.toMemoryFactUi(it) } }

    override suspend fun addFact(targetId: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        // 条数上限 50：超出静默丢弃（与提炼链路一致）
        if (profileRepository.countFacts(targetId) >= 50) return
        profileRepository.addFact(targetId, trimmed)
    }

    override suspend fun updateFact(factId: Long, text: String) {
        profileRepository.updateFact(factId, text)
    }

    override suspend fun deleteFact(factId: Long) {
        profileRepository.deleteFact(factId)
    }

    /** 全字段保存（名称/MBTI/吸引力分/关系状态/关键事件 timeline JSON 数组；note 代码层废弃不写） */
    override suspend fun updateTargetDetails(
        id: Long,
        name: String,
        mbti: String?,
        score: Int?,
        relationStatus: String?,
        timelineJson: String,
    ) {
        val e = profileRepository.getTarget(id) ?: return
        profileRepository.updateTarget(
            e.copy(
                codeName = name.trim().ifBlank { e.codeName },
                mbti = mbti,
                score = score,
                relationStatus = relationStatus,
                timeline = timelineJson,
            )
        )
    }

    // ===== v1.7.3 T3 崩溃日志导出 =====

    /** 返回可分享的 crash 文件 Uri（无则 null）；文件不存在/授权异常 → null 静默 */
    override suspend fun exportCrashLog(): Uri? {
        val file = crashLogStore.crashFile() ?: return null
        if (!file.exists()) return null
        return runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.getOrNull()
    }

    // ===== v1.7.3 T4 更新检查 =====

    override suspend fun checkUpdate(): UpdateCheckResult = updateChecker.check()

    override suspend fun downloadUpdateApk(info: com.wenyan.app.data.update.UpdateInfo): java.io.File? =
        updateChecker.download(info, context.cacheDir)

    /** 下载完成后唤起系统安装器（FileProvider + ACTION_VIEW + FLAG_GRANT_READ_URI_PERMISSION） */
    override suspend fun installApk(file: java.io.File): Boolean = runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.isSuccess

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

    /** v1.7.5 编辑页掩码回显：解密已保存 key（仅内存输入框，存储层仍是 Keystore 加密） */
    override suspend fun getProviderApiKey(providerId: Long): String? =
        providerRepository.decryptApiKey(providerId)

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
        // v1.7.3 隐私联动：删除崩溃日志目录与下载缓存（含事实表，随 profileRepository.clearAll 的 memoryFactDao.clear）
        crashLogStore.clear()
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
