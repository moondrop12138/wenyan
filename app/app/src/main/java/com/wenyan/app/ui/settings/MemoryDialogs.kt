package com.wenyan.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * v1.7.2/1.7.3 记忆档案弹窗（风格对齐 PrivacyDialogs.kt：AlertDialog + GtjShape.lg + surfaceElevated 容器）：
 * MemoryNameDialog（新建，空白不允许）/ MemoryDeleteDialog（二次确认 danger）。
 * v1.7.3 编辑弹窗已移除：档案行「编辑」→ 跳 MemoryEdit 页（MemoryEditDialog 废弃）。
 */

/** 新建记忆档案：名称 TextField，确认钮 accent，空白输入禁用确认 */
@Composable
fun MemoryNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val p = LocalGtjColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GtjShape.lg,
        containerColor = p.surfaceElevated,
        titleContentColor = p.fg,
        textContentColor = p.fgSecondary,
        title = { Text("添加记忆", style = GtjType.Title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("档案名称", style = GtjType.Label) },
                placeholder = { Text("如：小A", style = GtjType.BodySm, color = p.meta) },
                textStyle = GtjType.Body,
                singleLine = true,
                shape = GtjShape.md,
                colors = memoryFieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.trim().isNotEmpty()) {
                Text("创建", style = GtjType.Label, color = p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = GtjType.Label, color = p.muted)
            }
        },
    )
}

/** 删除记忆二次确认（复用 WipeDialog 模板）：确认钮 danger 色 */
@Composable
fun MemoryDeleteDialog(
    targetName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val p = LocalGtjColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GtjShape.lg,
        containerColor = p.surfaceElevated,
        titleContentColor = p.fg,
        textContentColor = p.fgSecondary,
        title = { Text("删除记忆", style = GtjType.Title) },
        text = { Text("删除后「$targetName」的记忆将无法恢复，确定删除？", style = GtjType.BodySm) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", style = GtjType.Label, color = p.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = GtjType.Label, color = p.muted)
            }
        },
    )
}

/** 记忆弹窗输入框配色（对齐 ProviderEditScreen.EditField：accent 聚焦边 / surface 底） */
@Composable
private fun memoryFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalGtjColors.current.accent,
    unfocusedBorderColor = LocalGtjColors.current.border,
    focusedContainerColor = LocalGtjColors.current.surface,
    unfocusedContainerColor = LocalGtjColors.current.surface,
    cursorColor = LocalGtjColors.current.accent,
)

/** v1.7.3 T2 @Preview：新建记忆弹窗 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFFF6F0E6)
@Composable
private fun MemoryNameDialogPreview() {
    com.wenyan.app.ui.theme.GtjTheme {
        MemoryNameDialog(onDismiss = {}, onConfirm = {})
    }
}

/** v1.7.3 T2 @Preview：删除记忆确认弹窗 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFFF6F0E6)
@Composable
private fun MemoryDeleteDialogPreview() {
    com.wenyan.app.ui.theme.GtjTheme {
        MemoryDeleteDialog(targetName = "小A", onDismiss = {}, onConfirm = {})
    }
}
