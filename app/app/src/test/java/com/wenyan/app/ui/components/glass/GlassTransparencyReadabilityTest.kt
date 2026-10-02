package com.wenyan.app.ui.components.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.DarkPalette
import com.wenyan.app.ui.theme.GtjContrast
import com.wenyan.app.ui.theme.LightPalette
import com.wenyan.app.ui.theme.withGlassFrost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.9.4 Mica 两组填充（web cautions：色相不同勿混用）的文字可读性自检（纯 JVM）。
 *
 * 卡片内的文字底不是主题底色，而是「流光背景 → backdrop 模糊 + saturate(170%)
 * → 玻璃填充」三层合成。引擎填充分两组（[GlassFill]，与 web 分组一一对应）：
 * - **Frost 组**（默认）：frost 版 --glass 直色（亮 rgb(253,249,242,.30)/暗 rgb(46,36,28,.30)），
 *   承载气泡/胶囊/弹层/侧栏行等全部卡片文字 → 正文必须 AA 达标；
 * - **Card 组**（仅顶栏/输入栏）：--wy-card-light/dark 渐变（亮 白 .150/.105、暗 .150/.150）——
 *   web 只把这两个元素做成这样透，栏内正文仅 fg（placeholder/meta 走内衬输入框 glassInputFill
 *   半透明底 + meta 字色），浅色 fg 全量程最坏 4.99:1 仍守 AA；fgSecondary/muted 在该组跌破 AA
 *   属规格如实结果，转为记录性断言（同 muted 用例风格）。
 *
 * 实测钉值（Float16 色彩管线口径，详见各用例）：Frost 亮 fg 6.95/fgSecondary 4.46/muted 2.68、
 * 暗 fg 9.01/fgSecondary 6.20/muted 4.07（hue0）；Card 组亮（最坏停靠点=底停）5.64/3.62/2.18、
 * 暗（最坏停靠点=顶停）8.19/5.63/3.70。
 *
 * 最坏停靠点选取依据（Card 组）：亮色两停同色（白）只差 alpha，底停 .105 < 顶停 .150 → 透出
 * 流光更多、更不利；暗色两停 alpha 相同，但顶停填充 rgb(42,46,56) 比底停 rgb(22,25,34) 亮 →
 * 叠出的卡内底更亮、对浅色文字更不利。
 *
 * 逐度扫描护栏延续：色相滑条 0-360° 全量程（W3C hue-rotate 矩阵保 luma），用生产矩阵
 * （[buildBackdropColorMatrix] + [GlassBackdropParams] 真实参数，非测试内复刻公式）。
 *
 * v1.9.4 四改 关系说明（不改任何断言）：色相自本版起挂在应用内容根部的全局 colorFilter 图层
 * （「流光+玻璃+文字」整树一起转，见 MainActivity / FluidAppearance.hueRotateColorMatrix），
 * [hueRotated] 与全局层共用同一条系数构造器（hueRotateCoefficients），故这里「只把流光基色转色相、
 * 文字保持原色」的逐度扫描是**更保守**的模型（全局层下文字与卡内底同步旋转、对比度更平），
 * 结论（全量程 ≥4.5）继续成立，钉值仍指向同一套 token。
 */
class GlassTransparencyReadabilityTest {

    /** 卡片内模糊底：流光基色经 backdrop 的 saturate(170%)（生产矩阵，行主序 4×5）。 */
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

    /** Card 组（悬浮栏）各主题最坏停靠点（选取依据见类注释）。 */
    private val lightBarWorstFill = LightPalette.glassCardFillBottom
    private val darkBarWorstFill = DarkPalette.glassCardFillTop

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

    // ── Frost 组（引擎默认填充，承载全部卡片正文）：fg/fgSecondary（暗）须 ≥AA ──

    @Test
    fun lightText_onFrostCard_passesAa() {
        val interior = cardInterior(LightPalette.glassFill, backdropFiltered(LightPalette.fluidA))
        assertReadable("浅色 fg/Frost 玻璃卡（流光最深色之上）", LightPalette.fg, interior)
    }

    @Test
    fun darkText_onFrostCard_passesAa() {
        val interior = cardInterior(DarkPalette.glassFill, backdropFiltered(DarkPalette.fluidA))
        assertReadable("深色 fg/Frost 玻璃卡（流光最亮色之上）", DarkPalette.fg, interior)
        assertReadable("深色 fgSecondary/Frost 玻璃卡（流光最亮色之上）", DarkPalette.fgSecondary, interior)
    }

