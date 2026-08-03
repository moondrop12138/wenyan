package com.goutoujunshi.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goutoujunshi.app.ui.contract.ModelInfo
import com.goutoujunshi.app.ui.contract.ProviderInfo
import com.goutoujunshi.app.ui.contract.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设置页状态（AC-09/11/12/18）：提供商列表、主/视觉模型、主题、隐私清除。
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

    init {
        viewModelScope.launch { repo.providers.collect { _providers.value = it } }
        viewModelScope.launch { repo.models.collect { _models.value = it } }
        viewModelScope.launch { repo.currentModelId.collect { _currentModelId.value = it } }
        viewModelScope.launch { repo.visionModelId.collect { _visionModelId.value = it } }
        viewModelScope.launch { repo.themeMode.collect { _themeMode.value = it } }
        viewModelScope.launch { repo.privacyAck.collect { _privacyAck.value = it } }
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
}
