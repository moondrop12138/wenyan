package com.goutoujunshi.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 唯一色值来源：docs/design-tokens.json（SPEC v1.0 §8 锁定）。
 * 组件内禁止硬编码任何色值；非 M3 槽位的扩展色统一经 LocalGtjColors 读取。
 * 例外白/黑：仅 #FFFFFF / #000000 允许直接使用，仍建议走 token。
 */
@Immutable
data class GtjPalette(
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val fg: Color,
    val fgSecondary: Color,
    val muted: Color,
    val meta: Color,
    val border: Color,
    val borderSoft: Color,
    val accent: Color,
    val accentOn: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val success: Color,
    val warn: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warm: Color,
    val warmSoft: Color,
    val warmOn: Color,
    val scrim: Color,
)

/** 浅色 Token（design-tokens.json color.light，v1.2 P1 暖橙系） */
val LightPalette = GtjPalette(
    bg = Color(0xFFFAF5EF),
    surface = Color(0xFFF4EDE4),
    surfaceElevated = Color(0xFFFFFFFF),
    fg = Color(0xFF2B2118),
    fgSecondary = Color(0xFF4A3D30),
    // 对比度：muted 在 bg(#FAF5EF)/surface(#F4EDE4) 上须 ≥4.5:1（WCAG AA 正文）
    muted = Color(0xFF6E5F4C),
    meta = Color(0xFFB3A48F),
    border = Color(0xFFE8DFD2),
    borderSoft = Color(0xFFF1E9DC),
    // 对比度：accent 白字须 ≥3:1（大字号 AA 例外，ContrastTest 记录性断言）
    accent = Color(0xFFCC6A2B),
    accentOn = Color(0xFFFFFFFF),
    accentHover = Color(0xFFB85F26),
    accentPressed = Color(0xFFA8541F),
    accentSoft = Color(0xFFFCEFE3),
    success = Color(0xFF16A34A),
    warn = Color(0xFFD97706),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0xFFFDECEC),
    warm = Color(0xFFCC6A2B),
    warmSoft = Color(0xFFFCEFE3),
    warmOn = Color(0xFFA8541F),
    scrim = Color(0x732B2118),
)

/** 深色 Token（design-tokens.json color.dark，v1.2 P1 暖橙系） */
val DarkPalette = GtjPalette(
    bg = Color(0xFF191411),
    surface = Color(0xFF211A16),
    surfaceElevated = Color(0xFF2A211B),
    fg = Color(0xFFF5EEE6),
    fgSecondary = Color(0xFFD8C9B8),
    muted = Color(0xFF9C8B78),
    meta = Color(0xFF6E6050),
    border = Color(0xFF382E25),
    borderSoft = Color(0xFF2E2620),
    accent = Color(0xFFEB8B4D),
    accentOn = Color(0xFFFFFFFF),
    accentHover = Color(0xFFF09A60),
    accentPressed = Color(0xFFCC6A2B),
    accentSoft = Color(0xFF3D2A1B),
    success = Color(0xFF16A34A),
    warn = Color(0xFFD97706),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0xFF2A1717),
    warm = Color(0xFFEB8B4D),
    warmSoft = Color(0xFF3D2A1B),
    warmOn = Color(0xFFF09A60),
    scrim = Color(0x99000000),
)

/** 供组件读取扩展色（warm/warn/dangerSoft/meta/accentSoft 等非 M3 槽位） */
val LocalGtjColors = staticCompositionLocalOf { LightPalette }

/** M3 ColorScheme 映射（浅色）。映射关系固定：accent→primary 等，勿随意改。 */
fun lightColorScheme(p: GtjPalette = LightPalette): ColorScheme = ColorScheme(
    primary = p.accent,
    onPrimary = p.accentOn,
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accentPressed,
    inversePrimary = p.accentHover,
    secondary = p.fgSecondary,
    onSecondary = p.bg,
    secondaryContainer = p.surface,
    onSecondaryContainer = p.fg,
    tertiary = p.warm,
    onTertiary = p.accentOn,
    tertiaryContainer = p.warmSoft,
    onTertiaryContainer = p.warmOn,
    background = p.bg,
    onBackground = p.fg,
    surface = p.surface,
    onSurface = p.fg,
    surfaceVariant = p.surfaceElevated,
    onSurfaceVariant = p.muted,
    surfaceTint = p.accent,
    inverseSurface = p.surfaceElevated,
    inverseOnSurface = p.fgSecondary,
    error = p.danger,
    onError = p.accentOn,
    errorContainer = p.dangerSoft,
    onErrorContainer = p.danger,
    outline = p.border,
    outlineVariant = p.borderSoft,
    scrim = p.scrim,
    surfaceBright = p.surfaceElevated,
    surfaceDim = p.surface,
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.surfaceElevated,
    surfaceContainerHighest = p.surfaceElevated,
    surfaceContainerLow = p.surface,
    surfaceContainerLowest = p.bg,
)

/** M3 ColorScheme 映射（深色）。深色 onPrimaryContainer 用 accent 保证对比度。 */
fun darkColorScheme(p: GtjPalette = DarkPalette): ColorScheme = ColorScheme(
    primary = p.accent,
    onPrimary = p.accentOn,
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accent,
    inversePrimary = p.accentHover,
    secondary = p.fgSecondary,
    onSecondary = p.bg,
    secondaryContainer = p.surface,
    onSecondaryContainer = p.fg,
    tertiary = p.warm,
    onTertiary = p.accentOn,
    tertiaryContainer = p.warmSoft,
    onTertiaryContainer = p.warmOn,
    background = p.bg,
    onBackground = p.fg,
    surface = p.surface,
    onSurface = p.fg,
    surfaceVariant = p.surfaceElevated,
    onSurfaceVariant = p.muted,
    surfaceTint = p.accent,
    inverseSurface = p.surfaceElevated,
    inverseOnSurface = p.fgSecondary,
    error = p.danger,
    onError = p.accentOn,
    errorContainer = p.dangerSoft,
    onErrorContainer = p.danger,
    outline = p.border,
    outlineVariant = p.borderSoft,
    scrim = p.scrim,
    surfaceBright = p.surfaceElevated,
    surfaceDim = p.surface,
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.surfaceElevated,
    surfaceContainerHighest = p.surfaceElevated,
    surfaceContainerLow = p.surface,
    surfaceContainerLowest = p.bg,
)