    // ── Frost 组记录性断言：浅色 fgSecondary ≈4.46 差线（改动前 Card 渐变组 3.62，回升但仍 <4.5），
    //    muted 同类短板——正文层用 fg（v1.9.4 评审修复：卡片/正文用色点的 fgSecondary 已改 fg：
    //    ErrorCard 正文、CoachCard 接住你+理由、空态示例问题），钉值防继续悄悄下滑。──

    @Test
    fun lightFgSecondary_onFrostCard_belowAa_recorded() {
        val interior = cardInterior(LightPalette.glassFill, backdropFiltered(LightPalette.fluidA))
        val r = GtjContrast.ratio(LightPalette.fgSecondary, interior)
        assertTrue(
            "浅色 fgSecondary/Frost 玻璃卡 应 <4.5（记录性事实；卡片正文已改用 fg，fgSecondary 不再作正文）",
            r < 4.5,
        )
        assertEquals("浅色 fgSecondary 钉值（回弹/继续变淡都先红）", 4.46, r, 0.15)
    }

    // ── Card 组（仅顶栏/输入栏悬浮栏）：正文仅 fg，fg 须 ≥AA ──

    @Test
    fun lightText_onBarGradient_passesAa() {
        val interior = cardInterior(lightBarWorstFill, backdropFiltered(LightPalette.fluidA))
        assertReadable("浅色 fg/悬浮栏 Card 渐变（流光最深色之上，最坏停靠点=底停）", LightPalette.fg, interior)
    }

    @Test
    fun darkText_onBarGradient_passesAa() {
        val interior = cardInterior(darkBarWorstFill, backdropFiltered(DarkPalette.fluidA))
        assertReadable("深色 fg/悬浮栏 Card 渐变（流光最亮色之上，最坏停靠点=顶停）", DarkPalette.fg, interior)
        // 栏内虽不用 fgSecondary 作正文，深色该值仍达标，一并守住
        assertReadable("深色 fgSecondary/悬浮栏 Card 渐变（最坏停靠点=顶停）", DarkPalette.fgSecondary, interior)
    }

    @Test
    fun lightFgSecondary_onBarGradient_belowAa_recorded() {
        val interior = cardInterior(lightBarWorstFill, backdropFiltered(LightPalette.fluidA))
        val r = GtjContrast.ratio(LightPalette.fgSecondary, interior)
        assertTrue(
            "浅色 fgSecondary/悬浮栏 Card 渐变 应 <4.5（web --wy-card 渐变本就只铺 .150/.105 白、" +
                "栏内正文用 fg 的规格取舍记录）",
            r < 4.5,
        )
        assertEquals("浅色 fgSecondary 钉值", 3.62, r, 0.15)
    }

    @Test
    fun documentedRatios_hold() {
        // 把 Color.kt 注释里写下的实测值钉在生产矩阵上（容差 0.15）：若 token 或矩阵公式再动，
        // 这里先红，而不是让注释悄悄过期。数值 = 「流光最深/最亮基色 → 玻璃填充 → 卡片内实色」上的
        // WCAG 对比度（v1.9.4 两组填充，frost=0.300，brightness=1.0）。
        val lightFrost = cardInterior(LightPalette.glassFill, backdropFiltered(LightPalette.fluidA))
        assertEquals("浅色 fg/Frost", 6.95, GtjContrast.ratio(LightPalette.fg, lightFrost), 0.15)
        assertEquals("浅色 fgSecondary/Frost", 4.46, GtjContrast.ratio(LightPalette.fgSecondary, lightFrost), 0.15)
        assertEquals("浅色 muted/Frost", 2.68, GtjContrast.ratio(LightPalette.muted, lightFrost), 0.15)
        val darkFrost = cardInterior(DarkPalette.glassFill, backdropFiltered(DarkPalette.fluidA))
        assertEquals("深色 fg/Frost", 9.01, GtjContrast.ratio(DarkPalette.fg, darkFrost), 0.15)
        assertEquals("深色 fgSecondary/Frost", 6.20, GtjContrast.ratio(DarkPalette.fgSecondary, darkFrost), 0.15)
        assertEquals("深色 muted/Frost", 4.07, GtjContrast.ratio(DarkPalette.muted, darkFrost), 0.15)
        val lightBar = cardInterior(lightBarWorstFill, backdropFiltered(LightPalette.fluidA))
        assertEquals("浅色 fg/Bar 渐变", 5.64, GtjContrast.ratio(LightPalette.fg, lightBar), 0.15)
        assertEquals("浅色 fgSecondary/Bar 渐变", 3.62, GtjContrast.ratio(LightPalette.fgSecondary, lightBar), 0.15)
        assertEquals("浅色 muted/Bar 渐变", 2.18, GtjContrast.ratio(LightPalette.muted, lightBar), 0.15)
        val darkBar = cardInterior(darkBarWorstFill, backdropFiltered(DarkPalette.fluidA))
        assertEquals("深色 fg/Bar 渐变", 8.19, GtjContrast.ratio(DarkPalette.fg, darkBar), 0.15)
        assertEquals("深色 fgSecondary/Bar 渐变", 5.63, GtjContrast.ratio(DarkPalette.fgSecondary, darkBar), 0.15)
        assertEquals("深色 muted/Bar 渐变", 3.70, GtjContrast.ratio(DarkPalette.muted, darkBar), 0.15)
    }

