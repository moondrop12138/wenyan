package com.wenyan.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenyan.app.ui.contract.CoachCard
import com.wenyan.app.ui.contract.ScriptStyle
import com.wenyan.app.ui.theme.EditorialType
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * v1.8.2 回答卡重构（editorial 编辑排版风，原型 home-editorial-remix 右版）：
 * 从「玻璃圆角气泡」改为「回信文章」——无卡片底，直接铺排：
 * 刊头（短规则线 + 温言·回信 + 时间）→ 衬线大标题（adviceCore）→
 * ① 接住你（左边线信笺体）② 先分清事实（灰底资料栏：已知/推测/未知 方块标记）
 * ③ 军师建议（策略 tag + 编号理由 + 底线式风格切换 + 灰底话术框 + 复制链接）
 * ④ 行动清单（01/02/03 + 结尾短规则线），段间细分隔线。
 *
 * 功能保留：风格切换（rememberSaveable 按消息记忆）、复制话术、长按菜单、
 * 记忆依据 / 知识库引用 / token 估算（低调尾部）。
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
    Column(
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
    ) {
        // ── 刊头：短规则线 + 温言·回信 + 时间 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(3.dp)
                        .background(p.accent),
                )
                Text("温言 · 回信", style = GtjType.Label.copy(fontSize = 12.sp), color = p.accent)
            }
            Spacer(Modifier.weight(1f))
            Text(formatNowTime(), style = GtjType.Caption, color = p.meta)
        }

        // ── 衬线大标题（adviceCore）──
        if (card.adviceCore.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = card.adviceCore,
                style = EditorialType.Display,
                color = p.fg,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ① 接住你
        if (card.empathy.isNotBlank()) {
            SectionDivider()
            SecKicker("接住你", "EMPATHY")
            Spacer(Modifier.height(10.dp))
            Text(
                text = card.empathy,
                style = GtjType.Body.copy(lineHeight = 26.sp),
                color = p.fgSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(
                            color = p.accent,
                            topLeft = Offset(0f, 0f),
                            size = Size(2.dp.toPx(), size.height),
                        )
                    }
                    .padding(start = 16.dp),
            )
        }

        // ② 先分清事实
        val hasFacts = card.factsKnown.isNotEmpty() || card.factsAssumed.isNotEmpty() || card.factsUnknown.isNotEmpty()
        if (hasFacts) {
            SectionDivider()
            SecKicker("先分清事实", "FACT CHECK")
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.surface.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                if (card.factsKnown.isNotEmpty()) {
                    FactGroup("已知", FactMark.Known, card.factsKnown)
                }
                if (card.factsAssumed.isNotEmpty()) {
                    FactGroup("推测", FactMark.Assumed, card.factsAssumed)
                }
                if (card.factsUnknown.isNotEmpty()) {
                    FactGroup("未知", FactMark.Unknown, card.factsUnknown)
                }
            }
        }

        // ③ 军师建议
        val hasAdvice = card.adviceTag.isNotBlank() || card.reasons.isNotEmpty() || card.styles.isNotEmpty() || card.replyTiming.isNotBlank()
        if (hasAdvice) {
            SectionDivider()
            SecKicker("军师建议", "COLUMN")
            if (card.adviceTag.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = card.adviceTag,
                    style = GtjType.Caption.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.12f.sp),
                    color = p.accent,
                    modifier = Modifier
                        .border(1.dp, p.accent, RoundedCornerShape(2.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
            if (card.reasons.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                card.reasons.forEachIndexed { index, reason ->
                    Row(Modifier.padding(bottom = 6.dp)) {
                        Text("${index + 1}.", style = EditorialType.No, color = p.accent, modifier = Modifier.width(24.dp))
                        Text(reason, style = GtjType.BodySm.copy(lineHeight = 23.sp), color = p.fgSecondary, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (card.styles.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                var selectedKey by rememberSaveable(messageId) { mutableStateOf(card.styles.firstOrNull()?.key.orEmpty()) }
                val current: ScriptStyle? = card.styles.firstOrNull { it.key == selectedKey } ?: card.styles.firstOrNull()
                // 底线式风格切换（editorial style-tabs）
                Row(Modifier.fillMaxWidth()) {
                    card.styles.forEach { style ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { selectedKey = style.key }
                                .padding(horizontal = 10.dp),
                        ) {
                            Text(
                                text = style.label.ifBlank { style.key },
                                style = GtjType.Caption.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.08f.sp),
                                color = if (style.key == current?.key) p.accent else p.muted,
                            )
                            Spacer(Modifier.height(7.dp))
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(2.dp)
                                    .background(if (style.key == current?.key) p.accent else Color.Transparent),
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = p.border)
                if (current != null && current.text.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    ScriptBox(
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

        // ④ 行动清单
        if (card.actions.isNotEmpty()) {
            SectionDivider()
            SecKicker("行动清单", "TAKEAWAYS")
            Spacer(Modifier.height(10.dp))
            card.actions.forEachIndexed { index, action ->
                Row(Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = "${(index + 1).toString().padStart(2, '0')}.",
                        style = EditorialType.No,
                        color = p.accent,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        text = action.text,
                        style = GtjType.BodySm.copy(lineHeight = 23.sp),
                        color = p.fg,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // 结尾短规则线（todo-end）
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(3.dp)
                    .background(p.accent),
            )
        }

        // ── 尾部：知识库引用 / 记忆依据 / token 估算（低调小字）──
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
        if (card.memoryCitations.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column {
                Text("记忆依据", style = GtjType.Caption, color = p.muted)
                card.memoryCitations.take(3).forEach { citation ->
                    Text(
                        text = "「$citation」",
                        style = GtjType.Caption,
                        color = p.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
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
                    color = p.muted,
                )
            }
        }
    }
}

/** 段间分隔线（editorial coach-sec border-top） */
@Composable
private fun SectionDivider() {
    val p = LocalGtjColors.current
    Spacer(Modifier.height(22.dp))
    HorizontalDivider(thickness = 1.dp, color = p.border)
    Spacer(Modifier.height(22.dp))
}

/** 段头：kicker + 英文小标（editorial sec-kicker） */
@Composable
private fun SecKicker(cn: String, en: String) {
    val p = LocalGtjColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(cn, style = GtjType.Label.copy(fontSize = 13.sp, letterSpacing = 0.1f.sp), color = p.accent)
        Spacer(Modifier.width(8.dp))
        Text(en, style = GtjType.Caption.copy(letterSpacing = 0.14f.sp), color = p.muted)
    }
}

/** 事实方块标记：实心=已知 / 空心描边=推测 / 淡描边=未知 */
private enum class FactMark { Known, Assumed, Unknown }

@Composable
private fun FactGroup(label: String, mark: FactMark, items: List<String>) {
    val p = LocalGtjColors.current
    Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Text(label, style = GtjType.Caption, color = p.muted)
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            Row(Modifier.padding(bottom = 5.dp)) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(8.dp)
                        .let {
                            when (mark) {
                                FactMark.Known -> it.background(p.accent)
                                FactMark.Assumed -> it.border(1.5.dp, p.accent)
                                FactMark.Unknown -> it.border(1.dp, p.muted)
                            }
                        },
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item,
                    style = GtjType.BodySm.copy(lineHeight = 22.sp),
                    color = if (mark == FactMark.Unknown) p.muted else p.fgSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 话术框（editorial script-box）：灰底直角 + 复制文字链接 */
@Composable
private fun ScriptBox(
    text: String,
    isClarification: Boolean,
    onCopy: (String) -> Unit,
) {
    val p = LocalGtjColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(
            text = if (isClarification) "先确认一下" else "可以直接发",
            style = GtjType.Caption,
            color = p.accent.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = text,
            style = GtjType.BodySm.copy(lineHeight = 23.sp),
            color = p.fg,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
        )
        if (!isClarification) {
            Spacer(Modifier.height(9.dp))
            Text(
                text = "复制话术",
                style = GtjType.Caption.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1f.sp),
                color = p.accent,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onCopy(text) },
            )
        }
    }
}

/**
 * 话术气泡（v1.6 样式，accentSoft 底 + 复制按钮；仅老 freetext 数据渲染用）：
 * FreetextBubble 复用本组件渲染老话术卡；v1.8.2 起新回答走 ScriptBox（editorial）。
 */
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

/** 刊头时间戳（HH:mm） */
private fun formatNowTime(): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

/** v1.8.2 T3 @Preview：editorial 回答文章（四段结构） */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFFF6F0E6)
@Composable
private fun CoachCardPreview() {
    com.wenyan.app.ui.theme.GtjTheme {
        CoachCard(
            card = com.wenyan.app.ui.contract.CoachCard(
                empathy = "这件事确实让人心里发堵，先稳住，我们一条条看。",
                adviceCore = "先别急着道歉，你们缺的是一次「轻接触」。",
                adviceTag = "轻接触策略",
                factsKnown = listOf("两天内回复间隔明显变长"),
                factsAssumed = listOf("她可能正忙或情绪低落"),
                factsUnknown = listOf("她这两天是否遇到了具体的事"),
                reasons = listOf("追问会把她的低能量归因到你身上", "单字回复期需要零负担入口"),
                styles = listOf(
                    ScriptStyle(key = "a", label = "自然流", text = "刚刷到一家店的提拉米苏，想到你上次说想吃——不急着回，先存着。"),
                    ScriptStyle(key = "b", label = "冷读", text = "你这两天好像有点累，等你缓过来再说。"),
                ),
                actions = listOf(
                    com.wenyan.app.ui.contract.ActionItemUi(label = "", text = "今晚到明天白天，不发任何追问式消息"),
                    com.wenyan.app.ui.contract.ActionItemUi(label = "", text = "明晚 8–9 点发上面那条轻内容"),
                ),
            ),
            messageId = 1L,
            onCopy = {},
        )
    }
}
