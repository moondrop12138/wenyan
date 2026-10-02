package com.wenyan.app.ui.components.glass

import android.os.Build

/**
 * 玻璃渲染模式（v1.9.4 探测降级为观测后的**唯一行为判定来源**）：SDK 版本是玻璃行为的
 * 唯一开关——能力探测（[GlassBlurCapabilityProbe]）已降级为运行时观测，其结论只进
 * logcat 与设置页状态行，不再否决任何玻璃行为（31+ 恒真实模糊；ROM 不渲染模糊构造的
 * 设备上模糊静默失效为已接受取舍，雾化兜底不再对 31+ 生效）。
 */
internal enum class GlassRenderMode {
    /** API 31+：RenderEffect 门槛之上——创建 backdrop、垫真实高斯模糊。 */
    REAL_BLUR,

    /** API < 31：静态玻璃 + 雾化兜底（[glassFogAlpha] √ 曲线随玻璃模糊度滑条响应）。 */
    FOG,
}

/**
 * 按 SDK 版本判玻璃渲染模式（纯函数，JVM 钉值于 GlassRenderModeTest，30/31 边界）：
 * - `sdkInt >= 31` → [GlassRenderMode.REAL_BLUR]：[rememberGlassBackdrop] 恒创建 backdrop；
 * - `sdkInt < 31` → [GlassRenderMode.FOG]：RenderEffect 门槛之下，静态玻璃 + 雾化兜底。
 * [rememberGlassBackdrop] 与 [liquidGlass] 的 fogFallback 双消费方共用本判定，防两处
 * 版本门槛漂移。internal（仅模块内 + 单测可见）：同 [glassFogAlpha] 惯例。
 */
internal fun glassRenderMode(sdkInt: Int): GlassRenderMode =
    if (sdkInt >= Build.VERSION_CODES.S) GlassRenderMode.REAL_BLUR else GlassRenderMode.FOG

/**
 * 设置页「玻璃」卡的探测结论文案（纯函数，JVM 钉值）：CAPABLE→已确认；FAILED→未通过
 * （TIMEOUT→超时）；PENDING/PROBING→未见结论。探测仅观测——文案只随
 * [GlassBlurCapabilityProbe.state]/[GlassBlurCapabilityProbe.failure] 翻转重组刷新，
 * 不改变任何玻璃行为。
 */
internal fun glassProbeStatusText(
    state: GlassBlurCapabilityProbe.ProbeState,
    failure: GlassBlurCapabilityProbe.ProbeFailure?,
): String = when (state) {
    GlassBlurCapabilityProbe.ProbeState.CAPABLE -> "已确认"
    GlassBlurCapabilityProbe.ProbeState.FAILED ->
        if (failure == GlassBlurCapabilityProbe.ProbeFailure.TIMEOUT) "超时" else "未通过"
    GlassBlurCapabilityProbe.ProbeState.PENDING,
    GlassBlurCapabilityProbe.ProbeState.PROBING,
    -> "未见结论"
}
