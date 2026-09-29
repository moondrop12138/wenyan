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
    // ── v1.7.0 液态玻璃（唯一来源 docs/design-tokens.json color.light/dark.glass.*，参数固化自 outputs/liquid-glass-prototype.html）──
    val glassFill: Color,          // 玻璃主填充
    val glassFillStrong: Color,    // 强玻璃（输入胶囊/高密度容器）
    val glassBorder: Color,        // 玻璃描边 1dp
    val glassEdgeHighlight: Color, // 顶部高光线（1.5dp 渐隐）
    val glassShadow: Color,        // 柔和外投影
    // ── v1.9.4 玻璃材质升级（唯一来源 docs/design-tokens.json color.light/dark.glass.*）──
    val glassHairlineOuter: Color, // 双发丝描边·外圈深线（玻璃边缘的物理厚度感）
    val glassHairlineInner: Color, // 双发丝描边·内圈亮线（对应桌面 inset 0 1px 内高光）
    val glassSpecular: Color,      // 顶部方向性 specular 高光基色（多停靠渐变）
    val glassInnerShade: Color,    // 底部内阴影
    val glassGrainLight: Color,    // 磨砂颗粒·亮噪色
    val glassGrainDark: Color,     // 磨砂颗粒·暗噪色
    val glowA: Color,              // 光斑 A（径向渐变中心色）
    val glowB: Color,              // 光斑 B
    val glowC: Color,              // 光斑 C
    // ── v1.9.4 流光背景（唯一来源 docs/design-tokens.json color.light/dark.fluid.*，与桌面 aqua-fluid.js paletteForTheme 完全一致）──
    val fluidA: Color,             // 流光基色 1：浅色陶土棕 / 深色深棕
    val fluidB: Color,             // 流光基色 2：浅色暖米白 / 深色暖黑
    val fluidC: Color,             // 流光基色 3：浅色白 / 深色近黑
    val dotConnected: Color,       // 状态点·已连接（橄榄绿）
    val dotConnecting: Color,      // 状态点·连接中（杏棕呼吸）
    val dotThinking: Color,        // 状态点·思考中（赭石呼吸）
    val dotFailure: Color,         // 状态点·失败（灰）
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
    // 玻璃（原型浅色值）：fill rgba(253,249,242,.40) / strong rgba(253,248,240,.549)（v1.9.4 三改通透化：.55/.72 → .40/.549，
    // 让流光背景透进卡片内部；边缘轮廓由双发丝描边 + specular 承担）/
    // border rgba(255,255,255,.75) / edge rgba(255,255,255,.95) / shadow rgba(110,70,30,.18)
    // 可读性核算（GlassTransparencyReadabilityTest）：卡片级 backdrop 最坏底（流光最深色 #C0743F 经 saturate(170%))
    // 上 fg ≈8.0:1、fgSecondary ≈5.1:1（WCAG AA ≥4.5:1 达标）；muted 标题小字 ≈3.1:1 与改动前 3.6:1 同量级
    // （浅色 muted 在流光最深处本就低于 AA，故正文层用 fg/fgSecondary，见 design-pages 无障碍基线）。
    // 色相滑条维度（评审修复）：基色经 W3C hue-rotate 矩阵旋转后 luma 保持，全量程逐度扫描
    // fg ≥7.51、fgSecondary ≥4.82（两主题均达标；曾用 HSL 旋转时 190°-344° 区间跌到 3.31:1）
    glassFill = Color(0x66FDF9F2), // 0.40 × 255 ≈ 102 = 0x66
    glassFillStrong = Color(0x8CFDF8F0), // 0.549 × 255 ≈ 140 = 0x8C（= 旧 glassFill 值）
    glassBorder = Color(0xBFFFFFFF),
    glassEdgeHighlight = Color(0xF2FFFFFF),
    glassShadow = Color(0x2E6E461E),
    // v1.9.4 材质升级（浅色）：外深内浅双发丝 + 多停靠 specular + 底部内阴影 + 磨砂颗粒
    glassHairlineOuter = Color(0x406E461E), // rgba(110,70,30,0.25) 陶土深棕细线
    glassHairlineInner = Color(0xB3FFFFFF), // rgba(255,255,255,0.70)（对齐桌面 inset 高光 .5 提亮）
    glassSpecular = Color(0x4DFFFFFF),      // rgba(255,255,255,0.30) 基色，停靠点在组件内展开
    glassInnerShade = Color(0x1A6E461E),    // rgba(110,70,30,0.10) 底部内阴影
    glassGrainLight = Color(0xFFFFFFFF),    // 颗粒亮噪（白）
    glassGrainDark = Color(0xFF6E461E),     // 颗粒暗噪（陶土深棕）
    // 光斑（v1.7.1 调柔：浅色浓度下调，避免径向渐变边缘生硬成色块；原型值 .85/.75/.55）
    glowA = Color(0x80F2CBA9), // rgba(242,203,169,.50)
    glowB = Color(0x6BDFA678), // rgba(223,166,120,.42)
    glowC = Color(0x4DC0743F), // rgba(192,116,63,.30)
    // 流光（v1.9.4：桌面 paletteForTheme 浅色 #C0743F/#F6F0E6/#FFFFFF）
    fluidA = Color(0xFFC0743F),
    fluidB = Color(0xFFF6F0E6),
    fluidC = Color(0xFFFFFFFF),
    // 状态点四态（跨主题恒定，原型 sdot）
    dotConnected = Color(0xFF7FA65A),
    dotConnecting = Color(0xFFDFA678),
    dotThinking = Color(0xFFC0743F),
    dotFailure = Color(0xFF9A8F85),
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
    // 玻璃（原型深色值）：fill rgba(46,36,28,.33) / strong rgba(34,26,20,.549)（v1.9.4 三改通透化：.45/.74 → .33/.549，
    // 与浅色同比例下调 ~27%，让深色流光（#5C3F28 暖褐涌动）透进卡片）/
    // border rgba(255,255,255,.12) / edge rgba(255,255,255,.35) / shadow rgba(0,0,0,.5)
    // 可读性核算：深色最深流光区卡片仍由 glassHairlineInner(10% 白) + specular 勾边；fg 合成底 ≈9:1 达标
    glassFill = Color(0x542E241C), // 0.33 × 255 ≈ 84 = 0x54
    glassFillStrong = Color(0x8C221A14), // 0.549 × 255 ≈ 140 = 0x8C
    glassBorder = Color(0x1FFFFFFF),
    glassEdgeHighlight = Color(0x59FFFFFF),
    glassShadow = Color(0x80000000),
    // v1.9.4 材质升级（深色）：外深内浅双发丝 + 多停靠 specular + 底部内阴影 + 磨砂颗粒
    glassHairlineOuter = Color(0x73000000), // rgba(0,0,0,0.45) 近黑细线
    glassHairlineInner = Color(0x1AFFFFFF), // rgba(255,255,255,0.10)（对齐桌面深色 inset .07 微调）
    glassSpecular = Color(0x1AFFFFFF),      // rgba(255,255,255,0.10) 基色
    glassInnerShade = Color(0x47000000),    // rgba(0,0,0,0.28) 底部内阴影
    glassGrainLight = Color(0xFFFFFFFF),    // 颗粒亮噪（白）
    glassGrainDark = Color(0xFF000000),     // 颗粒暗噪（黑）
    // 光斑（v1.7.1 微调：深色稍提亮保持暖氛围）
    glowA = Color(0x73CE8A56), // rgba(206,138,86,.45)
    glowB = Color(0x4DDFA678), // rgba(223,166,120,.30)
    glowC = Color(0xBF3A2A1C), // rgba(58,42,28,.75)
    // 流光（v1.9.4：桌面 paletteForTheme 深色 #5C3F28/#211A13/#0B0705）
    fluidA = Color(0xFF5C3F28),
    fluidB = Color(0xFF211A13),
    fluidC = Color(0xFF0B0705),
    // 状态点四态（跨主题恒定）
    dotConnected = Color(0xFF7FA65A),
    dotConnecting = Color(0xFFDFA678),
    dotThinking = Color(0xFFC0743F),
    dotFailure = Color(0xFF9A8F85),
)

