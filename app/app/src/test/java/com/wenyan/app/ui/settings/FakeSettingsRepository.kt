package com.wenyan.app.ui.settings

import com.wenyan.app.data.update.UpdateCheckResult
import com.wenyan.app.data.update.UpdateInfo
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.MemoryFactUi
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.contract.SettingsRepository
import com.wenyan.app.ui.contract.TargetUi
import com.wenyan.app.ui.contract.UsageMetricsUi
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_DEFAULT
import com.wenyan.app.ui.theme.FLUID_HUE_DEFAULT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * F77 精简：ui.settings 包四个测试各自复制粘贴的内存 SettingsRepository 收敛为单份共享 Fake
 * （原 FluidAppearanceSettingsFlowTest / MemoryEditViewModelTest / ProviderEditViewModelTest /
 * SettingsViewModelMemoryTest 各持一份约 60-110 行的全量桩，v1.9.4 记忆导出/导入桩四处手贴，
 * 每次接口扩充都要四处同步）。
 * 全量成员 + 可配置记录钩子：各测试只读自己关心的字段。默认行为镜像 RealSettingsRepository 契约：
 * 新建自动激活 / 删激活项回退 id 最大（最新）一条并重映射 isActive / setActiveTarget 刷新标记。
 */
internal class FakeSettingsRepository : SettingsRepository {

    // ===== 记忆档案（含记录钩子） =====
    val targetsFlow = MutableStateFlow<List<TargetUi>>(emptyList())
    val activeFlow = MutableStateFlow<Long?>(null)
    val memoryAutoFlow = MutableStateFlow(true)
    val created = mutableListOf<String>()
    val deleted = mutableListOf<Long>()
    val activated = mutableListOf<Long>()
    val ensureMigratedCalls = mutableListOf<Long>()

    // ===== 流光可调（FluidAppearanceSettingsFlowTest 用） =====
    val fluidHueFlow = MutableStateFlow(FLUID_HUE_DEFAULT)
    val bgBrightnessFlow = MutableStateFlow(BG_BRIGHTNESS_DEFAULT)
    val hueWrites = mutableListOf<Int>()
    val brightnessWrites = mutableListOf<Int>()

    // ===== 提供商/模型（ProviderEditViewModelTest 用） =====
    val addedModels = mutableListOf<Triple<Long, String, Boolean>>()
    var privacyAckValue = false
    var testConnectionCalls = 0
    var testConnectionResult: LlmError? = null
    val connectionStatusCalls = mutableListOf<Pair<Long, Boolean>>()
    val providerUpdates = mutableListOf<Triple<Long, String, String?>>()

    /** v1.7.5 编辑回显用：默认无 key，测试可配置 */
    var apiKeyValue: String? = null

    // ===== v1.9.4 记忆导出/导入桩（记录调用，结果可由用例改写） =====
    val writtenMemoryExports = mutableListOf<Pair<android.net.Uri, String>>()
    val importedMemoryUris = mutableListOf<android.net.Uri>()
    var fakeImportMemoryResult: Pair<Boolean, String> = true to "导入 0 个档案、0 条记忆"

    override val providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    override val models = MutableStateFlow<List<ModelInfo>>(emptyList())
    override val currentModelId = MutableStateFlow<Long?>(null)
    override val visionModelId = MutableStateFlow<Long?>(null)
    override val themeMode = MutableStateFlow("system")
    override val privacyAck = MutableStateFlow(false)
    override val targets: Flow<List<TargetUi>> = targetsFlow
    override val activeTargetId: Flow<Long?> = activeFlow
    override val memoryAutoEnabled: Flow<Boolean> = memoryAutoFlow
    override val fluidBackgroundEnabled = MutableStateFlow(true)
    override val fluidHue: Flow<Int> = fluidHueFlow
    override val bgBrightness: Flow<Int> = bgBrightnessFlow

    override suspend fun createTarget(name: String): Long {
        created.add(name)
        val id = (targetsFlow.value.maxOfOrNull { it.id } ?: 0L) + 1
        targetsFlow.value = targetsFlow.value + TargetUi(
            id = id, name = name.trim(), note = "", createdAt = 0L,
            isActive = activeFlow.value == null,
        )
        if (activeFlow.value == null) activeFlow.value = id
        return id
    }

