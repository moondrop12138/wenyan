package com.wenyan.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 无障碍对比度自检（WCAG 2.x AA：正文 ≥4.5:1，非文字 ≥3:1）。
 * 对应 design-pages.md 无障碍基线与 v1.1 对比度升级项；用纯 Kotlin 计算，无需设备。
 */
class ContrastTest {

    private fun assertRatio(fg: Color, bg: Color, min: Double, label: String) {
        val r = GtjContrast.ratio(fg, bg)
        assertTrue(
            "$label 对比度 ${GtjContrast.format(r)} 应 >= $min（fg=${fg.value} bg=${bg.value}）",
            r >= min,
        )
    }

    // ---- muted：正文层，白底/surface 均须 ≥4.5 ----
    @Test
    fun lightMuted_onBg_passesAa() {
        assertRatio(LightPalette.muted, LightPalette.bg, 4.5, "浅色 muted/bg(白)")
    }

    @Test
    fun lightMuted_onSurface_passesAa() {
        assertRatio(LightPalette.muted, LightPalette.surface, 4.5, "浅色 muted/surface")
    }

    @Test
    fun darkMuted_onBg_passesAa() {
        assertRatio(DarkPalette.muted, DarkPalette.bg, 4.5, "深色 muted/bg")
    }

    @Test
    fun darkMuted_onSurface_passesAa() {
        assertRatio(DarkPalette.muted, DarkPalette.surface, 4.5, "深色 muted/surface")
    }

    // ---- 升级后的文案层：meta 内容已改为 muted，占位符等仍允许弱对比 ----
    @Test
    fun lightFgSecondary_onSurface_passesAa() {
        // SettingsRow 值文字升级目标
        assertRatio(LightPalette.fgSecondary, LightPalette.surface, 4.5, "浅色 fgSecondary/surface")
    }

    @Test
    fun lightWarmOn_onBg_passesAa() {
        // warn 文案改用 warmOn 后达标
        assertRatio(LightPalette.warmOn, LightPalette.bg, 4.5, "浅色 warmOn/bg")
    }

    @Test
    fun darkWarmOn_onBg_passesAa() {
        assertRatio(DarkPalette.warmOn, DarkPalette.bg, 4.5, "深色 warmOn/bg")
    }

    // ---- 记录性断言：accent 白字 = 4.29:1，属设计锁定的大字号 AA 例外（design-pages §8）----
    @Test
    fun accentOn_onAccent_meetsLargeTextAa() {
        val r = GtjContrast.ratio(LightPalette.accentOn, LightPalette.accent)
        assertTrue("accent 白字 ${GtjContrast.format(r)} 应 >= 3.0（大字号 AA）", r >= 3.0)
    }

    // ---- 记录性断言：warn 浅色作正文不达标，证明必须走 warmOn ----
    @Test
    fun warnLight_asBodyText_failsAa() {
        val r = GtjContrast.ratio(LightPalette.warn, LightPalette.bg)
        assertTrue("warn 浅色白底 ${GtjContrast.format(r)} 应 < 4.5（故正文改用 warmOn）", r < 4.5)
    }

    // ---- v1.6 CoachCard 新增组合：军师建议段（策略 tag warmSoft 底 + 核心句 warmOn 字）、接住你 pill（accentSoft 底 + accent 字）----
    @Test
    fun lightWarmOn_onWarmSoft_passesAa() {
        assertRatio(LightPalette.warmOn, LightPalette.warmSoft, 4.5, "浅色 warmOn/warmSoft（策略标签）")
    }

    @Test
    fun darkWarmOn_onWarmSoft_passesAa() {
        assertRatio(DarkPalette.warmOn, DarkPalette.warmSoft, 4.5, "深色 warmOn/warmSoft（策略标签）")
    }

    @Test
    fun lightWarmOn_onSurfaceElevated_passesAa() {
        assertRatio(LightPalette.warmOn, LightPalette.surfaceElevated, 4.5, "浅色 warmOn/surfaceElevated（核心句）")
    }

    @Test
    fun darkWarmOn_onSurfaceElevated_passesAa() {
        assertRatio(DarkPalette.warmOn, DarkPalette.surfaceElevated, 4.5, "深色 warmOn/surfaceElevated（核心句）")
    }

    @Test
    fun lightAccent_onAccentSoft_passesAa() {
        assertRatio(LightPalette.accent, LightPalette.accentSoft, 4.5, "浅色 accent/accentSoft（接住你 pill）")
    }

    @Test
    fun darkAccent_onAccentSoft_passesAa() {
        assertRatio(DarkPalette.accent, DarkPalette.accentSoft, 4.5, "深色 accent/accentSoft（接住你 pill）")
    }

    // ---- v1.7.0 液态玻璃：玻璃合成底（fill 与 bg 混合后的实色）上文字须 ≥4.5 ----
    private fun blend(fg: Color, bg: Color): Color {
        val a = fg.alpha
        return Color(
            fg.red * a + bg.red * (1 - a),
            fg.green * a + bg.green * (1 - a),
            fg.blue * a + bg.blue * (1 - a),
        )
    }

    private fun glassStrongBottom(bg: Color): Color = blend(LightPalette.glassFillStrong, bg)

    private fun glassStrongDarkBottom(bg: Color): Color = blend(DarkPalette.glassFillStrong, bg)

    @Test
    fun lightFg_onGlassStrong_passesAa() {
        assertRatio(LightPalette.fg, glassStrongBottom(LightPalette.bg), 4.5, "浅色 fg/glassFillStrong 合成底")
    }

    @Test
    fun lightMuted_onGlassStrong_passesAa() {
        assertRatio(LightPalette.muted, glassStrongBottom(LightPalette.bg), 4.5, "浅色 muted/glassFillStrong 合成底")
    }

    @Test
    fun darkFg_onGlassStrong_passesAa() {
        assertRatio(DarkPalette.fg, glassStrongDarkBottom(DarkPalette.bg), 4.5, "深色 fg/glassFillStrong 合成底")
    }

    @Test
    fun darkMuted_onGlassStrong_passesAa() {
        assertRatio(DarkPalette.muted, glassStrongDarkBottom(DarkPalette.bg), 4.5, "深色 muted/glassFillStrong 合成底")
    }

    // 用户气泡：tint 渐变最浓停靠点叠在 glassFill 合成底之上（取最坏点验证 ink 文字）
    @Test
    fun lightInk_onUserBubbleTintMax_passesAa() {
        val glassBottom = blend(LightPalette.glassFill, LightPalette.bg)
        val tintMax = LightUserBubbleTint.first().second // 0.48 停靠点
        assertRatio(LightPalette.fg, blend(tintMax, glassBottom), 4.5, "浅色 fg/用户气泡 tint 最浓点")
    }

    @Test
    fun darkInk_onUserBubbleTintMax_passesAa() {
        val glassBottom = blend(DarkPalette.glassFill, DarkPalette.bg)
        val tintMax = DarkUserBubbleTint.first().second // 0.50 停靠点
        assertRatio(DarkPalette.fg, blend(tintMax, glassBottom), 4.5, "深色 fg/用户气泡 tint 最浓点")
    }
}
