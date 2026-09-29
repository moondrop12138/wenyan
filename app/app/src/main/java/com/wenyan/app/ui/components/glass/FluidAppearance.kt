package com.wenyan.app.ui.components.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_DEFAULT
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_MAX
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_MIN
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * v1.9.4 流光外观可调（色相 + 背景亮度）· 纯函数内核
 *
 * 唯一来源 docs/design-tokens.json component.fluidAppearance；纯计算（无状态、零副作用），
 * 可被 JVM 单测直接断言（FluidAppearanceTest / GlassTransparencyReadabilityTest 逐度扫描）。
 * 消费点：
 * - 色相：MainActivity 在**应用内容根部**挂的全局 colorFilter 图层（drawWithContent +
 *   saveLayer 的 paint 滤镜，合成期套矩阵；本项目 Compose ui 1.8 的 GraphicsLayerScope
 *   尚无 colorFilter 属性，故用此等价实现。v1.9.4 四改起色相对齐 web body 级 filter
 *   语义——「流体+玻璃+文字」整树一起转；[hueRotated] 与之共享同一条 W3C 矩阵系数，
 *   并供单测逐色断言）；[FluidBackground]/[GlowBackground] 画**未旋转**基色，防二次旋转；
 * - 亮度：[FluidBackground]（AGSL 主路径，veil 写 uVeil）、[GlowBackground]（降级路径，
 *   veil 叠矩形）——veil 语义与色相无关，仍只作用流光背景层。
 *
 * 桌面语义对照（D:/wenyan/app/desktop/src/main/resources/static/styles.css:493 与 app.js applyGlass）：
 * - 色相：桌面 CSS `filter: hue-rotate(var(--wy-fluid-hue))` 作用在 body，安卓用**同一条
 *   W3C 矩阵**挂在内容根部整树生效，两端同一个色相数字 = 同一种色相语义。已知偏差（web 无
 *   此概念）：Compose Dialog/ModalBottomSheet 等独立窗口不在内容根部子树内，不随全局层旋转；
 * - 亮度：桌面 `--wy-brightness-white/black` 只按主题单向叠色（浅色只叠白、深色只叠黑），
 *   本文件的 [brightnessVeil] 与之一致。
 */

/**
 * W3C hue-rotate 3×3 行主序系数（行主序 3×3，逐行与 Filter Effects 规范字面一致）。
 * [hueRotated]（逐色应用）与 [hueRotateColorMatrix]（全局 ColorMatrix）共享，保证两端
 * 永远是同一条算子。入参须已 `mod 360` 且非 0（0 时两处各自短路返回原样/单位矩阵）。
 */
private fun hueRotateCoefficients(shift: Float): FloatArray {
    // 三角函数走 Double 再收窄回 Float：kotlin.math.PI 是 Double，混算会把矩阵系数拖成 Double
    val radians = shift.toDouble() * PI / 180.0
    val cosA = cos(radians).toFloat()
    val sinA = sin(radians).toFloat()
    return floatArrayOf(
        0.213f + cosA * 0.787f - sinA * 0.213f,
        0.715f - cosA * 0.715f - sinA * 0.715f,
        0.072f - cosA * 0.072f + sinA * 0.928f,
        0.213f - cosA * 0.213f + sinA * 0.143f,
        0.715f + cosA * 0.285f + sinA * 0.140f,
        0.072f - cosA * 0.072f - sinA * 0.283f,
        0.213f - cosA * 0.213f - sinA * 0.787f,
        0.715f - cosA * 0.715f + sinA * 0.715f,
        0.072f + cosA * 0.928f + sinA * 0.072f,
    )
}

/**
 * 色相旋转的全局 [ColorMatrix] 版本（MainActivity 内容根部全局 colorFilter 图层专用：
 * drawWithContent + saveLayer 的 paint 滤镜，matrix 经 `.values` 送 android.graphics.
 * ColorMatrixColorFilter）。与 [hueRotated] 同一条 W3C 矩阵（系数见 [hueRotateCoefficients]）；
 * RGB 行按矩阵旋转，alpha 行恒为单位（不动透明度，半透明像素只转色相）。行和恒为 1
 * （0.213+0.715+0.072）→ luma 保持。degrees 为 0/±360 的整数倍时返回单位矩阵——调用方
 * （MainActivity）据此在默认色相下整体跳过该层（零开销）。
 */
fun hueRotateColorMatrix(degrees: Float): ColorMatrix {
    val shift = degrees.mod(360f)
    if (shift == 0f) return ColorMatrix()
    val m = hueRotateCoefficients(shift)
    return ColorMatrix(
        floatArrayOf(
            m[0], m[1], m[2], 0f, 0f,
            m[3], m[4], m[5], 0f, 0f,
            m[6], m[7], m[8], 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

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
 * 色相转一圈、卡片内底色的明度基本不动。逐度扫描实测（**v1.9.4 当前配方**：填充 frost .30、
 * backdrop saturate(170%)/brightness 1.0，口径见 GlassTransparencyReadabilityTest）：
 * Frost 组浅色 fg 6.95（hue0）/6.47（全量程最坏）、深色 fg 9.01/8.66、深色 fgSecondary 6.20/5.96；
 * 悬浮栏 Card 渐变组浅色 fg 5.64/4.99、深色 fg 8.19/7.75。
 * 浅色 fgSecondary（4.46/4.15）与 muted（浅 2.68/2.50、暗 4.07/3.91）低于 AA 4.5 —— 这是 Mica 对齐把
 * 填充收到 frost .30 后的如实结果（**旧 fill .40 时代的 7.51/4.82 不适用于本配方**，勿再引用）；
 * 处置是正文用色而非 token：卡片与正文用色点一律用 fg（v1.9.4 评审修复把 ErrorCard 正文、
 * CoachCard 接住你/理由、空态示例问题的 fgSecondary 改成 fg），token 与 web 对齐优先，不回退填充。
 * 护栏见 GlassTransparencyReadabilityTest 的逐度扫描用例（上述钉值均在其中）。
 *
 * 代价（如实记录）：矩阵是线性近似，高饱和色会掉一点饱和度（#C0743F 转 120° → #289D6A，
 * 比 HSL 旋转同角度得到的浓绿更柔和）、越界通道会被夹取（↔ 往返偏差 ≤1/255）。这正是 CSS hue-rotate
 * 的既有观感，桌面端同款，不作为缺陷。
 *
 * 边界：degrees 为 0/±360 的整数倍时**原样返回入参**（保证默认值 0 下「色相未生效」的观感
 * 与不可调版本逐位一致）；纯灰/纯白（r==g==b）也原样返回——矩阵本身保灰，
 * 但显式短路才能保证「逐位相等」而不只是「量化后相等」。
 *
 * 消费点：单测护栏（FluidAppearanceTest 逐色断言 / GlassTransparencyReadabilityTest 逐度
 * 扫描）；生产路径的全局层走 [hueRotateColorMatrix]（同一系数，见上）。
 */
fun hueRotated(color: Color, degrees: Float): Color {
    val shift = degrees.mod(360f)
    if (shift == 0f) return color
    val r = color.red
    val g = color.green
    val b = color.blue
    if (r == g && g == b) return color
    val m = hueRotateCoefficients(shift)
    return Color(
        (m[0] * r + m[1] * g + m[2] * b).coerceIn(0f, 1f), // R 行
        (m[3] * r + m[4] * g + m[5] * b).coerceIn(0f, 1f), // G 行
        (m[6] * r + m[7] * g + m[8] * b).coerceIn(0f, 1f), // B 行
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
