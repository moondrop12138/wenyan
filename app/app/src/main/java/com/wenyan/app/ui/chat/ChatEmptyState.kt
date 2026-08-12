package com.wenyan.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import com.wenyan.app.ui.theme.EditorialType
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import java.util.Calendar

/**
 * 空状态（editorial 编辑排版风，原型 home-editorial-remix 右版）：
 * - 中文数字日期行（二〇二六年八月十一日 · 夜）+ 陶土棕短规则线（24×3）
 * - 衬线大标题「今天想聊点什么？」
 * - 壹/贰/叁 索引列表（上分隔线，点击填入输入栏）
 * 粘贴/截图入口已由输入栏回形针承接（v1.8.2 起空状态不再重复展示引导卡）。
 * v1.9.0（2026-08-12）：排版协调——版心（296dp）居中 + 内部左对齐 + 垂直偏上（顶部留白=可用高度 12%）。
 */
private val EMPTY_INDEX = listOf(
    "帮我分析一段聊天记录",
    "这句话该怎么回比较好",
    "我们之间最近有点不对劲",
)

@Composable
fun ChatEmptyState(
    onExampleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    // v1.9.0（2026-08-12）：排版协调（对齐 HTML 稿 empty-state-balance 右版）。
    // 水平：内容收进 296dp 版心并整体居中，内部保持左对齐（editorial 版心惯例），不再贴屏幕左缘；
    // 垂直：由正中改为偏上（顶部留白 = 可用高度 12%，刊头感），不再悬在上下正中间。
    // 父容器（ChatScreen 内容区）为确定高度（fillMaxSize），maxHeight.isFinite 成立；
    // 极端约束下退化为固定 84dp 顶部留白。
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize(),
    ) {
        val topPad = if (maxHeight.isFinite) maxHeight * 0.12f else 84.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPad),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 版心：限宽 296dp 并整体居中；内部保持左对齐
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 296.dp),
            ) {
                // 日期行：中文数字 + 时辰（editorial empty-date，版心内左对齐）
                Text(
                    text = formatEditorialDate(),
                    style = GtjType.Caption.copy(letterSpacing = 0.14f.sp),
                    color = p.muted,
                )
                Spacer(Modifier.height(16.dp))
                // 短规则线（rule-short 24×3 accent）
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(3.dp)
                        .background(p.accent),
                )
                Spacer(Modifier.height(18.dp))
                // 衬线大标题（版心内左对齐）
                Text(
                    text = "今天想聊\n点什么？",
                    style = EditorialType.Display,
                    color = p.fg,
                )
                Spacer(Modifier.height(30.dp))
                // 索引列表（壹/贰/叁，条目上分隔线）
                Column(modifier = Modifier.fillMaxWidth()) {
                    EMPTY_INDEX.forEachIndexed { index, text ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onExampleClick(text) }
                                .padding(horizontal = 4.dp, vertical = 15.dp),
                        ) {
                            Text(
                                text = CN_NUMERALS[index],
                                style = EditorialType.No,
                                color = p.accent,
                                modifier = Modifier.width(28.dp),
                            )
                            Text(
                                text = text,
                                style = GtjType.Body.copy(fontSize = 15.5f.sp, lineHeight = 25f.sp),
                                color = p.fgSecondary,
                            )
                        }
                        if (index < EMPTY_INDEX.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = p.borderSoft,
                                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 中文数字（索引序号用） */
private val CN_NUMERALS = listOf("壹", "贰", "叁")

/** 中文数字字符表：0-9 → 〇一二三四五六七八九 */
private val CN_DIGITS = listOf('〇', '一', '二', '三', '四', '五', '六', '七', '八', '九')

/** 数字 → 中文数字（两位数直接拼读，如 11→十一，26→二十六） */
private fun toCnNumber(n: Int): String {
    require(n in 0..99)
    if (n < 10) return CN_DIGITS[n].toString()
    val tens = n / 10
    val ones = n % 10
    val sb = StringBuilder()
    if (tens > 1) sb.append(CN_DIGITS[tens])
    sb.append('十')
    if (ones > 0) sb.append(CN_DIGITS[ones])
    return sb.toString()
}

/** 时辰划分（editorial「· 夜」的语感，与桌面 editorialDate 一致）：0-4 夜 / 5-10 晨 / 11-16 午 / 17-18 夕 / 19-23 夜 */
private fun timeOfDayLabel(hour: Int): String = when (hour) {
    in 0..4 -> "夜"
    in 5..10 -> "晨"
    in 11..16 -> "午"
    in 17..18 -> "夕"
    else -> "夜"
}

/** 「二〇二六年八月十一日 · 夜」格式（editorial 空状态日期行） */
internal fun formatEditorialDate(): String {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val yearCn = year.toString().map { CN_DIGITS[it - '0'] }.joinToString("")
    return "${yearCn}年${toCnNumber(month)}月${toCnNumber(day)}日 · ${timeOfDayLabel(hour)}"
}
