package com.wenyan.app.ui.theme

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

/** 浅色 Token（design-tokens.json color.light，v1.6.1 陶土棕×暖米白，与启动图标配色对齐） */
val LightPalette = GtjPalette(
    bg = Color(0xFFF6F0E6),
    surface = Color(0xFFEFE6D8),
    surfaceElevated = Color(0xFFFDFAF3),
    fg = Color(0xFF2B2118),
    fgSecondary = Color(0xFF4B4032),
    // 对比度：muted 在 bg(#F6F0E6)/surface(#EFE6D8) 上 ≈4.9-5.7:1（WCAG AA 正文 ≥4.5:1）
    muted = Color(0xFF6E6050),
    meta = Color(0xFFA49785),
    border = Color(0xFFE3D8C5),
    borderSoft = Color(0xFFEFE6D6),
    // 对比度：accent 白字 ≈5.4:1（远超 AA）；accent on accentSoft ≈4.6:1（接住你 pill 小字达标）
    accent = Color(0xFFA4551C),
    accentOn = Color(0xFFFFFFFF),
    accentHover = Color(0xFF934A14),
    accentPressed = Color(0xFF7F400F),
    accentSoft = Color(0xFFF7ECE0),
    success = Color(0xFF16A34A),
    warn = Color(0xFFD97706),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0xFFFBEAE3),
    warm = Color(0xFFC0743F),
    warmSoft = Color(0xFFF7EADC),
    warmOn = Color(0xFF8F4F24),
    scrim = Color(0x732B2118),
)

/** 深色 Token（design-tokens.json color.dark，v1.6.1 暖黑×杏棕，与启动图标配色对齐） */
val DarkPalette = GtjPalette(
    bg = Color(0xFF17120E),
    surface = Color(0xFF211A13),
    surfaceElevated = Color(0xFF2B221A),
    fg = Color(0xFFF1EAE0),
    fgSecondary = Color(0xFFCFC3B1),
    // 对比度：muted 在 bg(#17120E) 上 ≈6.4:1（WCAG AA 正文 ≥4.5:1）
    muted = Color(0xFFAC9D8A),
    meta = Color(0xFF6E6153),
    border = Color(0xFF3A3026),
    borderSoft = Color(0xFF2E261D),
    // 深色主色为杏棕：onAccent 用深棕黑字（M3 深色惯例），对比度 ≈6.4:1
    accent = Color(0xFFCE8A56),
    accentOn = Color(0xFF221104),
    accentHover = Color(0xFFDB9C6B),
    accentPressed = Color(0xFFBE7A45),
    accentSoft = Color(0xFF332417),
    success = Color(0xFF16A34A),
    warn = Color(0xFFD97706),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0xFF2A1717),
    warm = Color(0xFFDFA678),
    warmSoft = Color(0xFF3A2A1C),
    warmOn = Color(0xFFF2CBA9),
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
