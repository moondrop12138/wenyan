package com.wenyan.app.ui.components.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * v1.8.0 液态玻璃 2.0 · AGSL Shader 工厂
 *
 * 提供 iOS 26 Liquid Glass 核心特征的 RuntimeShader 实现：
 * - 边缘透镜（Lens Edge）：玻璃边缘的亮边/辉光 + 伪折射色散条纹与动态波纹
 *
 * v1.8.1 B4：移除光斑交互（Glow Interaction）——链路未接通且每帧重组开销大，已删 dead path。
 * v1.9.4：删除 createRefractionShader/createSpecularShader 及 REFRACTION_SHADER/SPECULAR_SHADER
 * ——全仓库无调用点（死路径），且从未真正接入渲染；边缘透镜的色散/波纹统一由
 * LENS_EDGE_SHADER 承担。同次修复 LENS_EDGE_SHADER 的 vec3（AGSL 不支持，真机编译
 * 抛异常被 runCatching 吞掉 → 色散/波纹从未渲染）为 float3。
 *
 * 降级策略：API < 33 或 AGSL 编译失败时返回 null，调用方回退到静态亮边方案。
 */
object LiquidGlassShaders {

    /**
     * 边缘透镜 Shader：玻璃边缘的亮边与辉光 + iOS 风格伪折射色散条纹。
     * uniform（AGSL 合法类型：float2/float3/float4，禁 vec*）：
     *   uResolution: float2
     *   uRadius: float
     *   uEdgeColor: float4 - 边缘亮边颜色
     *   uGlowColor: float4 - 辉光颜色（深色模式用）
     *   uIsDarkMode: float - 0=浅色，1=深色
     *   uTime: float - 时间（秒），驱动动态折射波纹（v1.9.4 起由调用方每帧真实传入）
     *   uRefractionStrength: float - 折射强度 0~1
     */
    private const val LENS_EDGE_SHADER = """
        uniform float2 uResolution;
        uniform float uRadius;
        uniform float4 uEdgeColor;
        uniform float4 uGlowColor;
        uniform float uIsDarkMode;
        uniform float uTime;
        uniform float uRefractionStrength;

        float edgeDistance(float2 pos, float2 size, float radius) {
            float2 halfSize = size * 0.5;
            float2 center = halfSize;
            float2 d = abs(pos - center) - (halfSize - radius);
            float outside = length(max(d, 0.0));
            float inside = min(max(d.x, d.y), 0.0);
            float dist = outside + inside;
            float maxDist = min(halfSize.x, halfSize.y);
            return clamp(dist / maxDist, 0.0, 1.0);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / uResolution;
            float edge = edgeDistance(fragCoord, uResolution, uRadius);

            // === 基础边缘亮边（原有） ===
            float edgeLine = smoothstep(0.92, 0.98, edge) * smoothstep(1.0, 0.98, edge);
            float glow = smoothstep(0.85, 1.0, edge) * uIsDarkMode * 0.4;
            float lightEdge = edgeLine * (1.0 - uIsDarkMode) * 0.6;

            // === iOS 风格伪折射：色散条纹 + 动态波纹 ===
            // 只在边缘 25% 区域生效
            float refractionZone = smoothstep(0.75, 1.0, edge) * uRefractionStrength;

            // 动态波纹：sin 波动模拟折射流动
            float wave = sin(uTime * 2.0 + edge * 20.0) * 0.5 + 0.5;  // 0~1
            float wave2 = sin(uTime * 1.3 + uv.x * 30.0) * 0.5 + 0.5;  // 第二频率

            // 色散条纹：3 层 RGB 分离，模拟色差/色散
            float stripeFreq = 40.0;  // 条纹密度
            float stripe = sin(edge * stripeFreq + uTime * 3.0) * 0.5 + 0.5;
            float stripe2 = sin(edge * stripeFreq * 1.3 + uTime * 2.0 + 1.0) * 0.5 + 0.5;
            float stripe3 = sin(edge * stripeFreq * 0.7 + uTime * 2.5 + 2.0) * 0.5 + 0.5;

            // 色散颜色：轻微 RGB 偏移（红/绿/蓝条纹）
            // v1.9.4 根因①修复：AGSL/SkSL 不支持 vec3（真机 RuntimeShader 编译抛异常被
            // runCatching 吞掉 → 色散/波纹从未渲染，静默回退静态亮边），规范类型为 float3
            float3 dispersion = float3(
                stripe * 0.15,      // R 偏移
                stripe2 * 0.12,     // G 偏移
                stripe3 * 0.10      // B 偏移
            ) * refractionZone;

            // 动态高光：波纹驱动的高光带
            float dynamicHighlight = wave * wave2 * refractionZone * 0.25;

            // 组合：基础边缘 + 色散 + 动态高光
            float4 baseEdge = uEdgeColor * lightEdge + uGlowColor * glow;
            float3 finalColor = baseEdge.rgb + dispersion + dynamicHighlight;

            // 透明度：边缘区域整体提亮
            float finalAlpha = baseEdge.a + refractionZone * 0.1;

            return half4(finalColor, finalAlpha);
        }
    """

    /**
     * 创建边缘透镜 Shader（API 33+）
     * v1.9.4 根因②修复：调用方（LiquidGlass 的 onDrawBehind）必须每帧传入真实 time
     * 驱动 uTime——此前从不传（默认 0f），即便编译通过所有 sin(uTime…) 也静止。
     * @param time 时间（秒），动画帧内由调用方覆写 uniform
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createLensEdgeShader(
        size: Size,
        cornerRadius: Float,
        edgeColor: Color,
        glowColor: Color,
        isDarkMode: Boolean,
        time: Float = 0f,
        refractionStrength: Float = 0.5f,
    ): RuntimeShader? {
        // v1.8.1 B2 修复：个别 ROM 上 AGSL 编译失败抛 IllegalArgumentException，
        // 调用点在绘制路径（onDrawBehind），必须 runCatching 回退，否则直接崩溃
        return runCatching {
            RuntimeShader(LENS_EDGE_SHADER).apply {
                setFloatUniform("uResolution", size.width, size.height)
                setFloatUniform("uRadius", cornerRadius)
                setFloatUniform("uEdgeColor", edgeColor.red, edgeColor.green, edgeColor.blue, edgeColor.alpha)
                setFloatUniform("uGlowColor", glowColor.red, glowColor.green, glowColor.blue, glowColor.alpha)
                setFloatUniform("uIsDarkMode", if (isDarkMode) 1f else 0f)
                setFloatUniform("uTime", time)
                setFloatUniform("uRefractionStrength", refractionStrength)
            }
        }.getOrNull()
    }

    /**
     * 检查当前设备是否支持 RuntimeShader（API 33+）
     */
    fun isRuntimeShaderSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * 检查当前设备是否支持 RenderEffect（API 31+）
     */
    fun isRenderEffectSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
