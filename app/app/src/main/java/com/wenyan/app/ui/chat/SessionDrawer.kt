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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.components.glass.GlassSurface
import com.wenyan.app.ui.contract.SessionSummaryUi
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 左滑抽屉 - 历史会话列表（v1.5 WY-04，Arc/Things 温暖质感）：
 * - 头部：产品名"温言"+ 副标题"恋爱决策支持"
 * - "新建会话"陶土棕胶囊按钮
 * - 会话列表（标题 + 首条消息预览），当前会话 8% 陶土棕底高亮
 * - 长按会话条目 → 删除（由上层弹确认）
 *
 * 由 ChatScreen 用 ModalNavigationDrawer 承载；面板宽度 312，右侧圆角 24。
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
            // v1.7.0：抽屉宽按原型收窄 312 → 300
            .width(300.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // v1.5 头部：产品名 + 副标题（设计稿 WY-04）
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("温言", style = GtjType.Title, color = p.fg)
            Spacer(Modifier.height(2.dp))
            Text("恋爱决策支持", style = GtjType.Caption, color = p.meta)
        }
        // v1.7.1-4：新建会话 = 液态玻璃胶囊（strong 玻璃 + accent 字，与玻璃侧栏同材质）
        GlassSurface(
            onClick = onNewSession,
            shape = RoundedCornerShape(22.dp),
            strong = true,
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
                    tint = p.accent,
                )
                Spacer(Modifier.width(6.dp))
                Text("新建会话", style = GtjType.Subtitle, color = p.accent)
            }
        }
        Spacer(Modifier.height(16.dp))
        // 会话列表标题
        Text(
            "最近会话",
            style = GtjType.Caption,
            color = p.muted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))

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
    // v1.7.0：会话行 = 玻璃（未选中）；当前会话保持 accentSoft + accent 边强调
    if (isCurrent) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            shape = GtjShape.md,
            color = p.accentSoft,
            border = BorderStroke(1.dp, p.accent.copy(alpha = 0.35f)),
        ) {
            SessionItemContent(session, p)
        }
    } else {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            shape = GtjShape.md,
        ) {
            SessionItemContent(session, p)
        }
    }
}

@Composable
private fun SessionItemContent(
    session: SessionSummaryUi,
    p: com.wenyan.app.ui.theme.GtjPalette,
) {
        // v1.5：标题 + 首条消息预览（设计稿 WY-04 会话项）
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.title,
                    style = GtjType.BodySm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = p.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = formatSessionTime(session.createdAt),
                style = GtjType.Caption,
                color = p.meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
