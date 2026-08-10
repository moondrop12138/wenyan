package com.wenyan.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenyan.app.ui.theme.EditorialType
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import java.util.Calendar

/**
 * v1.8.2 空状态重构（editorial 编辑排版风，原型 home-editorial-remix 右版）：
 * - 中文数字日期行（二〇二六年八月十一日 · 夜）+ 陶土棕短规则线（24×3）
 * - 衬线大标题「今天想聊点什么？」
 * - 壹/贰/叁 索引列表（上分隔线，点击填入输入栏）
 * 粘贴/截图入口已由输入栏回形针承接（v1.8.2 起空状态不再重复展示引导卡）。
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 日期行：中文数字 + 时辰（editorial empty-date）
        Text(
            text = formatEditorialDate(),
            style = GtjType.Caption.copy(letterSpacing = 0.14f.sp),
            color = p.muted,
            textAlign = TextAlign.Center,
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
        // 衬线大标题
        Text(
            text = "今天想聊\n点什么？",
            style = EditorialType.Display,
            color = p.fg,
            textAlign = TextAlign.Center,
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

/** 时辰划分（editorial「· 夜」的语感）：0-5 夜 / 5-11 晨 / 11-17 午 / 17-19 夕 / 19-24 夜 */
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
