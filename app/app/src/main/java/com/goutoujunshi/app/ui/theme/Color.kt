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

/** 浅色 Token（design-tokens.json color.light） */
val LightPalette = GtjPalette(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F8FA),
    surfaceElevated = Color(0xFFFFFFFF),
    fg = Color(0xFF111827),
    fgSecondary = Color(0xFF374151),
    muted = Color(0xFF6B7280),
    meta = Color(0xFF9CA3AF),
    border = Color(0xFFE5E7EB),
    borderSoft = Color(0xFFEEF0F3),
    accent = Color(0xFF4D6BFE),
    accentOn = Color(0xFFFFFFFF),
    accentHover = Color(0xFF3D5AF5),
    accentPressed = Color(0xFF2E47E0),
    accentSoft = Color(0xFFEEF1FF),
    success = Color(0xFF16A34A),
    warn = Color(0xFFD97706),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0xFFFDECEC),
    warm = Color(0xFFE8873E),
    warmSoft = Color(0xFFFCF1E7),
    warmOn = Color(0xFFB45309),
    scrim = Color(0x73000000),
)

/** 深色 Token（design-tokens.json color.dark） */
val DarkPalette = GtjPalette(
    bg = Color(0xFF0F1117),
    surface = Color(0xFF161B22),
    surfaceElevated = Color(0xFF1C222C),
    fg = Color(0xFFF2F4F8),
    fgSecondary = Color(0xFFC9D1DB),
    muted = Color(0xFF8B949E),
    meta = Color(0xFF62666D),
    border = Color(0xFF262B36),
    borderSoft = Color(0xFF20262F),
    accent = Color(0xFF4D6BFE),
    accentOn = Color(0xFFFFFFFF),
    accentHover = Color(0xFF5D77FF),
    accentPressed = Color(0xFF3A52E8),
    accentSoft = Color(0xFF1E2440),
    success = Color(0xFF16A34A),
    warn = Color(0xFFD97706),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0xFF2A1717),
    warm = Color(0xFFE8873E),
    warmSoft = Color(0xFF2B1F14),
    warmOn = Color(0xFFE8873E),
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
