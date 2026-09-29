package com.wenyan.app.ui.components.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.DarkPalette
import com.wenyan.app.ui.theme.GtjContrast
import com.wenyan.app.ui.theme.LightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.9.4 三改 卡片通透化（glass fill alpha 下调）后的文字可读性自检（纯 JVM）。
 *
 * 卡片内的文字底不是主题底色，而是「流光背景 → backdrop 模糊 + saturate(170%)/brightness(1.03)
 * → 玻璃半透明填充」三层合成。本测试取**每个主题最不利的流光基色**（浅色取最深暖橙 fluidA，
 * 深色取最亮暖褐 fluidA——深色主题文字是浅色，底越亮越不利），用生产矩阵
 * （[buildBackdropColorMatrix] + [GlassBackdropParams] 真实参数，非测试内复刻公式）算出
 * 卡片内实色，再按 WCAG AA（正文 ≥4.5:1）断言 fg / fgSecondary。
 *
 * 实测值（改动后，见 Color.kt 注释）：浅色 fg 7.97:1 / fgSecondary 5.12:1；
 * 深色 fg 8.95:1 / fgSecondary 6.15:1 —— 均达标。muted 小字在最深流光上 ≈3.1:1（改动前 3.6:1），
 * 与「浅色 muted 在流光最深处本就低于 AA」同量级，故不作为断言（正文层用 fg/fgSecondary）。
 *
 * v1.9.4 三改·评审修复（色相维度）：上面那组是 hue=0 的值；色相滑条可把流光基色转到任何色相，
 * 卡片内底色会跟着变，故另加**逐度扫描**用例（lightText/darkText_staysReadableAcrossTheWholeHueRange）
 * 守全量程。当时选 HSL 旋转（保 S/L）曾在这里漏底：HSL 保不住相对亮度，#C0743F 转 215° 得 #3F40C0
 * （相对亮度 0.2406 → 0.062），fgSecondary 在 190°-344° 全线 <4.5、最坏 3.31:1；
 * 换成 W3C hue-rotate 矩阵（= 桌面 CSS filter 算子，行和 = 1 保 luma）后全量程 fg ≥7.51 /
 * fgSecondary ≥4.82，且最坏点仍优于「通透化之前」的基线（4.43 / 2.67）。
 */
class GlassTransparencyReadabilityTest {

    /** 卡片内模糊底：流光基色经 backdrop 的 saturate(170%) + brightness(1.03)（生产矩阵，行主序 4×5）。 */
    private fun backdropFiltered(base: Color): Color {
        // 直接按 row*5+column 取 FloatArray：ColorMatrix 的行主序布局就是构造时传入的 20 个 float
        // （values[0..4] = R 行），不走 get(row, column) 访问器以免其索引约定歧义
        val v = buildBackdropColorMatrix(GlassBackdropParams.SATURATION, GlassBackdropParams.BRIGHTNESS).values
        fun channel(row: Int) = (
            v[row * 5] * base.red + v[row * 5 + 1] * base.green + v[row * 5 + 2] * base.blue + v[row * 5 + 4]
            ).coerceIn(0f, 1f)
        return Color(channel(0), channel(1), channel(2), base.alpha)
    }

    /** 玻璃填充叠在卡片内模糊底之上（[GtjContrast.composite] = 标准 alpha 合成）。 */
    private fun cardInterior(fill: Color, backdrop: Color): Color =
        GtjContrast.composite(fill, backdrop)

    /**
     * 色相滑条 0-360° **逐度**扫描，取某文字色在卡片内的最低对比度及其色相：
     * 卡片内底 = 流光基色经 [hueRotated]（生产函数）→ backdrop 滤镜（生产矩阵）→ 玻璃填充合成。
     * 逐度扫（而非只取滑条 5° 落点）是为了让任何步进/算子改动都逃不过这道护栏。
     */
    private fun worstRatio(text: Color, fill: Color, fluidBase: Color): Pair<Double, Int> {
        var worst = Double.MAX_VALUE
        var worstHue = -1
        for (hue in 0..360) {
            val interior = cardInterior(fill, backdropFiltered(hueRotated(fluidBase, hue.toFloat())))
            val r = GtjContrast.ratio(text, interior)
            if (r < worst) {
                worst = r
                worstHue = hue
            }
        }
        return worst to worstHue
    }

