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
import com.wenyan.app.llm.LlmErrorCode
import com.wenyan.app.ui.components.glass.GlassSurface
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * 错误文案映射（SPEC 5.2 / llm-contract §4，全项目唯一文案源，design-pages 页面7）
 * v1.7.x 修复：code 实为 LlmErrorCode 枚举名（toLlmError 传入 code.name），此前误按 "401"/"404" 数字匹配全部落空，
 * 导致 401/404 也一律显示"模型返回错误"且不出现"去设置检查 API Key"按钮、不可重试错误也显示重试。
 */
private data class ErrorUi(
    val title: String,
    val body: String,
    val hasSettings: Boolean = false,
    val showCancel: Boolean = false,
    val showRetry: Boolean = true,
)

private fun errorUi(code: String, fallback: String): ErrorUi = when (code) {
    LlmErrorCode.UNAUTHORIZED.name ->
        ErrorUi("API Key 无效", "请到设置检查你的 API Key", hasSettings = true, showCancel = true, showRetry = false)
    LlmErrorCode.FORBIDDEN.name -> ErrorUi("服务拒绝访问", "请检查账户状态")
    LlmErrorCode.MODEL_NOT_FOUND.name ->
        ErrorUi("模型不存在", "请检查模型名（可能已退役）", hasSettings = true, showRetry = false)
    LlmErrorCode.RATE_LIMITED.name -> ErrorUi("请求过于频繁或额度已用尽", "稍后重试", showCancel = true)
    LlmErrorCode.SERVER_ERROR.name -> ErrorUi("模型服务异常", "请稍后重试", showCancel = true)
    // F38 精简：删除 v1.1 遗留的裸字符串 "timeout"/"disconnect" 死分支——当前 LlmError.code
    // 全部产出方（toLlmError 传枚举 name / LlmClient IOException 归一 / repo ad-hoc 码）均不会
    // 产出这两个小写字面量
    LlmErrorCode.CONNECT_TIMEOUT.name, LlmErrorCode.READ_TIMEOUT.name ->
        ErrorUi("连接中断", "可重试或停止", showCancel = true)
    // v1.7.1 终检：非 localhost 明文地址被网络安全策略拦截（对应 LlmErrorCode.UNSUPPORTED_URL）
    LlmErrorCode.UNSUPPORTED_URL.name ->
        ErrorUi("地址不受支持", "仅支持 https:// 地址；本地模型服务请填 http://localhost", hasSettings = true, showRetry = false)
    LlmErrorCode.EMPTY_CONTENT.name, LlmErrorCode.PARSE_ERROR.name ->
        ErrorUi("响应异常", "模型未返回可用内容，可重试或更换模型", showCancel = true)
    // 离线弱网发送前预检：repo 短路错误卡——消息已落库，重试 persistUser=false 不重复落库。
    // 与 UI 拦截弹（输入保留、尚未发送）严格区分，不共用一句。
    LlmErrorCode.NO_NETWORK.name ->
        ErrorUi("当前无网络", "你的消息已在列表中，联网后点重试即可", showCancel = true)
    else -> ErrorUi("模型返回错误", fallback, showCancel = true)
}

/** 认证/配置类错误码（图标用 muted 而非 danger） */
private val AUTH_LIKE_CODES = setOf(
    LlmErrorCode.UNAUTHORIZED.name,
    LlmErrorCode.FORBIDDEN.name,
    LlmErrorCode.MODEL_NOT_FOUND.name,
    LlmErrorCode.UNSUPPORTED_URL.name,
)

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
                    tint = if (error.code in AUTH_LIKE_CODES) p.muted else p.danger,
                )
                Spacer(Modifier.width(10.dp))
                Text(ui.title, style = GtjType.Subtitle, color = p.fg)
            }
            Spacer(Modifier.padding(top = 6.dp))
            // v1.9.4 评审修复：卡片正文层由 fgSecondary 改 fg——Mica 对齐把玻璃填充收到 frost .30 后，
            // 浅色 fgSecondary 在本卡（Frost 组）流光最深色之上的卡内实色实测 4.46:1（hue0）/ 全量程
            // 最坏 4.15:1 <AA 4.5；同条件 fg 为 6.95/6.47 达标。token 未动（web 对齐优先），改正文用色。
            Text(ui.body, style = GtjType.BodySm, color = p.fg)
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
