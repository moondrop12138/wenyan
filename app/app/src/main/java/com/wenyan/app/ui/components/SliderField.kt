package com.wenyan.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import kotlin.math.roundToInt

/**
 * 滑块字段（onboarding 主观评分 0-100 step5 / 情绪强度 0-10 step1，design-pages 页面2）：
 * 轨道 borderSoft + 滑块 accent + label 实时显示分值。支持无障碍增减（M3 Slider 内置）。
 */
@Composable
fun SliderField(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 5,
) {
    val p = LocalGtjColors.current
    val stepsCount = ((range.last - range.first) / step).coerceAtLeast(0)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = GtjType.Label, color = p.muted)
            Spacer(Modifier.weight(1f))
            Text(value.toString(), style = GtjType.Subtitle, color = p.accent)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = stepsCount,
            // 无障碍：滑块无内置 label，用 contentDescription 关联字段名（M3 会自动播报当前值）
            modifier = Modifier.semantics { contentDescription = label },
            colors = SliderDefaults.colors(
                thumbColor = p.accent,
                activeTrackColor = p.accent,
                inactiveTrackColor = p.borderSoft,
            ),
        )
        Spacer(Modifier.height(4.dp))
    }
}
