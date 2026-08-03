package com.goutoujunshi.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角体系唯一来源：docs/design-tokens.json radius。
 * 四级体系 + pill：sm=8 / md=12 / lg=16 / xl=24 / pill=圆形。
 */
object GtjShape {
    /** 半径数值（供组合圆角时取用，如气泡小圆角） */
    val smRadius = 8.dp
    val mdRadius = 12.dp
    val lgRadius = 16.dp
    val xlRadius = 24.dp

    val sm = RoundedCornerShape(smRadius)
    val md = RoundedCornerShape(mdRadius)
    val lg = RoundedCornerShape(lgRadius)
    val xl = RoundedCornerShape(xlRadius)
    val pill = CircleShape
    /** 气泡右上/左下小圆角半径（用户/AI 气泡用） */
    val bubbleTailSmRadius = 4.dp
    val bubbleTailSm = RoundedCornerShape(bubbleTailSmRadius)
}

/** Material3 Shapes 映射。 */
val GtjShapes = Shapes(
    extraSmall = GtjShape.sm,
    small = GtjShape.md,
    medium = GtjShape.lg,
    large = GtjShape.xl,
    extraLarge = GtjShape.pill,
)
