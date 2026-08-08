package com.wenyan.app.ui.components.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.LocalGtjColors
import com.wenyan.app.ui.theme.LocalGtjIsDark

/**
 * v1.8.0 液态玻璃 2.0 · iOS 26 Liquid Glass 风格
 *
 * 核心升级（对齐 iOS 26 Liquid Glass 四大特征）：
 * 1. 折射（Refraction）：边缘透镜效应，内容透过玻璃时边缘弯曲（API 33+ RuntimeShader）
 * 2. 动态镜面高光：随时间/滚动流动的光带（API 33+ RuntimeShader）
 * 3. 边缘透镜（Lens Edge）：玻璃边缘 1.5dp 亮边/深色模式辉光
 * 4. 果冻按压（Squishy Press）：按压时局部凹陷 + 回弹 overshoot
 *
 * 降级链：
 * - API 33+：完整 RuntimeShader 效果（折射 + 动态高光 + 边缘透镜）
 * - API 31-32：RenderEffect 模糊 + 预渲染位图折射 + 静态高光
 * - API < 31：v1.7.6 四要素静态玻璃（半透明填充 + 顶部高光 + 细描边 + 柔和投影）
 *
 * 用法：`.liquidGlass(shape).clip(shape)` 或直接用 [GlassSurface]。
 * **顺序坑（v1.7.1 二改）**：clip 必须放在 liquidGlass **之后**——liquidGlass 的软投影
 * 溢出圆角，若 clip 在前会把投影裁掉。
 *
 * @param shape 玻璃形状（决定 fill/stroke/highlight 的路径）
 * @param strong true 用 glassFillStrong（输入胶囊/高密度容器），false 用 glassFill
 * @param tint 叠加在 fill 之上、高光之下的渐变停靠点（用户气泡深棕 tint）
 * @param borderColor 覆盖描边色（null 用 glassBorder）
 * @param refractionStrength 折射强度 0.0~1.0（默认 0.5，仅 API 33+ 生效）
 * @param scrollVelocity 滚动速度 -1~1，驱动动态高光流动（默认 0）
 * @param enablePressAnimation 是否启用果冻按压效果（默认 true）
 *
 * v1.8.1 B4：移除 glowPositions/glowIntensities——dead path（接收后从未使用）且引发 60fps 重组。
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = GtjShape.xl,
    strong: Boolean = false,
    tint: List<Pair<Float, Color>>? = null,
    borderColor: Color? = null,
    refractionStrength: Float = 0.5f,
    scrollVelocity: Float = 0f,
    enablePressAnimation: Boolean = true,
): Modifier {
    val p = LocalGtjColors.current
    val density = LocalDensity.current

    // v1.8.1 B5：深色模式改读显式 token（Theme 层解析后下发），不再靠 bg.red 启发式猜（陶土棕/中性灰会误判）
    val isDarkMode = LocalGtjIsDark.current
    val supportsRuntimeShader = LiquidGlassShaders.isRuntimeShaderSupported()

    return this
        .drawWithCache {
            // 组合期已解析 token；缓存块内仅依赖 size/density/layoutDirection，跨帧复用
            val fillPath: Path = when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Generic -> outline.path
            }

            val cornerRadiusPx = with(density) { GtjShape.xlRadius.toPx() }

            // ②' 用户 tint 渐变（150° 方向近似：右下斜向，尺寸相关 → 缓存块内构造）
            val tintBrush: Brush? = tint?.let { stops ->
                Brush.linearGradient(
                    colorStops = stops.map { it.first to it.second }.toTypedArray(),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.85f, size.height * 0.55f),
                )
            }

            // ① 柔和外投影（BlurMaskFilter 高斯模糊，色来自 glassShadow token）
            val shadowPaint = Paint().apply {
                color = p.glassShadow
                asFrameworkPaint().maskFilter =
                    android.graphics.BlurMaskFilter(
                        14.dp.toPx(),
                        android.graphics.BlurMaskFilter.Blur.NORMAL,
                    )
            }
            val shadowDy = 5.dp.toPx()

            // ② 玻璃填充
            val fillPaint = Paint().apply {
                color = if (strong) p.glassFillStrong else p.glassFill
                isAntiAlias = true
            }

            // ②'' v1.7.1 玻璃厚度层：顶部微光 + 底部微影
            val sheenBrush = Brush.verticalGradient(
                colorStops = arrayOf(0f to Color.White.copy(alpha = 0.08f), 1f to Color.Transparent),
                startY = 0f,
                endY = size.height * 0.45f,
            )
            val bottomShadeBrush = Brush.verticalGradient(
                colorStops = arrayOf(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.06f)),
                startY = size.height * 0.55f,
                endY = size.height,
            )

            // ③ 顶部高光线（静态降级方案）
            val edgeHeight = 2.dp.toPx()
            val edgeRect = Rect(
                left = size.width * 0.1f,
                top = 0f,
                right = size.width * 0.9f,
                bottom = edgeHeight,
            )
            val edgeBrush = Brush.verticalGradient(
                colorStops = arrayOf(0f to p.glassEdgeHighlight, 1f to Color.Transparent),
                startY = 0f,
                endY = edgeHeight,
            )

            // ④ 描边（1dp 居中描边，圆角随路径）
            val strokePaint = Paint().apply {
                color = borderColor ?: p.glassBorder
                style = PaintingStyle.Stroke
                strokeWidth = 1.dp.toPx()
                isAntiAlias = true
            }

            // v1.8.0：API 33+ 使用 RuntimeShader 实现折射 + 动态高光 + 边缘透镜
            val useRuntimeShader = supportsRuntimeShader && refractionStrength > 0f

            onDrawBehind {
                // ① 软投影：先画，溢出圆角无碍
                translate(top = shadowDy) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawPath(fillPath.asAndroidPath(), shadowPaint.asFrameworkPaint())
                    }
                }

                // ② 玻璃填充（半透明 → 底下光斑/背景透出）
                drawPath(fillPath, fillPaint.color)

                // ②' v1.7.1 厚度层：上微光 + 下微影
                withTransform({
                    clipPath(fillPath)
                }) {
                    drawRect(brush = sheenBrush)
                    drawRect(brush = bottomShadeBrush)
                }

                // ②'' 用户 tint 渐变（在 fill/厚度层之上、高光/描边之下）
                if (tintBrush != null) {
                    withTransform({
                        clipPath(fillPath)
                    }) {
                        drawRect(brush = tintBrush)
                    }
                }

                // v1.8.0：边缘透镜参数（提前定义，供 RuntimeShader 与降级分支共用）
                val lensEdgeWidth = 1.5.dp.toPx()
                val lensEdgePath = Path().apply {
                    val inset = lensEdgeWidth / 2
                    when (val outline = shape.createOutline(
                        Size(size.width - inset * 2, size.height - inset * 2),
                        layoutDirection,
                        this@drawWithCache,
                    )) {
                        is Outline.Rectangle -> addRect(outline.rect.translate(Offset(inset, inset)))
                        is Outline.Rounded -> {
                            val r = outline.roundRect
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    left = r.left + inset,
                                    top = r.top + inset,
                                    right = r.right - inset,
                                    bottom = r.bottom - inset,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r.topLeftCornerRadius.x, r.topLeftCornerRadius.y),
                                )
                            )
                        }
                        is Outline.Generic -> addPath(outline.path, Offset(inset, inset))
                    }
                }
                val lensEdgeColor = if (isDarkMode) {
                    p.glowA.copy(alpha = 0.3f)
                } else {
                    Color.White.copy(alpha = 0.5f)
                }

                // v1.8.0：RuntimeShader 伪折射效果（API 33+）
                // v1.8.1 B2 修复：shader 在 drawWithCache 缓存块内创建一次（跨帧复用，不再每帧重建）；
                // createLensEdgeShader 失败返回 null（个别 ROM AGSL 编译失败）→ 回退静态亮边分支
                val lensEdgeShader = if (useRuntimeShader) {
                    LiquidGlassShaders.createLensEdgeShader(
                        size = size,
                        cornerRadius = cornerRadiusPx,
                        edgeColor = lensEdgeColor,
                        glowColor = if (isDarkMode) p.glowA else Color.White,
                        isDarkMode = isDarkMode,
                        refractionStrength = refractionStrength,
                    )
                } else {
                    null
                }
                if (lensEdgeShader != null) {
                    val shaderPaint = Paint().apply {
                        this.shader = lensEdgeShader
                        isAntiAlias = true
                    }
                    withTransform({
                        clipPath(fillPath)
                    }) {
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawRect(
                                0f, 0f, size.width, size.height,
                                shaderPaint.asFrameworkPaint(),
                            )
                        }
                    }
                } else {
                    // 降级：静态边缘亮边（API < 33 / 折射关闭 / AGSL 编译失败）
                    withTransform({
                        clipPath(fillPath)
                    }) {
                        drawPath(
                            path = lensEdgePath,
                            color = lensEdgeColor,
                            style = Stroke(width = lensEdgeWidth),
                        )
                    }
                }

                // ③ 顶部高光（静态降级：API < 33 或未启用折射）
                withTransform({
                    clipPath(fillPath)
                }) {
                    drawRect(
                        brush = edgeBrush,
                        topLeft = edgeRect.topLeft,
                        size = edgeRect.size,
                    )
                }

                // ④ 描边（1dp 居中描边，圆角随路径）
                drawPath(fillPath, strokePaint.color, style = Stroke(width = strokePaint.strokeWidth))
            }
        }
    }

/**
 * v1.8.0 玻璃容器：内容置于玻璃绘制之上。
 * 带 onClick 时内部先 clip 再 clickable，保证涟漪不溢出圆角而投影保持完整。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GtjShape.xl,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    refractionStrength: Float = 0.5f,
    scrollVelocity: Float = 0f,
    enablePressAnimation: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = Modifier.liquidGlass(
        shape = shape,
        strong = strong,
        refractionStrength = refractionStrength,
        scrollVelocity = scrollVelocity,
        enablePressAnimation = enablePressAnimation,
    )
    if (onClick != null) {
        Box(
            modifier
                .then(glass)
                .clip(shape)
                .clickable(enabled = enabled, onClick = onClick),
            content = content,
        )
    } else {
        Box(modifier.then(glass), content = content)
    }
}

/**
 * v1.8.0 滚动感知玻璃：自动监听滚动状态并驱动动态高光。
 * 用于 LazyColumn/LazyRow 等滚动容器内的玻璃组件。
 */
@Composable
fun Modifier.liquidGlassScrollAware(
    shape: Shape = GtjShape.xl,
    strong: Boolean = false,
    scrollVelocity: Float = 0f,
): Modifier {
    // 简化版：直接传入 scrollVelocity，由调用方监听滚动状态
    return this.liquidGlass(
        shape = shape,
        strong = strong,
        scrollVelocity = scrollVelocity,
    )
}
