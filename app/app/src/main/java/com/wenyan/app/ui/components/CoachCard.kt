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
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenyan.app.ui.chat.RevealController
import com.wenyan.app.ui.chat.revealUnit
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
    createdAt: Long? = null,
    reveal: RevealController? = null,
    // 渐显中点按卡片本体 → 立即全量（由 ChatScreen 传入 revealPair 收尾）
    onTapReveal: () -> Unit = {},
) {
    val p = LocalGtjColors.current
    var windowPos by remember { mutableStateOf(Offset.Zero) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 渐显中整块对读屏隐藏——alpha=0 的不可见文字不被聚焦朗读，全量/播完后出现
            //（读屏用户点风格 tab/复制/长按菜单同样收尾，不会被卡住）；历史直出恒可见。
            // v1.2.1 无障碍补偿（对齐 MessageBubble）：pointerInput 吃掉点按/长按手势后，
            // 补 click/longClick 语义动作（读屏双击收尾渐显、长按开消息菜单）；
            // 渐显期 invisibleToUser 整卡隐藏时语义动作同样不可达，收尾走风格 tab/复制/菜单旁路。
            .semantics {
                onClick(label = "查看消息") {
                    onTapReveal()
                    true
                }
                if (onLongClick != null) {
                    onLongClick(label = "打开消息操作菜单") {
                        onLongClick.invoke(windowPos)
                        true
                    }
                }
                if (reveal != null && !reveal.finished) {
                    invisibleToUser()
                }
            }
            .onGloballyPositioned { windowPos = it.positionInWindow() }
            .then(
                if (onLongClick != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTapReveal() },
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
                // 短规则线与 "brand" 同淡入：同属刊头首现元素，不先出空骨架
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(3.dp)
                        .revealUnit(reveal, "brand")
                        .background(p.accent),
                )
                Text(
                    text = "温言 · 回信",
                    style = GtjType.Label.copy(fontSize = 12.sp),
                    color = p.accent,
                    modifier = Modifier.revealUnit(reveal, "brand"),
                )
            }
            Spacer(Modifier.weight(1f))
            // v1.8.2-fix（审查 P2-4）：刊头时间用消息创建时间——历史消息不再显示"渲染时刻"
            val timeText = formatMessageTime(createdAt)
            Text(
                text = timeText,
                style = GtjType.Caption,
                color = p.meta,
                modifier = Modifier.revealUnit(reveal, "time"),
            )
        }

        // ── 衬线大标题（adviceCore）──
        if (card.adviceCore.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            // v1.8.2-fix（审查 P3-9）：去掉 4 行截断——桌面端不截断，双端行为对齐
            Text(
                text = card.adviceCore,
                style = EditorialType.Display,
                color = p.fg,
                modifier = Modifier.revealUnit(reveal, "core"),
            )
        }

        // ① 接住你
        if (card.empathy.isNotBlank()) {
            SectionDivider(reveal, "kickerEmpathyCn")
            SecKicker("接住你", "EMPATHY", "kickerEmpathyCn", "kickerEmpathyEn", reveal)
            Spacer(Modifier.height(10.dp))
            Text(
                text = card.empathy,
                style = GtjType.Body.copy(lineHeight = 26.sp),
                // v1.9.4 评审修复：正文层改 fg（原 fgSecondary）——浅色 fgSecondary 在 Mica frost .30
                // 的玻璃面上全量程最坏 4.15:1 <AA 4.5；本卡直接铺在流光背景上（非玻璃填充），同属
                // 正文层，一并统一为 fg（正文对比由 fg 承担，token 未动）
                color = p.fg,
                // 修饰符顺序敏感：revealUnit（graphicsLayer）必须在 drawBehind 外层，
                // 否则 accent 竖条画在层外、渐显第一帧就常显；padding 留在最内，竖条位置不变
                modifier = Modifier
                    .fillMaxWidth()
                    .revealUnit(reveal, "empathy")
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
            SectionDivider(reveal, "kickerFactsCn")
            SecKicker("先分清事实", "FACT CHECK", "kickerFactsCn", "kickerFactsEn", reveal)
            Spacer(Modifier.height(10.dp))
            // 面板底随首个事实组一起淡入：key 取首个非空组的 label（与计划入表顺序 known→assumed→unknown 同源）
            val factsPanelKey = when {
                card.factsKnown.isNotEmpty() -> "factsKnownLabel"
                card.factsAssumed.isNotEmpty() -> "factsAssumedLabel"
                else -> "factsUnknownLabel"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .revealUnit(reveal, factsPanelKey)
                    .background(p.surface.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                if (card.factsKnown.isNotEmpty()) {
                    FactGroup("已知", FactMark.Known, card.factsKnown, reveal, "factsKnown")
                }
                if (card.factsAssumed.isNotEmpty()) {
                    FactGroup("推测", FactMark.Assumed, card.factsAssumed, reveal, "factsAssumed")
                }
                if (card.factsUnknown.isNotEmpty()) {
                    FactGroup("未知", FactMark.Unknown, card.factsUnknown, reveal, "factsUnknown")
                }
            }
        }

        // ③ 军师建议
        val hasAdvice = card.adviceTag.isNotBlank() || card.reasons.isNotEmpty() || card.styles.isNotEmpty() || card.replyTiming.isNotBlank()
        if (hasAdvice) {
            SectionDivider(reveal, "kickerAdviceCn")
            SecKicker("军师建议", "COLUMN", "kickerAdviceCn", "kickerAdviceEn", reveal)
            if (card.adviceTag.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = card.adviceTag,
                    style = GtjType.Caption.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.12f.sp),
                    color = p.accent,
                    // 修饰符顺序敏感：revealUnit 在 border 外层，描边框随文字一起淡入（非常显空框）
                    modifier = Modifier
                        .revealUnit(reveal, "adviceTag")
                        .border(1.dp, p.accent, RoundedCornerShape(2.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
            if (card.reasons.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                card.reasons.forEachIndexed { index, reason ->
                    Row(Modifier.padding(bottom = 6.dp)) {
                        Text(
                            text = "${index + 1}.",
                            style = EditorialType.No,
                            color = p.accent,
                            modifier = Modifier.width(24.dp).revealUnit(reveal, "reasonNo[$index]"),
                        )
                        // v1.9.4 评审修复：理由正文同 [card.empathy]，fgSecondary → fg
                        Text(
                            text = reason,
                            style = GtjType.BodySm.copy(lineHeight = 23.sp),
                            color = p.fg,
                            modifier = Modifier.weight(1f).revealUnit(reveal, "reason[$index]"),
                        )
                    }
                }
            }
            if (card.styles.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                // 选中随风格签名重置：完全重答原位更新（id 不变）时，若重答后的风格 key 集合变化，
                // 沿用旧 selectedKey 会选中"非首非空"的风格、与渐显计划（任一非空即定框，默认首个非空）
                // 脱钩——话术框恒显或计划空耗。签名取 key 集合，顺序/话术文本变化不重置用户选择。
                val styleSig = rememberSaveable(messageId) { mutableStateOf(card.styles.map { it.key }) }
                if (styleSig.value != card.styles.map { it.key }) {
                    styleSig.value = card.styles.map { it.key }
                }
                var selectedKey by rememberSaveable(messageId, styleSig.value) {
                    mutableStateOf(card.styles.firstOrNull { it.text.isNotBlank() }?.key.orEmpty())
                }
                // 选中恒为"首个非空话术风格"兜底：旧 key 已不在列表 / 对应话术为空时回落，
                // 与计划侧「任一非空即定框」同源（见 coachCardRevealPlan 注释）
                val current: ScriptStyle? = card.styles.firstOrNull { it.key == selectedKey && it.text.isNotBlank() }
                    ?: card.styles.firstOrNull { it.text.isNotBlank() }
                // 底线式风格切换（editorial style-tabs）
                Row(Modifier.fillMaxWidth()) {
                    card.styles.forEachIndexed { index, style ->
                        val tabLabel = style.label.ifBlank { style.key }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    // 渐显中点风格 tab → 立即全量，避免切换后话术半截才补全
                                    reveal?.finishNow()
                                    selectedKey = style.key
                                }
                                .padding(horizontal = 10.dp),
                        ) {
                            Text(
                                text = tabLabel,
                                style = GtjType.Caption.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.08f.sp),
                                color = if (style.key == current?.key) p.accent else p.muted,
                                modifier = Modifier.revealUnit(reveal, "styleTab[$index]"),
                            )
                            Spacer(Modifier.height(7.dp))
                            // 下划线随所属 tab 文字同淡入（同 key），选中态切换不重开渐显
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(2.dp)
                                    .revealUnit(reveal, "styleTab[$index]")
                                    .background(if (style.key == current?.key) p.accent else Color.Transparent),
                            )
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = p.border,
                    // tab 区下方的细线随首个 tab 一起淡入（首个风格恒入表，见 coachCardRevealPlan）
                    modifier = Modifier.revealUnit(reveal, "styleTab[0]"),
                )
                if (current != null && current.text.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    ScriptBox(
                        text = current.text,
                        isClarification = card.isClarification,
                        onCopy = onCopy,
                        reveal = reveal,
                    )
                }
            }
            if (card.replyTiming.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                val timingText = "发送时机：${card.replyTiming}"
                Text(
                    text = timingText,
                    style = GtjType.Caption,
                    color = p.muted,
                    modifier = Modifier.revealUnit(reveal, "replyTiming"),
                )
            }
        }

        // ④ 行动清单
        if (card.actions.isNotEmpty()) {
            SectionDivider(reveal, "kickerActionsCn")
            SecKicker("行动清单", "TAKEAWAYS", "kickerActionsCn", "kickerActionsEn", reveal)
            Spacer(Modifier.height(10.dp))
            card.actions.forEachIndexed { index, action ->
                Row(Modifier.padding(bottom = 8.dp)) {
                    val noText = (index + 1).toString().padStart(2, '0') + "."
                    Text(
                        text = noText,
                        style = EditorialType.No,
                        color = p.accent,
                        modifier = Modifier.width(30.dp).revealUnit(reveal, "actionNo[$index]"),
                    )
                    Text(
                        text = action.text,
                        style = GtjType.BodySm.copy(lineHeight = 23.sp),
                        color = p.fg,
                        modifier = Modifier.weight(1f).revealUnit(reveal, "action[$index]"),
                    )
                }
            }
            // 结尾短规则线（todo-end）：随末条行动一起淡入（actions 非空时末条恒入表）
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(3.dp)
                    .revealUnit(reveal, "action[${card.actions.size - 1}]")
                    .background(p.accent),
            )
        }

        // ── 尾部：知识库引用 / 记忆依据 / token 估算（低调小字）──
        if (card.citations.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp), tint = p.muted)
                Spacer(Modifier.width(6.dp))
                val citationsText = "参考：" + card.citations.joinToString(" · ")
                Text(
                    text = citationsText,
                    style = GtjType.Caption,
                    color = p.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.revealUnit(reveal, "citations"),
                )
            }
        }
        if (card.memoryCitations.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column {
                Text(
                    text = "记忆依据",
                    style = GtjType.Caption,
                    color = p.muted,
                    modifier = Modifier.revealUnit(reveal, "memoryLabel"),
                )
                card.memoryCitations.take(3).forEachIndexed { index, citation ->
                    val memoryText = "「$citation」"
                    Text(
                        text = memoryText,
                        style = GtjType.Caption,
                        color = p.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 4.dp, top = 2.dp)
                            .revealUnit(reveal, "memory[$index]"),
                    )
                }
            }
        }
        if (card.tokenEstimate > 0) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                val tokenText = "本次消耗估算：~${card.tokenEstimate} token"
                Text(
                    text = tokenText,
                    style = GtjType.Caption,
                    color = p.muted,
                    modifier = Modifier.revealUnit(reveal, "tokenEstimate"),
                )
            }
        }
    }
}

