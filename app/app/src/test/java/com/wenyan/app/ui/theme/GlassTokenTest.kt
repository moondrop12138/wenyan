package com.wenyan.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.0 液态玻璃 token 自检（纯 JVM）：
 * - glass 四要素 + glow + dot 字段存在且 alpha 区间正确
 * - strong 填充比普通玻璃更不透明（高密度容器可读性）
 * - glow 光斑为暖色相（R > B，与陶土棕系一致）
 * - dot 四态跨主题恒定
 * - alpha 合成函数与已知值一致
 */
class GlassTokenTest {

    private fun alpha(c: Color) = c.alpha
    private fun isWarm(c: Color) = c.red > c.blue

    @Test
    fun glassTokens_haveValidAlpha() {
        listOf(
            LightPalette.glassFill to "浅 glassFill",
            LightPalette.glassFillStrong to "浅 glassFillStrong",
            LightPalette.glassBorder to "浅 glassBorder",
            LightPalette.glassEdgeHighlight to "浅 glassEdgeHighlight",
            DarkPalette.glassFill to "深 glassFill",
            DarkPalette.glassFillStrong to "深 glassFillStrong",
            DarkPalette.glassBorder to "深 glassBorder",
            DarkPalette.glassEdgeHighlight to "深 glassEdgeHighlight",
        ).forEach { (c, label) ->
            assertTrue("$label alpha 应 ∈ (0,1)，实际 ${alpha(c)}", alpha(c) in 0.001f..0.999f)
        }
    }

    @Test
    fun glassFillStrong_isMoreOpaqueThanFill() {
        assertTrue(
            "strong alpha 应大于普通 fill（浅）",
            alpha(LightPalette.glassFillStrong) > alpha(LightPalette.glassFill),
        )
        assertTrue(
            "strong alpha 应大于普通 fill（深）",
            alpha(DarkPalette.glassFillStrong) > alpha(DarkPalette.glassFill),
        )
    }

    @Test
    fun glowTokens_areWarmHued() {
        listOf(
            LightPalette.glowA, LightPalette.glowB, LightPalette.glowC,
            DarkPalette.glowA, DarkPalette.glowB, DarkPalette.glowC,
        ).forEach { c ->
            assertTrue("光斑应为暖色相（R>B）：${c.value}", isWarm(c))
        }
    }

    @Test
    fun glowTokens_haveLowAlpha() {
        listOf(
            LightPalette.glowA, LightPalette.glowB, LightPalette.glowC,
            DarkPalette.glowA, DarkPalette.glowB, DarkPalette.glowC,
        ).forEach { c ->
            assertTrue("光斑 alpha 应低（≤0.95）：${c.value}", alpha(c) <= 0.95f)
        }
    }

    @Test
    fun statusDotColors_areThemeInvariant() {
        // 四态色跨浅深恒定（原型 sdot 全局固定）
        listOf(
            "connected" to (LightPalette.dotConnected to DarkPalette.dotConnected),
            "connecting" to (LightPalette.dotConnecting to DarkPalette.dotConnecting),
            "thinking" to (LightPalette.dotThinking to DarkPalette.dotThinking),
            "failure" to (LightPalette.dotFailure to DarkPalette.dotFailure),
        ).forEach { (name, pair) ->
            assertTrue("$name 状态点浅深应同色", pair.first == pair.second)
        }
    }

    @Test
    fun userBubbleTint_isThreeStopWarmGradient() {
        assertTrue("浅 tint 3 停靠点", LightUserBubbleTint.size == 3)
        assertTrue("深 tint 3 停靠点", DarkUserBubbleTint.size == 3)
        LightUserBubbleTint.map { it.second }.forEach { c -> assertTrue("浅 tint 暖色相", isWarm(c)) }
        DarkUserBubbleTint.map { it.second }.forEach { c -> assertTrue("深 tint 暖色相", isWarm(c)) }
    }

    @Test
    fun composite_matchesKnownValues() {
        // 已知值：白 50% 叠黑 = 中灰(0.5)
        val gray = GtjContrast.composite(Color.White.copy(alpha = 0.5f), Color.Black)
        assertTrue("白50%叠黑应≈中灰", kotlin.math.abs(gray.red - 0.5f) < 0.01f)
        // 完全不透明 over = 自身
        val solid = GtjContrast.composite(Color.Red, Color.Blue)
        assertTrue("完全不透明 over = 自身", solid == Color.Red)
    }

    @Test
    fun coachInnerCard_tokensExist() {
        assertTrue("军师内卡 fill alpha ∈(0,1)", alpha(LightCoachInnerFill) in 0.001f..0.999f)
        assertTrue("深色内卡 fill alpha ∈(0,1)", alpha(DarkCoachInnerFill) in 0.001f..0.999f)
    }
}
