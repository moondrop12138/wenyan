package com.wenyan.app.ui.components.glass

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.wenyan.app.ui.theme.LocalBgBrightness
import com.wenyan.app.ui.theme.LocalFluidHue
import com.wenyan.app.ui.theme.LocalGtjColors
import com.wenyan.app.ui.theme.LocalGtjIsDark
import com.wenyan.app.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** 单个光斑：半径/基准位置/漂移周期与幅度/相位（原型 g1~g3：22/26/30s，大小 340/300/260px）。 */
private data class Glow(
    val radiusRatio: Float,   // 相对屏宽
    val xRatio: Float,        // 基准中心 x（相对屏宽）
    val yRatio: Float,        // 基准中心 y（相对屏高）
    val period: Float,        // 秒
    val ampRatio: Float,      // 漂移幅度（相对屏宽）
    val phase: Float,
)

private val GLOWS = listOf(
    Glow(radiusRatio = 0.44f, xRatio = 0.20f, yRatio = 0.33f, period = 22f, ampRatio = 0.06f, phase = 0f),
    Glow(radiusRatio = 0.38f, xRatio = 0.82f, yRatio = 0.69f, period = 26f, ampRatio = 0.05f, phase = PI.toFloat() * 0.5f),
    Glow(radiusRatio = 0.33f, xRatio = 0.50f, yRatio = 0.98f, period = 30f, ampRatio = 0.05f, phase = PI.toFloat() * 1.2f),
)

/**
 * v1.8.0 液态玻璃 2.0 · 全屏光斑层
 *
 * 渲染在页面内容之下（根 Box 首子）；玻璃半透明填充让光斑透出形成"液体"氛围。
 * - 动画：withInfiniteAnimationFrameNanos 手动驱动（系统"移除动画"时自动暂停帧回调），
 *   3 个光斑 sin 错相漂移，只触发 Canvas 重绘不触发重组。
 * - reducedMotion：静态光斑（t=0 初始位置）。
 * - 性能：3 个 radialGradient 每帧重绘，软边由渐变自带（无需真 blur）。
 * - 光斑强度随时间轻微波动，模拟「呼吸感」。
 *
 * v1.8.1 B4：移除 onGlowPositionsChanged/GlowState——该链路为 dead path
 * （liquidGlass 接收光斑参数后从未使用），且每帧写 state 导致 60fps 全屏重组。
 *
 * v1.9.4 三改：[FluidBackground] 的降级路径（API < 33 / AGSL 编译失败），故必须与主路径
 * 消费同一套可调设置，否则「流光色相/背景亮度」在旧机型上静默失效：
 * - 色相：光斑三色同走 [hueRotated]（与 fluidA/B/C 同一旋转语义）；
 * - 亮度：绘制末尾叠一层 [brightnessVeil] 矩形（与 shader 的 uVeil 同语义，默认 50 = 不画）。
 */
@Composable
fun GlowBackground(
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    val reduced = rememberReducedMotion()
    // v1.9.4 三改：与 FluidBackground 同一套可调设置（降级路径同样响应）
    val hueDegrees = LocalFluidHue.current
    val bgBrightness = LocalBgBrightness.current
    val isDark = LocalGtjIsDark.current
    // 光斑三色（色相旋转后）与亮度 veil 均在重组级算好：原先 colors 在 draw lambda 内每帧
    // listOf(...) 新建列表（每帧一次分配），随本次接入一并移出帧循环
    val colors = remember(p.glowA, p.glowB, p.glowC, hueDegrees) {
        listOf(
            hueRotated(p.glowA, hueDegrees.toFloat()),
            hueRotated(p.glowB, hueDegrees.toFloat()),
            hueRotated(p.glowC, hueDegrees.toFloat()),
        )
    }
    val veil = remember(bgBrightness, isDark) { brightnessVeil(bgBrightness, isDark) }
    var frameNanos by remember { mutableLongStateOf(0L) }

    if (!reduced) {
        LaunchedEffect(Unit) {
            while (currentCoroutineContext().isActive) {
                withInfiniteAnimationFrameNanos { frameNanos = it }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = frameNanos / 1e9f

        GLOWS.forEachIndexed { i, g ->
            val speed = t / g.period * 2f * PI.toFloat()
            val cx = g.xRatio * w + sin(speed + g.phase) * g.ampRatio * w
            val cy = g.yRatio * h + sin(speed * 0.7f + g.phase) * g.ampRatio * w * 0.6f
            val radius = g.radiusRatio * w

            // 光斑强度「呼吸」波动（0.7~1.0 之间缓慢变化）
            val breathe = 0.85f + 0.15f * sin(speed * 0.3f + g.phase * 2f)

            // v1.7.1：三段衰减（中心亮 → 中段半透明 → 边缘透明），模拟 CSS blur 的柔和漫射
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to colors[i],
                        0.45f to colors[i].copy(alpha = colors[i].alpha * 0.35f * breathe),
                        1f to Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }

        // v1.9.4 三改 背景亮度：与 FluidBackground 的 uVeil 同语义（半透明色叠全屏）。
        // 默认 50 → Transparent，直接跳过，零额外绘制
        if (veil.alpha > 0f) drawRect(color = veil)
    }
}
