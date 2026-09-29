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
    val glassFill: Color,          // 玻璃主填充（Frost 组直色：侧栏/胶囊/气泡/弹层/toast，对应桌面 frost 版 --glass）
    val glassFillStrong: Color,    // 强玻璃（web --glass-strong 镜像 token，当前无消费方，供高密度容器备选）
    val glassBorder: Color,        // 玻璃描边 1dp（引擎外圈描边，= web border: 1px solid var(--glass-border)）
    val glassEdgeHighlight: Color, // 顶部高光线（1.5dp 渐隐）
    val glassShadow: Color,        // 柔和外投影远影（玻璃卡片默认，= web --g-shadow 0 14px 40px；Mica 栏另有栏级阴影）
    // ── v1.9.4 Mica 悬浮栏渐变填充 + 栏级阴影/聚焦环（唯一来源 docs/design-tokens.json
    // color.*.glass.cardFill*/shadowTopBar/shadowInputBar/focusRing；对应桌面增强 Mica
    // --wy-card-light/dark（styles.css:526-531）、0 8px 28px|32px 双阴影（styles.css:535/539/556/559）、
    // :focus-within 0 0 0 2px var(--l2b)（styles.css:542/562）；frost 默认 0.300。仅顶栏/输入栏
    // 经 GlassFill.Card 选用——web cautions：与 Frost 组色相不同，勿混用）──
    val glassCardFillTop: Color,          // 渐变顶停靠点（亮=白 frost×0.50 / 暗=rgb(42,46,56) frost×0.50）
    val glassCardFillBottom: Color,       // 渐变底停靠点（亮=白 frost×0.35 / 暗=rgb(22,25,34) frost×0.50）
    val glassShadowTopBar: Color,         // 顶栏投影色（web 0 8px 28px）
    val glassShadowInputBar: Color,       // 输入栏投影色（web 0 8px 32px 单独二次覆盖，不与顶栏合并）
    val glassFocusRing: Color,            // 输入栏聚焦环（web :focus-within 追加 0 0 0 2px var(--l2b)）
    // ── v1.9.4 玻璃材质（唯一来源 docs/design-tokens.json color.light/dark.glass.*）──
    // 卡片近影（web --g-shadow 第二影 `0 2px 6px rgba(110,70,30,.1)`，styles.css:18）：暗色
    // web 的 --g-shadow 只有一条（styles.css:40）→ 暗色取全透明，alpha==0 时引擎直接跳过不画。
    val glassShadowNear: Color,
    // ── v1.9.4 Mica 内衬输入框 / chip 半透明底（唯一来源 docs/design-tokens.json
    // color.light/dark.glass.inputFill/insetShade/insetHighlight/chip；对应桌面 :root
    // --inp（styles.css:24/46）、--inset（styles.css:20/42）、.input-box 底部
    // `inset 0 -1px 0 rgba(255,255,255,.5)`（styles.css:224/228）、--chip（styles.css:20/42））──
    val glassInputFill: Color,     // 内衬输入框半透明底（亮白 .55 / 暗白 .07）
    val glassInsetShade: Color,    // 输入框顶部内凹阴影基色（亮 rgba(120,80,40,.09) / 暗 rgba(0,0,0,.32)）
    val glassInsetHighlight: Color, // 输入框底部 1px 白高光（亮 .50 / 暗 .08）
    val glassChip: Color,          // chip 半透明 accent 底（亮 rgba(164,85,28,.12) / 暗 rgba(206,138,86,.18)）
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
    // 玻璃（v1.9.4 对齐 web 增强 Mica 两组填充，frost 默认 0.300）：
    // fill rgba(253,249,242,.30)（= 桌面 frost 版 --glass，styles.css:483；Frost 组默认填充）/
    // fillStrong rgba(253,248,240,.47)（= --glass-strong frost+0.17，styles.css:484；弹层 .sheet
    // 即 glass-strong 组，见 ModelSheet）；
    // 顶/输入栏填充走下方 Mica 渐变 cardFill*（--wy-card-light，styles.css:526-528），
    // 顶/底 alpha = frost×0.50/frost×0.35 → .150/.105；边框 border rgba(255,255,255,.75)
    // （styles.css:82，Mica 下原样生效，引擎外圈描边实绘）/ edge rgba(255,255,255,.95)
    // 可读性核算（GlassTransparencyReadabilityTest，backdrop=saturate(170%)+brightness(1.0)）：
    // Frost 组卡片：流光最深色 #C0743F 上 fg 全量程最坏 ≈6.47:1（AA 达标）、浅色 fgSecondary
    // ≈4.46/全量程 4.15 低于 AA；悬浮栏 Card 渐变组（.150/.105 白）fg 最坏 ≈4.99:1 达标、
    // fgSecondary ≈3.20/muted ≈1.93 低于 AA——故**正文/卡片用色点一律用 fg**（v1.9.4 评审修复：
    // ErrorCard 正文、CoachCard「接住你」与理由、空态示例问题已由 fgSecondary 改 fg），
    // fgSecondary/muted 只承担非正文的次级信息，token 值不变（web 对齐优先，不回退填充）。
    glassFill = Color(0x4DFDF9F2), // 0.30 × 255 ≈ 77 = 0x4D
    glassFillStrong = Color(0x78FDF8F0), // 0.47 × 255 ≈ 120 = 0x78
    glassBorder = Color(0xBFFFFFFF),
    glassEdgeHighlight = Color(0xF2FFFFFF),
    glassShadow = Color(0x2E6E461E), // rgba(110,70,30,0.18)（web --g-shadow 远影 0 14px 40px）
    // v1.9.4 Mica 悬浮栏渐变填充（--wy-card-light：白 frost×0.50 → frost×0.35）+ 栏阴影/聚焦环
    glassCardFillTop = Color(0x26FFFFFF),          // 0.150 × 255 ≈ 38 = 0x26
    glassCardFillBottom = Color(0x1BFFFFFF),       // 0.105 × 255 ≈ 27 = 0x1B
    glassShadowTopBar = Color(0x1A6E461E),         // rgba(110,70,30,0.10)（web 0 8px 28px）
    glassShadowInputBar = Color(0x1F6E461E),       // rgba(110,70,30,0.12)（web 0 8px 32px）
    glassFocusRing = Color(0x38A4551C),            // rgba(164,85,28,0.22) = web --l2b 亮值
    glassShadowNear = Color(0x1A6E461E),           // rgba(110,70,30,0.10) = web --g-shadow 近影 0 2px 6px
    // v1.9.4 内衬输入框 / chip（web --inp / --inset / .input-box 底部高光 / --chip）
    glassInputFill = Color(0x8CFFFFFF),            // 0.55 × 255 = 140 = 0x8C（web --inp 亮值）
    glassInsetShade = Color(0x17605028),           // rgba(120,80,40,0.09)（web --inset 亮值）
    glassInsetHighlight = Color(0x80FFFFFF),       // rgba(255,255,255,0.50)（.input-box inset 0 -1px 0 亮值）
    glassChip = Color(0x1FA4551C),                 // rgba(164,85,28,0.12)（web --chip 亮值）
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
    // 玻璃（v1.9.4 对齐 web 增强 Mica 两组填充，frost 默认 0.300）：
    // fill rgba(46,36,28,.30)（= 桌面 frost 版 --glass，styles.css:487；Frost 组默认填充）/
    // fillStrong rgba(34,26,20,.59)（= --glass-strong frost+0.29，styles.css:488；弹层 .sheet
    // 即 glass-strong 组，见 ModelSheet）；
    // 顶/输入栏填充走下方 Mica 渐变 cardFill*（--wy-card-dark，styles.css:529-531），
    // 顶/底 alpha = frost×0.50 → .150，色 rgb(42,46,56)→rgb(22,25,34) /
    // border rgba(255,255,255,.12)（styles.css:39，引擎外圈描边实绘）/ edge rgba(255,255,255,.35)
    // 可读性核算（GlassTransparencyReadabilityTest）：Frost 组卡片 fg 最坏 ≈8.66、fgSecondary ≈5.96
    // （均 AA 达标）；悬浮栏 Card 渐变组 fg 最坏 ≈7.75、fgSecondary ≈5.33 达标；muted ≈3.50 低于 AA（记录性短板）。
    glassFill = Color(0x4D2E241C), // 0.30 × 255 ≈ 77 = 0x4D
    glassFillStrong = Color(0x96221A14), // 0.59 × 255 ≈ 150 = 0x96
    glassBorder = Color(0x1FFFFFFF),
    glassEdgeHighlight = Color(0x59FFFFFF),
    glassShadow = Color(0x80000000), // rgba(0,0,0,0.50)（web 暗 --g-shadow 0 14px 40px）
    // v1.9.4 Mica 悬浮栏渐变填充（--wy-card-dark：rgb(42,46,56)/rgb(22,25,34) 两停 frost×0.50）+ 栏阴影/聚焦环
    glassCardFillTop = Color(0x262A2E38),          // rgba(42,46,56,0.150) 顶部（较亮，深色最坏停靠点）
    glassCardFillBottom = Color(0x26161922),       // rgba(22,25,34,0.150) 底部
    glassShadowTopBar = Color(0x4D000000),         // rgba(0,0,0,0.30)（web 0 8px 28px）
    glassShadowInputBar = Color(0x66000000),       // rgba(0,0,0,0.40)（web 0 8px 32px）
    glassFocusRing = Color(0x33CE8A56),            // rgba(206,138,86,0.20) = web --l2b 暗值
    glassShadowNear = Color(0x00000000),           // web 暗色 --g-shadow 只有一条（styles.css:40）→ 近影不画
    // v1.9.4 内衬输入框 / chip（web --inp / --inset / .input-box 底部高光 / --chip 暗值）
    glassInputFill = Color(0x12FFFFFF),            // 0.07 × 255 ≈ 18 = 0x12（web --inp 暗值）
    glassInsetShade = Color(0x52000000),           // rgba(0,0,0,0.32)（web --inset 暗值）
    glassInsetHighlight = Color(0x14FFFFFF),       // rgba(255,255,255,0.08)（.input-box inset 0 -1px 0 暗值）
    glassChip = Color(0x2ECE8A56),                 // rgba(206,138,86,0.18)（web --chip 暗值）
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
 * 流光色相（度，0-360）：v1.9.4 四改起在 MainActivity 应用内容根部挂 W3C hue-rotate 全局
 * colorFilter 图层（drawWithContent + saveLayer 合成期套矩阵，矩阵构造器
 * [com.wenyan.app.ui.components.glass.hueRotateColorMatrix]），
 * 流光与 UI 层整树一起转（对齐 web body 级 `filter: hue-rotate` 语义）；
 * FluidBackground/GlowBackground 画未旋转基色，防二次旋转。默认 0 = 直接透传（原色，零开销）。
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
