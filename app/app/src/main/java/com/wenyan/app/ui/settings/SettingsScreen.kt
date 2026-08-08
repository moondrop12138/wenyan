package com.wenyan.app.ui.settings

import com.wenyan.app.BuildConfig
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wenyan.app.data.update.UpdateInfo
import com.wenyan.app.ui.components.GtjIconButton
import com.wenyan.app.ui.components.ModelSheet
import com.wenyan.app.ui.components.Tag
import com.wenyan.app.ui.components.TagKind
import com.wenyan.app.ui.components.ThickDivider
import com.wenyan.app.ui.components.glass.GlassSurface
import com.wenyan.app.ui.components.glass.GlowBackground
import com.wenyan.app.ui.components.glass.liquidGlass
import com.wenyan.app.ui.components.glass.rememberGlowState
import com.wenyan.app.ui.contract.AppContainer
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.contract.TargetUi
import com.wenyan.app.ui.navigation.rememberViewModel
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

private enum class PickerTarget { MAIN, VISION }

/**
 * 设置页（/settings，SPEC §7 页面3）：模型服务 / 记忆 / 外观 / 隐私与安全 分组。
 * v1.7.3：档案行编辑 → 跳 MemoryEdit 页；新增「导出诊断日志」「检查更新」行。
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onEditProvider: (Long) -> Unit,
    onEditTarget: (Long) -> Unit,
) {
    val vm: SettingsViewModel = rememberViewModel("SettingsViewModel") {
        SettingsViewModel(container.settingsRepository)
    }
    val providers by vm.providers.collectAsState()
    val models by vm.models.collectAsState()
    val currentId by vm.currentModelId.collectAsState()
    val visionId by vm.visionModelId.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val showPrivacy by vm.showPrivacyDialog.collectAsState()
    val showWipe by vm.showWipeDialog.collectAsState()
    val targets by vm.targets.collectAsState()
    val memoryAutoEnabled by vm.memoryAutoEnabled.collectAsState()
    val toastMessage by vm.toastMessage.collectAsState()
    val showNameDialog by vm.showNameDialog.collectAsState()
    val editTarget by vm.editTarget.collectAsState()
    val deleteTarget by vm.deleteTarget.collectAsState()
    val p = LocalGtjColors.current
    val context = LocalContext.current
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }

    // v1.7.2 切换激活档案 Toast（一次性事件，消费后清空）
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

    // v1.8.0：液态玻璃 2.0 · 光斑状态共享
    val glowState = rememberGlowState()

    // v1.7.1：根 Box 加主题背景（防系统深色下 windowBackground 透出导致浅色模式变暗底）
    Box(Modifier.fillMaxSize().background(p.bg)) {
        GlowBackground(onGlowPositionsChanged = glowState::update)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // v1.8.0 液态玻璃 2.0：顶栏悬浮胶囊（光斑交互 + 边缘透镜）
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
                        .liquidGlass(
                            shape = GtjShape.inputBar,
                            glowPositions = glowState.positions,
                            glowIntensities = glowState.intensities,
                        )
                        .clip(GtjShape.inputBar),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    ) {
                        GtjIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", onClick = onBack)
                        Text("设置", style = GtjType.Title, color = p.fg)
                    }
                } // 玻璃胶囊
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            item { SettingsSectionHeader("模型服务") }
            item {
                // v1.3.1 主模型条目去掉类型标签（"纯文本"/"支持视觉"），label 垂直居中与右侧模型名对齐
                SettingsRow(
                    label = "主模型",
                    value = models.firstOrNull { it.id == currentId }?.name ?: "未选择",
                    // v1.7.1-4：与视觉模型行等高（补 caption 成两行结构）
                    caption = "对话与截图分析使用的默认模型",
                    onClick = { pickerTarget = PickerTarget.MAIN },
                )
            }
            item {
                SettingsRow(
                    label = "视觉模型",
                    value = models.firstOrNull { it.id == visionId }?.name ?: "未选择",
                    caption = "用于非多模态主模型的截图分析",
                    onClick = { pickerTarget = PickerTarget.VISION },
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("提供商", style = GtjType.Label, color = p.muted, modifier = Modifier.weight(1f))
                    GtjIconButton(icon = Icons.Outlined.Add, contentDescription = "添加提供商", onClick = { onEditProvider(-1L) }, tint = p.accent, iconSize = 20.dp)
                }
            }
            if (providers.isEmpty()) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("还没有模型服务，添加一个开始使用", style = GtjType.BodySm, color = p.muted)
                    }
                }
            } else {
                // key 加 "provider_" 前缀：与下方「记忆」分组 items 的 key 空间隔离。
                // provider/target 是两张独立自增表，id 均从 1 开始——同一 LazyColumn 内若都用裸 id 作 key，
                // provider id=1 与 target id=1 会撞 key，滚动到记忆分组时 Compose 抛
                // "Key was already used"（LayoutNodeSubcompositionsState）→ 100% 闪退。
                items(providers, key = { "provider_${it.id}" }) { provider ->
                    ProviderRow(provider = provider, onClick = { onEditProvider(provider.id) })
                }
            }
            // ===== v1.7.2 「记忆」分组（模型服务之后、外观之前） =====
            item { ThickDivider() }
            item { SettingsSectionHeader("记忆") }
            if (targets.isEmpty()) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("还没有记忆档案，添加一个开始使用", style = GtjType.BodySm, color = p.muted)
                    }
                }
            } else {
                // key 加 "target_" 前缀：与「模型服务」分组 providers items 的 key 空间隔离（防撞 key 闪退，见上）
                items(targets, key = { "target_${it.id}" }) { target ->
                    MemoryTargetRow(
                        target = target,
                        onClick = { vm.setActiveTarget(target) },
                        // v1.7.3 编辑图标 → 跳档案详情页（替代改名弹窗）
                        onEdit = { onEditTarget(target.id) },
                        onDelete = { vm.requestDeleteTarget(target) },
                    )
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("添加记忆", style = GtjType.Label, color = p.accent, modifier = Modifier.weight(1f))
                    GtjIconButton(icon = Icons.Outlined.Add, contentDescription = "添加记忆", onClick = vm::requestCreateTarget, tint = p.accent, iconSize = 20.dp)
                }
            }
            item {
                // v1.7.2 自动记忆开关行（玻璃行 + Switch，默认开）
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = GtjShape.md,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自动记忆", style = GtjType.Body, color = p.fg)
                            Text("回复后自动提炼新事实写入当前档案", style = GtjType.Caption, color = p.muted)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = memoryAutoEnabled,
                            onCheckedChange = vm::setMemoryAutoEnabled,
                            // 无障碍：Switch 显式关联 label
                            modifier = Modifier.semantics { contentDescription = "自动记忆" },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = p.accentOn,
                                checkedTrackColor = p.accent,
                                uncheckedTrackColor = p.borderSoft,
                            ),
                        )
                    }
                }
            }
            item {
                Text(
                    "选择本次咨询对象的记忆，不同对象互不干扰",
                    style = GtjType.Caption,
                    color = p.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item { ThickDivider() }
            item { SettingsSectionHeader("外观") }
            item {
                ThemePicker(current = themeMode, onSelect = vm::setTheme, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item { ThickDivider() }
            item { SettingsSectionHeader("隐私与安全") }
            item {
                SettingsRow(
                    label = "隐私声明",
                    value = "数据将发送至你配置的第三方模型服务",
                    icon = Icons.Outlined.Info,
                    onClick = vm::requestPrivacy,
                )
            }
            item {
                // v1.7.3 T3 导出诊断日志（ShareIntent + FileProvider 发送 last_crash.txt，无用户内容）
                SettingsRow(
                    label = "导出诊断日志",
                    value = "崩溃日志本地文件",
                    icon = null,
                    onClick = {
                        vm.exportCrashLog { uri ->
                            if (uri == null) {
                                Toast.makeText(context, "暂无崩溃日志可导出", Toast.LENGTH_SHORT).show()
                            } else {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(Intent.createChooser(intent, "导出诊断日志")) }
                            }
                        }
                    },
                )
            }
            item {
                // v1.8.0：清除全部档案也统一为玻璃卡片样式（与提供商/设置行一致）
                GlassSurface(
                    onClick = vm::requestWipe,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = GtjShape.md,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = p.danger)
                        Spacer(Modifier.width(12.dp))
                        Text("清除全部档案", style = GtjType.Body, color = p.danger, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = p.danger)
                    }
                }
            }
            item {
                Text(
                    // 版本号读 BuildConfig，随 build.gradle.kts 单一来源，不再硬编码
                    "温言 v${BuildConfig.VERSION_NAME}",
                    style = GtjType.Caption,
                    // 对比度：版本号升到 muted 4.8:1
                    color = p.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item {
                // v1.7.3 T4 手动检查更新（GitHub Releases 直连；失败静默/Toast 由 VM 处理）
                SettingsRow(
                    label = "检查更新",
                    value = if (vm.checkingUpdate) "检查中…" else "v${BuildConfig.VERSION_NAME}",
                    icon = null,
                    onClick = vm::checkUpdate,
                )
            }
        }
    }
    } // Box（GlowBackground + Scaffold）

    pickerTarget?.let { target ->
        val list = if (target == PickerTarget.VISION) models.filter { it.supportsVision } else models
        ModelSheet(
            models = list,
            currentModelId = if (target == PickerTarget.MAIN) currentId else visionId,
            onSelect = { id ->
                if (target == PickerTarget.MAIN) vm.setMainModel(id) else vm.setVisionModel(id)
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null },
            onManageProviders = { pickerTarget = null },
        )
    }

    if (showPrivacy) {
        PrivacyDialog(onDismiss = vm::dismissPrivacy, onAccept = vm::acceptPrivacy)
    }
    if (showWipe) {
        WipeDialog(onDismiss = vm::dismissWipe, onConfirm = { vm.confirmWipe(onBack) })
    }
    // ===== v1.7.2 记忆弹窗 =====
    if (showNameDialog) {
        MemoryNameDialog(onDismiss = vm::dismissCreateTarget, onConfirm = vm::createTarget)
    }
    // v1.7.3 编辑弹窗已移除：档案行「编辑」→ 跳 MemoryEdit 页（MemoryEditDialog 废弃）
    deleteTarget?.let { t ->
        MemoryDeleteDialog(
            targetName = t.name,
            onDismiss = vm::dismissDeleteTarget,
            onConfirm = { vm.deleteTarget(t.id) },
        )
    }
    // v1.7.3 T4 更新确认弹窗
    vm.updateAvailable?.let { info ->
        UpdateDialog(
            info = info,
            downloading = vm.downloading,
            onDownload = { vm.downloadAndInstall(info) },
            onDismiss = vm::dismissUpdateDialog,
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    val p = LocalGtjColors.current
    Text(
        text,
        style = GtjType.Label,
        color = p.muted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    caption: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = LocalGtjColors.current
    // v1.7.0：设置行 = 玻璃材质
    GlassSurface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = GtjShape.md,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (icon != null) {
                // v1.5：图标带 8% 底色圆角容器（设计稿 WY-06，温暖质感细节）
                Surface(
                    shape = com.wenyan.app.ui.theme.GtjShape.sm,
                    color = p.accent.copy(alpha = 0.08f),
                    modifier = Modifier.size(36.dp),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = p.accent)
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = GtjType.Body, color = p.fg)
                if (caption != null) {
                    // 对比度：说明 caption 升到 muted（surface 底 4.5:1 达标）
                    Text(caption, style = GtjType.Caption, color = p.muted)
                }
            }
            // 对比度：值文字在 surface(#F7F8FA) 底上 muted 仅 4.48:1，升到 fgSecondary 9.7:1
            Text(value, style = GtjType.BodySm, color = p.fgSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = p.meta)
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: ProviderInfo,
    onClick: () -> Unit,
) {
    val p = LocalGtjColors.current
    // v1.7.0：提供商行 = 玻璃卡片
    GlassSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = GtjShape.md,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            // v1.6.3 连接状态红绿灯（最左）：ok=绿灯，未测/失败=红灯（保存提供商后自动测试）
            val connected = provider.connectionStatus == "ok"
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (connected) p.success else p.danger, CircleShape)
                    .semantics { contentDescription = if (connected) "${provider.name} 已连接" else "${provider.name} 未连接" },
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(provider.name, style = GtjType.Body, color = p.fg)
                // 对比度：baseUrl 升到 muted 4.8:1
                Text(provider.baseUrl, style = GtjType.Caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val status = when {
                provider.isPreset -> "预设"
                provider.apiKeyConfigured -> "已配置"
                else -> "未配置"
            }
            Tag(text = status, kind = TagKind.NEUTRAL)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = p.meta)
        }
    }
}

/**
 * v1.7.2 记忆档案行（玻璃行）：
 * 左侧激活标识 = accent 实心对勾 / 未激活空心圆；中部名称 + caption；右侧编辑/删除 20dp 图标按钮。
 * 点行主体 = 切换激活档案（Toast 在 VM 内触发）。
 */
