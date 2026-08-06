package com.wenyan.app.ui.components

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
import com.wenyan.app.ui.components.glass.GlassSurface
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * 卡片体系（design-tokens.json component.card / divider / tag）。
 * v1.7.0：卡片 = 玻璃材质（glassFill + 顶高光 + 描边 + 软影），圆角 md。
 */
@Composable
fun GtjCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        GlassSurface(onClick = onClick, modifier = modifier, shape = GtjShape.md) {
            Box(Modifier.padding(16.dp)) { content() }
        }
    } else {
        GlassSurface(modifier = modifier, shape = GtjShape.md) {
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

/** 能力/状态标签（component.tag）：accent / neutral / warm / danger 四色，文字+色双通道 */
enum class TagKind { ACCENT, NEUTRAL, WARM, DANGER }

@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    kind: TagKind = TagKind.NEUTRAL,
    icon: ImageVector? = null,
) {
    val p = LocalGtjColors.current
    val (bg, fg) = when (kind) {
        // v1.6 ACCENT 绿系 pill（"接住你"段；暖色留给"军师建议"段，遵守每屏 ≤1 处暖色）
        TagKind.ACCENT -> p.accentSoft to p.accent
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
