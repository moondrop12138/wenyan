package com.wenyan.app.ui.contract

import com.wenyan.app.data.update.UpdateCheckResult
import com.wenyan.app.data.update.UpdateInfo
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

    /**
     * v1.7.5 取提供商已保存的明文 API Key（编辑页掩码回显用）。
     * 存储层仍是 Keystore 加密；解密结果仅进内存输入框 state，不落盘。
     */
    suspend fun getProviderApiKey(providerId: Long): String?

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

    /**
     * v1.7.4 打开档案详情前确保老 note 搬移完成（merge 语义幂等）：
     * 防「升级后先手工加事实，再首次注入」时老 note 内容永不搬移、静默丢失。
     */
    suspend fun ensureMigrated(targetId: Long)

    /** 切换激活档案 */
    suspend fun setActiveTarget(id: Long)

    /** 自动记忆开关 */
    suspend fun setMemoryAutoEnabled(enabled: Boolean)

    // ===== v1.7.3 事实单条管理 + 档案详情编辑 =====

    /** 档案全部事实（时间倒序） */
    fun observeFacts(targetId: Long): Flow<List<MemoryFactUi>>

    suspend fun addFact(targetId: Long, text: String)
    suspend fun updateFact(factId: Long, text: String)
    suspend fun deleteFact(factId: Long)

    /** v1.9.1 临时事实转永久（清空到期时间） */
    suspend fun makePermanent(factId: Long)

    /** v1.9.0 撤销最近一次自动写入：返回被撤销的 fact id 列表（空 = 无日志可撤销） */
    suspend fun undoLastMemoryWrite(): List<Long>

    /**
     * 全字段保存（名称/MBTI/吸引力分/关系状态/关键事件 timeline JSON 数组）。
     * 注意：v1.7.3 起 note 代码层废弃，本方法不写 note（保留旧数据）。
     */
    suspend fun updateTargetDetails(
        id: Long,
        name: String,
        mbti: String?,
        score: Int?,
        relationStatus: String?,
        timelineJson: String,
    )

    /** v1.7.3 T3 导出诊断日志：返回可分享的 crash 文件 Uri（无则 null） */
    suspend fun exportCrashLog(): android.net.Uri?

    /** v1.7.3 T4 检查更新：NewVersion / UpToDate / Failed */
    suspend fun checkUpdate(): UpdateCheckResult

    /** v1.7.3 T4 下载新版 APK 到 cacheDir（返回文件/null，失败静默） */
    suspend fun downloadUpdateApk(info: UpdateInfo): java.io.File?

    /** v1.7.3 T4 唤起系统安装器（FileProvider + ACTION_VIEW）；返回是否成功发起 */
    suspend fun installApk(file: java.io.File): Boolean
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
