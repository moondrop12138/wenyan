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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    /** v1.7.2 一次性 Toast（消费后清空，防重组重复弹） */
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _showNameDialog = MutableStateFlow(false)
    val showNameDialog: StateFlow<Boolean> = _showNameDialog.asStateFlow()

    private val _editTarget = MutableStateFlow<TargetUi?>(null)
    val editTarget: StateFlow<TargetUi?> = _editTarget.asStateFlow()

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

    /** 改名 + 编辑记忆正文 */
    fun updateTarget(id: Long, name: String, note: String) {
        _editTarget.value = null
        viewModelScope.launch { repo.updateTarget(id, name, note) }
    }

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

    fun requestEditTarget(target: TargetUi) {
        _editTarget.value = target
    }

    fun dismissEditTarget() {
        _editTarget.value = null
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
