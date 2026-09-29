package com.wenyan.app.ui.components.glass

import androidx.compose.ui.graphics.Color
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_DEFAULT
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_MAX
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_MIN
import com.wenyan.app.ui.theme.DarkPalette
import com.wenyan.app.ui.theme.FLUID_HUE_DEFAULT
import com.wenyan.app.ui.theme.FLUID_HUE_MAX
import com.wenyan.app.ui.theme.FLUID_HUE_MIN
import com.wenyan.app.ui.theme.LightPalette
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.9.4 三改 流光可调纯函数自检（纯 JVM，无设备）：色相旋转 [hueRotated] 与亮度 veil [brightnessVeil]。
 *
 * 重点守卫「默认值下观感必须与现状完全一致」：hue=0 必须**原样返回**（不做矩阵往返，逐位一致）、
 * brightness=50 必须返回 Transparent（shader 里 mix 恒等 / 降级路径整条绘制被跳过）。
 */
class FluidAppearanceTest {

    // ===== 色相：默认值与恒等边界 =====

    @Test
    fun `zero degrees returns identical color`() {
        // 默认值 0：三个基色都必须逐位等于入参（不是"近似相等"）
        assertEquals(LightPalette.fluidA, hueRotated(LightPalette.fluidA, 0f))
        assertEquals(LightPalette.fluidB, hueRotated(LightPalette.fluidB, 0f))
        assertEquals(DarkPalette.fluidC, hueRotated(DarkPalette.fluidC, 0f))
        assertEquals(0, FLUID_HUE_DEFAULT)
    }

    @Test
    fun `full turn is identity`() {
        assertEquals(LightPalette.fluidA, hueRotated(LightPalette.fluidA, 360f))
        assertEquals(LightPalette.fluidA, hueRotated(LightPalette.fluidA, -720f))
        assertEquals(DarkPalette.fluidA, hueRotated(DarkPalette.fluidA, FLUID_HUE_MAX.toFloat()))
    }

    @Test
    fun `grayscale stays untinted`() {
        // 纯白/纯灰（max == min，饱和度 0）无色相可转 → 原样返回
        assertEquals(Color.White, hueRotated(Color.White, 120f))
        assertEquals(Color(0xFF808080), hueRotated(Color(0xFF808080), 90f))
        assertEquals(Color.Black, hueRotated(Color.Black, 270f))
    }

    @Test
    fun `alpha is preserved`() {
        val semi = Color(0x542E241C)
        assertEquals(semi.alpha, hueRotated(semi, 90f).alpha, 1e-6f)
        assertEquals(1f, hueRotated(LightPalette.fluidA, 45f).alpha, 1e-6f)
    }

    // ===== 色相：旋转语义（W3C hue-rotate 矩阵：色相转、luma 保持）=====

    @Test
    fun `rotation shifts dominant channel as expected`() {
        val orange = LightPalette.fluidA // #C0743F H≈24.7°：R 主通道
        assertTrue("原色应以 R 为主", orange.red > orange.green && orange.green > orange.blue)
        // +120°：H 落到青绿区 → 主通道变 G（矩阵实测 #289D6A）
        val green = hueRotated(orange, 120f)
        assertTrue("+120° 后应以 G 为主：$green", green.green >= green.red && green.green >= green.blue)
        // -120°（= +240°）：H 落到蓝紫区 → 主通道变 B（矩阵实测 #9970D8）
        val blue = hueRotated(orange, -120f)
        assertTrue("-120° 后应以 B 为主：$blue", blue.blue >= blue.red && blue.blue >= blue.green)
        assertTrue("180° 亦应落到蓝区：${hueRotated(orange, 180f)}", hueRotated(orange, 180f).blue >= hueRotated(orange, 180f).red)
    }

    @Test
    fun `slider stops move smoothly across the hue circle`() {
        val stops = (0..360 step 5).map { hueRotated(LightPalette.fluidA, it.toFloat()) }
        assertEquals("0-360 步进 5° 应得 73 个落点", 73, stops.size)
        assertEquals("首尾（0° 与 360°）应回到同一色", stops.first(), stops.last())
        // 平滑：相邻档位只差一小步（不出现跳变）；8bit 量化下允许个别相邻档合并
        stops.zipWithNext().forEach { (a, b) ->
            assertTrue("相邻档位色差应 <0.25：$a → $b", channelDelta(a, b) < 0.25f)
        }
        // 覆盖：整圈存在远离原色的落点（色相真的转过去了）
        assertTrue(
            "应存在与原色差 >0.3 的落点",
            stops.maxOf { channelDelta(it, LightPalette.fluidA) } > 0.3f,
        )
        // 实测 72/73 互不相同（某一对相邻档位量化后同色），下界留 70 防退化
        assertTrue("档位应基本互不相同，实际 ${stops.distinct().size}", stops.distinct().size >= 70)
    }

    @Test
    fun `rotation is reversible within rounding`() {
        // 矩阵是线性近似，越界通道会被夹取 → 往返偏差 ≤ 1 个 8bit 台阶（实测 ≤1/255）；留 0.01 余量
        listOf(0f, 90f, 137f, 180f, 270f).forEach { deg ->
            val there = hueRotated(LightPalette.fluidA, deg)
            val back = hueRotated(there, -deg)
            assertTrue(
                "+$deg/-$deg 往返应回到原色：$back vs ${LightPalette.fluidA}",
                channelDelta(back, LightPalette.fluidA) <= 0.01f,
            )
        }
    }

