package com.wenyan.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * v1.7.0 液态玻璃·衍生视觉常量（唯一来源 outputs/liquid-glass-prototype.html）。
 *
 * GtjPalette 中的 glass 系 / glow 系 / dot 系 token 为单色值；本文件存放**多停渐变/双色组合**类参数
 * （用户气泡 tint、军师建议内卡），它们跨主题固定两套，由 remember 辅助函数按当前主题取用。
 * 色值仍以 docs/design-tokens.json 为唯一来源（实施时同步）。
 */

// ── 用户气泡：同款玻璃 + 深棕 tint 渐变 150°（原型 .buser，--utint / --uborder）──

/** 浅色：rgba(164,85,28) .48 → .26(50%) → .40 */
val LightUserBubbleTint: List<Pair<Float, Color>> = listOf(
    0.0f to Color(0x7AA4551C), // 0.48 × 255 ≈ 122 = 0x7A
    0.5f to Color(0x42A4551C), // 0.26 × 255 ≈ 66  = 0x42
    1.0f to Color(0x66A4551C), // 0.40 × 255 ≈ 102 = 0x66
)
val LightUserBubbleBorder: Color = Color(0x80A4551C) // rgba(164,85,28,.5)

/** 深色：rgba(206,138,86) .50 → .28(50%) → .42 */
val DarkUserBubbleTint: List<Pair<Float, Color>> = listOf(
    0.0f to Color(0x80CE8A56), // 0.50 × 255 = 128 = 0x80
    0.5f to Color(0x47CE8A56), // 0.28 × 255 ≈ 71  = 0x47
    1.0f to Color(0x6BCE8A56), // 0.42 × 255 ≈ 107 = 0x6B
)
val DarkUserBubbleBorder: Color = Color(0x80CE8A56)

// ── 军师建议内卡（原型 --l2 / --l2b）──

/** 浅色：暖米半透明 rgba(247,234,220,.72) + 淡棕描边 rgba(164,85,28,.22) */
val LightCoachInnerFill: Color = Color(0xB8F7EADC) // 0.72 × 255 ≈ 184 = 0xB8
val LightCoachInnerBorder: Color = Color(0x38A4551C) // 0.22 × 255 ≈ 56 = 0x38

/** 深色：白 10% + 杏棕描边 rgba(206,138,86,.2) */
val DarkCoachInnerFill: Color = Color(0x1AFFFFFF) // 0.10 × 255 ≈ 26 = 0x1A
val DarkCoachInnerBorder: Color = Color(0x33CE8A56) // 0.20 × 255 = 51 = 0x33

// ── 发送键渐变（原型 .inbar --send1/--send2/--sendic，150°）──

/** 浅色：#B5651F → #8A4A1B，图标白 */
val LightSendGradient: List<Pair<Float, Color>> = listOf(
    0.0f to Color(0xFFB5651F),
    1.0f to Color(0xFF8A4A1B),
)
val LightSendIcon: Color = Color.White

/** 深色：#E0A978 → #B06A35，图标深棕黑 */
val DarkSendGradient: List<Pair<Float, Color>> = listOf(
    0.0f to Color(0xFFE0A978),
    1.0f to Color(0xFFB06A35),
)
val DarkSendIcon: Color = Color(0xFF17120E)

/**
 * 当前主题是否为深色（按背景色与 LightPalette 比对，跨组件零额外状态）。
 * 供 Glass 组件在选择浅/深两套衍生参数时使用。
 */
@Composable
fun isDarkGtjTheme(): Boolean {
    val p = LocalGtjColors.current
    return p.bg != LightPalette.bg
}

/** 当前主题的用户气泡 tint 渐变（150°，3 停靠点）。 */
@Composable
fun rememberUserBubbleTint(): List<Pair<Float, Color>> =
    if (isDarkGtjTheme()) DarkUserBubbleTint else LightUserBubbleTint

/** 当前主题的用户气泡描边色。 */
@Composable
fun rememberUserBubbleBorder(): Color =
    if (isDarkGtjTheme()) DarkUserBubbleBorder else LightUserBubbleBorder

/** 当前主题的军师建议内卡（fill, border）。 */
@Composable
fun rememberCoachInnerCard(): Pair<Color, Color> =
    if (isDarkGtjTheme()) DarkCoachInnerFill to DarkCoachInnerBorder
    else LightCoachInnerFill to LightCoachInnerBorder

/** 当前主题的发送键渐变（150° 两停靠点）。 */
@Composable
fun rememberSendGradient(): List<Pair<Float, Color>> =
    if (isDarkGtjTheme()) DarkSendGradient else LightSendGradient

/** 当前主题的发送键图标色。 */
@Composable
fun rememberSendIconColor(): Color =
    if (isDarkGtjTheme()) DarkSendIcon else LightSendIcon
