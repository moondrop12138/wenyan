package com.wenyan.app.ui.onboarding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.wenyan.app.ui.components.glass.GtjWindowTheme
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * 跳过问卷二次确认（AC-02，design-pages 页面2）：
 * 主"继续填写" / 次"跳过，直接开聊"（确认后进对话，档案稍后补录）。
 */
@Composable
fun SkipDialog(
    onContinue: () -> Unit,
    onConfirmSkip: () -> Unit,
    hasModels: Boolean = true,
) {
    // v1.9.4 独立窗口色相跟随：AlertDialog 是独立 Android 窗口，主窗口全局 hue-rotate 层罩
    // 不到，包内取色/M3 槽位随全局色相旋转（hue=0 原样透传，观感与不包裹逐位一致）
    GtjWindowTheme {
        val p = LocalGtjColors.current
        AlertDialog(
            onDismissRequest = onContinue,
            shape = GtjShape.lg,
            containerColor = p.surfaceElevated,
            titleContentColor = p.fg,
            textContentColor = p.fgSecondary,
            title = { Text("跳过问卷也能开聊", style = GtjType.Title) },
            text = { Text(if (hasModels) "建议先花两分钟建档，分析会更准。档案稍后可在任何时候补录。" else "建议先花两分钟建档，分析会更准。档案稍后可在任何时候补录。当前还没有可用模型，跳过后先配置即可开聊。", style = GtjType.BodySm) },
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
}
