package com.wenyan.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG 2.x 对比度计算（供无障碍自检与单元测试复用）。
 * 基线：正文 ≥4.5:1；大字号 ≥3:1；非文字（图标/描边）≥3:1。
 */
object GtjContrast {

    /** sRGB 通道线性化（WCAG relative luminance 公式）。 */
    private fun linearize(c: Float): Double {
        val v = c.coerceIn(0f, 1f).toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG 相对亮度 L（0..1）。 */
    fun relativeLuminance(color: Color): Double {
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** 对比度 (L1+0.05)/(L2+0.05)，恒 ≥1。 */
    fun ratio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * v1.7.0 玻璃合成：over 半透明层叠在 under 之上的结果色（标准 alpha 合成）。
     * 用于纯 JVM 断言玻璃填充 / 用户气泡 tint 合成后的对比度。
     */
    fun composite(over: Color, under: Color): Color {
        val a = over.alpha
        val aOut = a + under.alpha * (1 - a)
        if (aOut <= 0f) return Color.Transparent
        return Color(
            (over.red * a + under.red * under.alpha * (1 - a)) / aOut,
            (over.green * a + under.green * under.alpha * (1 - a)) / aOut,
            (over.blue * a + under.blue * under.alpha * (1 - a)) / aOut,
            aOut,
        )
    }

    /** 格式化保留两位小数（测试断言输出友好）；显式 Locale.US 避免数字格式随系统语言变化。 */
    fun format(ratio: Double): String = String.format(java.util.Locale.US, "%.2f", ratio)
}
