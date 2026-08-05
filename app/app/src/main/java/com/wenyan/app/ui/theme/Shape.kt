package com.wenyan.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角体系唯一来源：docs/design-tokens.json radius。
 * 四级体系 + pill：sm=8 / md=12 / lg=16 / xl=18（v1.4 由 24 收至 18，更内敛） / pill=圆形。
 */
object GtjShape {
    /** 半径数值（供组合圆角时取用，如气泡小圆角） */
    val smRadius = 8.dp
    val mdRadius = 12.dp
    val lgRadius = 16.dp
    val xlRadius = 18.dp

    val sm = RoundedCornerShape(smRadius)
    val md = RoundedCornerShape(mdRadius)
    val lg = RoundedCornerShape(lgRadius)
    val xl = RoundedCornerShape(xlRadius)
    val pill = CircleShape
    /**
     * v1.3.1 输入栏圆角：固定值，不用 pill（CircleShape）。
     * 多行输入时输入框变高，pill 的圆角会随高度膨胀成巨大的胶囊（视觉失衡）；
     * 固定 20dp 在任何高度下都保持协调（介于 lg=16 / xl=24 之间）。
     */
    val inputRadius = 20.dp
    val input = RoundedCornerShape(inputRadius)
    /** 气泡右上/左下小圆角半径（用户/AI 气泡用） */
    val bubbleTailSmRadius = 4.dp
    val bubbleTailSm = RoundedCornerShape(bubbleTailSmRadius)
}

/** Material3 Shapes 映射。注意：extraLarge 不可用 pill（CircleShape）——
 *  ModalBottomSheet 等 M3 组件默认取 extraLarge，圆形会把弹窗渲染成半圆拱形。
 *  pill 仅限组件级显式引用（输入栏、chip、dragHandle 等）。 */
val GtjShapes = Shapes(
    extraSmall = GtjShape.sm,
    small = GtjShape.md,
    medium = GtjShape.lg,
    large = GtjShape.xl,
    extraLarge = GtjShape.xl,
)
