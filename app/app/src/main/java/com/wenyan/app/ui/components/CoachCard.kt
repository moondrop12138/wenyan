package com.wenyan.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenyan.app.ui.contract.CoachCard
import com.wenyan.app.ui.contract.ScriptStyle
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * v1.6 四段结构回答卡（参考旧版"狗头军师"回答结构，配色走温言陶土棕×暖米白，与启动图标对齐）：
 * 卡头（温言标记+时间戳）→ 接住你（棕 pill + 共情段落）→ 先分清事实（✓/?/○ 三组列表）→
 * 军师建议（策略 tag + 赭石核心句 + 编号理由 + 三风格切换 + 话术气泡复制）→ 现在可以做什么（行动清单）。
 *
 * 暖色约束：深棕/赭石强调只出现于"军师建议"段（策略 tag + 核心句，同一视觉焦点视为一处）；
 * "接住你" pill 用 accent 系（accentSoft 底 + accent 字）。
 * 风格切换纯本地（rememberSaveable(messageId) 按消息记忆），不重请求模型。
 * safety_override=true 时由外层改渲染 CrisisCard，本卡不渲染危机内容。
 */
@Composable
fun CoachCard(
    card: CoachCard,
    messageId: Long,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((Offset) -> Unit)? = null,
) {
    val p = LocalGtjColors.current
    var windowPos by remember { mutableStateOf(Offset.Zero) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { windowPos = it.positionInWindow() }
            .then(
                if (onLongClick != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {},
                            onLongPress = { offset -> onLongClick(windowPos + offset) },
                        )
                    }
                } else {
                    Modifier
                }
            ),
        shape = GtjShape.xl,
        color = p.surfaceElevated,
        border = BorderStroke(1.dp, p.borderSoft),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            // 卡头：温言标记（22dp 陶土棕圆 + 白"温"）+ 标题 + 时间戳（设计稿 WY-02/03 头部）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = p.accent, modifier = Modifier.size(22.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("温", style = GtjType.Caption.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp), color = p.accentOn)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("温言分析", style = GtjType.Caption, color = p.muted)
                Spacer(Modifier.weight(1f))
                Text(formatNowTime(), style = GtjType.Caption, color = p.meta)
            }

            if (card.empathy.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                EmpathyBlock(card.empathy)
            }

            val hasFacts = card.factsKnown.isNotEmpty() || card.factsAssumed.isNotEmpty() || card.factsUnknown.isNotEmpty()
            if (hasFacts) {
                Spacer(Modifier.height(14.dp))
                SectionTitle("先分清事实")
                Spacer(Modifier.height(10.dp))
                FactGroup("事实", Icons.Outlined.Check, p.success, card.factsKnown)
                if (card.factsAssumed.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FactGroup("推测", Icons.AutoMirrored.Outlined.HelpOutline, p.fgSecondary, card.factsAssumed)
                }
                if (card.factsUnknown.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FactGroup("未知", Icons.Outlined.RadioButtonUnchecked, p.meta, card.factsUnknown)
                }
            }

            val hasAdvice = card.adviceCore.isNotBlank() || card.reasons.isNotEmpty() || card.styles.isNotEmpty()
            if (hasAdvice) {
                Spacer(Modifier.height(14.dp))
                SectionDivider()
                Spacer(Modifier.height(14.dp))
                AdviceBlock(card, messageId, onCopy)
            }

            if (card.actions.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SectionDivider()
                Spacer(Modifier.height(14.dp))
                ActionsBlock(card.actions)
            }

            if (card.citations.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp), tint = p.muted)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "参考：" + card.citations.joinToString(" · "),
                        style = GtjType.Caption,
                        color = p.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (card.tokenEstimate > 0) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "本次消耗估算：~${card.tokenEstimate} token",
                        style = GtjType.Caption,
                        color = p.muted,
                    )
                }
            }
        }
    }
}

/** 段标题（v1.6 四段结构，字重 500） */
@Composable
private fun SectionTitle(text: String) {
    val p = LocalGtjColors.current
    Text(text, style = GtjType.Subtitle.copy(fontWeight = FontWeight.Medium), color = p.fg)
}

/** 接住你：陶土棕 pill + 共情段落 */
@Composable
private fun EmpathyBlock(text: String) {
    val p = LocalGtjColors.current
    Column {
        Tag("接住你", kind = TagKind.ACCENT)
        Spacer(Modifier.height(8.dp))
        Text(text, style = GtjType.Body, color = p.fg, lineHeight = 24.sp)
    }
}

