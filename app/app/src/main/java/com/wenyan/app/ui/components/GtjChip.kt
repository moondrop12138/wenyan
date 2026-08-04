package com.wenyan.app.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * Chip 体系（design-tokens.json component.chip）。
 * 高度 36dp、pill 圆角；选中态 accentSoft 底 + accent 边 + accent 字（双通道：文字+色）。
 */
@Composable
fun GtjChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val p = LocalGtjColors.current
    Surface(
        onClick = onClick,
        // 无障碍：单选 chips 组以 RadioButton 语义播报（选中态双通道：视觉 accent + 语义 selected）
        modifier = modifier
            .heightIn(min = 36.dp)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            },
        shape = RoundedCornerShape(9999.dp),
        color = if (selected) p.accentSoft else p.surface,
        contentColor = if (selected) p.accent else p.fg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) p.accent else p.border,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = GtjType.Label)
        }
    }
}

/** 空状态示例问题 chip（点击填入输入栏，不直接发送） */
@Composable
fun ExampleChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(9999.dp),
        color = p.surface,
        contentColor = p.fg,
        border = androidx.compose.foundation.BorderStroke(1.dp, p.border),
    ) {
        Text(text, style = GtjType.BodySm, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** 单选 chips 组（onboarding / 筛选复用）。selected 为 null 表示未选（允许留空）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            GtjChip(
                text = option,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

/** 通用占位行间距 */
@Composable
fun ChipSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(8.dp))
}
