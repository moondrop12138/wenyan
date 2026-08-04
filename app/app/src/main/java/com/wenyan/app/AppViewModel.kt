package com.wenyan.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenyan.app.ui.contract.AppContainer
import com.wenyan.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 顶层状态（主题三态 + 首启问卷完成态）。
 * 只做状态装配与路由决策，零业务；Repository 由 AppContainer 注入。
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.themeMode.collect { key ->
                _themeMode.value = ThemeMode.fromKey(key)
            }
        }
        viewModelScope.launch {
            container.onboardingRepository.onboardingCompleted.collect { done ->
                _onboardingCompleted.value = done
            }
        }
    }
}