/** 先分清事实：✓/?/○ 三组列表（空组整组隐藏） */
@Composable
private fun FactGroup(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    items: List<String>,
) {
    val p = LocalGtjColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = iconTint)
            Spacer(Modifier.width(6.dp))
            Text(label, style = GtjType.Label, color = p.fg)
        }
        Spacer(Modifier.height(6.dp))
        items.forEach { item ->
            Row(Modifier.padding(start = 22.dp, bottom = 4.dp)) {
                Text("· ", style = GtjType.BodySm, color = p.meta)
                Text(item, style = GtjType.BodySm, color = p.fg, lineHeight = 20.sp)
            }
        }
    }
}

/** 军师建议：策略 tag（赭石）+ 核心句（赭石加粗）+ 编号理由 + 三风格切换 + 话术气泡 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdviceBlock(
    card: CoachCard,
    messageId: Long,
    onCopy: (String) -> Unit,
) {
    val p = LocalGtjColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("军师建议")
            Spacer(Modifier.weight(1f))
            if (card.adviceTag.isNotBlank()) {
                Tag(card.adviceTag, kind = TagKind.WARM)
            }
        }
        if (card.adviceCore.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = card.adviceCore,
                style = GtjType.Subtitle.copy(fontWeight = FontWeight.SemiBold),
                color = p.warmOn,
                lineHeight = 24.sp,
            )
        }
        if (card.reasons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            card.reasons.forEachIndexed { index, reason ->
                Row(Modifier.padding(bottom = 4.dp)) {
                    Text("${index + 1}. ", style = GtjType.BodySm, color = p.warmOn)
                    Text(reason, style = GtjType.BodySm, color = p.fg, lineHeight = 20.sp)
                }
            }
        }
        if (card.styles.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            // 三风格切换：纯本地（按消息记忆；进程重建不丢）
            var selectedKey by rememberSaveable(messageId) { mutableStateOf(card.styles.firstOrNull()?.key.orEmpty()) }
            val current: ScriptStyle? = card.styles.firstOrNull { it.key == selectedKey } ?: card.styles.firstOrNull()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                card.styles.forEach { style ->
                    GtjChip(
                        text = style.label.ifBlank { style.key },
                        selected = style.key == current?.key,
                        onClick = { selectedKey = style.key },
                    )
                }
            }
            if (current != null && current.text.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                ScriptBubble(
                    text = current.text,
                    isClarification = card.isClarification,
                    onCopy = onCopy,
                )
            }
        }
        if (card.replyTiming.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("发送时机：${card.replyTiming}", style = GtjType.Caption, color = p.muted)
        }
    }
}

/** 话术气泡：accentSoft 底 + 复制按钮；uncertain 反问时隐藏复制、显示"先确认一下"。
 *  老 freetext 数据（FreetextBubble）也复用本组件渲染话术卡（v1.6 统一新样式）。 */
@Composable
internal fun ScriptBubble(
    text: String,
    isClarification: Boolean = false,
    onCopy: (String) -> Unit,
) {
    val p = LocalGtjColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GtjShape.sm,
        color = p.accentSoft,
        border = BorderStroke(1.dp, p.accent.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = if (isClarification) "先确认一下" else "可以直接发",
                style = GtjType.Caption,
                color = p.accent.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = text,
                style = GtjType.BodySm,
                color = p.fg,
                lineHeight = 20.sp,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isClarification) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(
                        onClick = { onCopy(text) },
                        shape = GtjShape.sm,
                        color = p.accent,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "复制话术", modifier = Modifier.size(14.dp), tint = p.accentOn)
                            Spacer(Modifier.width(4.dp))
                            Text("复制话术", style = GtjType.Label, color = p.accentOn)
                        }
                    }
                }
            }
        }
    }
}

/** 现在可以做什么：行动清单（label 小胶囊 + 文本，无按钮） */
@Composable
private fun ActionsBlock(actions: List<com.wenyan.app.ui.contract.ActionItemUi>) {
    val p = LocalGtjColors.current
    Column {
        SectionTitle("现在可以做什么")
        Spacer(Modifier.height(10.dp))
        actions.forEach { action ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = p.borderSoft,
                    contentColor = p.muted,
                ) {
                    Text(
                        text = action.label.ifBlank { "行动" },
                        style = GtjType.Caption,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = action.text,
                    style = GtjType.BodySm,
                    color = p.fg,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 卡片时间戳（设计稿 WY-02：HH:mm） */
private fun formatNowTime(): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
