package com.goutoujunshi.app.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goutoujunshi.app.ui.theme.GtjShape
import com.goutoujunshi.app.ui.theme.GtjType
import com.goutoujunshi.app.ui.theme.LocalGtjColors

/**
 * "这句怎么回"模式（AC-05，design-pages 页面6）：
 * 第一屏成品话术卡（primaryButton 底 + content_copy），第二屏时机/代价/后续分支（collapsible）。
 */
@Composable
fun ReplyCard(
    reply: String,
    timing: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 成品卡
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GtjShape.md,
            color = p.accent,
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("可以直接发", style = GtjType.Caption, color = p.accentOn.copy(alpha = 0.8f))
                Spacer(Modifier.height(4.dp))
                Text(reply, style = GtjType.Body, color = p.accentOn, maxLines = 10, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = { onCopy(reply) },
                    shape = GtjShape.sm,
                    color = p.accentOn,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制话术成品", modifier = Modifier.size(16.dp), tint = p.accent)
                        Spacer(Modifier.width(6.dp))
                        Text("复制话术", style = GtjType.Label, color = p.accent)
                    }
                }
            }
        }
        if (timing.isNotBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = GtjShape.md,
                color = p.surfaceElevated,
                border = BorderStroke(1.dp, p.border),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 触控：折叠头整行可点，高度 ≥48dp
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { expanded = !expanded },
                    ) {
                        Text("时机 / 代价 / 后续", style = GtjType.Subtitle, color = p.fg, modifier = Modifier.weight(1f))
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                            modifier = Modifier.size(20.dp),
                            tint = p.meta,
                        )
                    }
                    if (expanded) {
                        Spacer(Modifier.height(8.dp))
                        Text(timing, style = GtjType.BodySm, color = p.fgSecondary)
                    }
                }
            }
        }
    }
}
