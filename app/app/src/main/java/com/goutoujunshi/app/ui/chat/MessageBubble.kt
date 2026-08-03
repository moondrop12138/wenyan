package com.goutoujunshi.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.goutoujunshi.app.ui.contract.ChatMessageUi
import com.goutoujunshi.app.ui.contract.ChatRole
import com.goutoujunshi.app.ui.theme.GtjShape
import com.goutoujunshi.app.ui.theme.GtjType
import com.goutoujunshi.app.ui.theme.LocalGtjColors

/**
 * 消息气泡（design-tokens component.bubbleUser/bubbleAi，design-pages 页面1）：
 * 用户右对齐 accent 底 + accentOn 字（右下小圆角，maxWidth 82%）；
 * AI 左对齐 surface 底 + fg 字（左下小圆角，maxWidth 92%，borderSoft 边）。
 */
@Composable
fun MessageBubble(
    message: ChatMessageUi,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    val isUser = message.role == ChatRole.USER
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = if (isUser) 300.dp else 340.dp),
            shape = if (isUser) {
                RoundedCornerShape(
                    topStart = CornerSize(GtjShape.lgRadius),
                    topEnd = CornerSize(GtjShape.lgRadius),
                    bottomStart = CornerSize(GtjShape.lgRadius),
                    bottomEnd = CornerSize(GtjShape.bubbleTailSmRadius),
                )
            } else {
                RoundedCornerShape(
                    topStart = CornerSize(GtjShape.lgRadius),
                    topEnd = CornerSize(GtjShape.lgRadius),
                    bottomStart = CornerSize(GtjShape.bubbleTailSmRadius),
                    bottomEnd = CornerSize(GtjShape.lgRadius),
                )
            },
            color = if (isUser) p.accent else p.surface,
            contentColor = if (isUser) p.accentOn else p.fg,
            border = if (isUser) null else BorderStroke(1.dp, p.borderSoft),
        ) {
            Text(
                text = message.content,
                style = GtjType.Body,
                // 无障碍：读屏按"角色+内容"播报——用户消息前置"你说"，AI 消息保持原文
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics {
                        if (isUser) {
                            contentDescription = "你说：" + message.content
                        }
                    },
            )
        }
    }
}

/** 流式中的 AI 气泡（打字机增量文本，design-pages 页面1） */
@Composable
fun StreamingBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(
                topStart = CornerSize(GtjShape.lgRadius),
                topEnd = CornerSize(GtjShape.lgRadius),
                bottomStart = CornerSize(GtjShape.bubbleTailSmRadius),
                bottomEnd = CornerSize(GtjShape.lgRadius),
            ),
            color = p.surface,
            contentColor = p.fg,
            border = BorderStroke(1.dp, p.borderSoft),
        ) {
            Text(
                text = text,
                style = GtjType.Body,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
