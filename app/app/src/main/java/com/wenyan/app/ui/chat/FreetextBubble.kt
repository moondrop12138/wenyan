package com.wenyan.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.components.ScriptBubble
import com.wenyan.app.ui.contract.ChatMessageUi

/**
 * v1.3.1 freetext 融合气泡（仅老数据渲染；v1.6 新回复全部走 CoachCard）：
 * - 上方：可复制话术卡（复用 ScriptBubble 话术气泡，新样式）
 * - 下方：完整解释/分析正文（复用 MessageBubble AI 样式，自带左对齐/长按语义）
 * - reply 为空时由上层直接渲染 MessageBubble，本组件只处理非空 reply 的融合形态
 */
@Composable
internal fun FreetextBubble(
    message: ChatMessageUi,
    split: FreetextSplit,
    onCopyReply: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((Offset) -> Unit)? = null,
    reveal: RevealController? = null,
    // 渐显中点按气泡本体 → 立即全量（透传给内层 MessageBubble）
    onTapReveal: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (split.reply.isNotBlank()) {
            // 话术卡：accentSoft 底 + "可以直接发" + 复制按钮；不挂长按（卡内按钮已覆盖复制）
            // 逐段淡入：整卡按 "reply" key 挂淡入+上浮层（key 表见 RevealController.kt），
            // text 恒为 split.reply 全文
            Box(modifier = Modifier.revealUnit(reveal, "reply")) {
                ScriptBubble(
                    text = split.reply,
                    // 复制始终取原文全文（与渲染值一致，但保持显式原文不依赖渲染入参）
                    onCopy = { onCopyReply(split.reply) },
                )
            }
        }
        if (split.body.isNotBlank()) {
            // 正文：复用 MessageBubble（AI 左对齐，widthIn(max=340) 在 Column 内不撑破）；
            // reveal 透传，MessageBubble 内部按 "body" key 挂淡入层
            MessageBubble(
                message = message.copy(content = split.body),
                onLongClick = onLongClick,
                reveal = reveal,
                onTapReveal = onTapReveal,
            )
        }
    }
}
