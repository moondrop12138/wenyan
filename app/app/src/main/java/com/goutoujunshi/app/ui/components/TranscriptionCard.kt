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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goutoujunshi.app.ui.theme.GtjShape
import com.goutoujunshi.app.ui.theme.GtjType
import com.goutoujunshi.app.ui.theme.LocalGtjColors

/**
 * 截图转述确认卡（通道 B，AC-08，design-pages 页面5）：
 * 头部 image_search + 说明 + 转述 quoteBlock（可编辑）+ 重新选图/确认分析。
 * 三态：loading（转述中）/ error（提取失败重试）/ normal（可编辑确认）。
 */
@Composable
fun TranscriptionCard(
    transcription: String,
    onTranscriptionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onReselect: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: Boolean = false,
    onRetry: () -> Unit = {},
) {
    val p = LocalGtjColors.current
    var isEditing by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GtjShape.md,
        color = p.surfaceElevated,
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ImageSearch, contentDescription = null, modifier = Modifier.size(20.dp), tint = p.fgSecondary)
                Spacer(Modifier.width(8.dp))
                Text("AI 从截图中读出了这些内容", style = GtjType.Subtitle, color = p.fg, modifier = Modifier.weight(1f))
                // AC-08：编辑图标进入编辑态（此时 QuoteBlock 可编辑）
                GtjIconButton(
                    icon = Icons.Outlined.Edit,
                    contentDescription = if (isEditing) "完成编辑" else "编辑转述内容",
                    onClick = { isEditing = !isEditing },
                    tint = if (isEditing) p.accent else p.muted,
                    iconSize = 20.dp,
                )
            }
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = p.accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在提取截图文字…", style = GtjType.BodySm, color = p.muted)
                }
            } else if (error) {
                Text("截图文字提取失败", style = GtjType.Body, color = p.danger)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    GhostButton(text = "重试", onClick = onRetry, minHeight = 48.dp)
                }
            } else {
                Text("当前模型不支持看图，已用视觉模型提取文字。可修正后再分析。", style = GtjType.Caption, color = p.muted)
                QuoteBlock(
                    text = transcription,
                    editable = isEditing,
                    onTextChange = onTranscriptionChange,
                    label = "编辑转述内容",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    GhostButton(text = "重新选图", onClick = onReselect, minHeight = 48.dp)
                    PrimaryButton(text = "确认分析", onClick = onConfirm, icon = Icons.Outlined.Check, minHeight = 48.dp)
                }
            }
        }
    }
}
