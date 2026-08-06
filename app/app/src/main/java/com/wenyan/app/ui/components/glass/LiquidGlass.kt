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
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * v1.7.0 液态玻璃 · 自包含玻璃绘制（Compose 无 backdrop-filter，用"半透明填充 + 顶部高光 +
 * 细描边 + 柔和投影"四要素模拟玻璃质感，参数固化自 outputs/liquid-glass-prototype.html）。
 *
 * 用法：`.clip(shape).liquidGlass(shape)` 或直接用 [GlassSurface]。
 * 关键：liquidGlass 自身**不裁剪**（投影需要溢出圆角），由外层 clip 负责内容裁剪；
 * 不拦截任何手势，长按菜单 / readOnly 部分选择 / 滚动全部兼容。
 *
 * @param shape 玻璃形状（决定 fill/stroke/highlight 的路径）
 * @param strong true 用 glassFillStrong（输入胶囊/高密度容器），false 用 glassFill
 * @param tint 叠加在 fill 之上、高光之下的渐变停靠点（用户气泡深棕 tint，原型 --utint 150°）
 * @param borderColor 覆盖描边色（用户气泡棕描边，原型 --uborder；null 用 glassBorder）
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = GtjShape.xl,
    strong: Boolean = false,
    tint: List<Pair<Float, Color>>? = null,
    borderColor: Color? = null,
): Modifier {
    val p = LocalGtjColors.current
    return this.drawWithCache {
        // 组合期已解析 token；缓存块内仅依赖 size/density/layoutDirection，跨帧复用
        // Outline 无公开 toPath()，按派生类构造同形 Path（圆角/矩形/泛型三态）。
        // 注意：DrawScope 自身实现 Density 接口，作用域内 density 是 Float，需传 this 作 Density。
        val fillPath: Path = when (val outline = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> outline.path
        }

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

        // ②'' v1.7.1 玻璃厚度层：顶部微光（白 8% → 45% 高度渐隐）+ 底部微影（黑 6% ← 55% 渐显），
        // 模拟光线穿过玻璃的"上亮下暗"立体感（替代 CSS backdrop 的柔光层次）
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

        // ③ 顶部高光线：left/right 10% 收窄 + 垂直渐隐（edgeHighlight → 透明）
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

        onDrawBehind {
            // ① 软投影：先画，溢出圆角无碍（本 modifier 不裁剪，由外层 clip 管内容）
            //    DrawScope 无 drawPath(paint) 重载 → 走 nativeCanvas 用框架 Paint（BlurMaskFilter 软边）
            translate(top = shadowDy) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawPath(fillPath.asAndroidPath(), shadowPaint.asFrameworkPaint())
                }
            }
            // ② 玻璃填充（半透明 → 底下光斑/背景透出）
            drawPath(fillPath, fillPaint.color)
            // ②' v1.7.1 厚度层：上微光 + 下微影（clipPath 到圆角内）
            withTransform({
                clipPath(fillPath)
            }) {
                drawRect(brush = sheenBrush)
                drawRect(brush = bottomShadeBrush)
            }
            // ②'' 用户 tint 渐变（在 fill/厚度层之上、高光/描边之下，clipPath 到圆角内）
            if (tintBrush != null) {
                withTransform({
                    clipPath(fillPath)
                }) {
                    drawRect(brush = tintBrush)
                }
            }
            // ③ 顶部高光（clipPath 到圆角内）
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
 * 玻璃容器：内容置于玻璃绘制之上。带 onClick 时内部先 clip 再 clickable，
 * 保证涟漪不溢出圆角而投影保持完整。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GtjShape.xl,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = Modifier.liquidGlass(shape, strong)
    if (onClick != null) {
        Box(modifier.then(glass).clip(shape).clickable(enabled = enabled, onClick = onClick), content = content)
    } else {
        Box(modifier.then(glass), content = content)
    }
}
