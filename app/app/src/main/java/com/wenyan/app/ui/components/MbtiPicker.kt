package com.wenyan.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * MBTI 四组二选一分段（onboarding 屏1/屏2，design-pages 页面2）。
 * 用 chips 选中态（accentSoft 底 + accent 边 + accent 字），图形+文字双通道表达选中。
 * selected 为 null 表示该组未选（允许"不知道"留空）。
 */
@Composable
fun MbtiPicker(
    value: String?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showUnknown: Boolean = false,
) {
    val p = LocalGtjColors.current
    val groups = listOf(
        "E" to "I",
        "S" to "N",
        "T" to "F",
        "J" to "P",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        groups.forEach { (a, b) ->
            val selectedA = value?.contains(a) == true
            val selectedB = value?.contains(b) == true
            Column {
                ChoiceChips(
                    options = listOf("$a · ${dimensionName(a)}", "$b · ${dimensionName(b)}"),
                    selected = when {
                        selectedA -> "$a · ${dimensionName(a)}"
                        selectedB -> "$b · ${dimensionName(b)}"
                        else -> null
                    },
                    onSelect = { label ->
                        val letter = label.substringBefore(" · ")
                        onChange(buildMbti(value, letter))
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (showUnknown) {
            // 对比度：meta 浅色白底仅 2.5:1，引导性文案升到 muted 4.8:1
            Text("不知道可以留空", style = GtjType.Caption, color = p.muted)
        }
    }
}

private fun dimensionName(letter: String): String = when (letter) {
    "E" -> "外倾"; "I" -> "内倾"; "S" -> "实感"; "N" -> "直觉"
    "T" -> "思考"; "F" -> "情感"; "J" -> "计划"; "P" -> "随性"
    else -> ""
}

private fun buildMbti(current: String?, newLetter: String): String {
    val map = mutableMapOf<Char, Char>()
    current?.forEach { c -> map[c] = c }
    val pair = when (newLetter) {
        "E" -> 'E' to 'I'; "I" -> 'I' to 'E'; "S" -> 'S' to 'N'; "N" -> 'N' to 'S'
        "T" -> 'T' to 'F'; "F" -> 'F' to 'T'; "J" -> 'J' to 'P'; "P" -> 'P' to 'J'
        else -> return current.orEmpty()
    }
    map.remove(pair.second)
    map[pair.first] = pair.first
    return "EISNTFJP".filter { it in map.keys }
}
