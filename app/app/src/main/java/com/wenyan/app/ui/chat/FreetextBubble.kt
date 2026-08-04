package com.wenyan.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.components.ReplyCard
import com.wenyan.app.ui.contract.ChatMessageUi

/**
 * v1.3.1 freetext 融合气泡（截图参考：AI 一段分析 + 一条"可以直接发…复制话术"卡）：
 * - 上方：可复制话术卡（复用 ReplyCard 成品卡，timing 传空串隐藏时机卡）
 * - 下方：完整解释/分析正文（复用 MessageBubble AI 样式，自带左对齐/长按语义）
 * - reply 为空时由上层直接渲染 MessageBubble，本组件只处理非空 reply 的融合形态
 */
@Composable
internal fun FreetextBubble(
    message: ChatMessageUi,
    split: FreetextSplit,
    onCopyReply: (String) -> Unit,
    onLongClick: ((Offset) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (split.reply.isNotBlank()) {
            // 话术卡：accent 实底 + "可以直接发" + 复制按钮；不挂长按（卡内按钮已覆盖复制）
            ReplyCard(
                reply = split.reply,
                timing = "",
                onCopy = onCopyReply,
            )
        }
        if (split.body.isNotBlank()) {
            // 正文：复用 MessageBubble（AI 左对齐，widthIn(max=340) 在 Column 内不撑破）
            MessageBubble(
                message = message.copy(content = split.body),
                onLongClick = onLongClick,
            )
        }
    }
}