/**
 * 段间分隔线（editorial coach-sec border-top）。
 * [key] = 所属段的段头 kicker key（段存在时恒入表）：分隔线随本段首现元素一起淡入，
 * 不先出空骨架；透明 Spacer 不挂（无视觉）。
 */
@Composable
private fun SectionDivider(reveal: RevealController?, key: String) {
    val p = LocalGtjColors.current
    Spacer(Modifier.height(22.dp))
    HorizontalDivider(
        thickness = 1.dp,
        color = p.border,
        modifier = Modifier.revealUnit(reveal, key),
    )
    Spacer(Modifier.height(22.dp))
}

/** 段头：kicker + 英文小标（editorial sec-kicker）；[cnKey]/[enKey] 为渐显 key（见 coachCardRevealPlan） */
@Composable
private fun SecKicker(
    cn: String,
    en: String,
    cnKey: String,
    enKey: String,
    reveal: RevealController?,
) {
    val p = LocalGtjColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = cn,
            style = GtjType.Label.copy(fontSize = 13.sp, letterSpacing = 0.1f.sp),
            color = p.accent,
            modifier = Modifier.revealUnit(reveal, cnKey),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = en,
            style = GtjType.Caption.copy(letterSpacing = 0.14f.sp),
            color = p.muted,
            modifier = Modifier.revealUnit(reveal, enKey),
        )
    }
}