    // ── 色相滑条 0-360° 逐度扫描护栏 ──

    @Test
    fun lightText_onFrostCard_staysReadableAcrossTheWholeHueRange() {
        val (fgWorst, fgHue) = worstRatio(LightPalette.fg, LightPalette.glassFill, LightPalette.fluidA)
        assertTrue(
            "浅色 fg/Frost 全量程最低 ${GtjContrast.format(fgWorst)} @${fgHue}° 应 >= 4.5",
            fgWorst >= 4.5,
        )
        // 实测值（W3C 矩阵 + Frost .30）：fg 6.47 @220°（钉住，防悄悄回退）
        assertEquals("浅色 fg/Frost 全量程最低", 6.47, fgWorst, 0.25)
    }

    @Test
    fun darkText_onFrostCard_staysReadableAcrossTheWholeHueRange() {
        val (fgWorst, fgHue) = worstRatio(DarkPalette.fg, DarkPalette.glassFill, DarkPalette.fluidA)
        val (secWorst, secHue) = worstRatio(DarkPalette.fgSecondary, DarkPalette.glassFill, DarkPalette.fluidA)
        assertTrue(
            "深色 fg/Frost 全量程最低 ${GtjContrast.format(fgWorst)} @${fgHue}° 应 >= 4.5",
            fgWorst >= 4.5,
        )
        assertTrue(
            "深色 fgSecondary/Frost 全量程最低 ${GtjContrast.format(secWorst)} @${secHue}° 应 >= 4.5",
            secWorst >= 4.5,
        )
        // 实测值（W3C 矩阵 + Frost .30）：fg 8.66 @309°、fgSecondary 5.96 @309°（钉住，防悄悄回退）
        assertEquals("深色 fg/Frost 全量程最低", 8.66, fgWorst, 0.25)
        assertEquals("深色 fgSecondary/Frost 全量程最低", 5.96, secWorst, 0.25)
    }

    @Test
    fun lightText_onBarGradient_staysReadableAcrossTheWholeHueRange() {
        val (fgWorst, fgHue) = worstRatio(LightPalette.fg, lightBarWorstFill, LightPalette.fluidA)
        assertTrue(
            "浅色 fg/Bar 渐变全量程最低 ${GtjContrast.format(fgWorst)} @${fgHue}° 应 >= 4.5",
            fgWorst >= 4.5,
        )
        // 实测值（W3C 矩阵 + Card 渐变底停）：fg 4.99 @220°（钉住，防悄悄回退）
        assertEquals("浅色 fg/Bar 渐变全量程最低", 4.99, fgWorst, 0.25)
    }

    @Test
    fun darkText_onBarGradient_staysReadableAcrossTheWholeHueRange() {
        val (fgWorst, fgHue) = worstRatio(DarkPalette.fg, darkBarWorstFill, DarkPalette.fluidA)
        val (secWorst, secHue) = worstRatio(DarkPalette.fgSecondary, darkBarWorstFill, DarkPalette.fluidA)
        assertTrue(
            "深色 fg/Bar 渐变全量程最低 ${GtjContrast.format(fgWorst)} @${fgHue}° 应 >= 4.5",
            fgWorst >= 4.5,
        )
        assertTrue(
            "深色 fgSecondary/Bar 渐变全量程最低 ${GtjContrast.format(secWorst)} @${secHue}° 应 >= 4.5",
            secWorst >= 4.5,
        )
        // 实测值（W3C 矩阵 + Card 渐变顶停）：fg 7.75 @306°、fgSecondary 5.33 @306°（钉住，防悄悄回退）
        assertEquals("深色 fg/Bar 渐变全量程最低", 7.75, fgWorst, 0.25)
        assertEquals("深色 fgSecondary/Bar 渐变全量程最低", 5.33, secWorst, 0.25)
    }

