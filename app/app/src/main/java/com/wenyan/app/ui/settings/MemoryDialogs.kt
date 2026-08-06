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
 * v1.7.2 记忆档案弹窗三件套（风格对齐 PrivacyDialogs.kt：AlertDialog + GtjShape.lg + surfaceElevated 容器）：
 * MemoryNameDialog（新建，空白不允许）/ MemoryEditDialog（改名 + 正文多行 2-4 行）/ MemoryDeleteDialog（二次确认 danger）。
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

/** 编辑记忆档案：名称 TextField + 记忆正文多行 TextField（2-4 行），保存钮 → updateTarget(id, name, note) */
@Composable
fun MemoryEditDialog(
    initialName: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var note by remember { mutableStateOf(initialNote) }
    val p = LocalGtjColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GtjShape.lg,
        containerColor = p.surfaceElevated,
        titleContentColor = p.fg,
        textContentColor = p.fgSecondary,
        title = { Text("编辑记忆", style = GtjType.Title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("档案名称", style = GtjType.Label) },
                    textStyle = GtjType.Body,
                    singleLine = true,
                    shape = GtjShape.md,
                    colors = memoryFieldColors(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("记忆正文", style = GtjType.Label) },
                    placeholder = { Text("已记住的关于咨询对象的信息", style = GtjType.BodySm, color = p.meta) },
                    textStyle = GtjType.BodySm,
                    minLines = 2,
                    maxLines = 4,
                    shape = GtjShape.md,
                    colors = memoryFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, note) }, enabled = name.trim().isNotEmpty()) {
                Text("保存", style = GtjType.Label, color = p.accent)
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
