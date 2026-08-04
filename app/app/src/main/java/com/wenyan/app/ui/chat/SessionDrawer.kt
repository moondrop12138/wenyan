package com.wenyan.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.contract.SessionSummaryUi
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 左滑抽屉 - 历史会话列表（DeepSeek 客户端风格）：
 * - 顶部"新会话"按钮（accent 实心）
 * - 中部会话列表（标题 = 首条用户消息，副标题 = 创建时间），当前会话 accentSoft 高亮
 * - 长按会话条目 → 删除（由上层弹确认）
 * - 点会话条目 → 切换并关抽屉
 *
 * 由 ChatScreen 用 ModalNavigationDrawer 承载。
 */
@Composable
fun SessionDrawerContent(
    sessions: List<SessionSummaryUi>,
    currentSessionId: Long?,
    onNewSession: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onLongPressSession: (SessionSummaryUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // 顶部：标题 + 新建按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("聊天记录", style = GtjType.Title, color = p.fg, modifier = Modifier.weight(1f))
        }
        Surface(
            onClick = onNewSession,
            shape = GtjShape.md,
            color = p.accent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = p.accentOn,
                )
                Spacer(Modifier.width(6.dp))
                Text("新建会话", style = GtjType.Subtitle, color = p.accentOn)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无历史会话\n新建一个开始聊天吧",
                    style = GtjType.BodySm,
                    color = p.muted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionItem(
                        session = session,
                        isCurrent = session.id == currentSessionId,
                        onClick = { onSelectSession(session.id) },
                        onLongClick = { onLongPressSession(session) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(
    session: SessionSummaryUi,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val p = LocalGtjColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = GtjShape.sm,
        color = if (isCurrent) p.accentSoft else p.surface,
        border = if (isCurrent) BorderStroke(1.dp, p.accent.copy(alpha = 0.4f)) else null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isCurrent) p.accent else p.muted,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = GtjType.BodySm,
                    color = if (isCurrent) p.accent else p.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatSessionTime(session.createdAt),
                    style = GtjType.Caption,
                    color = p.muted,
                )
            }
        }
    }
}

private fun formatSessionTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMs
    val oneDay = 24L * 60 * 60 * 1000
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < oneDay -> "${diff / 3_600_000} 小时前"
        diff < 7 * oneDay -> "${diff / oneDay} 天前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(epochMs))
    }
}
