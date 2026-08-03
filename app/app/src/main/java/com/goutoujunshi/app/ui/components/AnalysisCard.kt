package com.goutoujunshi.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MenuBook
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goutoujunshi.app.ui.contract.AnalysisCard
import com.goutoujunshi.app.ui.theme.GtjShape
import com.goutoujunshi.app.ui.theme.GtjType
import com.goutoujunshi.app.ui.theme.LocalGtjColors

/**
 * 五步法结果卡片（design-pages 页面6）：
 * 结论置顶 headline + 引用行（知识透明 AC-06）+ 五段折叠 + 复制按钮 + token 消耗估算。
 * safety_override=true 时由外层改渲染 CrisisCard，本卡不渲染危机内容。
 */
@Composable
fun AnalysisCard(
    card: AnalysisCard,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GtjShape.md,
        color = p.surfaceElevated,
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (card.conclusion.isNotBlank()) {
                Text(
                    text = card.conclusion,
                    style = GtjType.Headline,
                    color = p.fg,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (card.citations.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp), tint = p.muted)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "参考：" + card.citations.joinToString(" · "),
                        style = GtjType.Caption,
                        // 对比度：知识透明文案（AC-06）升到 muted 4.8:1（meta 浅色白底仅 2.5:1）
                        color = p.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            if (card.reply.isNotBlank()) {
                ReplyCard(reply = card.reply, timing = card.replyTiming, onCopy = onCopy)
                Spacer(Modifier.height(12.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                card.steps.forEach { step ->
                    val meta = STEP_META_BY_KEY[step.key]
                    if (meta != null) {
                        val defaultExpanded = step.key != "advice" && step.key != "action"
                        var expanded by rememberSaveable(step.key) { mutableStateOf(defaultExpanded) }
                        AnalysisStepItem(
                            index = meta.index,
                            icon = meta.icon,
                            title = meta.title,
                            content = step.content,
                            items = step.items,
                            expanded = expanded,
                            onToggle = { expanded = !expanded },
                            isAction = meta.isAction,
                        )
                    }
                }
            }
            if (card.tokenEstimate > 0) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "本次消耗估算：~${card.tokenEstimate} token",
                        style = GtjType.Caption,
                        // 对比度：数值信息升到 muted 4.8:1
                        color = p.muted,
                    )
                }
            }
        }
    }
}

/**
 * 建议话术复制行（建议卡：accentSoft 底 + content_copy 按钮）。
 * 出现在 advice 段内部由外层通过 onCopy 处理剪贴板与"已复制"提示。
 */
@Composable
fun CopySuggestionRow(
    text: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GtjShape.sm,
        color = p.accentSoft,
        border = BorderStroke(1.dp, p.accent.copy(alpha = 0.35f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                style = GtjType.BodySm,
                color = p.fg,
                modifier = Modifier.weight(1f),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = { onCopy(text) },
                shape = GtjShape.sm,
                color = p.accent,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "复制建议话术", modifier = Modifier.size(16.dp), tint = p.accentOn)
                    Spacer(Modifier.width(4.dp))
                    Text("复制", style = GtjType.Label, color = p.accentOn)
                }
            }
        }
    }
}
