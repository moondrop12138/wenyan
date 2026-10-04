package com.wenyan.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.components.glass.GtjWindowTheme
import com.wenyan.app.ui.contract.RouteDiagnosticsUi
import com.wenyan.app.ui.contract.UsageMetricsUi
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * O6: 用量/诊断面板。仅展示累计指标，不含任何用户消息原文。
 * 路由成本透明：route 为 null = 老调用兼容（仅展示用量部分）；非 null 时追加路由诊断区。
 */
@Composable
fun UsageMetricsDialog(
    usage: UsageMetricsUi?,
    onDismiss: () -> Unit,
    route: RouteDiagnosticsUi? = null,
) {
    // v1.9.4 独立窗口色相跟随：AlertDialog 是独立 Android 窗口，主窗口全局 hue-rotate 层罩
    // 不到，包内取色/M3 槽位随全局色相旋转（hue=0 原样透传，观感与不包裹逐位一致）
    GtjWindowTheme {
        val p = LocalGtjColors.current
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = GtjShape.lg,
            containerColor = p.surfaceElevated,
            titleContentColor = p.fg,
            textContentColor = p.fgSecondary,
            title = { Text("用量 / 诊断", style = GtjType.Title) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    val u = usage
                    if (u == null) {
                        Text("暂无数据", style = GtjType.BodySm, color = p.muted)
                    } else {
                        MetricRow(p, "请求次数", u.totalRequests.toString())
                        MetricRow(p, "输入 token", u.totalInputTokens.toString())
                        MetricRow(p, "输出 token", u.totalOutputTokens.toString())
                        MetricRow(p, "平均首字延迟", "${u.avgTtftMs} ms")
                        MetricRow(p, "失败次数", u.failures.values.sum().toString())
                        if (u.failures.isNotEmpty()) {
                            Text("失败分类", style = GtjType.Caption, color = p.muted)
                            u.failures.entries.sortedByDescending { it.value }.forEach { (code, count) ->
                                Text("$code：$count", style = GtjType.Caption, color = p.muted)
                            }
                        }
                        val r = route
                        if (r != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "路由调用次数=buildInjection调用次数（本进程，未持久化）",
                                style = GtjType.Caption,
                                color = p.muted,
                            )
                            val totalRoutes = r.countsBySource.values.sum()
                            MetricRow(p, "路由调用次数", totalRoutes.toString())
                            if (r.countsBySource.isNotEmpty()) {
                                r.countsBySource.entries.sortedByDescending { it.value }.forEach { (source, count) ->
                                    Text("route_source=$source：$count", style = GtjType.Caption, color = p.muted)
                                }
                            }
                            val lastAt = r.lastAtMillis?.let {
                                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                    .format(java.util.Date(it))
                            }
                            if (r.lastSource != null) {
                                Text("最近一次来源：${r.lastSource}", style = GtjType.Caption, color = p.muted)
                            }
                            if (r.lastRouted.isNotEmpty()) {
                                Text(
                                    "最近一次候选：${r.lastRouted.joinToString("、")}",
                                    style = GtjType.Caption,
                                    color = p.muted,
                                )
                            }
                            if (r.lastInjected.isNotEmpty()) {
                                Text(
                                    "最近一次注入：${r.lastInjected.joinToString("、")}",
                                    style = GtjType.Caption,
                                    color = p.muted,
                                )
                            }
                            if (lastAt != null) {
                                Text("最近一次时间：$lastAt", style = GtjType.Caption, color = p.muted)
                            }
                            // 注入口径：文档为实际注入文档，与 knowledge_route_hit 同口径
                            Text(
                                "文档为实际注入文档（与 knowledge_route_hit 同口径）",
                                style = GtjType.Caption,
                                color = p.muted,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("关闭", style = GtjType.Label, color = p.accent)
                }
            },
        )
    }
}

@Composable
private fun MetricRow(p: com.wenyan.app.ui.theme.GtjPalette, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = GtjType.BodySm, color = p.muted, modifier = Modifier.weight(1f))
        Text(value, style = GtjType.BodySm, color = p.fgSecondary)
    }
}
