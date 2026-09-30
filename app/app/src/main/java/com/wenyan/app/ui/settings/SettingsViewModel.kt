package com.wenyan.app.ui.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenyan.app.data.update.UpdateCheckResult
import com.wenyan.app.data.update.UpdateInfo
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.contract.SettingsRepository
import com.wenyan.app.ui.contract.TargetUi
import com.wenyan.app.ui.contract.UsageMetricsUi
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_DEFAULT
import com.wenyan.app.ui.theme.FLUID_HUE_DEFAULT
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 拖滑条时 onValueChange 在拖拽中每次移动都触发；若每次直写 DataStore，
 * 一次拖拽会产生数百次 prefs 文件重写（DataStore edit = 读+序列化+原子替换整个文件）。
 * 这里做 trailing 合并：最后一次调用起算 [SLIDER_WRITE_MERGE_MS] 后落盘，期间的中间值丢弃——
 * 每次拖拽最坏 1 次写字 = 1 次写盘，且末值必写。60ms 低于感知阈值，
 * 背景仍随 DataStore 回流「拖到哪亮到哪」（观感为连续变化，非松手才跳）。
 */
private const val SLIDER_WRITE_MERGE_MS = 60L

/**
 * 设置页状态（AC-09/11/12/18）：提供商列表、主/视觉模型、主题、隐私清除。
 * v1.7.2 新增「记忆」分组：记忆档案列表 / 激活档案 / 自动记忆开关 / Toast / 三个弹窗状态。
 * v1.7.3 新增：导出诊断日志、更新检查/下载状态（T3/T4）。
 * 纯状态装配；读写全部经 SettingsRepository（后端实现）。
 */