    @Test
    fun `luma is preserved across the whole hue circle`() {
        // 这就是「色相可调不破坏卡片可读性」的机制：W3C 矩阵三行行和 = 1 且按 Rec.709 权重构造
        // → luma(0.213R+0.715G+0.072B，gamma 域) 保持（实测偏差 ±0.002）。
        // 相比之下 HSL 旋转保 S/L 但保不住亮度：浅色 fluidA 转 215° 相对亮度 0.2406 → 0.062，
        // 卡片内底随之压暗（修复前 190°-344° 区间 fgSecondary <4.5，最坏 3.31:1）。
        listOf(
            LightPalette.fluidA, LightPalette.fluidB, LightPalette.fluidC,
            DarkPalette.fluidA, DarkPalette.fluidB, DarkPalette.fluidC,
            LightPalette.glowA,
        ).forEach { c ->
            val base = luma709(c)
            (0..359 step 5).forEach { deg ->
                val rotated = luma709(hueRotated(c, deg.toFloat()))
                assertTrue(
                    "${c.value} +${deg}° 的 luma ${rotated} 应≈原值 $base（色相不得改变明度）",
                    abs(rotated - base) <= 0.02f,
                )
            }
        }
    }

    @Test
    fun `saturated colors do change`() {
        assertTrue(hueRotated(LightPalette.fluidA, 120f) != LightPalette.fluidA)
        assertTrue(hueRotated(DarkPalette.fluidA, 30f) != DarkPalette.fluidA)
    }

    // ===== 亮度 veil：中点 = 不叠加（默认值一致性）=====

    @Test
    fun `midpoint brightness adds no veil in either theme`() {
        assertEquals(Color.Transparent, brightnessVeil(BG_BRIGHTNESS_DEFAULT, isDark = false))
        assertEquals(Color.Transparent, brightnessVeil(BG_BRIGHTNESS_DEFAULT, isDark = true))
        assertEquals(50, BG_BRIGHTNESS_DEFAULT)
    }

    @Test
    fun `light theme brightens above midpoint only`() {
        val half = brightnessVeil(75, isDark = false)
        // Compose Color 的 alpha 落 8bit（0.5 → 128/255 = 0.50196）→ 容差取 1 个 8bit 台阶
        assertEquals(0.5f, half.alpha, ALPHA_STEP)
        assertEquals(1f, half.red, 1e-6f)
        assertEquals(1f, half.green, 1e-6f)
        assertEquals(1f, half.blue, 1e-6f)
        // 满量程 = 纯白
        assertEquals(Color.White, brightnessVeil(BG_BRIGHTNESS_MAX, isDark = false))
        // 桌面同语义：浅色主题 <50 不叠黑（叠黑会把深棕正文压到不可读）
        assertEquals(Color.Transparent, brightnessVeil(25, isDark = false))
        assertEquals(Color.Transparent, brightnessVeil(BG_BRIGHTNESS_MIN, isDark = false))
    }

    @Test
    fun `dark theme darkens below midpoint only`() {
        val half = brightnessVeil(25, isDark = true)
        assertEquals(0.5f, half.alpha, ALPHA_STEP)
        assertEquals(0f, half.red, 1e-6f)
        assertEquals(0f, half.green, 1e-6f)
        assertEquals(0f, half.blue, 1e-6f)
        assertEquals(Color.Black, brightnessVeil(BG_BRIGHTNESS_MIN, isDark = true))
        // 桌面同语义：深色主题 >50 不叠白
        assertEquals(Color.Transparent, brightnessVeil(75, isDark = true))
        assertEquals(Color.Transparent, brightnessVeil(BG_BRIGHTNESS_MAX, isDark = true))
    }

    @Test
    fun `veil alpha is a triangle ramp`() {
        assertEquals(0.2f, brightnessVeil(60, isDark = false).alpha, 1e-6f)
        assertEquals(1f, brightnessVeil(500, isDark = false).alpha, 1e-6f) // 越界夹取
        assertEquals(0.8f, brightnessVeil(10, isDark = true).alpha, 1e-6f)
        assertEquals(1f, brightnessVeil(-50, isDark = true).alpha, 1e-6f) // 越界夹取
    }

    @Test
    fun `out of range values never produce a veil on the disabled side`() {
        assertEquals(Color.Transparent, brightnessVeil(-50, isDark = false))
        assertEquals(Color.Transparent, brightnessVeil(500, isDark = true))
    }

    @Test
    fun `slider ranges match the confirmed spec`() {
        // 范围与用户确认一致：色相 0-360°、亮度 0-100（中点 50）
        assertEquals(0, FLUID_HUE_MIN)
        assertEquals(360, FLUID_HUE_MAX)
        assertEquals(0, BG_BRIGHTNESS_MIN)
        assertEquals(100, BG_BRIGHTNESS_MAX)
    }

    private fun channelDelta(a: Color, b: Color): Float =
        maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    /** Rec.709 luma（gamma 域加权和，权重即 W3C hue-rotate 矩阵行和用的那组）。 */
    private fun luma709(c: Color): Float = 0.213f * c.red + 0.715f * c.green + 0.072f * c.blue

    private companion object {
        /** Compose Color 的 alpha 存储精度 = 1 个 8bit 台阶（实测 0.5 读回 128/255 = 0.50196）。 */
        const val ALPHA_STEP = 1f / 255f
    }
}
