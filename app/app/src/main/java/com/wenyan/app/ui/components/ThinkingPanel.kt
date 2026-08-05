package com.wenyan.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * 思考过程折叠面板（v1.5 便签样式，设计稿 WY-02 ThinkingPanel）：
 * - 默认收起，只显示一行 "回顾了你们上周的对话记录…" + 展开箭头（向上）
 * - 展开后展示完整 reasoning_content（流式期间持续追加）
 * - 流式结束后默认收起，用户可点开回看
 *
 * 设计对齐：surface 底（浅色 #EFEAE1 灰绿便签感）+ r12 + muted 文字
 */
@Composable
fun ThinkingPanel(
    thinking: String,
    modifier: Modifier = Modifier,
    /** 是否流式中（流式中默认展开以便看到"AI 正在思考"），结束后收起 */
    streaming: Boolean = false,
) {
    if (thinking.isBlank()) return
    val p = LocalGtjColors.current
    var expanded by rememberSaveable(streaming) { mutableStateOf(streaming) }
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "thinkingArrow")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GtjShape.md,
        color = p.surface,
        border = BorderStroke(1.dp, p.borderSoft),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Surface(
                onClick = { expanded = !expanded },
                color = p.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = if (expanded) "收起思考过程" else "展开思考过程"
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = p.muted,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (streaming) "正在回顾对话…" else "回顾了你们之前的对话记录…",
                        style = GtjType.Caption,
                        color = p.muted,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(arrowRotation),
                        tint = p.meta,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = thinking,
                        style = GtjType.Caption,
                        color = p.fgSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}
