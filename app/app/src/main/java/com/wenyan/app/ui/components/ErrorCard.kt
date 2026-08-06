package com.wenyan.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.components.glass.GlassSurface
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/** 错误文案映射（SPEC 5.2 / llm-contract §4，全项目唯一文案源，design-pages 页面7） */
private data class ErrorUi(
    val title: String,
    val body: String,
    val hasSettings: Boolean = false,
    val showCancel: Boolean = false,
    val showRetry: Boolean = true,
)

private fun errorUi(code: String, fallback: String): ErrorUi = when {
    code == "401" -> ErrorUi("API Key 无效", "请到设置检查你的 API Key", hasSettings = true, showCancel = true, showRetry = false)
    code == "403" -> ErrorUi("服务拒绝访问", "请检查账户状态")
    code == "404" -> ErrorUi("模型不存在", "请检查模型名（可能已退役）", hasSettings = true, showRetry = false)
    code == "429" -> ErrorUi("请求过于频繁或额度已用尽", "稍后重试", showCancel = true)
    code.startsWith("5") -> ErrorUi("模型服务异常", "请稍后重试", showCancel = true)
    code == "timeout" || code == "disconnect" -> ErrorUi("连接中断", "可重试或停止", showCancel = true)
    else -> ErrorUi("模型返回错误", fallback, showCancel = true)
}

/**
 * AI 气泡内错误卡（design-pages 页面7）：主体 surfaceElevated + border，dangerSoft 仅图标点缀。
 * 原用户消息保留不丢内容；retry 按钮内联 spinner。
 */
@Composable
fun ErrorCard(
    error: LlmError,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onGoSettings: () -> Unit,
    modifier: Modifier = Modifier,
    retrying: Boolean = false,
) {
    val p = LocalGtjColors.current
    val ui = errorUi(error.code, error.message)
    // v1.7.0：错误卡 = 玻璃材质
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = GtjShape.md,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (error.code == "401" || error.code == "403" || error.code == "404") p.muted else p.danger,
                )
                Spacer(Modifier.width(10.dp))
                Text(ui.title, style = GtjType.Subtitle, color = p.fg)
            }
            Spacer(Modifier.padding(top = 6.dp))
            Text(ui.body, style = GtjType.BodySm, color = p.fgSecondary)
            Spacer(Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                if (ui.showCancel) {
                    GhostButton(text = "取消", onClick = onCancel, minHeight = 48.dp)
                }
                if (ui.hasSettings) {
                    SecondaryButton(text = "去设置检查 API Key", onClick = onGoSettings, minHeight = 48.dp)
                } else if (ui.showRetry) {
                    PrimaryButton(text = "重试", onClick = onRetry, loading = retrying, minHeight = 48.dp)
                }
            }
        }
    }
}
