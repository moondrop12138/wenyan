package com.wenyan.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.contract.SettingsRepository
import kotlinx.coroutines.launch

/** 测试连接结果三态（design-pages 页面3） */
data class TestResult(
    val ok: Boolean,
    val warn: Boolean,
    val message: String,
)

/**
 * 提供商编辑状态（AC-09/11）：名称/Host/Key（密文显隐）+ 模型管理 + 测试连接。
 * providerId <= 0 表示新建。
 */
class ProviderEditViewModel(
    private val repo: SettingsRepository,
    private val providerId: Long,
) : ViewModel() {

    val isNew: Boolean = providerId <= 0

    var name by mutableStateOf("")
    var baseUrl by mutableStateOf("")
    var apiKey by mutableStateOf("")
    var showKey by mutableStateOf(false)

    var models by mutableStateOf<List<ModelInfo>>(emptyList())
    var newModelName by mutableStateOf("")
    var newModelVision by mutableStateOf(false)

    /** v1.6.3 提供商连接状态（红绿灯）："ok"=绿灯，""=未测试/失败红灯 */
    var connectionStatus by mutableStateOf("")

    var testing by mutableStateOf(false)
    var testResult by mutableStateOf<TestResult?>(null)
    var saving by mutableStateOf(false)
    var showDeleteDialog by mutableStateOf(false)
    var showPrivacyDialog by mutableStateOf(false)
    var privacyAck by mutableStateOf(false)
    var pendingAction by mutableStateOf<PendingAction?>(null)

    /** 隐私确认后待执行的动作（AC-18：首次保存 Key 前必须确认） */
    sealed interface PendingAction {
        data object Save : PendingAction
        data object Test : PendingAction

        /** 隐私确认后先存 provider 再续加模型（保存原始添加意图，避免确认后丢失） */
        data class SaveAndAddModel(
            val modelName: String,
            val supportsVision: Boolean,
        ) : PendingAction
    }

    init {
        viewModelScope.launch {
            repo.privacyAck.collect { privacyAck = it }
        }
        if (!isNew) {
            viewModelScope.launch {
                repo.providers.collect { providers ->
                    providers.firstOrNull { it.id == providerId }?.let { p ->
                        name = p.name
                        baseUrl = p.baseUrl
                        connectionStatus = p.connectionStatus // v1.6.3 红绿灯
                    }
                }
            }
            viewModelScope.launch {
                repo.models.collect { list ->
                    models = list.filter { it.providerId == providerId }
                }
            }
        }
    }

    fun toggleKeyVisibility() {
        showKey = !showKey
    }

    fun testConnection() {
        // AC-18：填写了 API Key 但未确认隐私声明 → 先弹确认
        if (apiKey.isNotBlank() && !privacyAck) {
            pendingAction = PendingAction.Test
            showPrivacyDialog = true
            return
        }
        doTestConnection()
    }

    private fun doTestConnection() {
        if (testing) return
        testing = true
        testResult = null
        viewModelScope.launch {
            // 新建时先保存返回 id 再测；预设/已有直接测
            val id = if (isNew) {
                repo.saveProvider(name.ifBlank { "未命名服务" }, baseUrl, apiKey, isPreset = false)
            } else {
                repo.updateProvider(providerId, name, baseUrl, apiKey.ifBlank { null })
                providerId
            }
            testResult = when (val err = repo.testConnection(id)) {
                null -> TestResult(ok = true, warn = false, message = "连接正常，模型可用")
                else -> errorToResult(err)
            }
            testing = false
        }
    }

    /** AC-18：确认隐私声明后执行待办动作并持久化 ack */
    fun acceptPrivacy() {
        showPrivacyDialog = false
        viewModelScope.launch {
            repo.setPrivacyAck(true)
            privacyAck = true
            val action = pendingAction
            when (action) {
                PendingAction.Save -> doSave()
                PendingAction.Test -> doTestConnection()
                is PendingAction.SaveAndAddModel -> doSaveAndAddModel(action.modelName, action.supportsVision)
                null -> Unit
            }
            pendingAction = null
        }
    }

    fun dismissPrivacy() {
        showPrivacyDialog = false
        pendingAction = null
    }

    private fun errorToResult(err: LlmError): TestResult = when {
        err.code == "401" -> TestResult(ok = false, warn = false, message = "API Key 无效，请检查")
        err.code == "429" -> TestResult(ok = false, warn = true, message = "请求过于频繁或额度已用尽，稍后重试")
        err.code.startsWith("5") -> TestResult(ok = false, warn = true, message = "模型服务异常，请稍后重试")
        err.code == "timeout" -> TestResult(ok = false, warn = true, message = "连接超时，请检查网络或服务地址")
        else -> TestResult(ok = false, warn = true, message = err.message.ifBlank { "连接失败" })
    }

    fun addModel() {
        val nameTrim = newModelName.trim()
        if (nameTrim.isEmpty()) return
        // AC-18：填写了 API Key 但未确认隐私声明 → 先弹确认（新建场景），确认后仍续加模型
        if (isNew && apiKey.isNotBlank() && !privacyAck) {
            pendingAction = PendingAction.SaveAndAddModel(nameTrim, newModelVision)
            showPrivacyDialog = true
            return
        }
        doAddModel(nameTrim, newModelVision)
    }

    private fun doAddModel(nameTrim: String, supportsVision: Boolean) {
        viewModelScope.launch {
            val id = if (isNew) {
                repo.saveProvider(name.ifBlank { "未命名服务" }, baseUrl, apiKey, isPreset = false)
            } else providerId
            repo.addModel(id, nameTrim, supportsVision)
            newModelName = ""
            newModelVision = false
        }
    }

    /** 隐私确认后：先保存 provider，再按原意图添加模型（AC-18 意图保留） */
    private fun doSaveAndAddModel(modelName: String, supportsVision: Boolean) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            val id = if (isNew) {
                repo.saveProvider(name.ifBlank { "未命名服务" }, baseUrl, apiKey, isPreset = false)
            } else providerId
            repo.addModel(id, modelName, supportsVision)
            newModelName = ""
            newModelVision = false
            saving = false
        }
    }

    fun deleteModel(id: Long) {
        viewModelScope.launch { repo.deleteModel(id) }
    }

    fun setVision(id: Long, supportsVision: Boolean) {
        viewModelScope.launch { repo.setVisionFlag(id, supportsVision) }
    }

    /** v1.6.3 切换模型在主页"选择模型"弹层的可见性（替代原"设为默认"单选） */
    fun toggleSheetVisible(id: Long) {
        viewModelScope.launch { repo.toggleSheetVisible(id) }
    }

    fun requestDelete() {
        showDeleteDialog = true
    }

    fun dismissDelete() {
        showDeleteDialog = false
    }

    fun save(onDone: () -> Unit) {
        // AC-18：填写了 API Key 但未确认隐私声明 → 先弹确认
        if (apiKey.isNotBlank() && !privacyAck) {
            pendingAction = PendingAction.Save
            showPrivacyDialog = true
            return
        }
        doSave(onDone)
    }

    private fun doSave(onDone: () -> Unit = {}) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            val id = if (isNew) {
                repo.saveProvider(name.ifBlank { "未命名服务" }, baseUrl, apiKey, isPreset = false)
            } else {
                repo.updateProvider(providerId, name, baseUrl, apiKey.ifBlank { null })
                providerId
            }
            // v1.6.3 保存后立即测试连接并写入红绿灯状态：成功绿灯，失败/未填 Key 红灯
            if (apiKey.isBlank()) {
                repo.markConnectionStatus(id, ok = false)
            } else {
                val err = repo.testConnection(id)
                repo.markConnectionStatus(id, ok = err == null)
            }
            saving = false
            onDone()
        }
    }

    fun deleteProvider(onDone: () -> Unit) {
        viewModelScope.launch {
            if (!isNew) repo.deleteProvider(providerId)
            onDone()
        }
    }
}
