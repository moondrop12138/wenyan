package com.wenyan.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

/** v1.8.1 B5：显式深色模式标记（替代 bg.red 启发式，陶土棕/中性灰背景下不会误判） */
val LocalGtjIsDark = staticCompositionLocalOf { false }

/** 主题模式（DataStore.theme：light / dark / system，SPEC §8） */
enum class ThemeMode(val key: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * 全局主题装配：三态主题（浅色/深色/跟随系统），全部页面同体验（AC-16）。
 * v1.9.4 玻璃可调：[glassFrost]（0-1）按 web 公式运行时派生四项玻璃填充 alpha
 * （[withGlassFrost]），RGB 与静态基值 token 不变；默认 0.30 = 静态基值 frost 基准
 * （唯一调用点 MainActivity 传设置页磨砂度滑条值 / 100f）。
 */
@Composable
fun GtjTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    glassFrost: Float = 0.30f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = (if (dark) DarkPalette else LightPalette).withGlassFrost(glassFrost)
    val colorScheme = if (dark) darkColorScheme(palette) else lightColorScheme(palette)
    // v1.6.3 沉浸式手势小白条：系统栏外观跟随 App 主题（三态 DataStore 驱动）。
    // enableEdgeToEdge 是 Activity 一次性设置且只随系统深浅，这里用 SideEffect 按解析后的
    // dark 覆盖：三键图标色/状态栏图标色随主题；手势条颜色由 Android 15 系统自动对比
    // （浅底→深灰条、深底→浅条），无需也无法手动指定。API 26-28 无透明导航栏，
    // navigationBarColor 用背景色顶替，避免露出 windowBackground 白。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val ctl = WindowInsetsControllerCompat(window, view)
            ctl.isAppearanceLightStatusBars = !dark
            ctl.isAppearanceLightNavigationBars = !dark
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                window.navigationBarColor = palette.bg.toArgb()
            }
        }
    }
    CompositionLocalProvider(LocalGtjColors provides palette, LocalGtjIsDark provides dark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GtjTypography,
            shapes = GtjShapes,
            content = content,
        )
    }
}