/** 供组件读取扩展色（warm/warn/dangerSoft/meta/accentSoft 等非 M3 槽位） */
val LocalGtjColors = staticCompositionLocalOf { LightPalette }

/** v1.9.4 流光背景总开关（默认开启；设置页可 provides false，关闭后 FluidBackground 不绘制、露出主题底色） */
val LocalFluidBackground = staticCompositionLocalOf { true }

// ── v1.9.4 三改 流光可调（色相 + 背景亮度；唯一来源 docs/design-tokens.json component.fluidAppearance）──

/** 流光色相范围（度）。0 = 主题原色（与不可调版本完全一致），顺时针旋转 fluidA/B/C 三基色。 */
const val FLUID_HUE_MIN = 0
const val FLUID_HUE_MAX = 360
const val FLUID_HUE_DEFAULT = 0

/** 背景亮度范围。50 = 中点 = 不叠加任何 veil（与不可调版本完全一致）。 */
const val BG_BRIGHTNESS_MIN = 0
const val BG_BRIGHTNESS_MAX = 100
const val BG_BRIGHTNESS_DEFAULT = 50

/**
 * 流光色相（度，0-360）：FluidBackground 把 fluidA/B/C 做 W3C hue-rotate 矩阵旋转后送进 shader 的
 * uColor1/2/3；GlowBackground 降级路径同样旋转 glowA/B/C。默认 0 = 原色（原样返回，逐位一致）。
 */
val LocalFluidHue = staticCompositionLocalOf { FLUID_HUE_DEFAULT }

/**
 * 背景亮度（0-100）：>50 叠白、<50 叠黑（veil 语义与桌面 app.js applyGlass 的
 * --wy-brightness-white/black 完全一致，浅色主题只叠白、深色主题只叠黑）。
 * 默认 50 = 不叠加，观感与不可调版本完全一致。
 */
val LocalBgBrightness = staticCompositionLocalOf { BG_BRIGHTNESS_DEFAULT }

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