    @Test
    fun secondaryAndMuted_worstOverHueRange_recorded() {
        // 记录性断言：浅色 fgSecondary（Frost 4.15 / Bar 渐变 3.20）与 muted（Frost 亮 2.50 / 暗 3.91）
        // 全量程最坏 <4.5——正文层一律用 fg/（深色）fgSecondary，见类注释的取舍说明。
        val (lightSec, lightSecHue) = worstRatio(LightPalette.fgSecondary, LightPalette.glassFill, LightPalette.fluidA)
        val (lightMuted, lightMutedHue) = worstRatio(LightPalette.muted, LightPalette.glassFill, LightPalette.fluidA)
        val (darkMuted, darkMutedHue) = worstRatio(DarkPalette.muted, DarkPalette.glassFill, DarkPalette.fluidA)
        val (barSec, barSecHue) = worstRatio(LightPalette.fgSecondary, lightBarWorstFill, LightPalette.fluidA)
        assertTrue("浅色 fgSecondary/Frost 全量程 @${lightSecHue}° 应 <4.5（记录性短板）", lightSec < 4.5)
        assertTrue("浅色 muted/Frost 全量程 @${lightMutedHue}° 应 <4.5（正文不用 muted）", lightMuted < 4.5)
        assertTrue("深色 muted/Frost 全量程 @${darkMutedHue}° 应 <4.5（正文不用 muted）", darkMuted < 4.5)
        assertTrue("浅色 fgSecondary/Bar 渐变全量程 @${barSecHue}° 应 <4.5（栏内正文用 fg）", barSec < 4.5)
        assertEquals("浅色 fgSecondary/Frost 全量程最低", 4.15, lightSec, 0.25)
        assertEquals("浅色 muted/Frost 全量程最低", 2.50, lightMuted, 0.25)
        assertEquals("深色 muted/Frost 全量程最低", 3.91, darkMuted, 0.25)
        assertEquals("浅色 fgSecondary/Bar 渐变全量程最低", 3.20, barSec, 0.25)
    }

    // ── 配方方向护栏：两组填充 = web 分组（frost=0.300 实算值），不透明回退应拒绝 ──

    @Test
    fun glassFills_matchMicaGroups() {
        // Frost 组（默认）：web frost 版 --glass 直色，亮 rgb(253,249,242)/暗 rgb(46,36,28) 均 0.30
        assertTrue(
            "浅色 glassFill alpha 应 = 0.30（frost 版 --glass），实际 ${LightPalette.glassFill.alpha}",
            LightPalette.glassFill.alpha in 0.29f..0.31f,
        )
        assertTrue(
            "深色 glassFill alpha 应 = 0.30（frost 版 --glass），实际 ${DarkPalette.glassFill.alpha}",
            DarkPalette.glassFill.alpha in 0.29f..0.31f,
        )
        // strong 镜像 token（web --glass-strong）仍须比 Frost 更不透明（GlassTokenTest 亦有护栏）
        assertTrue(
            "glassFillStrong 应比 glassFill 更不透明（浅）",
            LightPalette.glassFillStrong.alpha > LightPalette.glassFill.alpha,
        )
        assertTrue(
            "glassFillStrong 应比 glassFill 更不透明（深）",
            DarkPalette.glassFillStrong.alpha > DarkPalette.glassFill.alpha,
        )
        // Card 组（仅顶栏/输入栏）：亮色 白 frost×0.50=0.150 / frost×0.35=0.105（styles.css:526-528）
        assertTrue(
            "浅色顶停 alpha 应 = frost×0.50 ≈ 0.149，实际 ${LightPalette.glassCardFillTop.alpha}",
            LightPalette.glassCardFillTop.alpha in 0.14f..0.16f,
        )
        assertTrue(
            "浅色底停 alpha 应 = frost×0.35 ≈ 0.106，实际 ${LightPalette.glassCardFillBottom.alpha}",
            LightPalette.glassCardFillBottom.alpha in 0.095f..0.115f,
        )
        // 暗色：两停均 frost×0.50 ≈ 0.149（styles.css:529-531）
        assertTrue(
            "深色顶停 alpha 应 = frost×0.50 ≈ 0.149，实际 ${DarkPalette.glassCardFillTop.alpha}",
            DarkPalette.glassCardFillTop.alpha in 0.14f..0.16f,
        )
        assertTrue(
            "深色底停 alpha 应 = frost×0.50 ≈ 0.149，实际 ${DarkPalette.glassCardFillBottom.alpha}",
            DarkPalette.glassCardFillBottom.alpha in 0.14f..0.16f,
        )
    }

