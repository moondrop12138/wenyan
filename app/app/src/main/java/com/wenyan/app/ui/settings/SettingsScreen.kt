package com.wenyan.app.ui.settings

import com.wenyan.app.BuildConfig
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.components.GtjIconButton
import com.wenyan.app.ui.components.ModelSheet
import com.wenyan.app.ui.components.Tag
import com.wenyan.app.ui.components.TagKind
import com.wenyan.app.ui.components.ThickDivider
import com.wenyan.app.ui.components.glass.GlassSurface
import com.wenyan.app.ui.components.glass.GlowBackground
import com.wenyan.app.ui.components.glass.liquidGlass
import com.wenyan.app.ui.contract.AppContainer
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.navigation.rememberViewModel
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

private enum class PickerTarget { MAIN, VISION }

/**
 * 设置页（/settings，SPEC §7 页面3）：模型服务 / 外观 / 隐私与安全 三分组。
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onEditProvider: (Long) -> Unit,
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
    val p = LocalGtjColors.current
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }

    // v1.7.1：根 Box 加主题背景（防系统深色下 windowBackground 透出导致浅色模式变暗底）
    Box(Modifier.fillMaxSize().background(p.bg)) {
        GlowBackground()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // v1.7.0：顶栏 = 玻璃条（与聊天页顶栏同材质）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = RectangleShape)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                ) {
                    GtjIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", onClick = onBack)
                    Text("设置", style = GtjType.Title, color = p.fg)
                }
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
                items(providers, key = { it.id }) { provider ->
                    ProviderRow(provider = provider, onClick = { onEditProvider(provider.id) })
                }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = p.fgSecondary)
                    Spacer(Modifier.width(12.dp))
                    Text("清除全部档案", style = GtjType.Body, color = p.danger, modifier = Modifier.weight(1f))
                    GtjIconButton(icon = Icons.Outlined.Delete, contentDescription = "清除全部档案", onClick = vm::requestWipe, tint = p.danger, iconSize = 20.dp)
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
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
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