    override suspend fun deleteTarget(id: Long) {
        deleted.add(id)
        targetsFlow.value = targetsFlow.value.filterNot { it.id == id }
        if (activeFlow.value == id) {
            // 与 RealSettingsRepository 契约一致：回退 id 最大（最新）的剩余档案并重映射 isActive
            val fallback = targetsFlow.value.maxByOrNull { it.id }?.id
            activeFlow.value = fallback
            targetsFlow.value = targetsFlow.value.map { it.copy(isActive = it.id == fallback) }
        }
    }

    override suspend fun setActiveTarget(id: Long) {
        activated.add(id)
        activeFlow.value = id
        targetsFlow.value = targetsFlow.value.map { it.copy(isActive = it.id == id) }
    }

    override suspend fun ensureMigrated(targetId: Long) {
        ensureMigratedCalls.add(targetId)
    }

    override suspend fun setMemoryAutoEnabled(enabled: Boolean) {
        memoryAutoFlow.value = enabled
    }

    override suspend fun setFluidBackgroundEnabled(enabled: Boolean) {
        fluidBackgroundEnabled.value = enabled
    }

    override suspend fun setFluidHue(degrees: Int) {
        hueWrites.add(degrees)
        fluidHueFlow.value = degrees
    }

    override suspend fun setBgBrightness(value: Int) {
        brightnessWrites.add(value)
        bgBrightnessFlow.value = value
    }

    override fun observeFacts(targetId: Long): Flow<List<MemoryFactUi>> = MutableStateFlow(emptyList())
    override suspend fun addFact(targetId: Long, text: String) = Unit
    override suspend fun updateFact(factId: Long, text: String) = Unit
    override suspend fun deleteFact(factId: Long) = Unit
    override suspend fun makePermanent(factId: Long) = Unit
    override suspend fun undoLastMemoryWrite(): List<Long> = emptyList()
    override suspend fun updateTargetDetails(
        id: Long, name: String, mbti: String?, score: Int?, relationStatus: String?, timelineJson: String,
    ) = Unit

    override suspend fun setCurrentModel(id: Long) = Unit
    override suspend fun setVisionModel(id: Long) = Unit
    override suspend fun setThemeMode(mode: String) = Unit
    override suspend fun testConnection(providerId: Long): LlmError? {
        testConnectionCalls++
        return testConnectionResult
    }
    override suspend fun saveProvider(name: String, baseUrl: String, apiKey: String, isPreset: Boolean): Long = 1L
    override suspend fun updateProvider(id: Long, name: String, baseUrl: String, apiKey: String?) {
        providerUpdates.add(Triple(id, name, apiKey))
    }
    override suspend fun deleteProviderApiKey(providerId: Long) = Unit
    override suspend fun deleteProvider(id: Long) = Unit
    override suspend fun getProviderApiKey(providerId: Long): String? = apiKeyValue
    override suspend fun addModel(providerId: Long, name: String, supportsVision: Boolean) {
        addedModels.add(Triple(providerId, name, supportsVision))
    }
    override suspend fun deleteModel(id: Long) = Unit
    override suspend fun toggleSheetVisible(id: Long) = Unit
    override suspend fun setVisionFlag(id: Long, supportsVision: Boolean) = Unit
    override suspend fun markConnectionStatus(providerId: Long, ok: Boolean) {
        connectionStatusCalls.add(providerId to ok)
    }
    override suspend fun setPrivacyAck(ack: Boolean) {
        privacyAckValue = ack
        privacyAck.value = ack
    }
    override suspend fun wipeAll() = Unit
    override suspend fun importBackup(uri: android.net.Uri): Pair<Boolean, String> = false to "测试未实现"
    override suspend fun exportMemoryJson(): String? = "{\"app\":\"wenyan-android\",\"version\":1}"
    override suspend fun writeMemoryExport(uri: android.net.Uri, json: String): Boolean {
        writtenMemoryExports.add(uri to json)
        return true
    }
    override suspend fun importMemoryMerge(uri: android.net.Uri): Pair<Boolean, String> {
        importedMemoryUris.add(uri)
        return fakeImportMemoryResult
    }
    override suspend fun exportCrashLog(): android.net.Uri? = null
    override suspend fun checkUpdate(): UpdateCheckResult = UpdateCheckResult.UpToDate
    override suspend fun downloadUpdateApk(info: UpdateInfo): java.io.File? = null
    override suspend fun installApk(file: java.io.File): Boolean = false
    override fun usageMetrics(): UsageMetricsUi = UsageMetricsUi(0L, 0L, 0L, 0L, emptyMap())
}