    private fun assertReadable(label: String, fg: Color, bg: Color) {
        val r = GtjContrast.ratio(fg, bg)
        assertTrue("$label 对比度 ${GtjContrast.format(r)} 应 >= 4.5（fg=${fg.value} bg=${bg.value}）", r >= 4.5)
    }

    @Test
    fun lightText_onTransparentCard_passesAa() {
        val interior = cardInterior(LightPalette.glassFill, backdropFiltered(LightPalette.fluidA))
        assertReadable("浅色 fg/通透玻璃卡（流光最深色之上）", LightPalette.fg, interior)
        assertReadable("浅色 fgSecondary/通透玻璃卡（流光最深色之上）", LightPalette.fgSecondary, interior)
    }

    @Test
    fun lightText_onStrongTransparentCard_passesAa() {
        val interior = cardInterior(LightPalette.glassFillStrong, backdropFiltered(LightPalette.fluidA))
        assertReadable("浅色 fg/通透强玻璃卡", LightPalette.fg, interior)
    }

    @Test
    fun darkText_onTransparentCard_passesAa() {
        val interior = cardInterior(DarkPalette.glassFill, backdropFiltered(DarkPalette.fluidA))
        assertReadable("深色 fg/通透玻璃卡（流光最亮色之上）", DarkPalette.fg, interior)
        assertReadable("深色 fgSecondary/通透玻璃卡（流光最亮色之上）", DarkPalette.fgSecondary, interior)
    }

    @Test
    fun darkText_onStrongTransparentCard_passesAa() {
        val interior = cardInterior(DarkPalette.glassFillStrong, backdropFiltered(DarkPalette.fluidA))
        assertReadable("深色 fg/通透强玻璃卡", DarkPalette.fg, interior)
    }

    @Test
    fun documentedRatios_hold() {
        // 把 Color.kt 注释里写下的实测值钉在生产矩阵上（容差 0.15）：若 token 或矩阵公式再动，
        // 这里先红，而不是让注释悄悄过期。数值 = 「流光最深/最亮基色 → 卡片内实色」上的 WCAG 对比度。
        val lightInterior = cardInterior(LightPalette.glassFill, backdropFiltered(LightPalette.fluidA))
        assertEquals("浅色 fg", 7.97, GtjContrast.ratio(LightPalette.fg, lightInterior), 0.15)
        assertEquals("浅色 fgSecondary", 5.12, GtjContrast.ratio(LightPalette.fgSecondary, lightInterior), 0.15)
        assertEquals("浅色 muted", 3.08, GtjContrast.ratio(LightPalette.muted, lightInterior), 0.15)
        val darkInterior = cardInterior(DarkPalette.glassFill, backdropFiltered(DarkPalette.fluidA))
        assertEquals("深色 fg", 8.95, GtjContrast.ratio(DarkPalette.fg, darkInterior), 0.15)
        assertEquals("深色 fgSecondary", 6.15, GtjContrast.ratio(DarkPalette.fgSecondary, darkInterior), 0.15)
        assertEquals("深色 muted", 4.04, GtjContrast.ratio(DarkPalette.muted, darkInterior), 0.15)
    }

    @Test
    fun mutedCaption_onDeepestFluid_staysBelowAa_beforeAndAfter() {
        // 记录性断言（与 ContrastTest 的 warnLight_asBodyText_failsAa 同风格）：muted 小字落在
        // 流光最深处本就低于 AA（改动前 3.61:1 → 改动后 3.08:1，同为「低于 4.5」量级），
        // 故正文层要求用 fg/fgSecondary（脚本与测试同矩阵复算，见 documentedRatios_hold）
        val interior = cardInterior(LightPalette.glassFill, backdropFiltered(LightPalette.fluidA))
        assertTrue(
            "浅色 muted/通透玻璃卡 应 <4.5（故标题正文不用 muted）",
            GtjContrast.ratio(LightPalette.muted, interior) < 4.5,
        )
    }

