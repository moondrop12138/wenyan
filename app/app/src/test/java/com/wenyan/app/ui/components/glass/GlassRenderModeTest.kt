package com.wenyan.app.ui.components.glass

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 玻璃行为判定纯函数的 JVM 钉值自检（v1.9.4 探测降级为观测后，行为唯一开关 = SDK 版本）：
 * - [glassRenderMode]：30/31 边界——31+ 恒真实模糊（探测结论不再否决任何行为），
 *   <31 雾化兜底；
 * - [glassProbeStatusText]：探测结论 → 设置页状态行文案的四态映射（仅观测，不改行为）。
 */
class GlassRenderModeTest {

    /** API 30（Android 11，RenderEffect 门槛之下）→ 雾化兜底。 */
    @Test
    fun `api 30 falls back to fog`() {
        assertEquals(GlassRenderMode.FOG, glassRenderMode(30))
    }

    /** API 31（Android 12，RenderEffect 门槛）→ 真实模糊（边界含 31）。 */
    @Test
    fun `api 31 uses real blur`() {
        assertEquals(GlassRenderMode.REAL_BLUR, glassRenderMode(31))
    }

    /** 更低/更高版本方向性扫描：<31 全雾化、31+ 全真实模糊。 */
    @Test
    fun `render mode follows sdk boundary monotonically`() {
        for (sdk in 21..30) assertEquals("sdk=$sdk", GlassRenderMode.FOG, glassRenderMode(sdk))
        for (sdk in 31..36) assertEquals("sdk=$sdk", GlassRenderMode.REAL_BLUR, glassRenderMode(sdk))
    }

    /** CAPABLE → 已确认。 */
    @Test
    fun `capable maps to confirmed`() {
        assertEquals(
            "已确认",
            glassProbeStatusText(GlassBlurCapabilityProbe.ProbeState.CAPABLE, null),
        )
    }

    /** FAILED + 非 TIMEOUT（异常/读回空/不渲染）→ 未通过。 */
    @Test
    fun `failed without timeout maps to not passed`() {
        assertEquals(
            "未通过",
            glassProbeStatusText(GlassBlurCapabilityProbe.ProbeState.FAILED, GlassBlurCapabilityProbe.ProbeFailure.EXCEPTION),
        )
        assertEquals(
            "未通过",
            glassProbeStatusText(GlassBlurCapabilityProbe.ProbeState.FAILED, GlassBlurCapabilityProbe.ProbeFailure.READ_BACK_EMPTY),
        )
        assertEquals(
            "未通过",
            glassProbeStatusText(GlassBlurCapabilityProbe.ProbeState.FAILED, null),
        )
    }

    /** FAILED + TIMEOUT → 超时（2s 兜底单独成态，与「不渲染」区分）。 */
    @Test
    fun `failed with timeout maps to timeout`() {
        assertEquals(
            "超时",
            glassProbeStatusText(GlassBlurCapabilityProbe.ProbeState.FAILED, GlassBlurCapabilityProbe.ProbeFailure.TIMEOUT),
        )
    }

    /** PENDING/PROBING → 未见结论（懒触发前/探测进行中）。 */
    @Test
    fun `pending and probing map to no verdict`() {
        assertEquals(
            "未见结论",
            glassProbeStatusText(GlassBlurCapabilityProbe.ProbeState.PENDING, null),
        )
        assertEquals(
            "未见结论",
            glassProbeStatusText(GlassBlurCapabilityProbe.ProbeState.PROBING, null),
        )
    }
}