@Composable
private fun MemoryTargetRow(
    target: TargetUi,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalGtjColors.current
    GlassSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = GtjShape.md,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (target.isActive) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "使用中",
                    modifier = Modifier.size(20.dp),
                    tint = p.accent,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .border(2.dp, p.muted, CircleShape),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(target.name, style = GtjType.Body, color = p.fg)
                // caption（v1.7.3-fix）：激活 =「使用中」/「使用中 · 已记住 N 条」（N=事实条数，替代已废弃的 note.length）；未激活 =「未使用」
                val caption = when {
                    target.isActive && target.factCount > 0 -> "使用中 · 已记住 ${target.factCount} 条"
                    target.isActive -> "使用中"
                    else -> "未使用"
                }
                Text(caption, style = GtjType.Caption, color = p.muted)
            }
            GtjIconButton(icon = Icons.Outlined.Edit, contentDescription = "编辑记忆", onClick = onEdit, iconSize = 20.dp)
            GtjIconButton(icon = Icons.Outlined.Delete, contentDescription = "删除记忆", onClick = onDelete, tint = p.danger, iconSize = 20.dp)
        }
    }
}

/** v1.7.3 T2 @Preview：档案行（激活态，含事实数 caption） */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFFF6F0E6)
@Composable
private fun MemoryTargetRowPreview() {
    com.wenyan.app.ui.theme.GtjTheme {
        MemoryTargetRow(
            target = TargetUi(id = 1L, name = "小A", note = "", createdAt = 0L, isActive = true, factCount = 3),
            onClick = {},
            onEdit = {},
            onDelete = {},
        )
    }
}

/**
 * v1.7.3 T4 更新确认弹窗：版本说明 + 「去下载」；下载中禁用按钮。
 * 失败静默/Toast 由 VM 处理（不阻塞主流程）。
 */
@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    downloading: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalGtjColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = com.wenyan.app.ui.theme.GtjShape.lg,
        containerColor = p.surfaceElevated,
        titleContentColor = p.fg,
        textContentColor = p.fgSecondary,
        title = { Text("发现新版本 v${info.versionName}", style = GtjType.Title) },
        text = {
            Text(
                info.notes.ifBlank { "修复与体验优化，建议升级。" },
                style = GtjType.BodySm,
                color = p.fgSecondary,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        },
        confirmButton = {
            TextButton(onClick = onDownload, enabled = !downloading) {
                Text(if (downloading) "下载中…" else "去下载", style = GtjType.Label, color = p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !downloading) {
                Text("取消", style = GtjType.Label, color = p.muted)
            }
        },
    )
}
