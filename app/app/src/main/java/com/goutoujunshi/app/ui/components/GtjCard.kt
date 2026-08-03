package com.goutoujunshi.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.goutoujunshi.app.ui.theme.GtjShape
import com.goutoujunshi.app.ui.theme.GtjType
import com.goutoujunshi.app.ui.theme.LocalGtjColors

/**
 * 卡片体系（design-tokens.json component.card / divider / tag）。
 * 卡片：surfaceElevated 底 + border 边 + radius-md，无默认阴影（深色用亮度递进代替）。
 */
@Composable
fun GtjCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val p = LocalGtjColors.current
    val shape = GtjShape.md
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = p.surfaceElevated, border = BorderStroke(1.dp, p.border)) {
            Box(Modifier.padding(16.dp)) { content() }
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = p.surfaceElevated, border = BorderStroke(1.dp, p.border)) {
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}

/** 细分割线（1dp borderSoft，design-tokens component.divider） */
@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    val p = LocalGtjColors.current
    androidx.compose.material3.HorizontalDivider(modifier = modifier, thickness = 1.dp, color = p.borderSoft)
}

/** 粗分割线（8dp border，设置页分组间隔，component.dividerThick） */
@Composable
fun ThickDivider(modifier: Modifier = Modifier) {
    val p = LocalGtjColors.current
    Box(modifier = modifier.fillMaxWidth().height(8.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.HorizontalDivider(thickness = 1.dp, color = p.border)
    }
}

/** 能力/状态标签（component.tag）：neutral / warm / danger 三色，文字+色双通道 */
enum class TagKind { NEUTRAL, WARM, DANGER }

@Composable
fun Tag(
    text: String,
    kind: TagKind = TagKind.NEUTRAL,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val p = LocalGtjColors.current
    val (bg, fg) = when (kind) {
        TagKind.NEUTRAL -> p.borderSoft to p.muted
        TagKind.WARM -> p.warmSoft to p.warmOn
        TagKind.DANGER -> p.dangerSoft to p.danger
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(9999.dp), color = bg, contentColor = fg) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            if (icon != null) {
                androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.width(14.dp).height(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = GtjType.Caption)
        }
    }
}
