package com.wenyan.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * 引用块（design-tokens component.quoteBlock）：surface 底 + borderSoft 边 + radius-sm + padding-lg。
 * 用于截图转述内容 / 事实拆分条目。editable=true 时切换为多行输入（转述确认卡编辑态）。
 */
@Composable
fun QuoteBlock(
    text: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    onTextChange: ((String) -> Unit)? = null,
    label: String? = null,
) {
    val p = LocalGtjColors.current
    if (editable) {
        TextField(
            value = text,
            onValueChange = { onTextChange?.invoke(it) },
            // 无障碍：编辑态输入框显式 label（design-pages 页面5"编辑 textField 有 label"）
            modifier = modifier
                .fillMaxWidth()
                .semantics { label?.let { contentDescription = it } },
            textStyle = GtjType.Body,
            shape = GtjShape.sm,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = p.surface,
                unfocusedContainerColor = p.surface,
                focusedIndicatorColor = p.accent,
                unfocusedIndicatorColor = p.borderSoft,
                cursorColor = p.accent,
            ),
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = GtjShape.sm,
            color = p.surface,
            border = BorderStroke(1.dp, p.borderSoft),
        ) {
            Box(Modifier.padding(16.dp)) {
                Text(text, style = GtjType.BodySm, color = p.fg)
            }
        }
    }
}
