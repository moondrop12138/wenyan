package com.wenyan.app.ui.components.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import com.wenyan.app.ui.theme.GtjShape

/**
 * v1.8.0 液态玻璃容器 · 统一封装滚动感知、光斑交互、按压动画
 *
 * 这是液态玻璃 2.0 的统一入口，自动处理：
 * - 滚动速度 → 动态高光流动
 * - 光斑位置 → 玻璃局部亮度提升
 * - 按压手势 → 果冻弹性形变
 *
 * 用法：
 * ```kotlin
 * val glowState = rememberGlowState()
 * GlowBackground(onGlowPositionsChanged = glowState::update)
 *
 * LiquidGlassContainer(
 *     listState = listState,  // 可选，滚动容器传入
 *     glowState = glowState,  // 可选，光斑交互
 * ) {
 *     // 内容
 * }
 * ```
 */
@Composable
fun LiquidGlassContainer(
    modifier: Modifier = Modifier,
    shape: Shape = GtjShape.xl,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    listState: LazyListState? = null,
    glowState: GlowState? = null,
    refractionStrength: Float = 0.5f,
    enablePressAnimation: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    // 滚动速度计算（-1 ~ 1 归一化）
    val scrollVelocity by remember(listState) {
        derivedStateOf {
            listState?.let {
                val firstVisible = it.firstVisibleItemIndex
                val scrollOffset = it.firstVisibleItemScrollOffset
                // 简化计算：滚动偏移变化率
                (scrollOffset / 1000f).coerceIn(-1f, 1f)
            } ?: 0f
        }
    }

    // 光斑位置（从 GlowState 读取）
    val glowPositions: List<Offset> = glowState?.positions ?: emptyList()
    val glowIntensities: List<Float> = glowState?.intensities ?: emptyList()

    val glass = Modifier.liquidGlass(
        shape = shape,
        strong = strong,
        refractionStrength = refractionStrength,
        scrollVelocity = scrollVelocity,
        glowPositions = glowPositions,
        glowIntensities = glowIntensities,
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
 * v1.8.0 滚动感知玻璃 Modifier：直接附加到任意组件。
 *
 * 用法：
 * ```kotlin
 * Box(Modifier.liquidGlassContainer(listState = listState, glowState = glowState)) {
 *     // 内容
 * }
 * ```
 */
@Composable
fun Modifier.liquidGlassContainer(
    shape: Shape = GtjShape.xl,
    strong: Boolean = false,
    listState: LazyListState? = null,
    glowState: GlowState? = null,
    refractionStrength: Float = 0.5f,
    enablePressAnimation: Boolean = true,
): Modifier {
    val scrollVelocity by remember(listState) {
        derivedStateOf {
            listState?.let {
                val scrollOffset = it.firstVisibleItemScrollOffset
                (scrollOffset / 1000f).coerceIn(-1f, 1f)
            } ?: 0f
        }
    }

    val glowPositions: List<Offset> = glowState?.positions ?: emptyList()
    val glowIntensities: List<Float> = glowState?.intensities ?: emptyList()

    return this.liquidGlass(
        shape = shape,
        strong = strong,
        refractionStrength = refractionStrength,
        scrollVelocity = scrollVelocity,
        glowPositions = glowPositions,
        glowIntensities = glowIntensities,
        enablePressAnimation = enablePressAnimation,
    )
}
