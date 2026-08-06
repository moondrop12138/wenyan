package com.wenyan.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.wenyan.app.ui.components.glass.GlassSurface
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import kotlinx.coroutines.launch

/**
 * 模型选择底部弹层（design-pages 页面4，AC-10）：
 * sheet token（surfaceElevated 顶圆角 xl + dragHandle）、提供商分组、能力徽标、
 * 选中态双通道（accentSoft 底 + check 图标 + accent 字），切换即生效并收起。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    models: List<ModelInfo>,
    currentModelId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    onManageProviders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // v1.7.1-6：液态玻璃 + 真高斯模糊——窗口级 FLAG_BLUR_BEHIND 在部分 ROM 不生效，
    // 兜底直接对 Activity decorView 设 RenderEffect（API 31+，最可靠），关闭时清理。
    // **坑**：dialog window 的 context 是 ContextThemeWrapper 而非 Activity，`as? Activity`
    // 必为 null → 必须沿 ContextWrapper 链 findActivity（v1.7.1-5 因此失效，本次修复）
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val sheetColor = if (canBlur) p.glassFillStrong else p.surfaceElevated
    val view = LocalView.current
    val dialogWindow = remember(view) { (view.parent as? DialogWindowProvider)?.window }
    val activity = remember(view) { view.context.findActivity() }
    SideEffect {
        if (canBlur) {
            if (dialogWindow != null) {
                dialogWindow.setBackgroundBlurRadius(24)
                dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
            // 兜底：整个 activity 内容模糊（窗口模糊不可用时仍生效）
            activity?.window?.decorView?.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(24f, 24f, android.graphics.Shader.TileMode.DECAL),
            )
        }
    }
    DisposableEffect(activity) {
        onDispose {
            // 弹层关闭必须清除，否则主界面持续模糊
            activity?.window?.decorView?.setRenderEffect(null)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // v1.7.0：顶圆角 28 + 半透明玻璃容器（glassFillStrong 透出背后光斑）
        shape = GtjShape.sheetTop,
        containerColor = sheetColor,
        dragHandle = { Surface(color = p.borderSoft, modifier = Modifier.size(width = 36.dp, height = 4.dp), shape = GtjShape.pill) {} },
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("选择模型", style = GtjType.Title, color = p.fg, modifier = Modifier.weight(1f))
                GtjIconButton(icon = Icons.Outlined.Close, contentDescription = "关闭", onClick = onDismiss, tint = p.muted)
            }
            val current = models.firstOrNull { it.id == currentModelId }
            if (current != null) {
                Text("当前：${current.name}", style = GtjType.Caption, color = p.muted)
            }
            Spacer(Modifier.height(8.dp))
            // v1.6.3 只展示模型管理里"可见"的模型（showInSheet 开关控制）
            val visible = models.filter { it.showInSheet }
            if (visible.isEmpty()) {
                EmptyState(
                    title = "没有可用模型",
                    actionText = "去设置添加",
                    onAction = onManageProviders,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    visible.groupBy { it.providerName }.forEach { (providerName, list) ->
                        item(key = "header_$providerName") {
                            Text(providerName, style = GtjType.Label, color = p.muted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        }
                        items(list, key = { "model_${it.id}" }) { model ->
                            ModelRow(
                                model = model,
                                selected = model.id == currentModelId,
                                onClick = {
                                    onSelect(model.id)
                                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                                },
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                GhostButton(text = "管理模型服务", onClick = onManageProviders, minHeight = 48.dp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val p = LocalGtjColors.current
    // v1.7.0：模型行 = 玻璃（未选中）；选中态保持 accentSoft 强调
    if (selected) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                },
            shape = GtjShape.lg,
            color = p.accentSoft,
            border = BorderStroke(1.5.dp, p.accent),
        ) {
            ModelRowContent(model, selected, p)
        }
    } else {
        GlassSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                },
            shape = GtjShape.lg,
        ) {
            ModelRowContent(model, selected, p)
        }
    }
}

@Composable
private fun ModelRowContent(
    model: ModelInfo,
    selected: Boolean,
    p: com.wenyan.app.ui.theme.GtjPalette,
) {
    // v1.5：图标缩写（前两位字母，如 DS / GPT），40dp 陶土棕底 r12 容器（设计稿 WY-05）
    val abbrev = model.name.take(2).uppercase()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // v1.7.1 二改：fillMaxSize 让 72dp 最小行高内内容垂直居中（此前内容贴顶）
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            // 模型图标容器：40dp r12，选中陶土棕实底 / 未选中灰
            Surface(
                shape = GtjShape.md,
                color = if (selected) p.accent else p.borderSoft,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        abbrev,
                        style = GtjType.Label.copy(fontSize = 13.sp),
                        color = if (selected) p.accentOn else p.muted,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = GtjType.Body,
                    color = p.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // 描述行：提供商 + 能力徽标（v1.5 副标题）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.providerName, style = GtjType.Caption, color = p.muted, maxLines = 1)
                    if (model.supportsVision) {
                        Spacer(Modifier.width(6.dp))
                        Tag(text = "视觉", kind = TagKind.NEUTRAL, icon = Icons.Outlined.Image)
                    }
                }
            }
            if (model.isDefault) {
                Spacer(Modifier.width(6.dp))
                Tag(text = "默认", kind = TagKind.NEUTRAL)
            }
            // v1.5：选中态——24dp 陶土棕实心圆 + 白对勾（设计稿 WY-05 选中标记）
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Surface(shape = CircleShape, color = p.accent, modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, contentDescription = "已选择", modifier = Modifier.size(14.dp), tint = p.accentOn)
                    }
                }
            }
        }
}

/** v1.7.1-6：沿 ContextWrapper 链向上找 Activity（Dialog 的 context 是 ContextThemeWrapper）。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