/**
 * 事实方块标记：实心=已知 / 空心描边=推测 / 淡描边=未知
 */
private enum class FactMark { Known, Assumed, Unknown }

/**
 * 事实组：[keyPrefix] 为渐显 key 前缀（"factsKnown"/"factsAssumed"/"factsUnknown"，
 * 见 coachCardRevealPlan）——label 用 "${keyPrefix}Label"、条目用 "${keyPrefix}[index]"。
 * 条目 key 勿在方括号里写 $index：KSP 任务按 language-version 1.9 解析，会报 Identifier expected。
 */
@Composable
private fun FactGroup(
    label: String,
    mark: FactMark,
    items: List<String>,
    reveal: RevealController?,
    keyPrefix: String,
) {
    val p = LocalGtjColors.current
    Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            text = label,
            style = GtjType.Caption,
            color = p.muted,
            modifier = Modifier.revealUnit(reveal, "${keyPrefix}Label"),
        )
        Spacer(Modifier.height(8.dp))
        items.forEachIndexed { index, item ->
            Row(Modifier.padding(bottom = 5.dp)) {
                Box(
                    // 修饰符顺序敏感：revealUnit（graphicsLayer）在 background/border 外层，
                    // 方块底/描边画在层内、随该行一起淡入；放内层则对方块是 no-op（空内容无可罩之物）
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(8.dp)
                        // 方块与条目 Text 共用条目 key：随该行一起淡入，不常显
                        .revealUnit(reveal, "$keyPrefix[$index]")
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
                    modifier = Modifier.weight(1f).revealUnit(reveal, "$keyPrefix[$index]"),
                )
            }
        }
    }
}

