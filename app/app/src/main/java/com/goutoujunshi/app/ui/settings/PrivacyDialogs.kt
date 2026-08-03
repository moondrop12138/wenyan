package com.goutoujunshi.app.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.goutoujunshi.app.ui.theme.GtjType
import com.goutoujunshi.app.ui.theme.LocalGtjColors

/**
 * 隐私声明（AC-18，design-pages 页面3 分组3）：首次配置 API Key 前必须确认。
 */
@Composable
fun PrivacyDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
) {
    val p = LocalGtjColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceElevated,
        titleContentColor = p.fg,
        textContentColor = p.fgSecondary,
        title = { Text("隐私声明", style = GtjType.Title) },
        text = { Text("你输入的聊天记录与截图将发送至你自行配置的第三方模型服务（API Key 由你提供）。App 本身无云端，历史数据仅保存在本机，可随时一键清除。", style = GtjType.BodySm) },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("确认", style = GtjType.Label, color = p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("暂不", style = GtjType.Label, color = p.muted)
            }
        },
    )
}

/**
 * 清除全部档案二次确认（AC-12，design-pages 页面3）：删除 Key/档案/会话全部本地数据。
 */
@Composable
fun WipeDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val p = LocalGtjColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceElevated,
        titleContentColor = p.fg,
        textContentColor = p.fgSecondary,
        title = { Text("清除全部档案", style = GtjType.Title) },
        text = { Text("将删除本机保存的全部数据：API Key、档案、会话记录与设置，且不可恢复。确定继续吗？", style = GtjType.BodySm) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认清除", style = GtjType.Label, color = p.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = GtjType.Label, color = p.muted)
            }
        },
    )
}