class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    private val _providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    val providers: StateFlow<List<ProviderInfo>> = _providers.asStateFlow()

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    private val _currentModelId = MutableStateFlow<Long?>(null)
    val currentModelId: StateFlow<Long?> = _currentModelId.asStateFlow()

    private val _visionModelId = MutableStateFlow<Long?>(null)
    val visionModelId: StateFlow<Long?> = _visionModelId.asStateFlow()

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _privacyAck = MutableStateFlow(false)
    val privacyAck: StateFlow<Boolean> = _privacyAck.asStateFlow()

    private val _showPrivacyDialog = MutableStateFlow(false)
    val showPrivacyDialog: StateFlow<Boolean> = _showPrivacyDialog.asStateFlow()

    private val _showWipeDialog = MutableStateFlow(false)
    val showWipeDialog: StateFlow<Boolean> = _showWipeDialog.asStateFlow()

    // O1: 从备份恢复
    private val _showImportDialog = MutableStateFlow(false)
    val showImportDialog: StateFlow<Boolean> = _showImportDialog.asStateFlow()

    // O6: 用量/诊断面板
    private val _showUsageDialog = MutableStateFlow(false)
    val showUsageDialog: StateFlow<Boolean> = _showUsageDialog.asStateFlow()

    private val _usage = MutableStateFlow<UsageMetricsUi?>(null)
    val usage: StateFlow<UsageMetricsUi?> = _usage.asStateFlow()

    var importing by mutableStateOf(false)
        private set

    // ===== v1.7.2 记忆档案 =====

    private val _targets = MutableStateFlow<List<TargetUi>>(emptyList())
    val targets: StateFlow<List<TargetUi>> = _targets.asStateFlow()

    private val _activeTargetId = MutableStateFlow<Long?>(null)
    val activeTargetId: StateFlow<Long?> = _activeTargetId.asStateFlow()

    private val _memoryAutoEnabled = MutableStateFlow(true)
    val memoryAutoEnabled: StateFlow<Boolean> = _memoryAutoEnabled.asStateFlow()

    /** v1.9.4 流光背景开关（默认 true；init 从 DataStore collect） */
    private val _fluidBackgroundEnabled = MutableStateFlow(true)
    val fluidBackgroundEnabled: StateFlow<Boolean> = _fluidBackgroundEnabled.asStateFlow()

    /** v1.9.4 三改 流光色相（0-360°，默认 0 = 主题原色） */
    private val _fluidHue = MutableStateFlow(FLUID_HUE_DEFAULT)
    val fluidHue: StateFlow<Int> = _fluidHue.asStateFlow()

    /** v1.9.4 三改 背景亮度（0-100，默认 50 = 不叠 veil） */
    private val _bgBrightness = MutableStateFlow(BG_BRIGHTNESS_DEFAULT)
    val bgBrightness: StateFlow<Int> = _bgBrightness.asStateFlow()

    /** 滑条写盘合并任务（见 [SLIDER_WRITE_MERGE_MS]）：新值到来即取消上一笔，只落最后一笔 */
    private var fluidHueWriteJob: Job? = null
    private var bgBrightnessWriteJob: Job? = null

    /** v1.7.2 一次性 Toast（消费后清空，防重组重复弹） */
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _showNameDialog = MutableStateFlow(false)
    val showNameDialog: StateFlow<Boolean> = _showNameDialog.asStateFlow()

    private val _deleteTarget = MutableStateFlow<TargetUi?>(null)
    val deleteTarget: StateFlow<TargetUi?> = _deleteTarget.asStateFlow()

    // ===== v1.7.3 T3 导出诊断日志 / T4 更新检查 =====

    /** v1.7.3 T4 更新检查中 */
    var checkingUpdate by mutableStateOf(false)
        private set

    /** v1.7.3 T4 有新版待确认（AlertDialog 数据源；null = 无弹窗） */
    var updateAvailable by mutableStateOf<UpdateInfo?>(null)
        private set

    /** v1.7.3 T4 正在下载 APK */
    var downloading by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { repo.providers.collect { _providers.value = it } }
        viewModelScope.launch { repo.models.collect { _models.value = it } }
        viewModelScope.launch { repo.currentModelId.collect { _currentModelId.value = it } }
        viewModelScope.launch { repo.visionModelId.collect { _visionModelId.value = it } }
        viewModelScope.launch { repo.themeMode.collect { _themeMode.value = it } }
        viewModelScope.launch { repo.privacyAck.collect { _privacyAck.value = it } }
        viewModelScope.launch { repo.targets.collect { _targets.value = it } }
        viewModelScope.launch { repo.activeTargetId.collect { _activeTargetId.value = it } }
        viewModelScope.launch { repo.memoryAutoEnabled.collect { _memoryAutoEnabled.value = it } }
        viewModelScope.launch { repo.fluidBackgroundEnabled.collect { _fluidBackgroundEnabled.value = it } }
        viewModelScope.launch { repo.fluidHue.collect { _fluidHue.value = it } }
        viewModelScope.launch { repo.bgBrightness.collect { _bgBrightness.value = it } }
    }

    fun setTheme(mode: String) {
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    fun setMainModel(id: Long) {
        viewModelScope.launch { repo.setCurrentModel(id) }
    }

    fun setVisionModel(id: Long) {
        viewModelScope.launch { repo.setVisionModel(id) }
    }

    fun requestPrivacy() {
        _showPrivacyDialog.value = true
    }

    fun dismissPrivacy() {
        _showPrivacyDialog.value = false
    }

    /** AC-18：确认隐私声明后允许配置 Key */
    fun acceptPrivacy() {
        _showPrivacyDialog.value = false
        viewModelScope.launch { repo.setPrivacyAck(true) }
    }

    fun requestUsage() {
        _usage.value = repo.usageMetrics()
        _showUsageDialog.value = true
    }

    fun dismissUsage() {
        _showUsageDialog.value = false
    }

    fun requestImport() {
        _showImportDialog.value = true
    }

    fun dismissImport() {
        _showImportDialog.value = false
    }

    /** O1: 选中的备份 JSON 文件 → 清空重建导入；成功/失败均 Toast 反馈 */
    fun confirmImport(uri: Uri) {
        _showImportDialog.value = false
        if (importing) return
        importing = true
        viewModelScope.launch {
            val (ok, error) = repo.importBackup(uri)
            importing = false
            _toastMessage.value = if (ok) "恢复完成：API Key 已脱敏，请重新输入" else error
        }
    }

    // ===== v1.9.4 记忆导出/导入（换机迁移）=====

    /** v1.9.4 记忆导出进行中（行内「导出中…」+ 防重复点击） */
    var exportingMemory by mutableStateOf(false)
        private set

    /** v1.9.4 记忆合并导入进行中 */
    var importingMemory by mutableStateOf(false)
        private set

    /**
     * v1.9.4 导出记忆到所选文件（CreateDocument 回调 uri）：
     * 生成 JSON 与写入 uri 均走 Repository 分层（与 importBackup 对齐）；成功/失败均 Toast。
     */
    fun exportMemoryTo(uri: Uri) {
        if (exportingMemory) return
        exportingMemory = true
        viewModelScope.launch {
            val json = repo.exportMemoryJson()
            val ok = json != null && repo.writeMemoryExport(uri, json)
            exportingMemory = false
            _toastMessage.value = if (ok) "记忆已导出" else "导出失败，请重试"
        }
    }

    /** v1.9.4 合并导入记忆（确认弹窗后调用）：结果描述（含新建/跳过条数）直接 Toast 反馈 */
    fun confirmMemoryImport(uri: Uri) {
        if (importingMemory) return
        importingMemory = true
        viewModelScope.launch {
            val (_, message) = repo.importMemoryMerge(uri)
            importingMemory = false
            _toastMessage.value = message
        }
    }

    fun requestWipe() {
        _showWipeDialog.value = true
    }

    fun dismissWipe() {
        _showWipeDialog.value = false
    }

    /** AC-12：清除全部档案（Key/档案/会话），确认后回对话页由 AppRoot 处理 */
    fun confirmWipe(onWiped: () -> Unit) {
        _showWipeDialog.value = false
        viewModelScope.launch {
            repo.wipeAll()
            onWiped()
        }
    }

    // ===== v1.7.2 记忆档案操作 =====

    /** 新建记忆档案（空白名称 UI 已禁，仍防御） */
    fun createTarget(name: String) {
        _showNameDialog.value = false
        viewModelScope.launch { repo.createTarget(name) }
    }

    // F52 精简：v1.7.3 移除「编辑记忆弹窗」后，editTarget 状态机（_editTarget/editTarget/
    // requestEditTarget/dismissEditTarget/updateTarget）在生产代码中零引用（唯一调用方是给
    // 死链路续命的测试），一并删除；repo.updateTarget 契约方法同步收敛（档案行「编辑」走
    // onEditTarget 导航 → MemoryEdit 页的 updateTargetDetails，不再经此方法）

    /** 删除记忆档案（确认后；删激活项自动回退在 repo 内完成） */
    fun deleteTarget(id: Long) {
        _deleteTarget.value = null
        viewModelScope.launch { repo.deleteTarget(id) }
    }

    /** 切换激活档案 + Toast「已切换到「X」的记忆」 */
    fun setActiveTarget(target: TargetUi) {
        viewModelScope.launch {
            repo.setActiveTarget(target.id)
            _toastMessage.value = "已切换到「${target.name}」的记忆"
        }
    }

    fun setMemoryAutoEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setMemoryAutoEnabled(enabled) }
    }

    /** v1.9.4 流光背景开关 */
    fun setFluidBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setFluidBackgroundEnabled(enabled) }
    }

    /**
     * v1.9.4 三改 流光色相（0-360°，滑条 step 限制在 UI 层，范围夹取在 DataStore 层）。
     * 本地 StateFlow 立即更新（滑条零延迟）；落盘走 [SLIDER_WRITE_MERGE_MS] 合并写。
     */
    fun setFluidHue(degrees: Int) {
        _fluidHue.value = degrees
        fluidHueWriteJob?.cancel()
        fluidHueWriteJob = viewModelScope.launch {
            delay(SLIDER_WRITE_MERGE_MS)
            repo.setFluidHue(degrees)
        }
    }

    /** v1.9.4 三改 背景亮度（0-100，50 = 中点）：同上，合并落盘 */
    fun setBgBrightness(value: Int) {
        _bgBrightness.value = value
        bgBrightnessWriteJob?.cancel()
        bgBrightnessWriteJob = viewModelScope.launch {
            delay(SLIDER_WRITE_MERGE_MS)
            repo.setBgBrightness(value)
        }
    }

    /** v1.9.0 撤销最近一次自动写入（无日志 → Toast 提示；有 → 删除对应事实） */
    fun undoLastMemoryWrite() {
        viewModelScope.launch {
            val removedIds = repo.undoLastMemoryWrite()
            if (removedIds.isEmpty()) {
                _toastMessage.value = "没有可撤销的自动记忆"
            } else {
                removedIds.forEach { repo.deleteFact(it) }
                _toastMessage.value = "已撤销最近一次自动记忆（${removedIds.size} 条）"
            }
        }
    }

    fun requestCreateTarget() {
        _showNameDialog.value = true
    }

    fun dismissCreateTarget() {
        _showNameDialog.value = false
    }

    fun requestDeleteTarget(target: TargetUi) {
        _deleteTarget.value = target
    }

    fun dismissDeleteTarget() {
        _deleteTarget.value = null
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    // ===== v1.7.3 T3 导出诊断日志 =====

    /** 导出崩溃日志：成功返回可分享 Uri，无则 null（UI 决定是否提示） */
    fun exportCrashLog(onResult: (Uri?) -> Unit) {
        viewModelScope.launch { onResult(repo.exportCrashLog()) }
    }

    // ===== v1.7.3 T4 更新检查 / 下载安装 =====

    /** 手动检查更新：NewVersion → 弹确认弹窗；UpToDate/Failed → Toast（静默不阻塞主流程） */
    fun checkUpdate() {
        if (checkingUpdate) return
        checkingUpdate = true
        viewModelScope.launch {
            when (val result = repo.checkUpdate()) {
                is UpdateCheckResult.NewVersion -> updateAvailable = result.info
                is UpdateCheckResult.UpToDate -> _toastMessage.value = "当前已是最新版本"
                is UpdateCheckResult.Failed -> _toastMessage.value = "检查更新失败，请稍后重试"
            }
            checkingUpdate = false
        }
    }

    fun dismissUpdateDialog() {
        updateAvailable = null
    }

    /** 下载新版 APK → 唤起系统安装器；失败 Toast（不阻塞） */
    fun downloadAndInstall(info: UpdateInfo) {
        if (downloading) return
        downloading = true
        updateAvailable = null
        viewModelScope.launch {
            val file = repo.downloadUpdateApk(info)
            val installed = file != null && repo.installApk(file)
            downloading = false
            if (!installed) {
                _toastMessage.value = "下载失败，请稍后重试"
            }
        }
    }
}
