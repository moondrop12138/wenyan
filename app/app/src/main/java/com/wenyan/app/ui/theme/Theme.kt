package com.wenyan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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

/** 全局主题装配：三态主题（浅色/深色/跟随系统），全部页面同体验（AC-16）。 */
@Composable
fun GtjTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    val colorScheme = if (dark) darkColorScheme(palette) else lightColorScheme(palette)
    CompositionLocalProvider(LocalGtjColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GtjTypography,
            shapes = GtjShapes,
            content = content,
        )
    }
}
