package com.wenyan.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenyan.app.ui.contract.AppContainer
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_DEFAULT
import com.wenyan.app.ui.theme.FLUID_HUE_DEFAULT
import com.wenyan.app.ui.theme.GLASS_FROST_DEFAULT
import com.wenyan.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 顶层状态（主题三态 + 首启问卷完成态 + 流光背景开关 v1.9.4）。
 * 只做状态装配与路由决策，零业务；Repository 由 AppContainer 注入。
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** v1.9.4 流光背景总开关（默认 true；DataStore 加载后随设置页开关实时更新） */
    private val _fluidBackground = MutableStateFlow(true)
    val fluidBackground: StateFlow<Boolean> = _fluidBackground.asStateFlow()

    /** v1.9.4 三改 流光色相（0-360°，默认 0 = 主题原色；设置页滑条写入后经 DataStore 回流） */
    private val _fluidHue = MutableStateFlow(FLUID_HUE_DEFAULT)
    val fluidHue: StateFlow<Int> = _fluidHue.asStateFlow()

    /** v1.9.4 三改 背景亮度（0-100，默认 50 = 不叠 veil） */
    private val _bgBrightness = MutableStateFlow(BG_BRIGHTNESS_DEFAULT)
    val bgBrightness: StateFlow<Int> = _bgBrightness.asStateFlow()

    /** v1.9.4 玻璃可调 磨砂度（0-100，默认 60 = 磨砂档；经 GtjTheme 的 withGlassFrost 派生玻璃填充 alpha） */
    private val _glassFrost = MutableStateFlow(GLASS_FROST_DEFAULT)
    val glassFrost: StateFlow<Int> = _glassFrost.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    /** M21 修复：首值是否已从 DataStore 加载（false 期间路由不可信，UI 渲染空白占位防闪现问卷页） */
    private val _onboardingLoaded = MutableStateFlow(false)
    val onboardingLoaded: StateFlow<Boolean> = _onboardingLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.themeMode.collect { key ->
                _themeMode.value = ThemeMode.fromKey(key)
            }
        }
        viewModelScope.launch {
            container.settingsRepository.fluidBackgroundEnabled.collect { enabled ->
                _fluidBackground.value = enabled
            }
        }
        viewModelScope.launch {
            container.settingsRepository.fluidHue.collect { degrees -> _fluidHue.value = degrees }
        }
        viewModelScope.launch {
            container.settingsRepository.bgBrightness.collect { value -> _bgBrightness.value = value }
        }
        viewModelScope.launch {
            container.settingsRepository.glassFrost.collect { value -> _glassFrost.value = value }
        }
        viewModelScope.launch {
            container.onboardingRepository.onboardingCompleted.collect { done ->
                _onboardingCompleted.value = done
                _onboardingLoaded.value = true
            }
        }
    }
}