/**
 * 话术框（editorial script-box）：灰底直角 + 复制文字链接。
 * [reveal] 渐显 key 固定为 scriptLabel / scriptText / scriptCopy（见 coachCardRevealPlan）；
 * 复制动作始终取 [text] 全文（渐显中点击也复制完整话术）。
 */
@Composable
private fun ScriptBox(
    text: String,
    isClarification: Boolean,
    onCopy: (String) -> Unit,
    reveal: RevealController?,
) {
    val p = LocalGtjColors.current
    val labelText = if (isClarification) "先确认一下" else "可以直接发"
    Column(
        // 灰底随话术 label 同淡入：ScriptBox 渲染当且仅当 scriptLabel 入表（见 coachCardRevealPlan）
        modifier = Modifier
            .fillMaxWidth()
            .revealUnit(reveal, "scriptLabel")
            .background(p.surface.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(
            text = labelText,
            style = GtjType.Caption,
            color = p.accent.copy(alpha = 0.8f),
            modifier = Modifier.revealUnit(reveal, "scriptLabel"),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = text,
            style = GtjType.BodySm.copy(lineHeight = 23.sp),
            color = p.fg,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.revealUnit(reveal, "scriptText"),
        )
        if (!isClarification) {
            Spacer(Modifier.height(9.dp))
            Text(
                text = "复制话术",
                style = GtjType.Caption.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1f.sp),
                color = p.accent,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable { onCopy(text) }
                    .revealUnit(reveal, "scriptCopy"),
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

/** 刊头时间戳（HH:mm）：优先消息创建时间，缺失（预览/兜底）时取当前时间 */
private fun formatMessageTime(createdAt: Long?): String {
    val ts = createdAt ?: System.currentTimeMillis()
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
}

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