    @Test
    fun lightText_staysReadableAcrossTheWholeHueRange() {
        val (fgWorst, fgHue) = worstRatio(LightPalette.fg, LightPalette.glassFill, LightPalette.fluidA)
        val (secWorst, secHue) = worstRatio(LightPalette.fgSecondary, LightPalette.glassFill, LightPalette.fluidA)
        // 评审修复（色相维度护栏）：色相滑条 0-360° 逐度扫描，卡片内正文不得跌破 AA。
        // 修复前（HSL 旋转）190°-344° 区间 fgSecondary 最低 3.31:1 —— 该护栏就是当时的漏网处
        assertTrue(
            "浅色 fg 全量程最低 ${GtjContrast.format(fgWorst)} @${fgHue}° 应 >= 4.5",
            fgWorst >= 4.5,
        )
        assertTrue(
            "浅色 fgSecondary 全量程最低 ${GtjContrast.format(secWorst)} @${secHue}° 应 >= 4.5",
            secWorst >= 4.5,
        )
        // 实测值（W3C 矩阵 + fill .40）：fg 7.51 @218°、fgSecondary 4.82 @218°（钉住，防悄悄回退）
        assertEquals("浅色 fg 全量程最低", 7.51, fgWorst, 0.25)
        assertEquals("浅色 fgSecondary 全量程最低", 4.82, secWorst, 0.25)
    }

    @Test
    fun darkText_staysReadableAcrossTheWholeHueRange() {
        val (fgWorst, fgHue) = worstRatio(DarkPalette.fg, DarkPalette.glassFill, DarkPalette.fluidA)
        val (secWorst, secHue) = worstRatio(DarkPalette.fgSecondary, DarkPalette.glassFill, DarkPalette.fluidA)
        assertTrue(
            "深色 fg 全量程最低 ${GtjContrast.format(fgWorst)} @${fgHue}° 应 >= 4.5",
            fgWorst >= 4.5,
        )
        assertTrue(
            "深色 fgSecondary 全量程最低 ${GtjContrast.format(secWorst)} @${secHue}° 应 >= 4.5",
            secWorst >= 4.5,
        )
        assertEquals("深色 fg 全量程最低", 8.60, fgWorst, 0.25)
        assertEquals("深色 fgSecondary 全量程最低", 5.91, secWorst, 0.25)
    }

    @Test
    fun mutedCaption_worstOverHueRange_isNoWorseThanBeforeTransparency() {
        // 记录性断言：muted 小字在流光最深处本就低于 AA（既有短板），色相可调后也不得比
        // 「通透化之前」的基线更差：修复前 HSL 旋转 + 旧 fill(.55) 全量程最低 2.67:1，
        // 现 W3C 矩阵 + fill(.40) 最低 2.90:1（两者都 <4.5，故正文层继续用 fg/fgSecondary）
        val (lightWorst, lightHue) = worstRatio(LightPalette.muted, LightPalette.glassFill, LightPalette.fluidA)
        assertEquals("浅色 muted 全量程最低", 2.90, lightWorst, 0.25)
        assertTrue("浅色 muted @${lightHue}° 应 <4.5（故正文不用 muted）", lightWorst < 4.5)
        val (darkWorst, _) = worstRatio(DarkPalette.muted, DarkPalette.glassFill, DarkPalette.fluidA)
        assertEquals("深色 muted 全量程最低", 3.88, darkWorst, 0.25)
        assertTrue("深色 muted 应 <4.5（故正文不用 muted）", darkWorst < 4.5)
    }

    @Test
    fun glassFills_areMoreTransparentThanBefore() {
        // 通透化方向护栏：浅色 .55 → .35~.42、深色 .45 → .30~.37（改动前的值应被拒绝）
        assertTrue(
            "浅色 glassFill alpha 应落在 .35~.42（通透化），实际 ${LightPalette.glassFill.alpha}",
            LightPalette.glassFill.alpha in 0.35f..0.42f,
        )
        assertTrue(
            "深色 glassFill alpha 应落在 .30~.37（同比例下调），实际 ${DarkPalette.glassFill.alpha}",
            DarkPalette.glassFill.alpha in 0.30f..0.37f,
        )
        assertTrue(
            "strong 仍须比普通 fill 更不透明（浅）",
            LightPalette.glassFillStrong.alpha > LightPalette.glassFill.alpha,
        )
        assertTrue(
            "strong 仍须比普通 fill 更不透明（深）",
            DarkPalette.glassFillStrong.alpha > DarkPalette.glassFill.alpha,
        )
    }

    @Test
    fun cardBackdrop_matchesTheDoubledBlurAndTwoTimesPadding() {
        // 通透化配套：卡片模糊 4dp → 8dp（采样余量维持 2× 不变式）
        assertEquals(8.dp, GlassBackdropParams.cardBlurRadius)
        assertEquals(GlassBackdropParams.cardBlurRadius * 2, GlassBackdropParams.cardSamplePadding)
        assertEquals(20.dp, GlassBackdropParams.blurRadius)
        assertEquals(GlassBackdropParams.blurRadius * 2, GlassBackdropParams.samplePadding)
    }
}
