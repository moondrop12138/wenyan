package com.wenyan.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.components.GtjIconButton
import com.wenyan.app.ui.components.PrimaryButton
import com.wenyan.app.ui.components.SecondaryButton
import com.wenyan.app.ui.components.glass.GlowBackground
import com.wenyan.app.ui.components.glass.liquidGlass
import com.wenyan.app.ui.contract.AppContainer
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.navigation.rememberViewModel
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * 提供商编辑（/settings/provider/:id，AC-09/11，design-pages 页面3）：
 * 名称/Host/Key 密文显隐 + 模型管理 + 测试连接三态 + 删除二次确认。
 */
@Composable
fun ProviderEditScreen(
    container: AppContainer,
    providerId: Long,
    onBack: () -> Unit,
) {
    val vm: ProviderEditViewModel = rememberViewModel("ProviderEdit_$providerId") {
        ProviderEditViewModel(container.settingsRepository, providerId)
    }
    val p = LocalGtjColors.current

    // v1.8.1 B4：移除 glowState 光斑共享——dead path 且每帧重组开销大

    // v1.7.1：根 Box 加主题背景（防系统深色下 windowBackground 透出导致浅色模式变暗底）
    Box(Modifier.fillMaxSize().background(p.bg)) {
        GlowBackground()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // v1.8.0 液态玻璃 2.0：顶栏悬浮胶囊（v1.8.1 B4 移除光斑 dead path）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .liquidGlass(shape = GtjShape.inputBar)
                        .clip(GtjShape.inputBar),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    ) {
                        GtjIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", onClick = onBack)
                        Text(if (vm.isNew) "添加提供商" else "编辑提供商", style = GtjType.Title, color = p.fg)
                    }
                } // 玻璃胶囊
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditField(label = "名称", value = vm.name, onChange = { vm.name = it }, mono = false)
                EditField(label = "Base URL / Host", value = vm.baseUrl, onChange = { vm.baseUrl = it }, mono = true, placeholder = "https://api.example.com")
                EditField(
                    label = "API Key",
                    value = vm.apiKey,
                    onChange = { vm.apiKey = it },
                    mono = true,
                    placeholder = "sk-…",
                    isSecret = !vm.showKey,
                    trailing = {
                        IconButton(onClick = vm::toggleKeyVisibility) {
                            Icon(
                                if (vm.showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "显示/隐藏 API Key",
                                tint = p.muted,
                            )
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(text = "测试连接", onClick = vm::testConnection, loading = vm.testing)
                }
                vm.testResult?.let { result ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (result.ok) Icons.Outlined.Check else Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (result.ok) p.success else if (result.warn) p.warn else p.danger,
                        )
                        Spacer(Modifier.width(8.dp))
                        // 对比度：warn 浅色白底仅 3.2:1，正文用 warmOn 5.0:1（图标保留 warn，非文字 3:1 达标）
                        Text(
                            result.message,
                            style = GtjType.BodySm,
                            color = if (result.ok) p.success else if (result.warn) p.warmOn else p.danger,
                        )
                    }
                }
            }

            // 模型管理
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = GtjShape.md,
                color = p.surfaceElevated,
                border = BorderStroke(1.dp, p.border),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("模型管理", style = GtjType.Label, color = p.muted)
                    // v1.6.3 每个模型包一个独立卡片框（沙色底+细边框），模型之间靠框自然分隔
                    vm.models.forEach { model ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = GtjShape.md,
                            color = p.surface,
                            border = BorderStroke(1.dp, p.borderSoft),
                        ) {
                            ModelManageRow(
                                model = model,
                                onSheetVisibleChange = { vm.toggleSheetVisible(model.id) },
                                onVisionChange = { vm.setVision(model.id, it) },
                                onDelete = { vm.deleteModel(model.id) },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = vm.newModelName,
                            onValueChange = { vm.newModelName = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("添加模型名", style = GtjType.BodySm, color = p.meta) },
                            textStyle = GtjType.Mono,
                            singleLine = true,
                            shape = GtjShape.md,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = p.accent,
                                unfocusedBorderColor = p.border,
                                focusedContainerColor = p.surface,
                                unfocusedContainerColor = p.surface,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        GtjIconButton(icon = Icons.Outlined.Add, contentDescription = "添加模型", onClick = vm::addModel, tint = p.accent, iconSize = 20.dp)
                    }
                }
            }

            if (!vm.isNew) {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    SecondaryButton(text = "删除此提供商", onClick = vm::requestDelete, modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PrimaryButton(text = "保存", onClick = { vm.save(onBack) }, loading = vm.saving, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    } // Box（GlowBackground + Scaffold）

    if (vm.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissDelete,
            shape = GtjShape.lg,
            containerColor = p.surfaceElevated,
            titleContentColor = p.fg,
            textContentColor = p.fgSecondary,
            title = { Text("删除此提供商？", style = GtjType.Title) },
            text = { Text("将同时删除其下所有模型。此操作不可恢复。", style = GtjType.BodySm) },
            confirmButton = {
                TextButton(onClick = { vm.deleteProvider(onBack) }) {
                    Text("删除", style = GtjType.Label, color = p.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDelete) {
                    Text("取消", style = GtjType.Label, color = p.muted)
                }
            },
        )
    }

    // AC-18：首次保存/测试 API Key 前强制隐私声明确认
    if (vm.showPrivacyDialog) {
        PrivacyDialog(onDismiss = vm::dismissPrivacy, onAccept = vm::acceptPrivacy)
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    mono: Boolean,
    placeholder: String = "",
    isSecret: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = LocalGtjColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, style = GtjType.Label) },
        placeholder = { Text(placeholder, style = GtjType.BodySm, color = p.meta) },
        textStyle = if (mono) GtjType.Mono else GtjType.Body,
        singleLine = true,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = { trailing?.invoke() },
        shape = GtjShape.md,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.accent,
            unfocusedBorderColor = p.border,
            focusedContainerColor = p.surface,
            unfocusedContainerColor = p.surface,
            cursorColor = p.accent,
        ),
    )
}

@Composable
private fun ModelManageRow(
    model: ModelInfo,
    onSheetVisibleChange: () -> Unit,
    onVisionChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalGtjColors.current
    // v1.6.3 两行式排布（外层为独立卡片框）：第一行模型名+删除；第二行缩进"主页/视觉"两个带标签开关
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(model.name, style = GtjType.Body, color = p.fg, modifier = Modifier.weight(1f))
            GtjIconButton(icon = Icons.Outlined.Delete, contentDescription = "删除模型", onClick = onDelete, tint = p.muted, iconSize = 20.dp)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("主页", style = GtjType.Caption, color = p.muted)
            Switch(
                checked = model.showInSheet,
                onCheckedChange = { onSheetVisibleChange() },
                // 无障碍：Switch 显式关联 label
                modifier = Modifier.semantics { contentDescription = "在主页模型选择中显示：${model.name}" },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = p.accentOn,
                    checkedTrackColor = p.accent,
                    uncheckedTrackColor = p.borderSoft,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text("视觉", style = GtjType.Caption, color = p.muted)
            Switch(
                checked = model.supportsVision,
                onCheckedChange = onVisionChange,
                // 无障碍：Switch 显式关联 label
                modifier = Modifier.semantics { contentDescription = "${model.name} 支持视觉" },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = p.accentOn,
                    checkedTrackColor = p.accent,
                    uncheckedTrackColor = p.borderSoft,
                ),
            )
        }
    }
}
