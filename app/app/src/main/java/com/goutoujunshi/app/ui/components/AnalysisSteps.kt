package com.goutoujunshi.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.goutoujunshi.app.ui.theme.GtjType
import com.goutoujunshi.app.ui.theme.LocalGtjColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mood

/** 五步法单段元数据（design-pages 页面6，图标锁定 Material Symbols Outlined；v1.2 去掉编号） */
data class StepMeta(
    val icon: ImageVector,
    val title: String,
    val isAction: Boolean,
)

val STEP_META_BY_KEY: Map<String, StepMeta> = mapOf(
    "emotion" to StepMeta(Icons.Outlined.Mood, "情绪落地", false),
    "facts" to StepMeta(Icons.AutoMirrored.Outlined.FactCheck, "事实拆分", false),
    "interests" to StepMeta(Icons.Outlined.Balance, "利益判断", false),
    "advice" to StepMeta(Icons.Outlined.Lightbulb, "明确建议", false),
    "action" to StepMeta(Icons.Outlined.Flag, "行动收束", true),
)

/**
 * 五步法单段（可折叠，默认 01-03 展开、04/05 折叠，design-pages 页面6）。
 * 头：图标+标题（行动收束带 warm 标签，全屏 <=1 处）+ chevron；v1.2 去掉 01/02 编号。
 */
@Composable
fun AnalysisStepItem(
    icon: ImageVector,
    title: String,
    content: String,
    items: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isAction: Boolean = false,
) {
    val p = LocalGtjColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // 触控：折叠头整行可点，高度 ≥48dp（design-pages 页面6 keyboard 可聚焦）
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(vertical = 10.dp),
        ) {
            // P1 风格：步骤图标 + 标题统一暖橙，白卡上视觉锚点（v1.2 去掉 01/02 编号）
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = p.accent)
            Spacer(Modifier.width(10.dp))
            Text(title, style = GtjType.Subtitle, color = p.accent)
            if (isAction) {
                Spacer(Modifier.width(8.dp))
                Tag(text = "行动收束", kind = TagKind.WARM)
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(20.dp),
                tint = p.meta,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (content.isNotBlank()) {
                    Text(content, style = GtjType.BodySm, color = p.fgSecondary)
                }
                items.forEach { item ->
                    QuoteBlock(text = item)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
