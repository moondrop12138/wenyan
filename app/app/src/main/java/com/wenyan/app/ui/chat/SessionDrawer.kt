package com.wenyan.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.components.Tag
import com.wenyan.app.ui.components.TagKind
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
    searchQuery: String = "",
    searchResults: List<Long> = emptyList(),
    onSearchQueryChange: (String) -> Unit = {},
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
                // v1.7.4：内容右移 12dp（截图反馈微调；offset 只平移绘制不占布局，右缘余量充足无溢出）
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .offset(x = 12.dp),
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

        // O3: 全文搜索（命中 sessionId 或标题包含关键词）
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("搜索会话 / 消息", style = GtjType.Caption, color = p.muted) },
            singleLine = true,
            textStyle = GtjType.BodySm,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        val filtered = if (searchQuery.isBlank()) sessions else sessions.filter {
            searchResults.contains(it.id) || it.title.contains(searchQuery, ignoreCase = true)
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (searchQuery.isBlank()) "暂无历史会话\n新建一个开始聊天吧" else "没有匹配的会话",
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
                // v1.7.3 F4 会话按记忆档案分组（v1.7.3-fix：分组键改 targetId，同名档案不再合并）；
                // 组内保持列表顺序（id DESC = 时间倒序）；每组前加档案名组头（取组内第一条的 targetName）；
                // 未关联（targetId=null）归最后
                val grouped = filtered.groupBy { it.targetId }
                val orderedGroups = grouped.filterKeys { it != null } +
                    (grouped[null]?.let { mapOf<Long?, List<SessionSummaryUi>>(null to it) } ?: emptyMap())
                orderedGroups.forEach { (_, groupSessions) ->
                    // 组头文字：组内第一条的 targetName（空/档案已删 → 回退未关联占位）
                    val groupName = groupSessions.firstOrNull()
                        ?.targetName?.trim()?.takeIf(String::isNotEmpty) ?: UNLINKED_GROUP
                    item(key = "group_${groupSessions.firstOrNull()?.targetId ?: UNLINKED_GROUP}") {
                        GroupHeader(groupName)
                    }
                    items(groupSessions, key = { "session_${it.id}" }) { session ->
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
}

/** v1.7.3 会话分组组头（accentSoft 小字，风格对齐「最近会话」标题）；未关联组显示占位 */
@Composable
private fun GroupHeader(name: String) {
    val p = LocalGtjColors.current
    val label = if (name == UNLINKED_GROUP) "未关联" else name
    Text(
        text = label,
        style = GtjType.Caption,
        color = p.accent,
        modifier = Modifier
            .fillMaxWidth()
            .background(p.accentSoft, GtjShape.sm)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 未关联会话组名（targetName=null，放最后） */
private const val UNLINKED_GROUP = "__unlinked__"

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
                // v1.7.2 会话归属档案 Tag：name.trim().take(4)（≤4 字全显，>4 字截前 4 字）；
                // targetName=null（老会话）不显示；空名称不显示
                session.targetName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                    Spacer(Modifier.width(6.dp))
                    Tag(text = name.take(4), kind = TagKind.NEUTRAL)
                }
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

/** v1.7.3 T2 @Preview：会话条目（含档案 Tag） */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFFF6F0E6)
@Composable
private fun SessionItemPreview() {
    com.wenyan.app.ui.theme.GtjTheme {
        SessionItem(
            session = SessionSummaryUi(id = 1L, title = "我们聊聊最近的状态", createdAt = System.currentTimeMillis() - 3600_000, targetName = "小A"),
            isCurrent = false,
            onClick = {},
            onLongClick = {},
        )
    }
}

/** v1.7.3 T2 @Preview：分组组头 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFFF6F0E6)
@Composable
private fun GroupHeaderPreview() {
    com.wenyan.app.ui.theme.GtjTheme {
        GroupHeader("小A")
    }
}
