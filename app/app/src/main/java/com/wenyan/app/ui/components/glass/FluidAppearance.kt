package com.wenyan.app.ui.components.glass

import androidx.compose.ui.graphics.Color
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_DEFAULT
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_MAX
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_MIN
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * v1.9.4 三改 流光外观可调（色相 + 背景亮度）· 纯函数内核
 *
 * 唯一来源 docs/design-tokens.json component.fluidAppearance；两个函数都是纯计算（无 Android 依赖、
 * 无状态、零副作用），故可被 JVM 单测直接断言（FluidAppearanceTest）。
 * 消费点：[FluidBackground]（AGSL 主路径，色相写 uColor1/2/3、亮度写 uVeil）、
 * [GlowBackground]（降级路径，色相作用 glowA/B/C、亮度叠 veil 矩形）、
 * 以及 reducedMotion 静态帧——静态帧与动画帧走同一段绘制代码，故两者自动生效。
 *
 * 桌面语义对照（D:/wenyan/app/desktop/src/main/resources/static/app.js applyGlass）：
 * - 色相：桌面用 CSS `filter: hue-rotate(var(--wy-fluid-hue))` 作用在 body（连带文字一起转），
 *   安卓用**同一条 W3C 矩阵**只转流光基色、不动 UI 文字/玻璃（避免玻璃卡片被整体串色，
 *   文字对比度也不随色相走——矩阵保 luma，逐度扫描 fg/fgSecondary 全量程 ≥4.82/7.51）；
 * - 亮度：桌面 `--wy-brightness-white/black` 只按主题单向叠色（浅色只叠白、深色只叠黑），
 *   本文件的 [brightnessVeil] 与之一致。
 */

/**
 * 色相旋转（**W3C Filter Effects 的 hue-rotate 线性矩阵** —— 桌面 styles.css:493
 * `filter: hue-rotate(var(--wy-fluid-hue))` 用的就是这条算子，两端同一个色相数字 = 同一种色相语义）。
 *
 * 为什么不用 HSL 旋转（保 S/L 那种）——评审实测的回归，值得写全：
 * HSL 旋转保得住 S/L，但保不住**相对亮度**。浅色 fluidA #C0743F 是 H24.7°/S0.506/L0.500
 * 的暖橙，L=0.5 时它仍是高亮度色（相对亮度 0.2406，因为黄绿橙在高亮度里贡献大）；
 * 转到 215° 得到 #3F40C0（L 照样 0.5，可蓝紫的相对亮度只有 0.062）——卡片内玻璃底随之压暗，
 * fgSecondary #4B4032 在 **190°-344°（滑条 40% 行程）** 掉到 4.5:1 以下，最坏 3.31:1 @216°
 * （逐度扫描，修复前 fill .40；即使退回通透化前的 fill .55 也只有 4.43:1，仍不达标）。
 * 本矩阵三行行和恒为 1（0.213+0.715+0.072）且按 Rec.709 权重构造 → **luma 保持**（实测偏差 ±0.002），
 * 色相转一圈、卡片内底色的明度基本不动：全量程 fg ≥7.51、fgSecondary ≥4.82（两种主题都达标），
 * 且最坏点仍优于「通透化之前」的基线（fgSecondary 4.82 vs 4.43、muted 2.90 vs 2.67）。
 * 护栏见 GlassTransparencyReadabilityTest 的逐度扫描用例。
 *
 * 代价（如实记录）：矩阵是线性近似，高饱和色会掉一点饱和度（#C0743F 转 120° → #289D6A，
 * 比 HSL 旋转同角度得到的浓绿更柔和）、越界通道会被夹取（↔ 往返偏差 ≤1/255）。这正是 CSS hue-rotate
 * 的既有观感，桌面端同款，不作为缺陷。
 *
 * 边界：degrees 为 0/±360 的整数倍时**原样返回入参**（保证默认值 0 下送进 shader 的
 * uColor1/2/3 与不可调版本逐位一致）；纯灰/纯白（r==g==b）也原样返回——矩阵本身保灰，
 * 但显式短路才能保证「逐位相等」而不只是「量化后相等」。
 */
fun hueRotated(color: Color, degrees: Float): Color {
    val shift = degrees.mod(360f)
    if (shift == 0f) return color
    val r = color.red
    val g = color.green
    val b = color.blue
    if (r == g && g == b) return color
    // 三角函数走 Double 再收窄回 Float：kotlin.math.PI 是 Double，混算会把矩阵系数拖成 Double
    val radians = shift.toDouble() * PI / 180.0
    val cosA = cos(radians).toFloat()
    val sinA = sin(radians).toFloat()
    // W3C Filter Effects §hue-rotate 矩阵（行主序 3×3，逐行与规范字面一致）
    val rr = 0.213f + cosA * 0.787f - sinA * 0.213f
    val rg = 0.715f - cosA * 0.715f - sinA * 0.715f
    val rb = 0.072f - cosA * 0.072f + sinA * 0.928f
    val gr = 0.213f - cosA * 0.213f + sinA * 0.143f
    val gg = 0.715f + cosA * 0.285f + sinA * 0.140f
    val gb = 0.072f - cosA * 0.072f - sinA * 0.283f
    val br = 0.213f - cosA * 0.213f - sinA * 0.787f
    val bg = 0.715f - cosA * 0.715f + sinA * 0.715f
    val bb = 0.072f + cosA * 0.928f + sinA * 0.072f
    return Color(
        (rr * r + rg * g + rb * b).coerceIn(0f, 1f),
        (gr * r + gg * g + gb * b).coerceIn(0f, 1f),
        (br * r + bg * g + bb * b).coerceIn(0f, 1f),
        color.alpha,
    )
}

/**
 * 背景亮度（0-100）→ 叠加 veil 色：>50 叠白、<50 叠黑，50 = 中点 = 完全不叠（[Color.Transparent]）。
 *
 * 与桌面 app.js applyGlass 逐行同构，含**单向语义**：浅色主题只在 >50 时叠白、深色主题只在 <50 时
 * 叠黑。这不是偷懒——浅色主题的 UI 是深棕文字（LightPalette.fg #2B2118），若 <50 叠黑，
 * 背景会被压到深色而正文仍是深棕（对比度坍塌到 ~1:1，整屏不可读）；深色主题反之。
 * 故「>50 叠白、<50 叠黑」在本实现里按主题各取有效方向，与 web 端完全一致。
 *
 * 越界值（脏数据/手工写 prefs）夹到 0..100，alpha 恒 ∈ [0,1]；alpha 为 0 时返回 Transparent，
 * 调用方可据此跳过整条绘制（默认 50 下零开销）。
 */
fun brightnessVeil(brightness: Int, isDark: Boolean): Color {
    val value = brightness.coerceIn(BG_BRIGHTNESS_MIN, BG_BRIGHTNESS_MAX)
    val alpha = if (isDark) {
        (BG_BRIGHTNESS_DEFAULT - value).coerceAtLeast(0) / 50f
    } else {
        (value - BG_BRIGHTNESS_DEFAULT).coerceAtLeast(0) / 50f
    }
    if (alpha <= 0f) return Color.Transparent
    return (if (isDark) Color.Black else Color.White).copy(alpha = alpha)
}