    @Test
    fun backdrop_matchesMicaBlurAndTwoTimesPadding() {
        // v1.9.4 收尾：运行时半径锁死 GLASS_BLUR_DEFAULT（100dp，设置页滑条已移除），
        // GlassBackdropParams 两个半径与运行时值一致，
        // 采样余量维持 2×blur 不变式（recordBlur 运行时 samplePadding = 2×传入半径）
        assertEquals(100.dp, GlassBackdropParams.blurRadius)
        assertEquals(GlassBackdropParams.blurRadius * 2, GlassBackdropParams.samplePadding)
        assertEquals(100.dp, GlassBackdropParams.cardBlurRadius)
        assertEquals(GlassBackdropParams.cardBlurRadius * 2, GlassBackdropParams.cardSamplePadding)
    }

    // ── 磨砂度滑条派生护栏（v1.9.4 玻璃可调）：withGlassFrost 只改 alpha、RGB 不变 ──

    @Test
    fun withGlassFrost_derivedAlphas_matchWebFormula() {
        // 公式出处（ui/theme/Color.kt GtjPalette.withGlassFrost，对齐 web styles.css:483-488/526-531）：
        // glassFill.alpha = frost；glassFillStrong.alpha = frost + (基值 strong − 基值 fill)（截到 1，
        // = web calc(frost+0.17)/calc(frost+0.29)）；cardFillTop/Bottom.alpha = frost × (基值 alpha /
        // 基值 fill.alpha)（= web calc(frost×0.50)/calc(frost×0.35) 与暗色 ×0.50；基 frost=0.30）。
        // 默认磨砂档 frost=0.60（= 磨砂度滑条 60%）：
        val light = LightPalette.withGlassFrost(0.60f)
        assertEquals("亮 glassFill = frost", 0.60f, light.glassFill.alpha, 0.02f)
        assertEquals("亮 cardFillTop = frost×0.50", 0.30f, light.glassCardFillTop.alpha, 0.02f)
        assertEquals("亮 cardFillBottom = frost×0.35", 0.21f, light.glassCardFillBottom.alpha, 0.02f)
        assertEquals("亮 glassFillStrong = frost+0.17", 0.77f, light.glassFillStrong.alpha, 0.02f)
        val dark = DarkPalette.withGlassFrost(0.60f)
        assertEquals("暗 glassFillStrong = frost+0.29", 0.89f, dark.glassFillStrong.alpha, 0.02f)
        // RGB 不变（只改 alpha）：四派生字段两主题逐一核对
        assertEquals("亮 glassFill RGB 不变", LightPalette.glassFill.copy(alpha = 1f), light.glassFill.copy(alpha = 1f))
        assertEquals(
            "亮 glassFillStrong RGB 不变",
            LightPalette.glassFillStrong.copy(alpha = 1f),
            light.glassFillStrong.copy(alpha = 1f),
        )
        assertEquals(
            "亮 cardFillTop RGB 不变",
            LightPalette.glassCardFillTop.copy(alpha = 1f),
            light.glassCardFillTop.copy(alpha = 1f),
        )
        assertEquals(
            "亮 cardFillBottom RGB 不变",
            LightPalette.glassCardFillBottom.copy(alpha = 1f),
            light.glassCardFillBottom.copy(alpha = 1f),
        )
        assertEquals("暗 glassFill RGB 不变", DarkPalette.glassFill.copy(alpha = 1f), dark.glassFill.copy(alpha = 1f))
        // 截到 1 的护栏：frost=1.0 时暗色 strong 0.29 增量会越界，必须被截住
        assertEquals("frost=1.0 时 strong 截到 1", 1f, DarkPalette.withGlassFrost(1f).glassFillStrong.alpha, 0.0001f)
        // 基点回归：frost = 基值 fill alpha（0.30196…，0x4D/255）时四字段应回到基值（容差 0.001，
        // 证明派生比例相对基值、非硬编码绝对值）；withGlassFrost 返回副本，静态基值 token 不会被改写
        val atBase = LightPalette.withGlassFrost(LightPalette.glassFill.alpha)
        assertEquals("frost=基值时 glassFill 回到基值", LightPalette.glassFill.alpha, atBase.glassFill.alpha, 0.001f)
        assertEquals("frost=基值时 cardFillTop 回到基值", LightPalette.glassCardFillTop.alpha, atBase.glassCardFillTop.alpha, 0.001f)
        assertEquals("frost=基值时 cardFillBottom 回到基值", LightPalette.glassCardFillBottom.alpha, atBase.glassCardFillBottom.alpha, 0.001f)
        assertEquals("frost=基值时 strong 回到基值", LightPalette.glassFillStrong.alpha, atBase.glassFillStrong.alpha, 0.001f)
    }
}
