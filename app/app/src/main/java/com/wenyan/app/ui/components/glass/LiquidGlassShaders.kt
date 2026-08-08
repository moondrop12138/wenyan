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
 * - 折射（Refraction）：边缘透镜效应，内容透过玻璃时边缘弯曲
 * - 动态镜面高光（Dynamic Specular）：随时间/滚动流动的光带
 * - 边缘透镜（Lens Edge）：玻璃边缘的亮边/辉光
 *
 * v1.8.1 B4：移除光斑交互（Glow Interaction）——链路未接通且每帧重组开销大，已删 dead path。
 *
 * 降级策略：API < 33 或 AGSL 编译失败时返回 null，调用方回退到静态亮边方案。
 */
object LiquidGlassShaders {

    /**
     * 折射 Shader：边缘区域内容向内弯曲，模拟凸透镜效果。
     * uniform:
     *   uResolution: vec2 - 玻璃尺寸（像素）
     *   uRadius: float - 圆角半径（像素）
     *   uRefractionStrength: float - 折射强度 0.0~1.0
     *   uTime: float - 时间（秒），用于动态折射波动
     */
    private const val REFRACTION_SHADER = """
        uniform shader uContentShader;
        uniform float2 uResolution;
        uniform float uRadius;
        uniform float uRefractionStrength;
        uniform float uTime;

        // 计算点到圆角矩形边缘的归一化距离（0=中心，1=边缘）
        float edgeDistance(float2 pos, float2 size, float radius) {
            float2 halfSize = size * 0.5;
            float2 center = halfSize;
            float2 d = abs(pos - center) - (halfSize - radius);
            float outside = length(max(d, 0.0));
            float inside = min(max(d.x, d.y), 0.0);
            float dist = outside + inside;
            // 归一化：0 在中心，1 在边缘
            float maxDist = min(halfSize.x, halfSize.y);
            return clamp(dist / maxDist, 0.0, 1.0);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / uResolution;
            float edge = edgeDistance(fragCoord, uResolution, uRadius);

            // 折射只在边缘 30% 区域生效
            float refractionZone = smoothstep(0.7, 1.0, edge);
            if (refractionZone < 0.001) {
                return uContentShader.eval(fragCoord);
            }

            // 折射偏移：边缘内容向内弯曲，带时间波动
            float wave = sin(uTime * 2.0 + edge * 10.0) * 0.002;
            float2 offset = (uv - 0.5) * refractionZone * uRefractionStrength * 0.03;
            offset += wave * refractionZone;

            float2 refractedCoord = fragCoord - offset * uResolution;
            return uContentShader.eval(refractedCoord);
        }
    """

    /**
     * 动态镜面高光 Shader：随时间流动的光带。
     * uniform:
     *   uResolution: vec2
     *   uTime: float
     *   uScrollVelocity: float - 滚动速度（-1~1），影响高光流动方向与强度
     *   uHighlightColor: vec4 - 高光颜色
     */
    private const val SPECULAR_SHADER = """
        uniform float2 uResolution;
        uniform float uTime;
        uniform float uScrollVelocity;
        uniform float4 uHighlightColor;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / uResolution;

            // 基础顶部高光（静态）
            float topGlow = smoothstep(0.0, 0.15, 1.0 - uv.y) * 0.3;

            // 动态流动光带：随时间+滚动速度移动
            float flowSpeed = 0.3 + abs(uScrollVelocity) * 0.5;
            float flowPos = fract(uTime * flowSpeed + uScrollVelocity * 0.3);
            float flowBand = smoothstep(0.0, 0.1, 1.0 - abs(uv.y - flowPos)) * 0.2;

            // 边缘高光（透镜亮边）
            float edgeX = smoothstep(0.0, 0.05, uv.x) * smoothstep(0.0, 0.05, 1.0 - uv.x);
            float edgeY = smoothstep(0.0, 0.05, uv.y) * smoothstep(0.0, 0.05, 1.0 - uv.y);
            float edgeGlow = (1.0 - edgeX * edgeY) * 0.15;

            // 滚动时高光增强
            float scrollBoost = 1.0 + abs(uScrollVelocity) * 0.8;

            float total = (topGlow + flowBand + edgeGlow) * scrollBoost;
            return half4(uHighlightColor.rgb, total * uHighlightColor.a);
        }
    """

    /**
     * 边缘透镜 Shader：玻璃边缘的亮边与辉光 + iOS 风格伪折射色散条纹。
     * uniform:
     *   uResolution: vec2
     *   uRadius: float
     *   uEdgeColor: vec4 - 边缘亮边颜色
     *   uGlowColor: vec4 - 辉光颜色（深色模式用）
     *   uIsDarkMode: float - 0=浅色，1=深色
     *   uTime: float - 时间（秒），驱动动态折射波纹
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
            vec3 dispersion = vec3(
                stripe * 0.15,      // R 偏移
                stripe2 * 0.12,     // G 偏移
                stripe3 * 0.10      // B 偏移
            ) * refractionZone;

            // 动态高光：波纹驱动的高光带
            float dynamicHighlight = wave * wave2 * refractionZone * 0.25;

            // 组合：基础边缘 + 色散 + 动态高光
            float4 baseEdge = uEdgeColor * lightEdge + uGlowColor * glow;
            vec3 finalColor = baseEdge.rgb + dispersion + dynamicHighlight;

            // 透明度：边缘区域整体提亮
            float finalAlpha = baseEdge.a + refractionZone * 0.1;

            return half4(finalColor, finalAlpha);
        }
    """

    /**
     * 创建折射 Shader（API 33+）
     * @param contentShader 内容 Shader（通常是 BitmapShader 或 Compose 的 layer shader）
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createRefractionShader(
        contentShader: RuntimeShader,
        size: Size,
        cornerRadius: Float,
        refractionStrength: Float = 0.5f,
        time: Float = 0f,
    ): RuntimeShader {
        return RuntimeShader(REFRACTION_SHADER).apply {
            setInputShader("uContentShader", contentShader)
            setFloatUniform("uResolution", size.width, size.height)
            setFloatUniform("uRadius", cornerRadius)
            setFloatUniform("uRefractionStrength", refractionStrength)
            setFloatUniform("uTime", time)
        }
    }

    /**
     * 创建动态镜面高光 Shader（API 33+）
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createSpecularShader(
        size: Size,
        time: Float = 0f,
        scrollVelocity: Float = 0f,
        highlightColor: Color = Color.White.copy(alpha = 0.4f),
    ): RuntimeShader {
        return RuntimeShader(SPECULAR_SHADER).apply {
            setFloatUniform("uResolution", size.width, size.height)
            setFloatUniform("uTime", time)
            setFloatUniform("uScrollVelocity", scrollVelocity)
            setFloatUniform("uHighlightColor", highlightColor.red, highlightColor.green, highlightColor.blue, highlightColor.alpha)
        }
    }

    /**
     * 创建边缘透镜 Shader（API 33+）
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
