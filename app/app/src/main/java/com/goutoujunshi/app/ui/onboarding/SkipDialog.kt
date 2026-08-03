package com.goutoujunshi.app.ui.onboarding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.goutoujunshi.app.ui.theme.GtjType
import com.goutoujunshi.app.ui.theme.LocalGtjColors

/**
 * 跳过问卷二次确认（AC-02，design-pages 页面2）：
 * 主"继续填写" / 次"跳过，直接开聊"（确认后进对话，档案稍后补录）。
 */
@Composable
fun SkipDialog(
    onContinue: () -> Unit,
    onConfirmSkip: () -> Unit,
) {
    val p = LocalGtjColors.current
    AlertDialog(
        onDismissRequest = onContinue,
        containerColor = p.surfaceElevated,
        titleContentColor = p.fg,
        textContentColor = p.fgSecondary,
        title = { Text("跳过问卷也能开聊", style = GtjType.Title) },
        text = { Text("建议先花两分钟建档，分析会更准。档案稍后可在任何时候补录。", style = GtjType.BodySm) },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text("继续填写", style = GtjType.Label, color = p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onConfirmSkip) {
                Text("跳过，直接开聊", style = GtjType.Label, color = p.muted)
            }
        },
    )
}
