package com.wenyan.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = GtjShape.xlRadius, topEnd = GtjShape.xlRadius),
        containerColor = p.surfaceElevated,
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
            if (models.isEmpty()) {
                EmptyState(
                    title = "没有可用模型",
                    actionText = "去设置添加",
                    onAction = onManageProviders,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    models.groupBy { it.providerName }.forEach { (providerName, list) ->
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
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            },
        shape = GtjShape.md,
        color = if (selected) p.accentSoft else p.surface,
        border = BorderStroke(1.dp, if (selected) p.accent.copy(alpha = 0.4f) else p.borderSoft),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                model.name,
                style = GtjType.Body,
                color = if (selected) p.accent else p.fg,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (model.supportsVision) {
                Tag(text = "看图", kind = TagKind.NEUTRAL, icon = Icons.Outlined.Image)
            }
            if (model.isDefault) {
                Spacer(Modifier.width(6.dp))
                Tag(text = "默认", kind = TagKind.NEUTRAL)
            }
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Outlined.Check, contentDescription = "已选择", modifier = Modifier.size(20.dp), tint = p.accent)
            }
        }
    }
}
