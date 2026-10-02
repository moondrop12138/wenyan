package com.wenyan.app.ui.components.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 悬浮栏降采样放大的录制缩放（[blurRecordScale]）纯 JVM 钉值自检（同
 * [GlassTransparencyReadabilityTest] 惯例：断言生产函数输出）。
 *
 * 背景：平台 RenderEffect 模糊半径有 ~64 物理像素硬上限（v1.9.4 玻璃可调二改实测：
 * 模拟器 API 35 上 52px 有效、66px 起整个效果被静默丢弃——「滑条拖大不更糊」的根因）。
 * 护栏意图：半径 ≤ 上限恒 1:1（既证可靠路径）；超限 s = 上限/半径，使层空间模糊半径
 * 恒 ≤ 上限；0/负半径防御恒 1（上层无模糊语义，禁得起 52/0 之类的除零）。
 */
class GlassRecordScaleTest {

    @Test
    fun `radius within cap is one to one`() {
        assertEquals(1f, blurRecordScale(0f), 0f)
        assertEquals(1f, blurRecordScale(20f), 0f)
        assertEquals(1f, blurRecordScale(52f), 0f)
        assertEquals(1f, blurRecordScale((-5f)), 0f)
    }

    @Test
    fun `radius beyond cap scales down to cap`() {
        // 20dp @420dpi = 52.5px：恰好压线，s ≈ 0.990（默认档走轻缩放路径）
        assertEquals(52f / 52.5f, blurRecordScale(52.5f), 1e-4f)
        // 40dp @420dpi = 105px（二改前实测已失效的档位）
        assertEquals(52f / 105f, blurRecordScale(105f), 1e-4f)
        // 100dp 满档 @420dpi = 262.5px
        assertEquals(52f / 262.5f, blurRecordScale(262.5f), 1e-4f)
    }

    @Test
    fun `layer space radius never exceeds cap`() {
        // 全量程扫描：半径×s 恒 ≤ 上限（层空间模糊效果永不触发平台丢弃）
        for (dp10 in 1..2000) {
            val radiusPx = dp10 / 10f * 2.625f // 420dpi → px
            val s = blurRecordScale(radiusPx)
            assertTrue("radius=${radiusPx}px 层空间 ${radiusPx * s}px 超上限", radiusPx * s <= 52f + 1e-3f)
        }
    }

    @Test
    fun `visual radius preserved by inverse upscale`() {
        // 画回放大 1/s 后，视觉模糊半径 = 滑条半径（降采样不偷力度）
        val radiusPx = 262.5f
        val s = blurRecordScale(radiusPx)
        assertEquals(radiusPx, 52f / s, 1e-3f)
    }
}
