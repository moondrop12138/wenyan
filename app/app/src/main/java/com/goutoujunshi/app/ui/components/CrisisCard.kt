package com.goutoujunshi.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goutoujunshi.app.ui.theme.GtjShape
import com.goutoujunshi.app.ui.theme.GtjType
import com.goutoujunshi.app.ui.theme.LocalGtjColors

/**
 * 危机转介卡（AC-13，design-pages 页面8）。
 * 设计原则：克制冷静明确——无红闪/无大面积警示色/无恋爱话术；shield 图标 muted；
 * 卡片 300ms 淡入（无脉冲/闪烁/抖动），"我知道了"后收起不再主动推送。
 */
@Composable
fun CrisisCard(
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
    safetyMessage: String = "",
) {
    val p = LocalGtjColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GtjShape.md,
        color = p.surfaceElevated,
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(24.dp), tint = p.muted)
                Spacer(Modifier.width(10.dp))
                Text("先处理安全，再处理关系", style = GtjType.Subtitle, color = p.fg)
            }
            if (safetyMessage.isNotBlank()) {
                Text(safetyMessage, style = GtjType.Body, color = p.fg)
            } else {
                Text("你不需要独自面对，也不需要马上做任何决定。先把当下安全放第一。", style = GtjType.Body, color = p.fg)
            }
            QuoteBlock(
                text = buildString {
                    append("1. 确保当下安全：离开可能升级的现场，去人多或熟悉的地方\n")
                    append("2. 联系可信的人：家人、朋友，或当地妇女维权/援助热线\n")
                    append("3. 必要时联系当地紧急服务（110 / 120），保存证据（截图、录音、就医记录）")
                },
            )
            Text("以上为一般安全指引，不替代专业帮助。", style = GtjType.Caption, color = p.muted)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                GhostButton(text = "我知道了", onClick = onAcknowledge, minHeight = 48.dp)
            }
        }
    }
}
